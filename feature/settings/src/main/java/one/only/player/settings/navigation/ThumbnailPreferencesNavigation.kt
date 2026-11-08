package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.thumbnail.ThumbnailPreferencesScreen

const val thumbnailPreferencesNavigationRoute = "thumbnail_preferences_route"

fun NavController.navigateToThumbnailPreferencesScreen(
    navOptions: NavOptions? = navOptions { launchSingleTop = true },
) {
    this.navigate(thumbnailPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.thumbnailPreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = thumbnailPreferencesNavigationRoute) {
        ThumbnailPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
