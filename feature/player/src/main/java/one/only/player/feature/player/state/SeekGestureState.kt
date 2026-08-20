package one.only.player.feature.player.state

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import one.only.player.feature.player.extensions.availableDurationMs
import one.only.player.feature.player.extensions.formatted
import one.only.player.feature.player.extensions.seekToRequestedPosition
import one.only.player.feature.player.extensions.setIsScrubbingModeEnabled

@UnstableApi
@Composable
fun rememberSeekGestureState(
    player: Player,
    sensitivity: Float = 0.5f,
    isSeekGestureEnabled: Boolean,
    onSeekCommitted: (Long) -> Unit = {},
): SeekGestureState {
    val currentOnSeekCommitted = rememberUpdatedState(onSeekCommitted)
    val seekGestureState = remember(player, sensitivity, isSeekGestureEnabled) {
        SeekGestureState(
            player = player,
            sensitivity = sensitivity,
            isSeekGestureEnabled = isSeekGestureEnabled,
            onSeekCommitted = { position -> currentOnSeekCommitted.value(position) },
        )
    }
    return seekGestureState
}

@Stable
class SeekGestureState(
    private val player: Player,
    private val isSeekGestureEnabled: Boolean = true,
    private val sensitivity: Float = 0.5f,
    private val onSeekCommitted: (Long) -> Unit = {},
) {
    var isSeeking: Boolean by mutableStateOf(false)
        private set

    var seekStartPosition: Long? by mutableStateOf(null)
        private set

    var seekAmount: Long? by mutableStateOf(null)
        private set

    var pendingSeekPosition: Long? by mutableStateOf(null)
        private set

    var shouldAnimatePreview: Boolean by mutableStateOf(false)
        private set

    private val dragStabilizer = SeekDragStabilizer()

    fun onSeek(value: Long) {
        val duration = player.availableDurationMs()
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val currentPosition = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L

        if (!isSeeking) {
            isSeeking = true
            shouldAnimatePreview = false
            seekStartPosition = currentPosition
            pendingSeekPosition = currentPosition
            player.setIsScrubbingModeEnabled(true)
        }

        val newPosition = value.coerceIn(0L, duration)
        pendingSeekPosition = newPosition
        seekAmount = (newPosition - seekStartPosition!!).coerceIn(
            minimumValue = 0 - seekStartPosition!!,
            maximumValue = duration - seekStartPosition!!,
        )
    }

    fun onSeekEnd() {
        commitPendingSeek()
        reset()
    }

    fun onDragStart() {
        if (!isSeekGestureEnabled) return
        val duration = player.availableDurationMs()
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val currentPosition = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L

        isSeeking = true
        shouldAnimatePreview = true
        dragStabilizer.reset()
        seekStartPosition = currentPosition
        pendingSeekPosition = currentPosition

        player.setIsScrubbingModeEnabled(true)
    }

    @OptIn(UnstableApi::class)
    fun onDrag(
        change: PointerInputChange,
        dragAmount: Float,
        hysteresisPx: Float,
    ) {
        val seekStartPosition = seekStartPosition ?: return
        val duration = player.availableDurationMs()
        if (duration == C.TIME_UNSET) return
        if (change.isConsumed) return

        val currentPreviewPosition = pendingSeekPosition ?: seekStartPosition
        if (currentPreviewPosition <= 0L && dragAmount < 0) return
        if (currentPreviewPosition >= duration && dragAmount > 0) return

        val stabilizedDragAmount = dragStabilizer.add(dragAmount, hysteresisPx) ?: return
        val newPosition = (seekStartPosition + (stabilizedDragAmount * (sensitivity * 100)).toLong())
            .coerceIn(0L, duration)
        if (newPosition == currentPreviewPosition) return

        pendingSeekPosition = newPosition
        seekAmount = (newPosition - seekStartPosition).coerceIn(
            minimumValue = 0 - seekStartPosition,
            maximumValue = duration - seekStartPosition,
        )
    }

    fun onDragEnd() {
        commitPendingSeek()
        reset()
    }

    private fun commitPendingSeek() {
        val pendingSeekPosition = pendingSeekPosition ?: return
        val seekAmount = seekAmount ?: return
        if (seekAmount == 0L) return
        val currentPosition = player.currentPosition.takeIf { it != C.TIME_UNSET }
        if (currentPosition == null || currentPosition != pendingSeekPosition) {
            player.seekToRequestedPosition(pendingSeekPosition)
        }
        onSeekCommitted(pendingSeekPosition)
    }

    private fun reset() {
        player.setIsScrubbingModeEnabled(false)
        isSeeking = false
        seekStartPosition = null
        seekAmount = null
        pendingSeekPosition = null
        shouldAnimatePreview = false

        dragStabilizer.reset()
    }
}

internal class SeekDragStabilizer {
    private var rawDragAmount = 0f
    private var stabilizedDragAmount = 0f

    fun add(dragAmount: Float, hysteresisPx: Float): Float? {
        rawDragAmount += dragAmount
        val safeHysteresis = hysteresisPx.coerceAtLeast(0f)
        val difference = rawDragAmount - stabilizedDragAmount
        if (abs(difference) <= safeHysteresis) return null

        stabilizedDragAmount = rawDragAmount - kotlin.math.sign(difference) * safeHysteresis
        return stabilizedDragAmount
    }

    fun reset() {
        rawDragAmount = 0f
        stabilizedDragAmount = 0f
    }
}

val SeekGestureState.seekAmountFormatted: String
    get() {
        val seekAmount = seekAmount ?: return ""
        val sign = if (seekAmount < 0) "-" else "+"
        return sign + abs(seekAmount).milliseconds.formatted()
    }

val SeekGestureState.seekToPositionFormated: String
    get() {
        val position = seekStartPosition ?: return ""
        val seekAmount = seekAmount ?: return ""
        return (position + seekAmount).milliseconds.formatted()
    }
