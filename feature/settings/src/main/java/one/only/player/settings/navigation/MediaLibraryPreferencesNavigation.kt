package one.only.player.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.settings.screens.medialibrary.FolderPreferencesScreen
import one.only.player.settings.screens.medialibrary.MediaLibraryPreferencesScreen
import one.only.player.settings.screens.medialibrary.ScanFolderPreferencesScreen

const val mediaLibraryPreferencesNavigationRoute = "media_library_preferences_route"
const val folderPreferencesNavigationRoute = "folder_preferences_route"
const val scanFolderPreferencesNavigationRoute = "scan_folder_preferences_route"

fun NavController.navigateToMediaLibraryPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(mediaLibraryPreferencesNavigationRoute, navOptions)
}

fun NavController.navigateToFolderPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(folderPreferencesNavigationRoute, navOptions)
}

fun NavController.navigateToScanFolderPreferencesScreen(navOptions: NavOptions? = navOptions { launchSingleTop = true }) {
    this.navigate(scanFolderPreferencesNavigationRoute, navOptions)
}

fun NavGraphBuilder.mediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onScanFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
) {
    miuixComposable(route = mediaLibraryPreferencesNavigationRoute) {
        MediaLibraryPreferencesScreen(
            onNavigateUp = onNavigateUp,
            onFolderSettingClick = onFolderSettingClick,
            onScanFolderSettingClick = onScanFolderSettingClick,
            onThumbnailSettingClick = onThumbnailSettingClick,
        )
    }
}

fun NavGraphBuilder.folderPreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = folderPreferencesNavigationRoute) {
        FolderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}

fun NavGraphBuilder.scanFolderPreferencesScreen(onNavigateUp: () -> Unit) {
    miuixComposable(route = scanFolderPreferencesNavigationRoute) {
        ScanFolderPreferencesScreen(onNavigateUp = onNavigateUp)
    }
}
