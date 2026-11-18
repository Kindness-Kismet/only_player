package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.ui.R

@Composable
fun ScreenOrientation.name(): String {
    val stringRes = when (this) {
        ScreenOrientation.AUTOMATIC -> R.string.automatic
        ScreenOrientation.LANDSCAPE_AUTO -> R.string.landscape_auto
        ScreenOrientation.LANDSCAPE -> R.string.landscape
        ScreenOrientation.LANDSCAPE_REVERSE -> R.string.landscape_reverse
        ScreenOrientation.VIDEO_ORIENTATION -> R.string.video_orientation
    }

    return stringResource(id = stringRes)
}
