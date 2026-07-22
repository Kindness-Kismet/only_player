package one.only.player.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import one.only.player.settings.Setting
import one.only.player.settings.SettingsScreen
import one.only.player.settings.navigation.aboutPreferencesScreen
import one.only.player.settings.navigation.appearancePreferencesScreen
import one.only.player.settings.navigation.audioPreferencesScreen
import one.only.player.settings.navigation.decoderPreferencesScreen
import one.only.player.settings.navigation.folderPreferencesScreen
import one.only.player.settings.navigation.generalPreferencesScreen
import one.only.player.settings.navigation.gesturePreferencesScreen
import one.only.player.settings.navigation.librariesScreen
import one.only.player.settings.navigation.logsScreen
import one.only.player.settings.navigation.mediaLibraryPreferencesScreen
import one.only.player.settings.navigation.navigateToAboutPreferences
import one.only.player.settings.navigation.navigateToAppearancePreferences
import one.only.player.settings.navigation.navigateToAudioPreferences
import one.only.player.settings.navigation.navigateToDecoderPreferences
import one.only.player.settings.navigation.navigateToFolderPreferencesScreen
import one.only.player.settings.navigation.navigateToGeneralPreferences
import one.only.player.settings.navigation.navigateToGesturePreferences
import one.only.player.settings.navigation.navigateToLibraries
import one.only.player.settings.navigation.navigateToLogs
import one.only.player.settings.navigation.navigateToMediaLibraryPreferencesScreen
import one.only.player.settings.navigation.navigateToPlayerPreferences
import one.only.player.settings.navigation.navigateToPrivacyPreferences
import one.only.player.settings.navigation.navigateToSubtitlePreferences
import one.only.player.settings.navigation.navigateToThumbnailPreferencesScreen
import one.only.player.settings.navigation.playerPreferencesScreen
import one.only.player.settings.navigation.privacyPreferencesScreen
import one.only.player.settings.navigation.subtitlePreferencesScreen
import one.only.player.settings.navigation.thumbnailPreferencesScreen

@Composable
fun SettingsRootPage(
    navController: NavHostController,
) {
    SettingsScreen(
        onNavigateUp = null,
        onItemClick = { setting -> navController.navigateToSetting(setting) },
    )
}

fun NavGraphBuilder.settingsDetailNavGraph(
    navController: NavHostController,
) {
    appearancePreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    mediaLibraryPreferencesScreen(
        onNavigateUp = navController::navigateUp,
        onFolderSettingClick = navController::navigateToFolderPreferencesScreen,
        onThumbnailSettingClick = navController::navigateToThumbnailPreferencesScreen,
    )
    thumbnailPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    folderPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    playerPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    gesturePreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    decoderPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    audioPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    subtitlePreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    privacyPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    generalPreferencesScreen(
        onNavigateUp = navController::navigateUp,
    )
    aboutPreferencesScreen(
        onLibrariesClick = navController::navigateToLibraries,
        onLogsClick = navController::navigateToLogs,
        onNavigateUp = navController::navigateUp,
    )
    librariesScreen(
        onNavigateUp = navController::navigateUp,
    )
    logsScreen(
        onNavigateUp = navController::navigateUp,
    )
}

private fun NavHostController.navigateToSetting(setting: Setting) {
    when (setting) {
        Setting.APPEARANCE -> navigateToAppearancePreferences()
        Setting.MEDIA_LIBRARY -> navigateToMediaLibraryPreferencesScreen()
        Setting.PLAYER -> navigateToPlayerPreferences()
        Setting.GESTURES -> navigateToGesturePreferences()
        Setting.DECODER -> navigateToDecoderPreferences()
        Setting.AUDIO -> navigateToAudioPreferences()
        Setting.SUBTITLE -> navigateToSubtitlePreferences()
        Setting.PRIVACY -> navigateToPrivacyPreferences()
        Setting.GENERAL -> navigateToGeneralPreferences()
        Setting.ABOUT -> navigateToAboutPreferences()
    }
}
