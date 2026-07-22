package one.only.player.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.videopicker.navigation.cloudBrowseScreen
import one.only.player.feature.videopicker.navigation.navigateToCloudBrowse
import one.only.player.feature.videopicker.screens.cloud.CloudHomeRoute

@Composable
fun CloudRootPage(
    navController: NavHostController,
) {
    CloudHomeRoute(
        onNavigateUp = navController::navigateUp,
        onServerClick = navController::navigateToCloudBrowse,
    )
}

fun NavGraphBuilder.cloudDetailNavGraph(
    context: Context,
    navController: NavHostController,
) {
    cloudBrowseScreen(
        onNavigateUp = navController::navigateUp,
        onDirectoryClick = { serverId, path ->
            navController.navigateToCloudBrowse(serverId = serverId, initialPath = path)
        },
        onPlayVideo = { uri, headers, initialSubtitleDirectoryUri, playlist, playlistRemotePaths ->
            val intent = Intent(context, PlayerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                if (headers.isNotEmpty()) {
                    val headerBundle = Bundle().apply {
                        headers.forEach { (key, value) -> putString(key, value) }
                    }
                    putExtra("headers", headerBundle)
                }
                putExtra("initial_subtitle_directory_uri", initialSubtitleDirectoryUri)
                if (playlist.size > 1) {
                    putParcelableArrayListExtra("video_list", ArrayList(playlist))
                    putStringArrayListExtra("video_remote_paths", ArrayList(playlistRemotePaths))
                }
            }
            context.startActivity(intent)
        },
    )
}
