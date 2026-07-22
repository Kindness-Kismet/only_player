package one.only.player.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import one.only.player.core.common.extensions.applyNavigationBarStyle

@Composable
fun NavigationBarColorEffect(
    activity: Activity,
    color: Color,
) {
    LaunchedEffect(activity, color) {
        activity.applyNavigationBarStyle(
            color = color.toArgb(),
            shouldUseDarkIcons = color.luminance() > 0.5f,
        )
    }
}
