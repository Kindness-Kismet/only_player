package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.subtitle.SubtitlePreferencesScreen

const val subtitlePreferencesNavigationRoute = "subtitle_preferences_route"

fun NavController.navigateToSubtitlePreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(subtitlePreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.subtitlePreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = subtitlePreferencesNavigationRoute) {
        SubtitlePreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
