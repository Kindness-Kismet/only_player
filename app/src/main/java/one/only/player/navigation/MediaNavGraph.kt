package one.only.player.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import one.only.player.MainActivity
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.model.Video
import one.only.player.feature.player.LandscapePlayerActivity
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.player.PortraitPlayerActivity
import one.only.player.feature.player.extensions.toActivityOrientation
import one.only.player.feature.player.service.PlayerService
import one.only.player.feature.videopicker.navigation.MediaPickerRoute
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.favoritesScreen
import one.only.player.feature.videopicker.navigation.mediaPickerScreen
import one.only.player.feature.videopicker.navigation.navigateToCloudBrowse
import one.only.player.feature.videopicker.navigation.navigateToCloudHome
import one.only.player.feature.videopicker.navigation.navigateToFavorites
import one.only.player.feature.videopicker.navigation.navigateToMediaPickerScreen
import one.only.player.feature.videopicker.navigation.navigateToRecycleBinScreen
import one.only.player.feature.videopicker.navigation.navigateToSearch
import one.only.player.feature.videopicker.navigation.searchScreen
import one.only.player.settings.navigation.navigateToSettings

@Serializable
data object MediaRootRoute

fun NavGraphBuilder.mediaNavGraph(
    context: Context,
    navController: NavHostController,
) {
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = {
                navController.popBackStack(MediaPickerRoute(), inclusive = false)
            },
            onSettingsClick = navController::navigateToSettings,
            onPlayVideo = { video, playerPreferences ->
                context.startPlayerActivity(
                    uri = video.uriString.toUri(),
                    launchOrientation = video.resolveLaunchOrientation(playerPreferences),
                )
            },
            onPlayUri = { uri ->
                context.startPlayerActivity(uri = uri)
            },
            onFolderClick = { folderPath, screenMode ->
                navController.navigateToMediaPickerScreen(
                    folderId = folderPath,
                    screenMode = screenMode,
                )
            },
            onRecycleBinClick = navController::navigateToRecycleBinScreen,
            onSearchClick = navController::navigateToSearch,
            onCloudClick = navController::navigateToCloudHome,
            onCloudServerClick = navController::navigateToCloudBrowse,
            onFavoritesClick = navController::navigateToFavorites,
            onExitAppClick = {
                context.stopService(Intent(context, PlayerService::class.java))
                navController.popBackStack(MediaPickerRoute(), inclusive = false)
                (context as? MainActivity)?.finishAffinity()
            },
        )

        searchScreen(
            onNavigateUp = navController::navigateUp,
            onPlayVideo = { video, playerPreferences, playlist ->
                context.startPlayerActivity(
                    uri = video.uriString.toUri(),
                    launchOrientation = video.resolveLaunchOrientation(playerPreferences),
                    playlist = playlist.map { it.uriString.toUri() },
                )
            },
            onFolderClick = { folderPath ->
                navController.navigateToMediaPickerScreen(
                    folderId = folderPath,
                    screenMode = MediaPickerScreenMode.LIBRARY,
                )
            },
        )

        favoritesScreen(
            onNavigateUp = navController::navigateUp,
            onPlayLocalVideo = { uri ->
                context.startPlayerActivity(uri = uri)
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
                context.startRemotePlayerActivity(
                    uri = uri,
                    headers = headers,
                    initialSubtitleDirectoryUri = initialSubtitleDirectoryUri,
                    playlist = playlist,
                    playlistRemotePaths = playlistRemotePaths,
                )
            },
        )
    }
}

private fun Context.startPlayerActivity(
    uri: Uri,
    launchOrientation: Int? = null,
    playlist: List<Uri> = emptyList(),
) {
    val activityClass = launchOrientation.playerActivityClass()
    val intent = Intent(this, activityClass).apply {
        action = Intent.ACTION_VIEW
        data = uri
        launchOrientation?.takeIf { activityClass == PlayerActivity::class.java }?.let {
            putExtra(PlayerActivity.EXTRA_LAUNCH_ORIENTATION, it)
        }
        if (playlist.isNotEmpty()) {
            putParcelableArrayListExtra("video_list", ArrayList(playlist))
        }
    }
    startActivity(intent)
}

private fun Context.startRemotePlayerActivity(
    uri: Uri,
    headers: Map<String, String>,
    initialSubtitleDirectoryUri: Uri?,
    playlist: List<Uri>,
    playlistRemotePaths: List<String>,
) {
    val intent = Intent(this, PlayerActivity::class.java).apply {
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
    startActivity(intent)
}

private fun Int?.playerActivityClass(): Class<out PlayerActivity> = when (this) {
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> LandscapePlayerActivity::class.java
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> PortraitPlayerActivity::class.java
    else -> PlayerActivity::class.java
}

private fun Video.resolveLaunchOrientation(playerPreferences: PlayerPreferences): Int? {
    val videoOrientation = resolveVideoOrientation()
    if (playerPreferences.playerScreenOrientation == ScreenOrientation.VIDEO_ORIENTATION) {
        return videoOrientation
    }

    val rememberedOrientation = playerPreferences.lastPlayerScreenOrientation
        ?.takeIf { playerPreferences.shouldRememberPlayerScreenOrientation }
        ?.toActivityOrientation()
    if (rememberedOrientation != null) return rememberedOrientation

    return playerPreferences.playerScreenOrientation.toActivityOrientation()
}

private fun Video.resolveVideoOrientation(): Int? {
    if (width <= 0 || height <= 0) return null

    return if (height >= width) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}
