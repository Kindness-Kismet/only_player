package one.only.player.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import one.only.player.core.common.extensions.applyNavigationBarStyle

fun resolveNavigationBarColor(
    destination: NavDestination?,
    defaultColor: Color,
    settingsColor: Color,
): Color = when {
    destination.isInSettingsGraph() -> settingsColor
    else -> defaultColor
}

private fun NavDestination?.isInSettingsGraph(): Boolean = this
    ?.hierarchy
    ?.any { destination -> destination.route == SETTINGS_ROUTE } == true

@Composable
fun NavigationBarColorEffect(
    activity: Activity,
    navController: NavHostController,
    defaultColor: Color,
    settingsColor: Color,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    NavigationBarColorEffect(
        activity = activity,
        color = resolveNavigationBarColor(
            destination = backStackEntry?.destination,
            defaultColor = defaultColor,
            settingsColor = settingsColor,
        ),
    )
}

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
