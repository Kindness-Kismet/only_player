package one.only.player.feature.player.state

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.feature.player.extensions.togglePlayerSystemBars

// 解锁按钮独立于控制栏的停留时长：出现后 1.5 秒自动隐藏
private val UNLOCK_BUTTON_HIDE_AFTER: Duration = 1.5.seconds

@Composable
fun rememberControlsVisibilityState(
    player: Player,
    hideAfter: Duration?,
): ControlsVisibilityState {
    val activity = LocalActivity.current
    val coroutineScope = rememberCoroutineScope()
    val controlsVisibilityState = remember(player) { ControlsVisibilityState(player, hideAfter, coroutineScope) }
    LaunchedEffect(player) { controlsVisibilityState.observe() }
    LaunchedEffect(hideAfter) { controlsVisibilityState.updateHideAfter(hideAfter) }
    LaunchedEffect(controlsVisibilityState.isControlsVisible, controlsVisibilityState.isControlsLocked) {
        if (controlsVisibilityState.isControlsLocked) {
            activity?.togglePlayerSystemBars(shouldShowControls = false)
            return@LaunchedEffect
        }
        activity?.togglePlayerSystemBars(
            shouldShowControls = controlsVisibilityState.isControlsVisible,
        )
    }
    return controlsVisibilityState
}

@Stable
class ControlsVisibilityState(
    private val player: Player,
    private var hideAfter: Duration?,
    private val scope: CoroutineScope,
) {
    private var autoHideControlsJob: Job? = null

    var isControlsVisible: Boolean by mutableStateOf(true)
        private set

    var isControlsLocked: Boolean by mutableStateOf(false)
        private set

    var isUnlockButtonVisible: Boolean by mutableStateOf(false)
        private set

    private var unlockButtonAutoHideJob: Job? = null

    fun updateHideAfter(duration: Duration?) {
        hideAfter = duration
        if (isControlsVisible && player.isPlaying) {
            autoHideControls()
        }
    }

    fun showControls(duration: Duration? = hideAfter) {
        isControlsVisible = true
        autoHideControls(duration)
    }

    fun hideControls() {
        autoHideControlsJob?.cancel()
        isControlsVisible = false
    }

    fun toggleControlsVisibility() {
        if (isControlsLocked) {
            if (isUnlockButtonVisible) {
                unlockButtonAutoHideJob?.cancel()
                isUnlockButtonVisible = false
            } else {
                showUnlockButton()
            }
            return
        }
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    fun lockControls() {
        isControlsLocked = true
        showUnlockButton()
    }

    fun unlockControls() {
        isControlsLocked = false
        unlockButtonAutoHideJob?.cancel()
        isUnlockButtonVisible = false
        showControls()
    }

    fun showUnlockButton() {
        isUnlockButtonVisible = true
        unlockButtonAutoHideJob?.cancel()
        unlockButtonAutoHideJob = scope.launch {
            delay(UNLOCK_BUTTON_HIDE_AFTER)
            isUnlockButtonVisible = false
        }
    }

    suspend fun observe() {
        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    autoHideControls()
                }
            }
        }
    }

    private fun autoHideControls(duration: Duration? = hideAfter) {
        autoHideControlsJob?.cancel()
        if (duration == null || duration == Duration.INFINITE) return
        autoHideControlsJob = scope.launch {
            delay(duration)
            if (player.isPlaying) {
                isControlsVisible = false
            }
        }
    }
}
