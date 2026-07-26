package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import one.only.player.settings.screens.medialibrary.FileExtensionPreferencesScreen
import one.only.player.settings.screens.medialibrary.FolderPreferencesScreen
import one.only.player.settings.screens.medialibrary.MediaLibraryPreferencesScreen

const val mediaLibraryPreferencesNavigationRoute = "media_library_preferences_route"
const val folderPreferencesNavigationRoute = "folder_preferences_route"
const val fileExtensionPreferencesNavigationRoute = "file_extension_preferences_route"

fun NavController.navigateToMediaLibraryPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(mediaLibraryPreferencesNavigationRoute, navOptions)
}

fun NavController.navigateToFolderPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(folderPreferencesNavigationRoute, navOptions)
}

fun NavController.navigateToFileExtensionPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(fileExtensionPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.mediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
    onFileExtensionSettingClick: () -> Unit = {},
) {
    composable(route = mediaLibraryPreferencesNavigationRoute) {
        MediaLibraryPreferencesScreen(
            onNavigateUp = onNavigateUp,
            onFolderSettingClick = onFolderSettingClick,
            onThumbnailSettingClick = onThumbnailSettingClick,
            onFileExtensionSettingClick = onFileExtensionSettingClick,
        )
    }
}

fun NavGraphBuilder.folderPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = folderPreferencesNavigationRoute) {
        FolderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}

fun NavGraphBuilder.fileExtensionPreferencesScreen(onNavigateUp: () -> Unit) {
    composable(route = fileExtensionPreferencesNavigationRoute) {
        FileExtensionPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
