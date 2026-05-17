package one.only.player.debug

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import one.only.player.core.model.PlayerPreferences
import one.only.player.feature.player.PlayerDebugCommandBridge
import one.only.player.feature.player.service.CustomCommands
import one.only.player.feature.player.service.PlayerService
import one.only.player.feature.player.service.setTransientPlaybackSpeed

internal fun Context.runPlayerAction(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "player.$action"
    if (action in UI_PLAYER_ACTIONS) return runPlayerUiAction(action)

    val value = extras.withTarget(target)
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
                        durationMs = controller.duration.safeTime(),
                        positionMs = controller.currentPosition.safeTime(),
                    )
                    "position" -> debugResult(
                        isOk = true,
                        message = "Player position: ${controller.currentPosition.safeTime()} ms",
                        command = command,
                        target = target,
                        durationMs = controller.duration.safeTime(),
                        positionMs = controller.currentPosition.safeTime(),
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

private fun runPlayerUiAction(action: String): Bundle {
    val command = "player.$action"
    val didHandle = PlayerDebugCommandBridge.dispatch(action)
    return debugResult(
        isOk = didHandle,
        message = if (didHandle) "Handled player UI action: $action" else "Player screen is not ready for action: $action",
        command = command,
        target = action,
    )
}

private suspend fun MediaController.awaitSeekTo(positionMs: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.SEEK_POSITION_MS_KEY, positionMs)
    }
    sendCustomCommand(CustomCommands.PRECISE_SEEK_TO.sessionCommand, args).await()
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
    withTimeout(3_000L) {
        val token = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        try {
            block(future.await())
        } finally {
            MediaController.releaseFuture(future)
        }
    }
}

private fun MediaController.debugStateBundle(
    command: String,
    target: String?,
    value: String? = null,
): Bundle = debugResult(
    isOk = true,
    message = "Player state: index=$currentMediaItemIndex count=$mediaItemCount mediaId=${currentMediaItem?.mediaId} position=${currentPosition.safeTime()} duration=${duration.safeTime()} playing=$isPlaying",
    command = command,
    target = target,
    value = value,
    durationMs = duration.safeTime(),
    positionMs = currentPosition.safeTime(),
    isPlaying = isPlaying,
    mediaItemCount = mediaItemCount,
    mediaItemIndex = currentMediaItemIndex,
    mediaId = currentMediaItem?.mediaId,
)

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
