package one.only.player.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.IntSize
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberVolumeAndBrightnessGestureState(
    volumeState: VolumeState,
    brightnessState: BrightnessState,
    isVolumeGestureEnabled: Boolean,
    isBrightnessGestureEnabled: Boolean,
    volumeGestureSensitivity: Float,
    brightnessGestureSensitivity: Float,
): VolumeAndBrightnessGestureState {
    val coroutineScope = rememberCoroutineScope()
    val volumeAndBrightnessGestureState = remember(volumeState, brightnessState) {
        VolumeAndBrightnessGestureState(
            volumeState = volumeState,
            brightnessState = brightnessState,
            isVolumeGestureEnabled = isVolumeGestureEnabled,
            isBrightnessGestureEnabled = isBrightnessGestureEnabled,
            volumeGestureSensitivity = volumeGestureSensitivity,
            brightnessGestureSensitivity = brightnessGestureSensitivity,
            coroutineScope = coroutineScope,
        )
    }
    return volumeAndBrightnessGestureState
}

@Stable
class VolumeAndBrightnessGestureState(
    private val volumeState: VolumeState,
    private val brightnessState: BrightnessState,
    private val isVolumeGestureEnabled: Boolean = true,
    private val isBrightnessGestureEnabled: Boolean = true,
    private val volumeGestureSensitivity: Float,
    private val brightnessGestureSensitivity: Float,
    private val coroutineScope: CoroutineScope,
) {
    var activeGesture: VerticalGesture? by mutableStateOf(null)
        private set

    var volumeChangePercentage: Int by mutableIntStateOf(0)
        private set

    var brightnessChangePercentage: Int by mutableIntStateOf(0)
        private set

    /** 手势过程中锁定显示值，避免系统音量广播回写导致百分比最后闪一下 */
    var displayedVolumePercentage: Int by mutableIntStateOf(0)
        private set

    var displayedBrightnessPercentage: Int by mutableIntStateOf(0)
        private set

    private var startingY = 0f
    private var startVolumePercentage = 0
    private var startBrightnessPercentage = 0
    private var job: Job? = null

    fun onDragStart(offset: Offset, size: IntSize) {
        val viewCenterX = size.width / 2
        job?.cancel()
        activeGesture = when {
            offset.x < viewCenterX -> VerticalGesture.BRIGHTNESS.takeIf { isBrightnessGestureEnabled }
            else -> VerticalGesture.VOLUME.takeIf { isVolumeGestureEnabled }
        }
        startingY = offset.y
        // 锁定起始百分比，避免系统音量广播回写导致指示器最后闪一下
        startVolumePercentage = volumeState.volumePercentage
        startBrightnessPercentage = brightnessState.brightnessPercentage
        displayedVolumePercentage = startVolumePercentage
        displayedBrightnessPercentage = startBrightnessPercentage
        volumeChangePercentage = 0
        brightnessChangePercentage = 0
    }

    fun onDrag(change: PointerInputChange, dragAmount: Float) {
        val activeGesture = activeGesture ?: return
        if (change.isConsumed) return

        when (activeGesture) {
            VerticalGesture.VOLUME -> {
                val maxVolumePercentage = volumeState.maxVolumePercentage
                val volumeChange = (startingY - change.position.y) * (volumeGestureSensitivity / 10)
                val newVolume = (startVolumePercentage + volumeChange.toInt())
                    .coerceIn(0, maxVolumePercentage)
                volumeChangePercentage = newVolume - startVolumePercentage
                brightnessChangePercentage = 0
                // 手势过程中用本地显示值，避免 setStreamVolume 广播触发百分比跳变闪烁
                displayedVolumePercentage = newVolume
                volumeState.updateVolumePercentage(newVolume)
            }

            VerticalGesture.BRIGHTNESS -> {
                val brightnessChange = (startingY - change.position.y) * (brightnessGestureSensitivity / 10)
                val newBrightness = (startBrightnessPercentage + brightnessChange.toInt())
                    .coerceIn(0, MAX_BRIGHTNESS_PERCENTAGE)
                brightnessChangePercentage = newBrightness - startBrightnessPercentage
                volumeChangePercentage = 0
                displayedBrightnessPercentage = newBrightness
                brightnessState.updateBrightnessPercentage(newBrightness)
            }
        }
    }

    fun onDragEnd() {
        startingY = 0f
        startVolumePercentage = 0
        startBrightnessPercentage = 0

        job?.cancel()
        job = coroutineScope.launch {
            // 百分比指示：略缩短停留，结束时直接消失，减少最后一帧闪动
            delay(500.milliseconds)
            activeGesture = null
            volumeChangePercentage = 0
            brightnessChangePercentage = 0
            displayedVolumePercentage = volumeState.volumePercentage
            displayedBrightnessPercentage = brightnessState.brightnessPercentage
        }
    }

    /** 手势进行中的稳定百分比（避免读 volumeState 时被系统广播回写闪一下） */
    fun indicatorVolumePercentage(): Int =
        if (activeGesture == VerticalGesture.VOLUME) displayedVolumePercentage else volumeState.volumePercentage

    fun indicatorBrightnessPercentage(): Int =
        if (activeGesture == VerticalGesture.BRIGHTNESS) {
            displayedBrightnessPercentage
        } else {
            brightnessState.brightnessPercentage
        }

    companion object {
        private const val MAX_BRIGHTNESS_PERCENTAGE = 100
    }
}

enum class VerticalGesture {
    VOLUME,
    BRIGHTNESS,
}
