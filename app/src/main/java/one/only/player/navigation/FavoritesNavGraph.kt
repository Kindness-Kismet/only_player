package one.only.player.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.videopicker.navigation.FavoritesRoute
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.favoritesScreen
import one.only.player.feature.videopicker.navigation.navigateToCloudBrowse
import one.only.player.feature.videopicker.navigation.navigateToMediaPickerScreen

// 收藏作为顶级 Tab，独立于 MediaRoot，允许底栏切 Tab 时保留其自身返回栈
@Serializable
data object FavoritesRootRoute

fun NavGraphBuilder.favoritesNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<FavoritesRootRoute>(startDestination = FavoritesRoute) {
        favoritesScreen(
            onNavigateUp = navController::navigateUp,
            onPlayLocalVideo = { uri ->
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = uri
                }
                context.startActivity(intent)
            },
            onOpenLocalFolder = { folderPath ->
                navController.navigateToMediaPickerScreen(
                    folderId = folderPath,
                    screenMode = MediaPickerScreenMode.LIBRARY,
                )
            },
            onOpenRemoteDirectory = { serverId, path ->
                navController.navigateToCloudBrowse(serverId = serverId, initialPath = path)
            },
            onPlayRemoteVideo = { uri, headers, initialSubtitleDirectoryUri, playlist, playlistRemotePaths ->
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
}
