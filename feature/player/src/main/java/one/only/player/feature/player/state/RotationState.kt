package one.only.player.feature.player.state

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import one.only.player.core.model.LastPlayerScreenOrientation
import one.only.player.core.model.ScreenOrientation
import one.only.player.feature.player.extensions.isPortrait
import one.only.player.feature.player.extensions.toActivityOrientation

@UnstableApi
@Composable
fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
    shouldRememberScreenOrientation: Boolean,
    lastScreenOrientation: LastPlayerScreenOrientation?,
    onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
): RotationState {
    val activity = LocalActivity.current as ComponentActivity
    val rotationState = remember(screenOrientation, shouldRememberScreenOrientation, lastScreenOrientation) {
        RotationState(
            activity = activity,
            screenOrientation = screenOrientation,
            shouldRememberScreenOrientation = shouldRememberScreenOrientation,
            lastScreenOrientation = lastScreenOrientation,
            onLastScreenOrientationChange = onLastScreenOrientationChange,
        )
    }
    DisposableEffect(activity, rotationState) {
        rotationState.handleListeners(this)
    }
    LaunchedEffect(player, rotationState) { rotationState.observe(player) }
    return rotationState
}

@Stable
class RotationState(
    private val activity: ComponentActivity,
    private val screenOrientation: ScreenOrientation,
    private val shouldRememberScreenOrientation: Boolean,
    private val lastScreenOrientation: LastPlayerScreenOrientation?,
    private val onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
) {
    var currentRequestedOrientation: Int by mutableIntStateOf(activity.requestedOrientation)
        private set

    private var hasSystemIgnoredOrientationRequest = false

    fun rotate() {
        val newOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LastPlayerScreenOrientation.PORTRAIT
            else -> LastPlayerScreenOrientation.LANDSCAPE
        }
        activity.requestedOrientation = newOrientation.toActivityOrientation()
        if (shouldRememberScreenOrientation) {
            onLastScreenOrientationChange(newOrientation)
        }
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val configurationChangedListener: Consumer<Configuration> = Consumer {
            currentRequestedOrientation = activity.requestedOrientation
            releaseOrientationRequestIfLetterboxed()
        }

        activity.addOnConfigurationChangedListener(configurationChangedListener)
        releaseOrientationRequestIfLetterboxed()

        onDispose {
            activity.removeOnConfigurationChangedListener(configurationChangedListener)
        }
    }

    /**
     * 系统开启 ignoreOrientationRequest 时（平板、部分新设备的竖持锁定场景），固定方向请求
     * 不会旋转屏幕，而是把整个播放器 letterbox 成屏幕中间的小窗。检测到 letterbox 就撤回
     * 方向请求恢复全屏窗口，并停止本次会话内的自动方向请求，避免反复触发。
     */
    private fun releaseOrientationRequestIfLetterboxed() {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        if (activity.isInMultiWindowMode) return
        val windowManager = activity.windowManager
        val windowBounds = windowManager.currentWindowMetrics.bounds
        val displayBounds = windowManager.maximumWindowMetrics.bounds
        if (windowBounds == displayBounds) return

        Log.d(TAG, "releaseOrientationRequestIfLetterboxed: window=$windowBounds display=$displayBounds")
        hasSystemIgnoredOrientationRequest = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        currentRequestedOrientation = activity.requestedOrientation
    }

    suspend fun observe(player: Player) {
        Log.d(TAG, "observe: player=${player.javaClass.simpleName}@${System.identityHashCode(player)}")
        setOrientation(player)
        maybeApplyVideoOrientation(player)

        // videoSize 是应用旋转后的显示宽高，作为方向决策的唯一数据源
        player.listen { events ->
            if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
                Log.d(TAG, "listen: videoSize=${player.videoSize.width}x${player.videoSize.height}")
                maybeApplyVideoOrientation(player)
            }
        }
    }

    private fun maybeApplyVideoOrientation(player: Player) {
        if (hasSystemIgnoredOrientationRequest) return
        if (screenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        val orientation = getVideoBasedOrientation(player)
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            Log.d(TAG, "applyOrientation: $orientation")
            activity.requestedOrientation = orientation
        }
    }

    private fun setOrientation(player: Player) {
        Log.d(TAG, "setOrientation: requestedOrientation=${activity.requestedOrientation}")
        if (hasSystemIgnoredOrientationRequest) return
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return

        activity.requestedOrientation = lastScreenOrientation
            ?.takeIf { shouldRememberScreenOrientation && screenOrientation != ScreenOrientation.VIDEO_ORIENTATION }
            ?.toActivityOrientation()
            ?: when (screenOrientation) {
                ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenOrientation.VIDEO_ORIENTATION -> getVideoBasedOrientation(player)
            }
    }

    private fun getVideoBasedOrientation(player: Player): Int {
        val videoSize = player.videoSize
        if (videoSize.width == 0 || videoSize.height == 0) {
            Log.d(TAG, "getVideoBasedOrientation: videoSize=${videoSize.width}x${videoSize.height} -> UNSPECIFIED")
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        Log.d(TAG, "getVideoBasedOrientation: videoSize=${videoSize.width}x${videoSize.height}, portrait=${videoSize.isPortrait}")
        return if (videoSize.isPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

private const val TAG = "RotationState"
