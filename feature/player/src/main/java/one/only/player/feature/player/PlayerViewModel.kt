package one.only.player.feature.player

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.round
import one.only.player.core.data.repository.ExternalSubtitleFontSource
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PlaybackMarkRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.SubtitleFontRepository
import one.only.player.core.data.repository.buildRemotePlaybackStateKey
import one.only.player.core.domain.GetSortedPlaylistUseCase
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.LastPlayerScreenOrientation
import one.only.player.core.model.LoopMode
import one.only.player.core.model.PlaybackMark
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlsLayout
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.model.VideoContentScale
import one.only.player.core.model.withSubtitleStyleFrom
import one.only.player.core.model.withVideoFilterAdjustment
import one.only.player.core.model.withVideoFiltersFrom
import one.only.player.core.model.withVideoSharpening
import one.only.player.feature.player.extensions.remoteFilePath
import one.only.player.feature.player.extensions.remoteProtocol
import one.only.player.feature.player.extensions.remoteServerId
import one.only.player.feature.player.state.SubtitleOptionsEvent
import one.only.player.feature.player.state.VideoZoomEvent

private fun Float.normalizeVideoFilter(
    minimumValue: Float,
    maximumValue: Float,
    decimals: Int = 2,
): Float = coerceIn(minimumValue, maximumValue).round(decimals)

internal fun normalizeVideoSharpening(value: Float): Float = value
    .normalizeVideoFilter(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackMarkRepository: PlaybackMarkRepository,
    private val preferencesRepository: PreferencesRepository,
    private val subtitleFontRepository: SubtitleFontRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
) : ViewModel() {

    private companion object {
        const val TAG = "PlayerViewModel"
    }

    var shouldPlayWhenReady: Boolean = true

    private val internalUiState = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
            applicationPreferences = preferencesRepository.applicationPreferences.value,
            shouldPreventScreenshots = preferencesRepository.applicationPreferences.value.shouldPreventScreenshots,
            shouldHideInRecents = preferencesRepository.applicationPreferences.value.shouldHideInRecents,
        ),
    )
    val uiState = internalUiState.asStateFlow()
    private val playbackMarkMediaUri = MutableStateFlow<String?>(null)
    private var playbackMarkMediaUriRequestId = 0L
    val playbackMarks = playbackMarkMediaUri
        .flatMapLatest { mediaUri ->
            if (mediaUri == null) {
                flowOf(emptyList())
            } else {
                playbackMarkRepository.observeByMediaUri(mediaUri)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                internalUiState.update { it.copy(playerPreferences = prefs) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                internalUiState.update {
                    it.copy(
                        applicationPreferences = prefs,
                        shouldPreventScreenshots = prefs.shouldPreventScreenshots,
                        shouldHideInRecents = prefs.shouldHideInRecents,
                    )
                }
            }
        }
        viewModelScope.launch {
            subtitleFontRepository.source.collect { source ->
                internalUiState.update { it.copy(externalSubtitleFontSource = source) }
            }
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> = getSortedPlaylistUseCase.invoke(uri)

    suspend fun getVideoByUri(uri: String): Video? = mediaRepository.getVideoByUri(uri)

    fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun updatePlayerVolume(percentage: Int) {
        viewModelScope.launch {
            val clampedPercentage = percentage.coerceIn(
                minimumValue = 0,
                maximumValue = PlayerPreferences.MAX_PLAYER_VOLUME_PERCENTAGE,
            )
            Logger.debug(TAG, "Remember player volume: percentage=$clampedPercentage")
            preferencesRepository.updatePlayerPreferences {
                it.copy(playerVolumePercentage = clampedPercentage)
            }
        }
    }

    fun updateLastPlayerScreenOrientation(value: LastPlayerScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { preferences ->
                if (!preferences.shouldRememberPlayerScreenOrientation) return@updatePlayerPreferences preferences
                preferences.copy(lastPlayerScreenOrientation = value)
            }
        }
    }

    fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    fun updateDecoderPriority(decoderPriority: DecoderPriority) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(decoderPriority = decoderPriority)
            }
        }
    }

    fun updateVideoBrightness(value: Float) {
        val normalizedValue = value.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS)
        updateVideoFilter("brightness=$normalizedValue") { it.copy(videoBrightness = normalizedValue) }
    }

    fun updateVideoFilters(preferences: PlayerPreferences) {
        val normalizedPreferences = preferences.normalizedVideoFilters()
        Logger.debug(TAG, "Update video filter from player: confirmed=$normalizedPreferences")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withVideoFiltersFrom(normalizedPreferences)
            }
        }
    }

    fun updateVideoContrast(value: Float) {
        val normalizedValue = value.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST)
        updateVideoFilter("contrast=$normalizedValue") { it.copy(videoContrast = normalizedValue) }
    }

    fun updateVideoSaturation(value: Float) {
        val normalizedValue = value.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION, decimals = 0)
        updateVideoFilter("saturation=$normalizedValue") { it.copy(videoSaturation = normalizedValue) }
    }

    fun updateVideoHue(value: Float) {
        val normalizedValue = value.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE, decimals = 0)
        updateVideoFilter("hue=$normalizedValue") { it.copy(videoHue = normalizedValue) }
    }

    fun updateVideoGamma(value: Float) {
        val normalizedValue = value.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA)
        updateVideoFilter("gamma=$normalizedValue") { it.copy(videoGamma = normalizedValue) }
    }

    fun updateVideoSharpening(value: Float) {
        val normalizedValue = normalizeVideoSharpening(value)
        updateVideoFilter("sharpening=$normalizedValue") { it.withVideoSharpening(normalizedValue) }
    }

    private fun PlayerPreferences.normalizedVideoFilters(): PlayerPreferences = copy(
        videoBrightness = videoBrightness.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS),
        videoContrast = videoContrast.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST),
        videoSaturation = videoSaturation.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION, decimals = 0),
        videoHue = videoHue.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE, decimals = 0),
        videoGamma = videoGamma.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA),
        videoSharpening = normalizeVideoSharpening(videoSharpening),
    )

    private fun updateVideoFilter(
        debugValue: String,
        transform: (PlayerPreferences) -> PlayerPreferences,
    ) {
        Logger.debug(TAG, "Update video filter from player: $debugValue")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { preferences ->
                preferences.withVideoFilterAdjustment(transform)
            }
        }
    }

    fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    fun updateSubtitleStyle(preferences: PlayerPreferences) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withSubtitleStyleFrom(preferences)
            }
        }
    }

    fun updatePlayerControlsCustomization(
        hiddenControls: Set<PlayerControl>,
        layout: PlayerControlsLayout,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    hiddenPlayerControls = hiddenControls,
                    playerControlsLayout = layout,
                )
            }
        }
    }

    fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.toPlaybackStateUri(), event.zoom)
            }
        }
    }

    fun onSubtitleOptionEvent(event: SubtitleOptionsEvent) {
        when (event) {
            is SubtitleOptionsEvent.DelayChanged -> {
                updateSubtitleDelay(event.mediaItem.toPlaybackStateUri(), event.delay)
            }
            is SubtitleOptionsEvent.SpeedChanged -> {
                updateSubtitleSpeed(event.mediaItem.toPlaybackStateUri(), event.speed)
            }
        }
    }

    private fun updateSubtitleDelay(uri: String, delay: Long) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleDelay(uri, delay)
        }
    }

    private fun updateSubtitleSpeed(uri: String, speed: Float) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleSpeed(uri, speed)
        }
    }

    fun updatePlaybackMarkMediaItem(mediaItem: MediaItem?) {
        val requestId = ++playbackMarkMediaUriRequestId
        if (mediaItem == null) {
            playbackMarkMediaUri.value = null
            return
        }

        viewModelScope.launch {
            val mediaUri = mediaItem.toPlaybackMarkMediaUri()
            if (requestId == playbackMarkMediaUriRequestId) {
                playbackMarkMediaUri.value = mediaUri
            }
        }
    }

    fun addPlaybackMark(
        mediaItem: MediaItem?,
        positionMs: Long,
        durationMs: Long,
    ) {
        viewModelScope.launch {
            addPlaybackMarkNow(
                mediaItem = mediaItem,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        }
    }

    suspend fun addPlaybackMarkNow(
        mediaItem: MediaItem?,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (mediaItem == null) return false
        val mediaUri = mediaItem.toPlaybackMarkMediaUri()
        playbackMarkRepository.add(
            PlaybackMark(
                mediaUri = mediaUri,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
            ),
        )
        return true
    }

    fun deletePlaybackMark(id: Long) {
        viewModelScope.launch {
            deletePlaybackMarkNow(id)
        }
    }

    suspend fun deletePlaybackMarkNow(id: Long) {
        playbackMarkRepository.deleteById(id)
    }

    fun resolvePlaybackStateUri(mediaItem: MediaItem): String = mediaItem.toPlaybackStateUri()

    private suspend fun MediaItem.toPlaybackMarkMediaUri(): String = buildRemotePlaybackStateKey(
        remoteProtocol = mediaMetadata.remoteProtocol,
        remoteServerId = mediaMetadata.remoteServerId,
        remoteFilePath = mediaMetadata.remoteFilePath,
    ) ?: mediaRepository.getCanonicalMediaUri(mediaId)

    private fun MediaItem.toPlaybackStateUri(): String = buildRemotePlaybackStateKey(
        remoteProtocol = mediaMetadata.remoteProtocol,
        remoteServerId = mediaMetadata.remoteServerId,
        remoteFilePath = mediaMetadata.remoteFilePath,
    ) ?: mediaId
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
    val applicationPreferences: ApplicationPreferences = ApplicationPreferences(),
    val shouldPreventScreenshots: Boolean = false,
    val shouldHideInRecents: Boolean = false,
    val externalSubtitleFontSource: ExternalSubtitleFontSource? = null,
)

sealed interface PlayerEvent
