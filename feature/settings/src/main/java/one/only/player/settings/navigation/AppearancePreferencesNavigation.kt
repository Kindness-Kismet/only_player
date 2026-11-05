package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.appearance.AppearancePreferencesScreen

const val appearancePreferencesNavigationRoute = "appearance_preferences_route"

fun NavController.navigateToAppearancePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(appearancePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.appearancePreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = appearancePreferencesNavigationRoute) {
        AppearancePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
