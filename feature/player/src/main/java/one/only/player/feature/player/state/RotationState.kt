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
import one.only.player.feature.player.extensions.toActivityOrientation
import one.only.player.feature.player.extensions.videoHeight
import one.only.player.feature.player.extensions.videoRotation
import one.only.player.feature.player.extensions.videoWidth

@UnstableApi
@Composable
fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
    shouldRememberScreenOrientation: Boolean,
    lastScreenOrientation: LastPlayerScreenOrientation?,
    perFileOrientation: LastPlayerScreenOrientation? = null,
    mediaIdentity: String? = null,
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
    // 切条/重进时 per-file 方向必须强制重算；不能依赖 requestedOrientation==UNSPECIFIED 的 early-return
    LaunchedEffect(rotationState, perFileOrientation, mediaIdentity, lastScreenOrientation) {
        rotationState.updatePreferredOrientation(
            perFileOrientation = perFileOrientation,
            lastScreenOrientation = lastScreenOrientation,
            mediaIdentity = mediaIdentity,
        )
        rotationState.applyPreferredOrientation(player, force = true)
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
    private var lastScreenOrientation: LastPlayerScreenOrientation?,
    private val onLastScreenOrientationChange: (LastPlayerScreenOrientation) -> Unit,
) {
    var currentRequestedOrientation: Int by mutableIntStateOf(activity.requestedOrientation)
        private set

    private var perFileOrientation: LastPlayerScreenOrientation? = null
    private var appliedMediaIdentity: String? = null

    fun updatePreferredOrientation(
        perFileOrientation: LastPlayerScreenOrientation?,
        lastScreenOrientation: LastPlayerScreenOrientation?,
        mediaIdentity: String?,
    ) {
        this.perFileOrientation = perFileOrientation
        this.lastScreenOrientation = lastScreenOrientation
        // mediaIdentity 变化时允许重新 force apply（即使 activity 方向已非 UNSPECIFIED）
        if (mediaIdentity != appliedMediaIdentity) {
            appliedMediaIdentity = mediaIdentity
        }
    }

    fun rotate() {
        val newOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LastPlayerScreenOrientation.PORTRAIT
            else -> LastPlayerScreenOrientation.LANDSCAPE
        }
        activity.requestedOrientation = newOrientation.toActivityOrientation()
        currentRequestedOrientation = activity.requestedOrientation
        // 手动旋转后始终写回记住方向（开关开启时）
        if (shouldRememberScreenOrientation) {
            onLastScreenOrientationChange(newOrientation)
        }
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val configurationChangedListener: Consumer<Configuration> = Consumer {
            currentRequestedOrientation = activity.requestedOrientation
        }

        activity.addOnConfigurationChangedListener(configurationChangedListener)

        onDispose {
            activity.removeOnConfigurationChangedListener(configurationChangedListener)
        }
    }

    suspend fun observe(player: Player) {
        Log.d(TAG, "observe: player=${player.javaClass.simpleName}@${System.identityHashCode(player)}")
        applyPreferredOrientation(player, force = true)
        maybeApplyVideoOrientation(player)

        // 视频尺寸通过 metadata extras 从 Service 端传递；切条强制按当前文件方向重算
        player.listen { events ->
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                val metadata = player.mediaMetadata
                Log.d(TAG, "transition: w=${metadata.videoWidth}, h=${metadata.videoHeight}, rot=${metadata.videoRotation}")
                applyPreferredOrientation(player, force = true)
                maybeApplyVideoOrientation(player)
                return@listen
            }
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                val metadata = player.mediaMetadata
                Log.d(TAG, "metadata: w=${metadata.videoWidth}, h=${metadata.videoHeight}, rot=${metadata.videoRotation}")
                maybeApplyVideoOrientation(player)
            }
        }
    }

    /**
     * 按 文件级 → 全局记住 → 设置模式 应用方向。
     * force=true 时忽略 activity 已有方向（切条/重进必须能从 B 的全局切回 A 的记住方向）。
     */
    fun applyPreferredOrientation(player: Player, force: Boolean = false) {
        Log.d(
            TAG,
            "applyPreferredOrientation: force=$force current=${activity.requestedOrientation} " +
                "perFile=$perFileOrientation last=$lastScreenOrientation",
        )
        if (!force && activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return

        val preferred = perFileOrientation
            ?: lastScreenOrientation?.takeIf { shouldRememberScreenOrientation }
        preferred?.toActivityOrientation()?.let { target ->
            if (activity.requestedOrientation != target) {
                activity.requestedOrientation = target
            }
            currentRequestedOrientation = activity.requestedOrientation
            return
        }

        val modeTarget = when (screenOrientation) {
            ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.VIDEO_ORIENTATION -> getVideoBasedOrientation(player)
        }
        if (modeTarget == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        if (activity.requestedOrientation != modeTarget) {
            activity.requestedOrientation = modeTarget
        }
        currentRequestedOrientation = activity.requestedOrientation
    }

    private fun maybeApplyVideoOrientation(player: Player) {
        if (screenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        // 已有文件级/全局记住方向时，不要被视频宽高覆盖
        if (perFileOrientation != null) return
        if (shouldRememberScreenOrientation && lastScreenOrientation != null) return
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        val orientation = getVideoBasedOrientation(player)
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            Log.d(TAG, "applyVideoOrientation: $orientation")
            activity.requestedOrientation = orientation
            currentRequestedOrientation = orientation
        }
    }

    private fun getVideoBasedOrientation(player: Player): Int {
        val metadata = player.mediaMetadata
        val width = metadata.videoWidth ?: 0
        val height = metadata.videoHeight ?: 0
        if (width == 0 || height == 0) {
            Log.d(TAG, "getVideoBasedOrientation: metadata=${width}x$height -> UNSPECIFIED")
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        val rotation = metadata.videoRotation ?: 0

        val visuallyPortrait = if (rotation == 90 || rotation == 270) {
            width >= height
        } else {
            height >= width
        }

        Log.d(TAG, "getVideoBasedOrientation: ${width}x$height, rotation=$rotation, portrait=$visuallyPortrait")
        return if (visuallyPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

private const val TAG = "RotationState"
