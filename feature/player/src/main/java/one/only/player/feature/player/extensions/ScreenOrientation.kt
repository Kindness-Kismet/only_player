package one.only.player.feature.player.extensions

import android.content.pm.ActivityInfo
import one.only.player.core.model.LastPlayerScreenOrientation
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.model.VideoLandscapeOrientation

fun LastPlayerScreenOrientation.toActivityOrientation(
    landscapeOrientation: VideoLandscapeOrientation = VideoLandscapeOrientation.SENSOR,
): Int = when (this) {
    LastPlayerScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    LastPlayerScreenOrientation.LANDSCAPE -> landscapeOrientation.toActivityOrientation()
}

internal fun VideoLandscapeOrientation.toActivityOrientation(): Int = when (this) {
    VideoLandscapeOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    VideoLandscapeOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    VideoLandscapeOrientation.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
}

fun ScreenOrientation.toActivityOrientation(videoOrientation: Int? = null): Int = when (this) {
    ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ScreenOrientation.VIDEO_ORIENTATION -> videoOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
