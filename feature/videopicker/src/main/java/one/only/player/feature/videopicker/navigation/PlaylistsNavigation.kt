package one.only.player.feature.videopicker.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.feature.videopicker.screens.playlists.PlaylistsRoute as PlaylistsScreenRoute

@Serializable
data object PlaylistsRoute

fun NavController.navigateToPlaylists(navOptions: NavOptions? = null) {
    this.navigate(PlaylistsRoute, navOptions)
}

fun NavGraphBuilder.playlistsScreen(
    onNavigateUp: () -> Unit,
    onPlayVideos: (video: Video, playerPreferences: PlayerPreferences, playlist: List<Uri>) -> Unit,
) {
    miuixComposable<PlaylistsRoute> {
        PlaylistsScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayVideos = onPlayVideos,
        )
    }
}
