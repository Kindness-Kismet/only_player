package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.player.PlayerControlsCustomizeScreen
import one.only.player.settings.screens.player.PlayerPreferencesScreen

const val playerPreferencesNavigationRoute = "player_preferences_route"
const val playerControlsCustomizeNavigationRoute = "player_controls_customize_route"

fun NavController.navigateToPlayerPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(playerPreferencesNavigationRoute, navOptions)
}

fun NavController.navigateToPlayerControlsCustomize(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(playerControlsCustomizeNavigationRoute, navOptions)
}

fun NavGraphBuilder.playerPreferencesScreen(
    onNavigateUp: () -> Unit,
    onCustomizeControlsClick: () -> Unit,
) {
    miuixComposable(route = playerPreferencesNavigationRoute) {
        PlayerPreferencesScreen(
            onNavigateUp = onNavigateUp,
            onCustomizeControlsClick = onCustomizeControlsClick,
        )
    }
}

fun NavGraphBuilder.playerControlsCustomizeScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = playerControlsCustomizeNavigationRoute) {
        PlayerControlsCustomizeScreen(onNavigateUp = onNavigateUp)
    }
}
