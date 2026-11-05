package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.general.GeneralPreferencesScreen

const val generalPreferencesNavigationRoute = "general_preferences_route"

fun NavController.navigateToGeneralPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(generalPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.generalPreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = generalPreferencesNavigationRoute) {
        GeneralPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
