package one.only.player.feature.player.state

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.feature.player.extensions.diagnostics
import one.only.player.feature.player.extensions.formatted
import one.only.player.feature.player.extensions.hasRenderedFirstFrame
import one.only.player.feature.player.extensions.toLogString

@UnstableApi
@Composable
fun rememberMediaPresentationState(player: Player): MediaPresentationState {
    val mediaPresentationState = remember { MediaPresentationState(player) }
    LaunchedEffect(player) { mediaPresentationState.observe() }
    return mediaPresentationState
}

@Stable
class MediaPresentationState(
    private val player: Player,
    @param:IntRange(from = 0) private val tickIntervalMs: Long = 500,
) {
    private var pauseDiagnosticsJob: Job? = null
    private var bufferingStartedAt = 0L
    private var stallCount = 0
    private var totalStallDurationMs = 0L
    private var hasReachedReady = false
    private var isCurrentBufferingStall = false

    var position: Long by mutableLongStateOf(0L)
        private set

    var duration: Long by mutableLongStateOf(0L)
        private set

    var bufferedPosition: Long by mutableLongStateOf(0L)
        private set

    var remainingBufferedDuration: Long by mutableLongStateOf(0L)
        private set

    var bufferedPercentage: Int by mutableStateOf(0)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    var isLoading: Boolean by mutableStateOf(true)
        private set

    var isBuffering: Boolean by mutableStateOf(false)
        private set

    var hasRenderedFirstFrame: Boolean by mutableStateOf(false)
        private set

    suspend fun observe() {
        updatePosition()
        updateDuration()
        updateBuffering()
        isPlaying = player.isPlaying
        isLoading = player.isLoading
        hasRenderedFirstFrame = player.mediaMetadata.hasRenderedFirstFrame

        coroutineScope {
            val diagnosticsScope = this
            launch {
                player.listen { events ->
                    if (events.containsAny(
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_MEDIA_METADATA_CHANGED,
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                        )
                    ) {
                        updateDuration()
                    }

                    if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                        updateBuffering()
                        logPlaybackDiagnostics("playbackState")
                    }

                    if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                        val hasMetadataRenderedFirstFrame = player.mediaMetadata.hasRenderedFirstFrame
                        if (this@MediaPresentationState.hasRenderedFirstFrame != hasMetadataRenderedFirstFrame) {
                            this@MediaPresentationState.hasRenderedFirstFrame = hasMetadataRenderedFirstFrame
                            logPlaybackDiagnostics("mediaMetadataChanged")
                        }
                    }

                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        bufferingStartedAt = 0L
                        stallCount = 0
                        totalStallDurationMs = 0L
                        hasReachedReady = false
                        isCurrentBufferingStall = false
                        this@MediaPresentationState.hasRenderedFirstFrame = player.mediaMetadata.hasRenderedFirstFrame
                        logPlaybackDiagnostics("mediaItemTransition")
                    }

                    if (events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) {
                        this@MediaPresentationState.hasRenderedFirstFrame = true
                        logPlaybackDiagnostics("renderedFirstFrame")
                    }

                    if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                        logVideoSize(player.videoSize)
                    }

                    if (events.contains(Player.EVENT_PLAYER_ERROR)) {
                        logPlayerError(player.playerError)
                    }

                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                        this@MediaPresentationState.isPlaying = player.isPlaying
                        logPlaybackDiagnostics("isPlayingChanged")
                        schedulePauseDiagnostics(diagnosticsScope)
                    }

                    if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                        updatePosition()
                        logPlaybackDiagnostics("positionDiscontinuity")
                    }

                    if (events.containsAny(Player.EVENT_IS_LOADING_CHANGED)) {
                        this@MediaPresentationState.isLoading = player.isLoading
                        updateBufferedValues()
                        logPlaybackDiagnostics("loadingChanged")
                    }
                }
            }

            while (true) {
                delay(tickIntervalMs)
                if (player.isPlaying) {
                    updatePosition()
                }
                updateBufferedValues()
                if (duration == 0L) {
                    updateDuration()
                }
            }
        }
    }

    fun onSeekCommitted(positionMs: Long) {
        position = positionMs.coerceAtLeast(0L)
    }

    private fun updatePosition() {
        position = player.currentPosition.coerceAtLeast(0L)
    }

    private fun updateDuration() {
        duration = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: player.mediaMetadata.durationMs?.takeIf { it > 0L }
            ?: 0L
    }

    private fun updateBufferedValues() {
        val currentPosition = player.currentPosition
            .takeIf { it != C.TIME_UNSET && it >= 0L }
            ?: 0L
        bufferedPosition = player.bufferedPosition
            .takeIf { it != C.TIME_UNSET && it >= 0L }
            ?: 0L
        remainingBufferedDuration = (bufferedPosition - currentPosition).coerceAtLeast(0L)
        bufferedPercentage = player.bufferedPercentage
    }

    private fun updateBuffering() {
        val wasBuffering = isBuffering
        val isBufferingNow = player.playbackState == Player.STATE_BUFFERING
        if (isBufferingNow && !wasBuffering) {
            bufferingStartedAt = System.currentTimeMillis()
            isCurrentBufferingStall = hasReachedReady && player.playWhenReady
        } else if (!isBufferingNow && wasBuffering && bufferingStartedAt != 0L) {
            val stallDuration = System.currentTimeMillis() - bufferingStartedAt
            if (isCurrentBufferingStall) {
                stallCount++
                totalStallDurationMs += stallDuration
            }
            Logger.info(
                TAG,
                "Buffering ended durationMs=$stallDuration isStall=$isCurrentBufferingStall stallCount=$stallCount " +
                    "totalStallDurationMs=$totalStallDurationMs ${player.diagnostics().toLogString()}",
            )
            bufferingStartedAt = 0L
            isCurrentBufferingStall = false
        }
        if (player.playbackState == Player.STATE_READY) hasReachedReady = true
        isBuffering = isBufferingNow
        updateBufferedValues()
    }

    private fun schedulePauseDiagnostics(scope: CoroutineScope) {
        pauseDiagnosticsJob?.cancel()
        if (player.isPlaying) return
        pauseDiagnosticsJob = scope.launch {
            delay(PAUSE_DIAGNOSTICS_DELAY_MS)
            logPlaybackDiagnostics("pausedFor7s")
        }
    }

    private fun logPlaybackDiagnostics(reason: String) {
        updatePosition()
        updateDuration()
        updateBufferedValues()
        val diagnostics = player.diagnostics()
        val currentStallDuration = bufferingStartedAt
            .takeIf { it != 0L }
            ?.let { System.currentTimeMillis() - it }
        Logger.info(
            TAG,
            "Playback diagnostics reason=$reason ${diagnostics.toLogString()} " +
                " hasRenderedFirstFrame=$hasRenderedFirstFrame stallCount=$stallCount " +
                "stallDurationMs=${currentStallDuration ?: 0L} totalStallDurationMs=$totalStallDurationMs " +
                "videoSize=${player.videoSize.width}x${player.videoSize.height} " +
                "unappliedRotation=${player.videoSize.unappliedRotationDegrees}",
        )
    }

    private fun logVideoSize(videoSize: VideoSize) {
        Logger.info(
            TAG,
            "Video size changed width=${videoSize.width} height=${videoSize.height} unappliedRotation=${videoSize.unappliedRotationDegrees} pixelRatio=${videoSize.pixelWidthHeightRatio}",
        )
    }

    private fun logPlayerError(error: PlaybackException?) {
        if (error == null) return
        Logger.error(
            TAG,
            "Player error code=${error.errorCode} name=${error.errorCodeName} ${player.diagnostics().toLogString()}",
            error,
        )
    }

    private companion object {
        private const val TAG = "MediaPresentationState"
        private const val PAUSE_DIAGNOSTICS_DELAY_MS = 7_000L
    }
}

val MediaPresentationState.positionFormatted: String
    get() = position.milliseconds.formatted()

val MediaPresentationState.durationFormatted: String
    get() = duration.milliseconds.formatted()

val MediaPresentationState.pendingPositionFormatted: String
    get() = (duration - position).milliseconds.formatted()
