package one.only.player.feature.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
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
import one.only.player.core.domain.DownloadOnlineSubtitleUseCase
import one.only.player.core.domain.GetSortedPlaylistUseCase
import one.only.player.core.domain.SearchOnlineSubtitlesUseCase
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.AudioEqualizerBand
import one.only.player.core.model.AudioEqualizerBuiltInPreset
import one.only.player.core.model.AudioEqualizerPreset
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.LastPlayerScreenOrientation
import one.only.player.core.model.LoopMode
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleMatchHint
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.model.OnlineSubtitleResult
import one.only.player.core.model.PlaybackMark
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.model.VideoContentScale
import one.only.player.core.model.VideoFilterPreset
import one.only.player.core.model.toAudioEqualizerPreset
import one.only.player.core.model.toVideoFilterPreset
import one.only.player.core.model.withAudioEqualizerBandLevel
import one.only.player.core.model.withAudioEqualizerBuiltInPresetApplied
import one.only.player.core.model.withAudioEqualizerPresetApplied
import one.only.player.core.model.withAudioEqualizerPresetDeleted
import one.only.player.core.model.withAudioEqualizerPresetSaved
import one.only.player.core.model.withSubtitleStyleFrom
import one.only.player.core.model.withVideoFilterPresetApplied
import one.only.player.core.model.withVideoFilterPresetDeleted
import one.only.player.core.model.withVideoFilterPresetSaved
import one.only.player.core.model.withVideoFiltersFrom
import one.only.player.core.ui.base.DataState
import one.only.player.feature.player.extensions.remoteFilePath
import one.only.player.feature.player.extensions.remoteProtocol
import one.only.player.feature.player.extensions.remoteServerId
import one.only.player.feature.player.extensions.toReleaseName
import one.only.player.feature.player.extensions.toSearchQuery
import one.only.player.feature.player.state.OnlineSubtitleEvent
import one.only.player.feature.player.state.OnlineSubtitleSearchUiState
import one.only.player.feature.player.state.VideoZoomEvent
import one.only.player.feature.player.subtitle.OnlineSubtitleRepository

private fun Float.normalizeVideoFilter(
    minimumValue: Float,
    maximumValue: Float,
    decimals: Int = 2,
): Float = coerceIn(minimumValue, maximumValue).round(decimals)

internal fun normalizeVideoSharpening(value: Float): Float = value
    .normalizeVideoFilter(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING)

private data class AmbienceFrame(
    val mediaKey: String,
    val bitmap: Bitmap,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackMarkRepository: PlaybackMarkRepository,
    private val preferencesRepository: PreferencesRepository,
    private val subtitleFontRepository: SubtitleFontRepository,
    private val onlineSubtitleRepository: OnlineSubtitleRepository,
    private val searchOnlineSubtitlesUseCase: SearchOnlineSubtitlesUseCase,
    private val downloadOnlineSubtitleUseCase: DownloadOnlineSubtitleUseCase,
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

    private val internalOnlineSubtitleSearch = MutableStateFlow(
        OnlineSubtitleSearchUiState(preferences = preferencesRepository.playerPreferences.value.onlineSubtitleSearchPreferences),
    )
    val onlineSubtitleSearch = internalOnlineSubtitleSearch.asStateFlow()

    // 下载与挂载分居数据层与播放服务，只推送一次性事件，避免重复挂载
    private val internalOnlineSubtitleEvents = Channel<OnlineSubtitleEvent>(Channel.BUFFERED)
    val onlineSubtitleEvents = internalOnlineSubtitleEvents.receiveAsFlow()
    private var subtitleSearchJob: Job? = null
    private var subtitleDownloadJob: Job? = null
    private var subtitleTrackSyncJob: Job? = null
    private var subtitleMediaId: String? = null
    private var subtitleReleaseName: String = ""
    private var hasEditedSubtitleQuery = false
    private val playbackMarkMediaUri = MutableStateFlow<String?>(null)

    // 仅保留当前媒体有效帧，供界面重建后继续显示。
    private var ambienceFrame: AmbienceFrame? = null
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
                if (!prefs.isAmbienceModeEnabled) ambienceFrame = null
                internalUiState.update { it.copy(playerPreferences = prefs) }
                if (internalOnlineSubtitleSearch.value.preferences != prefs.onlineSubtitleSearchPreferences) {
                    subtitleSearchJob?.cancel()
                    internalOnlineSubtitleSearch.update {
                        it.copy(preferences = prefs.onlineSubtitleSearchPreferences, outcome = null)
                    }
                }
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

    fun updateAmbienceModeEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(isAmbienceModeEnabled = isEnabled) }
        }
    }

    fun ambienceFrameFor(mediaKey: String?): Bitmap? = ambienceFrame
        ?.takeIf { it.mediaKey == mediaKey }
        ?.bitmap

    // 新媒体首帧到位前用于避免背景黑闪。
    fun latestAmbienceFrame(): Bitmap? = ambienceFrame?.bitmap

    fun updateAmbienceFrame(
        mediaKey: String?,
        bitmap: Bitmap,
    ) {
        if (mediaKey == null) return
        ambienceFrame = AmbienceFrame(mediaKey = mediaKey, bitmap = bitmap)
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

    fun updateVideoFilters(preferences: PlayerPreferences) {
        val normalizedPreferences = preferences.normalizedVideoFilters()
        Logger.debug(TAG, "Update video filter from player: confirmed=$normalizedPreferences")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withVideoFiltersFrom(normalizedPreferences)
            }
        }
    }

    fun applyVideoFilterPreset(preset: VideoFilterPreset) {
        Logger.debug(TAG, "Apply video filter preset from player: name=${preset.name}")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withVideoFilterPresetApplied(preset)
            }
        }
    }

    fun saveVideoFilterPreset(name: String) {
        Logger.debug(TAG, "Save video filter preset from player: name=$name")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withVideoFilterPresetSaved(it.toVideoFilterPreset(name, System.currentTimeMillis()))
            }
        }
    }

    fun deleteVideoFilterPreset(preset: VideoFilterPreset) {
        Logger.debug(TAG, "Delete video filter preset from player: name=${preset.name}")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withVideoFilterPresetDeleted(preset)
            }
        }
    }

    fun setAudioEqualizerEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldApplyAudioEqualizer = isEnabled)
            }
        }
    }

    fun updateAudioEqualizerBand(
        band: AudioEqualizerBand,
        levelDb: Int,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                if (!it.shouldApplyAudioEqualizer) return@updatePlayerPreferences it
                it.withAudioEqualizerBandLevel(band, levelDb)
            }
        }
    }

    fun applyAudioEqualizerBuiltInPreset(preset: AudioEqualizerBuiltInPreset) {
        Logger.debug(TAG, "Apply built-in equalizer preset from player: preset=$preset")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withAudioEqualizerBuiltInPresetApplied(preset)
            }
        }
    }

    fun applyAudioEqualizerPreset(preset: AudioEqualizerPreset) {
        Logger.debug(TAG, "Apply equalizer preset from player: name=${preset.name}")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withAudioEqualizerPresetApplied(preset)
            }
        }
    }

    fun saveAudioEqualizerPreset(name: String) {
        Logger.debug(TAG, "Save equalizer preset from player: name=$name")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withAudioEqualizerPresetSaved(it.toAudioEqualizerPreset(name, System.currentTimeMillis()))
            }
        }
    }

    fun deleteAudioEqualizerPreset(preset: AudioEqualizerPreset) {
        Logger.debug(TAG, "Delete equalizer preset from player: name=${preset.name}")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withAudioEqualizerPresetDeleted(preset)
            }
        }
    }

    private fun PlayerPreferences.normalizedVideoFilters(): PlayerPreferences = copy(
        videoBrightness = videoBrightness.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS),
        videoContrast = videoContrast.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST),
        videoSaturation = videoSaturation.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION, decimals = 0),
        videoHue = videoHue.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE, decimals = 0),
        videoGamma = videoGamma.normalizeVideoFilter(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA),
        videoSharpening = normalizeVideoSharpening(videoSharpening),
    )

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

    fun updateOnlineSubtitleMediaItem(
        mediaItem: MediaItem?,
        title: String?,
    ) {
        val mediaId = mediaItem?.mediaId
        val query = mediaItem.toSearchQuery(title)
        subtitleReleaseName = mediaItem.toReleaseName(title)
        if (subtitleMediaId == mediaId) {
            if (hasEditedSubtitleQuery || query == internalOnlineSubtitleSearch.value.query) return
            subtitleSearchJob?.cancel()
            internalOnlineSubtitleSearch.update { it.copy(query = query, outcome = null) }
            return
        }
        subtitleMediaId = mediaId
        hasEditedSubtitleQuery = false
        subtitleSearchJob?.cancel()
        subtitleDownloadJob?.cancel()
        internalOnlineSubtitleSearch.update {
            OnlineSubtitleSearchUiState(
                query = query,
                preferences = it.preferences,
            )
        }
    }

    // 已挂载字幕的来源编号存在各自的元数据里，逐个读回才能标记搜索结果的已添加状态
    fun updateAddedOnlineSubtitles(
        addedSubtitleIds: List<String>,
        selectedSubtitleId: String?,
    ) {
        subtitleTrackSyncJob?.cancel()
        if (addedSubtitleIds.isEmpty()) {
            internalOnlineSubtitleSearch.update { it.copy(addedKeys = emptySet(), selectedKey = null) }
            return
        }
        subtitleTrackSyncJob = viewModelScope.launch {
            val keysById = addedSubtitleIds.associateWith { id ->
                onlineSubtitleRepository.getMetadata(Uri.parse(id))?.searchResultKey
            }
            internalOnlineSubtitleSearch.update {
                it.copy(
                    addedKeys = keysById.values.filterNotNull().toSet(),
                    selectedKey = selectedSubtitleId?.let(keysById::get),
                )
            }
        }
    }

    fun onOnlineSubtitleQueryChange(query: String) {
        hasEditedSubtitleQuery = true
        subtitleSearchJob?.cancel()
        internalOnlineSubtitleSearch.update { it.copy(query = query, outcome = null) }
    }

    fun onOnlineSubtitleLanguageFilterChange(languageFilter: OnlineSubtitleLanguageFilter) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(onlineSubtitleSearchPreferences = it.onlineSubtitleSearchPreferences.copy(languageFilter = languageFilter))
            }
        }
    }

    fun onOnlineSubtitleProviderToggle(provider: OnlineSubtitleProvider) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(onlineSubtitleSearchPreferences = it.onlineSubtitleSearchPreferences.withProviderToggled(provider))
            }
        }
    }

    fun onSearchOnlineSubtitles() {
        if (subtitleMediaId == null) return
        val state = internalOnlineSubtitleSearch.value
        if (state.isSearching || state.query.isBlank()) return

        val preferredSubtitleLanguage = preferencesRepository.playerPreferences.value.preferredSubtitleLanguage
        internalOnlineSubtitleSearch.update { it.copy(outcome = DataState.Loading) }
        subtitleSearchJob = viewModelScope.launch {
            try {
                searchOnlineSubtitlesUseCase(
                    query = state.query,
                    languageCode = state.preferences.resolveLanguageCode(preferredSubtitleLanguage),
                    providers = state.preferences.providers,
                    matchHint = OnlineSubtitleMatchHint.from(
                        releaseName = subtitleReleaseName.ifEmpty { state.query },
                        preferredSubtitleLanguage = preferredSubtitleLanguage,
                    ),
                ).collect { result ->
                    internalOnlineSubtitleSearch.update { it.copy(outcome = DataState.Success(result)) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Logger.error(TAG, "Online subtitle search failed", exception)
                internalOnlineSubtitleSearch.update { it.copy(outcome = DataState.Error(exception)) }
            }
        }
    }

    fun onDownloadOnlineSubtitle(result: OnlineSubtitleResult) {
        val mediaId = subtitleMediaId ?: return
        if (internalOnlineSubtitleSearch.value.downloadingKey != null) return
        internalOnlineSubtitleSearch.update { it.copy(downloadingKey = result.key) }
        subtitleDownloadJob = viewModelScope.launch {
            try {
                val payload = downloadOnlineSubtitleUseCase(result)
                val subtitle = onlineSubtitleRepository.importSubtitle(payload.bytes, payload.extension, result)
                internalOnlineSubtitleEvents.send(OnlineSubtitleEvent.Saved(subtitle.uri, mediaId))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Logger.error(TAG, "Online subtitle download failed", exception)
                internalOnlineSubtitleEvents.send(OnlineSubtitleEvent.Failed(exception, mediaId))
            } finally {
                if (subtitleMediaId == mediaId) {
                    internalOnlineSubtitleSearch.update { it.copy(downloadingKey = null) }
                }
            }
        }
    }

    // 取消后 finally 里的状态清理仍会执行，界面据此收起取消入口
    fun onCancelOnlineSubtitleDownload() {
        if (internalOnlineSubtitleSearch.value.downloadingKey == null) return
        subtitleDownloadJob?.cancel()
        internalOnlineSubtitleSearch.update { it.copy(downloadingKey = null) }
    }

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
