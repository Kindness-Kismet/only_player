package one.only.player.debug

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import one.only.player.core.model.PlayerPreferences
import one.only.player.feature.player.PlayerDebugCommandBridge
import one.only.player.feature.player.extensions.diagnostics
import one.only.player.feature.player.extensions.isApproximateSeekEnabled
import one.only.player.feature.player.extensions.toLogString
import one.only.player.feature.player.model.VideoChapter
import one.only.player.feature.player.model.currentChapterIndex
import one.only.player.feature.player.service.CustomCommands
import one.only.player.feature.player.service.PlayerService
import one.only.player.feature.player.service.getPlaybackStallMetrics
import one.only.player.feature.player.service.getVideoChapters
import one.only.player.feature.player.service.getVideoFormatDebugInfo
import one.only.player.feature.player.service.setTransientPlaybackSpeed

internal fun Context.runPlayerAction(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "player.$action"
    val value = extras.withTarget(target)
    if (action in UI_PLAYER_ACTIONS) return runPlayerUiAction(action, value)

    return runCatching {
        runBlocking {
            withMediaController { controller ->
                when (action) {
                    "play" -> controller.play()
                    "pause" -> controller.pause()
                    "toggle_play_pause" -> if (controller.isPlaying) controller.pause() else controller.play()
                    "next" -> controller.seekToNextMediaItem()
                    "previous" -> controller.seekToPreviousMediaItem()
                    "seek_to" -> controller.awaitSeekTo(value.requiredLongMillis(EXTRA_VALUE))
                    "seek_by" -> controller.awaitSeekTo((controller.currentPosition + value.requiredLongMillis(EXTRA_VALUE)).coerceAtLeast(0L))
                    "stress_seek" -> controller.stressSeek(value)
                    "chapter.seek" -> controller.awaitSeekTo(
                        controller.requireVideoChapter(value.requiredInt(EXTRA_VALUE)).startTimeMs,
                    )
                    "chapter.next" -> controller.awaitSeekTo(
                        controller.requireAdjacentVideoChapter(offset = 1).startTimeMs,
                    )
                    "chapter.previous" -> controller.awaitSeekTo(
                        controller.requireAdjacentVideoChapter(offset = -1).startTimeMs,
                    )
                    "long_press_speed" -> runLongPressSpeed(controller, value)
                    "stop" -> controller.stop()
                    "shuffle" -> controller.shuffleModeEnabled = value.getBoolean(EXTRA_ENABLED, !controller.shuffleModeEnabled)
                    "loop" -> controller.repeatMode = value.optionalRepeatMode() ?: controller.repeatMode.nextRepeatMode()
                    else -> error("Unknown player action: $action")
                }
                controller.debugStateBundle(
                    command = command,
                    target = action,
                    value = value.debugValue(),
                )
            }
        }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle player action: $action",
            command = command,
            target = action,
        )
    }
}

internal fun Context.runPlayerGet(target: String): Bundle {
    val command = "player.$target"
    return runCatching {
        runBlocking {
            withMediaController { controller ->
                when (target) {
                    "state" -> controller.debugStateBundle(
                        command = command,
                        target = target,
                    )
                    "duration" -> debugResult(
                        isOk = true,
                        message = "Player duration: ${controller.duration.safeTime()} ms",
                        command = command,
                        target = target,
                        mediaDurationMs = controller.duration.safeTime(),
                    )
                    "position" -> debugResult(
                        isOk = true,
                        message = "Player position: ${controller.currentPosition.safeTime()} ms",
                        command = command,
                        target = target,
                        currentPositionMs = controller.currentPosition.safeTime(),
                    )
                    "cues" -> debugResult(
                        isOk = true,
                        message = controller.currentCues.cues.joinToString(separator = " | ") { cue ->
                            cue.text?.toString().orEmpty().ifBlank { "<non-text>" }
                        },
                        command = command,
                        target = target,
                        value = controller.currentCues.cues.size.toString(),
                    )
                    "video_format" -> controller.videoFormatBundle(
                        command = command,
                        target = target,
                    )
                    "chapters" -> controller.chaptersDebugBundle(
                        command = command,
                        target = target,
                    )
                    else -> error("Unknown player info target: $target")
                }
            }
        }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to get player info: $target",
            command = command,
            target = target,
        )
    }
}

private fun runPlayerUiAction(action: String, extras: Bundle? = null): Bundle {
    val command = "player.$action"
    val didHandle = PlayerDebugCommandBridge.dispatch(action, extras)
    return debugResult(
        isOk = didHandle,
        message = if (didHandle) "Handled player UI action: $action" else "Player screen is not ready for action: $action",
        command = command,
        target = action,
        value = extras?.getString(EXTRA_VALUE),
    )
}

private suspend fun MediaController.awaitSeekTo(positionMs: Long) {
    if (!awaitMediaReady()) {
        error("Player is not ready for seek")
    }
    val startPositionMs = currentPosition.safeTime()
    val isApproximateSeekEnabled = currentMediaItem?.mediaMetadata?.isApproximateSeekEnabled == true ||
        positionMs >= FAST_SEEK_MIN_DURATION_MS
    val args = Bundle().apply {
        putLong(CustomCommands.SEEK_POSITION_MS_KEY, positionMs)
    }
    val result = sendCustomCommand(CustomCommands.PRECISE_SEEK_TO.sessionCommand, args).await()
    if (result.resultCode != SessionResult.RESULT_SUCCESS) {
        error("Precise seek command failed: ${result.resultCode}")
    }
    if (!awaitSettledSeek(positionMs, startPositionMs, isApproximateSeekEnabled)) {
        error("Seek did not settle: target=$positionMs position=${currentPosition.safeTime()} state=$playbackState")
    }
}

private suspend fun MediaController.awaitMediaReady(): Boolean {
    val startedAtMs = SystemClock.elapsedRealtime()
    while (SystemClock.elapsedRealtime() - startedAtMs < MEDIA_READY_TIMEOUT_MS) {
        if (mediaItemCount > 0 && currentMediaItem != null && duration != C.TIME_UNSET) return true
        delay(SEEK_SETTLE_POLL_INTERVAL_MS)
    }
    return false
}

private suspend fun MediaController.requireVideoChapter(index: Int): VideoChapter {
    if (!awaitMediaReady()) error("Player is not ready for chapters")
    return getVideoChapters().getOrNull(index) ?: error("Chapter index out of range: $index")
}

private suspend fun MediaController.requireAdjacentVideoChapter(offset: Int): VideoChapter {
    if (!awaitMediaReady()) error("Player is not ready for chapters")
    val chapters = getVideoChapters()
    val currentIndex = chapters.currentChapterIndex(currentPosition.safeTime())
    val targetIndex = when {
        currentIndex != null -> currentIndex + offset
        offset > 0 -> 0
        else -> error("No previous chapter")
    }
    return chapters.getOrNull(targetIndex) ?: error("No chapter at index: $targetIndex")
}

private suspend fun MediaController.chaptersDebugBundle(
    command: String,
    target: String,
): Bundle {
    if (!awaitMediaReady()) error("Player is not ready for chapters")
    val chapters = getVideoChapters()
    return debugResult(
        isOk = true,
        message = chapters.joinToString(separator = " | ") { chapter ->
            "${chapter.index}@${chapter.startTimeMs}@${chapter.endTimeMs}@${chapter.title.orEmpty()}"
        },
        command = command,
        target = target,
        value = chapters.size.toString(),
    )
}

private suspend fun MediaController.awaitSettledSeek(
    targetPositionMs: Long,
    startPositionMs: Long,
    isApproximateSeekEnabled: Boolean,
): Boolean {
    val startedAtMs = SystemClock.elapsedRealtime()
    while (SystemClock.elapsedRealtime() - startedAtMs < SEEK_SETTLE_TIMEOUT_MS) {
        playerError?.let { error("Seek failed: ${it.errorCodeName} (${it.errorCode}): ${it.message}") }
        val position = currentPosition.safeTime()
        val duration = duration.safeTime().takeIf { it > 0L }
        val targetPosition = duration?.let { targetPositionMs.coerceIn(0L, it) } ?: targetPositionMs.coerceAtLeast(0L)
        val requestedDistance = kotlin.math.abs(targetPosition - startPositionMs)
        val remainingDistance = kotlin.math.abs(position - targetPosition)
        // 短距离跳转的容差必须小于请求距离，避免原地未跳转被判为落稳。
        val exactTolerance = if (requestedDistance == 0L) SEEK_SETTLE_TOLERANCE_MS else minOf(SEEK_SETTLE_TOLERANCE_MS, requestedDistance / 2)
        val isExactPositionReady = remainingDistance <= exactTolerance
        val isApproximatePositionReady = isApproximateSeekEnabled &&
            kotlin.math.abs(position - startPositionMs) > SEEK_SETTLE_TOLERANCE_MS &&
            remainingDistance < requestedDistance &&
            remainingDistance <= APPROXIMATE_SEEK_SETTLE_TOLERANCE_MS
        val isPositionReady = isExactPositionReady || isApproximatePositionReady
        val hasSettled = when (playbackState) {
            Player.STATE_READY -> isPositionReady
            Player.STATE_ENDED -> duration != null && duration - targetPosition <= exactTolerance && isExactPositionReady
            else -> false
        }
        if (hasSettled) return true
        delay(SEEK_SETTLE_POLL_INTERVAL_MS)
    }
    return false
}

private suspend fun MediaController.stressSeek(extras: Bundle) {
    val count = extras.getString(EXTRA_VALUE)?.toInt() ?: 100
    val intervalMs = extras.getString("interval_ms")?.toLong() ?: 120L
    require(count in 1..1_000 && intervalMs in 1..1_000 && count * intervalMs <= 15_000L)
    check(awaitMediaReady()) { "Player is not ready for seek" }
    val mediaId = currentMediaItem?.mediaId
    val durationMs = duration
    require(durationMs > 0L && durationMs != C.TIME_UNSET)

    suspend fun setSeekMode(
        command: CustomCommands,
        key: String,
        isEnabled: Boolean,
    ) {
        val result = sendCustomCommand(command.sessionCommand, Bundle().apply { putBoolean(key, isEnabled) }).await()
        check(result.resultCode == SessionResult.RESULT_SUCCESS) { "Seek mode command failed: ${result.resultCode}" }
    }

    try {
        setSeekMode(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED, CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY, true)
        setSeekMode(CustomCommands.SET_IS_SEEK_PREVIEW_ENABLED, CustomCommands.IS_SEEK_PREVIEW_ENABLED_KEY, true)
        repeat(count) { index ->
            check(currentMediaItem?.mediaId == mediaId) { "Media changed during seek stress" }
            playerError?.let { error("Seek failed: ${it.errorCodeName}") }
            val percentage = if (index % 2 == 0) 10 + index % 30 else 60 + index % 30
            seekTo(durationMs * percentage / 100)
            delay(intervalMs)
        }
    } finally {
        withContext(NonCancellable) {
            setSeekMode(CustomCommands.SET_IS_SEEK_PREVIEW_ENABLED, CustomCommands.IS_SEEK_PREVIEW_ENABLED_KEY, false)
            setSeekMode(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED, CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY, false)
        }
    }
}

private suspend fun runLongPressSpeed(
    controller: MediaController,
    extras: Bundle,
) {
    val speed = extras.requiredFloat(EXTRA_VALUE).coerceIn(
        PlayerPreferences.MIN_LONG_PRESS_CONTROLS_SPEED,
        PlayerPreferences.MAX_LONG_PRESS_CONTROLS_SPEED,
    )
    val durationMs = extras.requiredLongMillis(EXTRA_DURATION_MS).coerceAtLeast(1L)
    val originalSpeed = controller.playbackParameters.speed
    try {
        if (!controller.isPlaying) controller.play()
        controller.setTransientPlaybackSpeed(speed)
        delay(durationMs)
    } finally {
        controller.setTransientPlaybackSpeed(originalSpeed)
    }
}

private suspend fun <T> Context.withMediaController(block: suspend (MediaController) -> T): T = withContext(Dispatchers.Main) {
    withTimeout(CONTROLLER_ACTION_TIMEOUT_MS) {
        val token = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        try {
            block(future.await())
        } finally {
            MediaController.releaseFuture(future)
        }
    }
}

private suspend fun MediaController.videoFormatBundle(
    command: String,
    target: String?,
): Bundle {
    val result = getVideoFormatDebugInfo()
    if (result.resultCode != SessionResult.RESULT_SUCCESS) error("Video format command failed: ${result.resultCode}")

    val extras = result.extras
    val decoderPriority = extras.getString(CustomCommands.VIDEO_DECODER_PRIORITY_KEY).orEmpty()
    val decoderName = extras.getString(CustomCommands.VIDEO_DECODER_NAME_KEY).orEmpty()
    val width = extras.getInt(CustomCommands.VIDEO_WIDTH_KEY)
    val height = extras.getInt(CustomCommands.VIDEO_HEIGHT_KEY)
    val transfer = extras.getInt(CustomCommands.VIDEO_COLOR_TRANSFER_KEY)
    val standard = extras.getInt(CustomCommands.VIDEO_COLOR_STANDARD_KEY)
    val range = extras.getInt(CustomCommands.VIDEO_COLOR_RANGE_KEY)
    val isHdr = extras.getBoolean(CustomCommands.IS_VIDEO_HDR_KEY)
    val areEffectsAvailable = extras.getBoolean(CustomCommands.IS_VIDEO_EFFECTS_AVAILABLE_KEY)
    val areEffectsActive = extras.getBoolean(CustomCommands.IS_VIDEO_EFFECTS_ACTIVE_KEY)
    return Bundle(extras).apply {
        putAll(
            debugResult(
                isOk = true,
                message = "Video format: decoder=$decoderPriority/$decoderName size=${width}x$height transfer=$transfer standard=$standard range=$range hdr=$isHdr effectsAvailable=$areEffectsAvailable effectsActive=$areEffectsActive",
                command = command,
                target = target,
                value = transfer.toString(),
            ),
        )
    }
}

@OptIn(UnstableApi::class)
private suspend fun MediaController.debugStateBundle(
    command: String,
    target: String?,
    value: String? = null,
): Bundle {
    val diagnostics = diagnostics()
    val stallMetrics = getPlaybackStallMetrics()
    return debugResult(
        isOk = true,
        message = "Player state: ${diagnostics.toLogString()} stallCount=${stallMetrics.count} " +
            "currentStallDurationMs=${stallMetrics.currentDurationMs} totalStallDurationMs=${stallMetrics.totalDurationMs}",
        command = command,
        target = target,
        value = value,
        currentPositionMs = diagnostics.positionMs,
        mediaDurationMs = diagnostics.durationMs,
        isCurrentlyPlaying = diagnostics.isPlaying,
        sourceType = diagnostics.sourceType,
        playbackState = diagnostics.playbackState,
        playbackStateName = diagnostics.playbackStateName,
        bufferedPositionMs = diagnostics.bufferedPositionMs,
        remainingBufferedDurationMs = diagnostics.remainingBufferedDurationMs,
        bufferedPercentage = diagnostics.bufferedPercentage,
        playbackSpeed = diagnostics.playbackSpeed,
        audioBitrate = diagnostics.audioBitrate,
        videoBitrate = diagnostics.videoBitrate,
        playWhenReady = diagnostics.playWhenReady,
        isLoading = diagnostics.isLoading,
        playerErrorCode = diagnostics.errorCode,
        playerErrorName = diagnostics.errorCodeName,
        stallCount = stallMetrics.count,
        currentStallDurationMs = stallMetrics.currentDurationMs,
        totalStallDurationMs = stallMetrics.totalDurationMs,
    )
}

private fun Bundle.optionalRepeatMode(): Int? {
    val rawValue = getString(EXTRA_VALUE) ?: return null
    return when (rawValue.trim().lowercase().replace('-', '_')) {
        "off" -> Player.REPEAT_MODE_OFF
        "one" -> Player.REPEAT_MODE_ONE
        "all" -> Player.REPEAT_MODE_ALL
        else -> error("Unknown loop mode: $rawValue")
    }
}

private fun Int.nextRepeatMode(): Int = when (this) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
    else -> Player.REPEAT_MODE_OFF
}

private fun Long.safeTime(): Long = takeIf { it != C.TIME_UNSET } ?: 0L

private const val CONTROLLER_ACTION_TIMEOUT_MS = 25_000L
private const val MEDIA_READY_TIMEOUT_MS = 8_000L
private const val SEEK_SETTLE_TIMEOUT_MS = 20_000L
private const val SEEK_SETTLE_POLL_INTERVAL_MS = 100L
private const val SEEK_SETTLE_TOLERANCE_MS = 1_500L
private const val APPROXIMATE_SEEK_SETTLE_TOLERANCE_MS = 30_000L
private const val FAST_SEEK_MIN_DURATION_MS = 120_000L
