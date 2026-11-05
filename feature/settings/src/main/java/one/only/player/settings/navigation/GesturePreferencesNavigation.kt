package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.gesture.GesturePreferencesScreen

const val gesturePreferencesNavigationRoute = "gesture_preferences_route"

fun NavController.navigateToGesturePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(gesturePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.gesturePreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = gesturePreferencesNavigationRoute) {
        GesturePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
