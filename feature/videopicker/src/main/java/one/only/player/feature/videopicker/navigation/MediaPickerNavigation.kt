package one.only.player.feature.videopicker.navigation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.feature.videopicker.screens.mediapicker.MediaPickerRoute

const val folderIdArg = "folderId"
const val screenModeArg = "screenMode"

internal class FolderArgs(
    val folderId: String?,
    val screenMode: MediaPickerScreenMode,
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            folderId = when (val rawFolderId = savedStateHandle.get<Any?>(folderIdArg)) {
                is String -> Uri.decode(rawFolderId)
                else -> null
            },
            screenMode = when (val rawScreenMode = savedStateHandle.get<Any?>(screenModeArg)) {
                is MediaPickerScreenMode -> rawScreenMode
                is String -> rawScreenMode.let(MediaPickerScreenMode::valueOf)
                else -> MediaPickerScreenMode.LIBRARY
            },
        )
}

@Serializable
enum class MediaPickerScreenMode {
    LIBRARY,
    RECYCLE_BIN,
}

@Serializable
data class MediaPickerRoute(
    val folderId: String? = null,
    val screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
)

fun NavController.navigateToMediaPickerScreen(
    folderId: String,
    screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
    navOptions: NavOptions? = null,
) {
    val encodedFolderId = Uri.encode(folderId)
    this.navigate(MediaPickerRoute(folderId = encodedFolderId, screenMode = screenMode), navOptions)
}

fun NavController.navigateToRecycleBinScreen(navOptions: NavOptions? = null) {
    this.navigate(MediaPickerRoute(screenMode = MediaPickerScreenMode.RECYCLE_BIN), navOptions)
}

fun NavController.navigateToMoveTargetScreen(navOptions: NavOptions? = null) {
    this.navigate(MediaPickerRoute(), navOptions)
}

fun NavGraphBuilder.mediaPickerScreen(
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences) -> Unit,
    onPlayUri: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onAncestorFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onCloudClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitAppClick: () -> Unit,
    onMoveSelectionStarted: () -> Unit,
    onMoveSelectionClosed: () -> Unit,
) {
    miuixComposable<MediaPickerRoute> {
        MediaPickerRoute(
            onPlayVideo = onPlayVideo,
            onPlayUri = onPlayUri,
            onNavigateUp = onNavigateUp,
            onNavigateHome = onNavigateHome,
            onFolderClick = onFolderClick,
            onAncestorFolderClick = onAncestorFolderClick,
            onRecycleBinClick = onRecycleBinClick,
            onSearchClick = onSearchClick,
            onHistoryClick = onHistoryClick,
            onPlaylistsClick = onPlaylistsClick,
            onCloudClick = onCloudClick,
            onFavoritesClick = onFavoritesClick,
            onSettingsClick = onSettingsClick,
            onExitAppClick = onExitAppClick,
            onMoveSelectionStarted = onMoveSelectionStarted,
            onMoveSelectionClosed = onMoveSelectionClosed,
        )
    }
}
