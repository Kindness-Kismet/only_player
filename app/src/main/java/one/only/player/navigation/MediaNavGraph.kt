package one.only.player.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import one.only.player.MainActivity
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.model.Video
import one.only.player.feature.player.LandscapePlayerActivity
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.player.PortraitPlayerActivity
import one.only.player.feature.player.extensions.toActivityOrientation
import one.only.player.feature.player.service.PlayerService
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.mediaPickerScreen
import one.only.player.feature.videopicker.navigation.navigateToMediaPickerScreen
import one.only.player.feature.videopicker.navigation.navigateToRecycleBinScreen
import one.only.player.feature.videopicker.navigation.navigateToSearch
import one.only.player.feature.videopicker.navigation.searchScreen
import one.only.player.feature.videopicker.screens.mediapicker.MediaPickerRoute as MediaPickerScreenRoute

@Composable
fun MediaRootPage(
    context: Context,
    navController: NavHostController,
    onRootSelected: (RootDestination) -> Unit,
) {
    MediaPickerScreenRoute(
        onNavigateUp = navController::navigateUp,
        onNavigateHome = {},
        onSettingsClick = { onRootSelected(RootDestination.SETTINGS) },
        onPlayVideo = { video, playerPreferences ->
            context.startPlayerActivity(
                uri = video.uriString.toUri(),
                launchOrientation = video.resolveLaunchOrientation(playerPreferences),
            )
        },
        onPlayUri = context::startPlayerActivity,
        onFolderClick = { folderPath, screenMode ->
            navController.navigateToMediaPickerScreen(
                folderId = folderPath,
                screenMode = screenMode,
            )
        },
        onRecycleBinClick = navController::navigateToRecycleBinScreen,
        onSearchClick = navController::navigateToSearch,
        onCloudClick = { onRootSelected(RootDestination.CLOUD) },
        onFavoritesClick = { onRootSelected(RootDestination.FAVORITES) },
        onExitAppClick = { context.exitApp() },
    )
}

fun NavGraphBuilder.mediaDetailNavGraph(
    context: Context,
    navController: NavHostController,
    onRootSelected: (RootDestination) -> Unit,
) {
    mediaPickerScreen(
        onNavigateUp = navController::navigateUp,
        onNavigateHome = { onRootSelected(RootDestination.HOME) },
        onSettingsClick = { onRootSelected(RootDestination.SETTINGS) },
        onPlayVideo = { video, playerPreferences ->
            context.startPlayerActivity(
                uri = video.uriString.toUri(),
                launchOrientation = video.resolveLaunchOrientation(playerPreferences),
            )
        },
        onPlayUri = context::startPlayerActivity,
        onFolderClick = { folderPath, screenMode ->
            navController.navigateToMediaPickerScreen(
                folderId = folderPath,
                screenMode = screenMode,
            )
        },
        onRecycleBinClick = navController::navigateToRecycleBinScreen,
        onSearchClick = navController::navigateToSearch,
        onCloudClick = { onRootSelected(RootDestination.CLOUD) },
        onFavoritesClick = { onRootSelected(RootDestination.FAVORITES) },
        onExitAppClick = { context.exitApp() },
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
}

private fun Context.exitApp() {
    stopService(Intent(this, PlayerService::class.java))
    (this as? MainActivity)?.finishAffinity()
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
