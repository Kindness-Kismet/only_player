package one.only.player.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import one.only.player.MainActivity
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.ApplicationPreferences
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
import one.only.player.feature.videopicker.navigation.mediaPickerScreen
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
    onCloudClick: (() -> Unit)? = null,
    onFavoritesClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
) {
    // Bound method refs can be KFunction; wrap as lambdas for () -> Unit params.
    val cloudClick: () -> Unit = onCloudClick ?: { navController.navigateToCloudHome() }
    val favoritesClick: () -> Unit = onFavoritesClick ?: { navController.navigateToFavorites() }
    val settingsClick: () -> Unit = onSettingsClick ?: { navController.navigateToSettings() }
    val preferencesRepository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        MediaNavGraphEntryPoint::class.java,
    ).preferencesRepository()
    navigation<MediaRootRoute>(startDestination = MediaPickerRoute()) {
        mediaPickerScreen(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = {
                navController.popBackStack(MediaPickerRoute(), inclusive = false)
            },
            onSettingsClick = settingsClick,
            onPlayVideo = { video, playerPreferences ->
                context.startPlayerActivity(
                    uri = video.uriString.toUri(),
                    title = video.nameWithExtension,
                    launchOrientation = video.resolveLaunchOrientation(
                        playerPreferences = playerPreferences,
                        applicationPreferences = preferencesRepository.applicationPreferences.value,
                    ),
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
            onCloudClick = cloudClick,
            onFavoritesClick = favoritesClick,
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
                    title = video.nameWithExtension,
                    launchOrientation = video.resolveLaunchOrientation(
                        playerPreferences = playerPreferences,
                        applicationPreferences = preferencesRepository.applicationPreferences.value,
                    ),
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
}



@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaNavGraphEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}

private fun Context.startPlayerActivity(
    uri: Uri,
    title: String? = null,
    launchOrientation: Int? = null,
    playlist: List<Uri> = emptyList(),
) {
    val activityClass = launchOrientation.playerActivityClass()
    val intent = Intent(this, activityClass).apply {
        action = Intent.ACTION_VIEW
        data = uri
        // content:// 无扩展名时，把文件名塞进 title，供开播盖 content_scale / per-file 命中。
        title?.takeIf { it.isNotBlank() }?.let {
            putExtra(one.only.player.feature.player.utils.PlayerApi.API_TITLE, it)
        }
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

private fun Video.resolveLaunchOrientation(
    playerPreferences: PlayerPreferences,
    applicationPreferences: ApplicationPreferences,
): Int? {
    // 1) 文件级记住方向：开播即用，避免先竖后横闪一下
    val perFileOrientation = applicationPreferences
        .perFilePreferenceForPath(nameWithExtension)
        ?.screenOrientation
        ?.toActivityOrientation()
    if (perFileOrientation != null) return perFileOrientation

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
