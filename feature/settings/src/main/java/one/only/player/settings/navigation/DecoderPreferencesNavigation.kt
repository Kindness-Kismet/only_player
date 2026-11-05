package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.decoder.DecoderPreferencesScreen

const val decoderPreferencesNavigationRoute = "decoder_preferences_route"

fun NavController.navigateToDecoderPreferences(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(decoderPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.decoderPreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = decoderPreferencesNavigationRoute) {
        DecoderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
