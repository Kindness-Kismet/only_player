package one.only.player.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.feature.videopicker.screens.favorites.FavoritesRoute as FavoritesScreenRoute

@Serializable
data object FavoritesRoute

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) {
    this.navigate(FavoritesRoute, navOptions)
}

fun NavGraphBuilder.favoritesScreen(
    onNavigateUp: () -> Unit,
    onPlayLocalVideo: (Uri) -> Unit,
    onOpenLocalFolder: (String) -> Unit,
    onOpenRemoteDirectory: (Long, String) -> Unit,
    onPlayRemoteVideo: (Uri, Map<String, String>, Uri?, List<Uri>, List<String>) -> Unit,
) {
    miuixComposable<FavoritesRoute> {
        FavoritesScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayLocalVideo = onPlayLocalVideo,
            onOpenLocalFolder = onOpenLocalFolder,
            onOpenRemoteDirectory = onOpenRemoteDirectory,
            onPlayRemoteVideo = onPlayRemoteVideo,
        )
    }
}
