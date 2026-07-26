package one.only.player.feature.videopicker.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.feature.videopicker.screens.search.SearchRoute

@Serializable
object SearchRoute

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    this.navigate(SearchRoute, navOptions)
}

fun NavGraphBuilder.searchScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences, playlist: List<Video>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
) {
    composable<SearchRoute> {
        SearchRoute(
            onPlayVideo = onPlayVideo,
            onNavigateUp = onNavigateUp,
            onFolderClick = onFolderClick,
        )
    }
}
