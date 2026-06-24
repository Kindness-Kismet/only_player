package one.only.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.Logger
import one.only.player.core.common.Utils
import one.only.player.core.common.storagePermission
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.composables.PermissionMissingView
import one.only.player.core.ui.composables.rememberRuntimePermissionState
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.preview.VideoPickerPreviewParameterProvider
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.videopicker.composables.MediaView
import one.only.player.feature.videopicker.composables.NoVideosFound
import one.only.player.feature.videopicker.composables.QuickSettingsDialog
import one.only.player.feature.videopicker.composables.RenameDialog
import one.only.player.feature.videopicker.composables.SelectionMenuItem
import one.only.player.feature.videopicker.composables.TextIconToggleButton
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.state.SelectedFolder
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.rememberSelectionManager

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences) -> Unit,
    onPlayUri: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloudClick: () -> Unit,
    onCloudServerClick: (Long) -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitAppClick: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerScreen(
        uiState = uiState,
        onPlayVideo = onPlayVideo,
        onPlayUri = onPlayUri,
        onNavigateUp = onNavigateUp,
        onNavigateHome = onNavigateHome,
        onFolderClick = onFolderClick,
        onRecycleBinClick = onRecycleBinClick,
        onSearchClick = onSearchClick,
        onCloudClick = onCloudClick,
        onCloudServerClick = onCloudServerClick,
        onFavoritesClick = onFavoritesClick,
        onSettingsClick = onSettingsClick,
        onExitAppClick = onExitAppClick,
        onEvent = viewModel::onEvent,
    )
}

internal fun shouldEnableTitleLongPressHomeNavigation(
    isInSelectionMode: Boolean,
    folderName: String?,
    shouldNavigateHomeOnTitleLongPress: Boolean,
): Boolean {
    if (isInSelectionMode) return false
    if (folderName == null) return false
    return shouldNavigateHomeOnTitleLongPress
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPickerScreen(
    uiState: MediaPickerUiState,
    onNavigateUp: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onPlayVideo: (Video, PlayerPreferences) -> Unit = { _, _ -> },
    onPlayUri: (Uri) -> Unit = {},
    onFolderClick: (String, MediaPickerScreenMode) -> Unit = { _, _ -> },
    onRecycleBinClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onCloudClick: () -> Unit = {},
    onCloudServerClick: (Long) -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onExitAppClick: () -> Unit = {},
    onEvent: (MediaPickerUiEvent) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val permissionState = rememberRuntimePermissionState(permission = storagePermission)
    val lazyGridState = rememberLazyGridState()
    val context = LocalContext.current
    var restoredPlaybackAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    val selectVideoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { it?.let { onPlayUri(it) } },
    )

    var shouldShowQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var shouldShowMainMenu by rememberSaveable { mutableStateOf(false) }
    var shouldShowSelectionMenu by rememberSaveable { mutableStateOf(false) }
    var shouldShowUrlDialog by rememberSaveable { mutableStateOf(false) }

    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var shouldShowDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }
    var shouldShowMoveProgressDialog by rememberSaveable { mutableStateOf(false) }

    val isLibraryMode = uiState.screenMode == MediaPickerScreenMode.LIBRARY
    val isTitleLongPressHomeNavigationEnabled = shouldEnableTitleLongPressHomeNavigation(
        isInSelectionMode = selectionManager.isInSelectionMode,
        folderName = uiState.folderName,
        shouldNavigateHomeOnTitleLongPress = uiState.preferences.shouldNavigateHomeOnTitleLongPress,
    )

    val isRecycleBinMode = uiState.screenMode == MediaPickerScreenMode.RECYCLE_BIN
    val shouldShowRecycleBinEntry = isLibraryMode &&
        uiState.folderName == null &&
        uiState.preferences.isRecycleBinEnabled
    val homeCloudServers = uiState.homeCloudServers.takeIf {
        isLibraryMode && uiState.folderName == null
    }.orEmpty()
    val deleteAction = when {
        isRecycleBinMode -> MediaPickerDeleteAction.PermanentlyDelete
        uiState.preferences.isRecycleBinEnabled -> MediaPickerDeleteAction.MoveToRecycleBin
        else -> MediaPickerDeleteAction.PermanentlyDelete
    }
    val selectedItemsSize = selectionManager.selectedFolders.size + selectionManager.selectedVideos.size
    val totalItemsSize = (uiState.mediaDataState as? DataState.Success)?.value?.run { folderList.size + mediaList.size } ?: 0
    val recentlyPlayedVideo = (uiState.mediaDataState as? DataState.Success)?.value?.recentlyPlayedVideo
    val isMoveMode = uiState.moveSelection != null
    val moveResult = uiState.moveResult
    val moveResultMessage = when {
        moveResult == null -> null
        moveResult.canceledCount > 0 -> stringResource(R.string.move_cancelled, moveResult.movedCount, moveResult.failedCount)
        moveResult.movedCount > 0 && moveResult.failedCount > 0 -> stringResource(
            R.string.move_partial_success,
            moveResult.movedCount,
            moveResult.failedCount,
        )
        moveResult.movedCount > 0 -> stringResource(R.string.move_success, moveResult.movedCount)
        else -> stringResource(R.string.move_failed)
    }
    val deleteResultMessage = when (uiState.deleteResult) {
        MediaPickerDeleteResult.Deleted -> stringResource(R.string.delete_success)
        MediaPickerDeleteResult.MovedToRecycleBin -> stringResource(R.string.move_to_recycle_bin_success)
        MediaPickerDeleteResult.DeleteFailed -> stringResource(R.string.delete_failed)
        null -> null
    }
    val canMoveToCurrentFolder = when {
        !isMoveMode -> false
        !isLibraryMode -> false
        uiState.folderPath == null -> false
        else -> uiState.moveSelection.canMoveTo(uiState.folderPath)
    }
    val moveProgress = uiState.moveProgress

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = (
                    uiState.folderName ?: stringResource(
                        if (isRecycleBinMode) {
                            R.string.recycle_bin
                        } else {
                            R.string.app_name
                        },
                    )
                    ).takeIf { !selectionManager.isInSelectionMode } ?: "",
                fontWeight = FontWeight.Bold.takeIf { uiState.folderName == null },
                onTitleLongClick = if (isTitleLongPressHomeNavigationEnabled) {
                    { onNavigateHome() }
                } else {
                    null
                },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { selectionManager.exitSelectionMode() }
                                .padding(8.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                            Text(
                                text = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else if (uiState.folderName != null || isRecycleBinMode) {
                        FilledTonalIconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (isMoveMode) {
                        TextButton(
                            onClick = {
                                uiState.folderPath?.let { folderPath ->
                                    onEvent(MediaPickerUiEvent.MoveSelectionToFolder(folderPath))
                                }
                            },
                            enabled = canMoveToCurrentFolder && !uiState.isMovingSelection,
                            modifier = Modifier.testTag("btn_move_here"),
                        ) {
                            Text(
                                text = stringResource(
                                    if (uiState.isMovingSelection) R.string.moving else R.string.move_here,
                                ),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onEvent(MediaPickerUiEvent.CancelMoveSelection) },
                            enabled = !uiState.isMovingSelection,
                            modifier = Modifier.testTag("btn_cancel_move"),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.cancel),
                            )
                        }
                    } else if (selectionManager.isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    (uiState.mediaDataState as? DataState.Success)?.value?.let { folder ->
                                        folder.folderList.forEach { selectionManager.selectFolder(it) }
                                        folder.mediaList.forEach { selectionManager.selectVideo(it) }
                                    }
                                } else {
                                    selectionManager.clearSelection()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (selectedItemsSize != totalItemsSize) {
                                    NextIcons.SelectAll
                                } else {
                                    NextIcons.DeselectAll
                                },
                                contentDescription = if (selectedItemsSize != totalItemsSize) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                            )
                        }
                        Box {
                            FilledTonalIconButton(
                                onClick = { shouldShowSelectionMenu = true },
                                modifier = Modifier.testTag("btn_selection_actions"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Menu,
                                    contentDescription = stringResource(id = R.string.menu),
                                )
                            }
                            SelectionActionsMenu(
                                expanded = shouldShowSelectionMenu,
                                onDismissRequest = { shouldShowSelectionMenu = false },
                                deleteAction = deleteAction,
                                shouldShowRestoreAction = isRecycleBinMode,
                                shouldShowMoveAction = isLibraryMode,
                                shouldShowFavoriteAction = isLibraryMode,
                                shouldShowRenameAction = selectionManager.isSingleVideoSelected && isLibraryMode,
                                shouldShowInfoAction = selectionManager.isSingleVideoSelected,
                                shouldShowExcludeAction = selectionManager.selectedFolders.isNotEmpty() && isLibraryMode,
                                onRenameAction = {
                                    shouldShowSelectionMenu = false
                                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsMenu
                                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsMenu
                                    showRenameActionFor = video
                                },
                                onInfoAction = {
                                    shouldShowSelectionMenu = false
                                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@SelectionActionsMenu
                                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                                        ?.find { it.uriString == selectedVideo.uriString } ?: return@SelectionActionsMenu
                                    showInfoActionFor = video
                                    selectionManager.clearSelection()
                                },
                                onMoveAction = {
                                    shouldShowSelectionMenu = false
                                    onEvent(
                                        MediaPickerUiEvent.StartMoveSelection(
                                            videoUris = selectionManager.selectedVideos.map { it.uriString },
                                            folderPaths = selectionManager.selectedFolders.map { it.path },
                                        ),
                                    )
                                    selectionManager.exitSelectionMode()
                                },
                                onFavoriteAction = {
                                    shouldShowSelectionMenu = false
                                    val rootFolder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@SelectionActionsMenu
                                    val selectedVideos = selectionManager.selectedVideos.mapNotNull { selectedVideo ->
                                        rootFolder.allMediaList.firstOrNull { video -> video.uriString == selectedVideo.uriString }
                                    }
                                    val selectedFolders = selectionManager.selectedFolders.mapNotNull { selectedFolder ->
                                        rootFolder.folderList.firstOrNull { folder -> folder.path == selectedFolder.path }
                                    }
                                    onEvent(MediaPickerUiEvent.AddFavorites(selectedVideos, selectedFolders))
                                    selectionManager.exitSelectionMode()
                                },
                                onShareAction = {
                                    shouldShowSelectionMenu = false
                                    onEvent(MediaPickerUiEvent.ShareVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                                },
                                onRestoreAction = {
                                    shouldShowSelectionMenu = false
                                    onEvent(MediaPickerUiEvent.RestoreVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                                    selectionManager.clearSelection()
                                },
                                onDeleteAction = {
                                    shouldShowSelectionMenu = false
                                    shouldShowDeleteVideosConfirmation = true
                                },
                                onExcludeAction = {
                                    shouldShowSelectionMenu = false
                                    val paths = selectionManager.selectedFolders.map { it.path }
                                    onEvent(MediaPickerUiEvent.ExcludeFolders(paths))
                                    selectionManager.exitSelectionMode()
                                },
                            )
                        }
                    } else {
                        if (isLibraryMode) {
                            IconButton(onClick = onSearchClick) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(id = R.string.search),
                                )
                            }
                            if (shouldShowRecycleBinEntry) {
                                IconButton(onClick = onRecycleBinClick) {
                                    Icon(
                                        imageVector = NextIcons.DeleteSweep,
                                        contentDescription = stringResource(id = R.string.recycle_bin),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { shouldShowQuickSettingsDialog = true },
                                modifier = Modifier.testTag("btn_quick_settings"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.DashBoard,
                                    contentDescription = stringResource(id = R.string.quick_settings),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { shouldShowMainMenu = true },
                                    modifier = Modifier.testTag("btn_main_menu"),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.ExpandMore,
                                        contentDescription = stringResource(id = R.string.menu),
                                    )
                                }
                                DropdownMenu(
                                    expanded = shouldShowMainMenu,
                                    onDismissRequest = { shouldShowMainMenu = false },
                                    modifier = Modifier.testTag("menu_main_actions"),
                                    shape = RoundedCornerShape(10.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    ),
                                ) {
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.open_network_stream),
                                        icon = NextIcons.Link,
                                        testTag = "item_main_menu_network_stream",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            shouldShowUrlDialog = true
                                        },
                                    )
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.open_local_video),
                                        icon = NextIcons.FileOpen,
                                        testTag = "item_main_menu_local_video",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            selectVideoFileLauncher.launch("video/*")
                                        },
                                    )
                                    recentlyPlayedVideo?.let { video ->
                                        SelectionMenuItem(
                                            text = stringResource(id = R.string.recently_played),
                                            icon = NextIcons.History,
                                            testTag = "item_main_menu_recently_played",
                                            onClick = {
                                                shouldShowMainMenu = false
                                                onPlayVideo(video, uiState.playerPreferences)
                                            },
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                    )
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.favorites),
                                        icon = NextIcons.LibraryBooks,
                                        testTag = "item_main_menu_favorites",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onFavoritesClick()
                                        },
                                    )
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.cloud_servers),
                                        icon = NextIcons.Cloud,
                                        testTag = "item_main_menu_cloud",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onCloudClick()
                                        },
                                    )
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.settings),
                                        icon = NextIcons.Settings,
                                        testTag = "item_main_menu_settings",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onSettingsClick()
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                                    )
                                    SelectionMenuItem(
                                        text = stringResource(id = R.string.exit),
                                        icon = NextIcons.Close,
                                        testTag = "item_main_menu_exit_app",
                                        onClick = {
                                            shouldShowMainMenu = false
                                            onExitAppClick()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.displayCutout,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                PermissionMissingView(
                    isGranted = permissionState.isGranted,
                    shouldShowRationale = permissionState.shouldShowRationale,
                    permission = permissionState.permission,
                    launchPermissionRequest = { permissionState.launchPermissionRequest() },
                ) {
                    val shouldShowRefreshIndicator = uiState.isRefreshing || uiState.mediaDataState is DataState.Loading
                    val updatedScaffoldPadding = scaffoldPadding.copy(top = 0.dp, start = 0.dp).withBottomFallback()
                    PullToRefreshBox(
                        modifier = Modifier.fillMaxSize(),
                        isRefreshing = shouldShowRefreshIndicator,
                        onRefresh = { onEvent(MediaPickerUiEvent.Refresh) },
                    ) {
                        when (uiState.mediaDataState) {
                            DataState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }

                            is DataState.Error -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.unknown_error),
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }

                            is DataState.Success -> {
                                val rootFolder = uiState.mediaDataState.value
                                val hasLocalContent = rootFolder?.let {
                                    it.folderList.isNotEmpty() || it.mediaList.isNotEmpty()
                                } == true
                                val hasHomeContent = hasLocalContent || homeCloudServers.isNotEmpty()
                                if (!hasHomeContent) {
                                    NoVideosFound(contentPadding = updatedScaffoldPadding)
                                } else {
                                    MediaView(
                                        rootFolder = rootFolder ?: Folder.rootFolder,
                                        preferences = uiState.preferences,
                                        homeCloudServers = homeCloudServers,
                                        onFolderClick = {
                                            onEvent(MediaPickerUiEvent.CacheFolderSnapshot(it))
                                            onFolderClick(it.path, uiState.screenMode)
                                        },
                                        onCloudServerClick = { server -> onCloudServerClick(server.id) },
                                        onVideoClick = { video ->
                                            if (!isMoveMode) onPlayVideo(video, uiState.playerPreferences)
                                        },
                                        selectionManager = selectionManager,
                                        lazyGridState = lazyGridState,
                                        contentPadding = updatedScaffoldPadding,
                                        onVideoLoaded = { onEvent(MediaPickerUiEvent.AddToSync(it)) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (moveProgress != null) {
                    MoveProgressButton(
                        progress = moveProgress.completedCount.toFloat() / moveProgress.totalCount.coerceAtLeast(1),
                        onClick = { shouldShowMoveProgressDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(scaffoldPadding.withBottomFallback())
                            .padding(end = 21.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }

    LaunchedEffect(moveResultMessage) {
        val message = moveResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(MediaPickerUiEvent.ClearMoveResult)
    }

    LaunchedEffect(deleteResultMessage) {
        val message = deleteResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(MediaPickerUiEvent.ClearDeleteResult)
    }

    LaunchedEffect(uiState.folderPath) {
        restoredPlaybackAnchor = null
    }

    LaunchedEffect(
        uiState.folderPath,
        uiState.preferences.shouldRestoreLastPlayedMediaInFolders,
        uiState.mediaDataState,
    ) {
        if (!uiState.preferences.shouldRestoreLastPlayedMediaInFolders) return@LaunchedEffect
        val folderPath = uiState.folderPath ?: return@LaunchedEffect
        val rootFolder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@LaunchedEffect
        val playbackAnchor = uiState.preferences.localFolderLastPlayedMediaUris[folderPath]
        val recentlyPlayedVideo = rootFolder.recentlyPlayedVideo ?: return@LaunchedEffect
        val restoreToken = playbackAnchor ?: recentlyPlayedVideo.uriString
        if (restoredPlaybackAnchor == restoreToken) return@LaunchedEffect
        val scrollIndex = resolveRestoreScrollIndex(
            rootFolder = rootFolder,
            mediaViewMode = uiState.preferences.mediaViewMode,
            lastPlayedMediaUri = playbackAnchor,
            recentlyPlayedVideo = recentlyPlayedVideo,
        ) ?: return@LaunchedEffect

        Logger.debug(
            TAG,
            "Restore last played media: mode=${uiState.preferences.mediaViewMode}, index=$scrollIndex, " +
                "folders=${rootFolder.folderList.size}, videos=${rootFolder.mediaList.size}",
        )
        lazyGridState.scrollToItem(scrollIndex)
        restoredPlaybackAnchor = restoreToken
    }

    LaunchedEffect(selectionManager.isInSelectionMode, isMoveMode) {
        if (selectionManager.isInSelectionMode || isMoveMode) {
            shouldShowMainMenu = false
        } else {
            shouldShowSelectionMenu = false
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    if (shouldShowQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            onDismiss = { shouldShowQuickSettingsDialog = false },
            updatePreferences = { onEvent(MediaPickerUiEvent.UpdateMenu(it)) },
        )
    }

    if (shouldShowUrlDialog) {
        NetworkUrlDialog(
            onDismiss = { shouldShowUrlDialog = false },
            onDone = { onPlayUri(it.toUri()) },
        )
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(MediaPickerUiEvent.RenameVideo(video.uriString.toUri(), it))
                showRenameActionFor = null
                selectionManager.clearSelection()
            },
        )
    }

    showInfoActionFor?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismiss = { showInfoActionFor = null },
        )
    }

    if (shouldShowMoveProgressDialog && moveProgress != null) {
        MoveProgressDialog(
            movedCount = moveProgress.completedCount,
            totalCount = moveProgress.totalCount,
            onCancelRemaining = {
                onEvent(MediaPickerUiEvent.CancelRemainingMoveSelection)
                shouldShowMoveProgressDialog = false
            },
            onContinue = { shouldShowMoveProgressDialog = false },
        )
    }

    if (shouldShowDeleteVideosConfirmation) {
        DeleteConfirmationDialog(
            selectedVideos = selectionManager.selectedVideos,
            selectedFolders = selectionManager.selectedFolders,
            deleteAction = deleteAction,
            onConfirm = {
                when (deleteAction) {
                    MediaPickerDeleteAction.MoveToRecycleBin -> {
                        onEvent(MediaPickerUiEvent.MoveVideosToRecycleBin(selectionManager.allSelectedVideos.toList()))
                    }

                    MediaPickerDeleteAction.PermanentlyDelete -> {
                        onEvent(MediaPickerUiEvent.PermanentlyDeleteVideos(selectionManager.allSelectedVideos.toList()))
                    }
                }
                selectionManager.exitSelectionMode()
                shouldShowDeleteVideosConfirmation = false
            },
            onCancel = { shouldShowDeleteVideosConfirmation = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoveProgressButton(
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .testTag("btn_move_progress"),
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(26.dp),
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun MoveProgressDialog(
    movedCount: Int,
    totalCount: Int,
    onCancelRemaining: () -> Unit,
    onContinue: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onContinue,
        title = { Text(stringResource(R.string.move_progress_title)) },
        content = {
            Text(
                text = stringResource(
                    R.string.move_progress_message,
                    movedCount,
                    totalCount,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onCancelRemaining) {
                Text(stringResource(R.string.cancel_remaining_move))
            }
        },
        dismissButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.continue_move))
            }
        },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    modifier: Modifier = Modifier,
    selectedVideos: Set<SelectedVideo>,
    selectedFolders: Set<SelectedFolder>,
    deleteAction: MediaPickerDeleteAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                    stringResource(R.string.move_to_recycle_bin)
                } else {
                    when {
                        selectedVideos.isEmpty() -> when (selectedFolders.size) {
                            1 -> stringResource(R.string.delete_one_folder)
                            else -> stringResource(R.string.delete_folders, selectedFolders.size)
                        }

                        selectedFolders.isEmpty() -> when (selectedVideos.size) {
                            1 -> stringResource(R.string.delete_one_video)
                            else -> stringResource(R.string.delete_videos, selectedVideos.size)
                        }

                        else -> stringResource(R.string.delete_items, selectedFolders.size + selectedVideos.size)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = modifier,
            ) {
                Text(
                    text = stringResource(
                        if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                            R.string.move_to_recycle_bin
                        } else {
                            R.string.delete_permanently
                        },
                    ),
                )
            }
        },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            val selectedVideoList = selectedVideos.toList()
            val allSelectedVideos = (selectedVideoList + selectedFolders.flatMap(SelectedFolder::mediaList)).distinctBy(SelectedVideo::uriString)
            val totalDuration = allSelectedVideos.sumOf(SelectedVideo::duration)
            val totalSize = allSelectedVideos.sumOf(SelectedVideo::size)
            val warningText = if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                stringResource(R.string.move_to_recycle_bin_info)
            } else if ((selectedFolders.size + selectedVideos.size) == 1) {
                stringResource(R.string.delete_item_info)
            } else {
                stringResource(R.string.delete_items_info)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (allSelectedVideos.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.delete_summary_count, allSelectedVideos.size),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.delete_summary_size, Utils.formatFileSize(totalSize)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.delete_summary_duration, Utils.formatDurationMillis(totalDuration)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = allSelectedVideos.take(5).joinToString(separator = "\n") { it.nameWithExtension },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (allSelectedVideos.size > 5) {
                        Text(
                            text = stringResource(R.string.delete_summary_more, allSelectedVideos.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
    )
}

private fun resolveRestoreScrollIndex(
    rootFolder: Folder,
    mediaViewMode: MediaViewMode,
    lastPlayedMediaUri: String?,
    recentlyPlayedVideo: Video,
): Int? {
    val targetVideo = lastPlayedMediaUri
        ?.let { uri -> rootFolder.allMediaList.firstOrNull { video -> video.uriString == uri } }
        ?: recentlyPlayedVideo
    val targetIndex = rootFolder.mediaList.indexOfFirst { video -> video.uriString == targetVideo.uriString }
    if (targetIndex >= 0) {
        return when (mediaViewMode) {
            MediaViewMode.VIDEOS,
            MediaViewMode.FOLDERS,
            -> targetIndex

            MediaViewMode.FOLDER_TREE -> rootFolder.folderTreeVideoGridIndex(targetIndex)
        }
    }

    if (mediaViewMode == MediaViewMode.VIDEOS) return null
    val folderIndex = rootFolder.folderList.indexOfFirst { folder -> folder.isRecentlyPlayedVideo(targetVideo) }
    if (folderIndex < 0) return null

    return when (mediaViewMode) {
        MediaViewMode.FOLDERS -> folderIndex
        MediaViewMode.FOLDER_TREE -> folderIndex + rootFolder.folderHeaderOffset
        MediaViewMode.VIDEOS -> null
    }
}

private const val TAG = "MediaPickerScreen"

private val Folder.folderHeaderOffset: Int
    get() = if (folderList.isNotEmpty()) 1 else 0

private fun Folder.folderTreeVideoGridIndex(targetIndex: Int): Int {
    val spacerOffset = if (folderList.isNotEmpty()) 1 else 0
    val videoHeaderOffset = if (mediaList.isNotEmpty()) 1 else 0
    return folderHeaderOffset + folderList.size + spacerOffset + videoHeaderOffset + targetIndex
}

private enum class MediaPickerDeleteAction {
    MoveToRecycleBin,
    PermanentlyDelete,
}

@Composable
private fun SelectionActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    deleteAction: MediaPickerDeleteAction,
    shouldShowRestoreAction: Boolean,
    shouldShowMoveAction: Boolean,
    shouldShowFavoriteAction: Boolean,
    shouldShowRenameAction: Boolean,
    shouldShowInfoAction: Boolean,
    shouldShowExcludeAction: Boolean,
    onRestoreAction: () -> Unit,
    onRenameAction: () -> Unit,
    onInfoAction: () -> Unit,
    onMoveAction: () -> Unit,
    onFavoriteAction: () -> Unit,
    onShareAction: () -> Unit,
    onDeleteAction: () -> Unit,
    onExcludeAction: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("menu_selection_actions"),
        shape = RoundedCornerShape(10.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        ),
    ) {
        if (shouldShowRestoreAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.restore),
                icon = NextIcons.ArrowUpward,
                testTag = "item_selection_restore",
                onClick = onRestoreAction,
            )
        }
        if (shouldShowMoveAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.move),
                icon = NextIcons.Folder,
                testTag = "item_selection_move",
                onClick = onMoveAction,
            )
        }
        if (shouldShowFavoriteAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.add_to_favorites),
                icon = NextIcons.LibraryBooks,
                testTag = "item_selection_add_favorites",
                onClick = onFavoriteAction,
            )
        }
        if (shouldShowRenameAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.rename),
                icon = NextIcons.Edit,
                testTag = "item_selection_rename",
                onClick = onRenameAction,
            )
        }
        if (shouldShowInfoAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.info),
                icon = NextIcons.Info,
                testTag = "item_selection_info",
                onClick = onInfoAction,
            )
        }
        SelectionMenuItem(
            text = stringResource(id = R.string.share),
            icon = NextIcons.Share,
            testTag = "item_selection_share",
            onClick = onShareAction,
        )
        if (shouldShowExcludeAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.exclude),
                icon = NextIcons.FolderOff,
                testTag = "item_selection_exclude",
                onClick = onExcludeAction,
            )
        }
        SelectionMenuItem(
            text = stringResource(
                id = when (deleteAction) {
                    MediaPickerDeleteAction.MoveToRecycleBin -> R.string.move_to_recycle_bin
                    MediaPickerDeleteAction.PermanentlyDelete -> {
                        if (shouldShowRestoreAction) {
                            R.string.delete_permanently
                        } else {
                            R.string.delete
                        }
                    }
                },
            ),
            icon = NextIcons.Delete,
            testTag = "item_selection_delete",
            onClick = onDeleteAction,
        )
    }
}

@Composable
private fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_stream)) },
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.example_url)) },
            )
        },
        confirmButton = {
            DoneButton(
                isEnabled = url.isNotBlank(),
                onClick = { onDone(url) },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@PreviewScreenSizes
@PreviewLightDark
@Composable
private fun MediaPickerScreenPreview(
    @PreviewParameter(VideoPickerPreviewParameterProvider::class)
    videos: List<Video>,
) {
    OnlyPlayerTheme {
        MediaPickerScreen(
            uiState = MediaPickerUiState(
                folderPath = null,
                folderName = null,
                mediaDataState = DataState.Success(
                    value = Folder(
                        name = "Root Folder",
                        path = "/root",
                        dateModified = System.currentTimeMillis(),
                        folderList = listOf(
                            Folder(name = "Folder 1", path = "/root/folder1", dateModified = System.currentTimeMillis()),
                            Folder(name = "Folder 2", path = "/root/folder2", dateModified = System.currentTimeMillis()),
                        ),
                        mediaList = videos,
                    ),
                ),
                preferences = ApplicationPreferences().copy(
                    mediaViewMode = MediaViewMode.FOLDER_TREE,
                    mediaLayoutMode = MediaLayoutMode.GRID,
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun ButtonPreview() {
    Surface {
        TextIconToggleButton(
            text = "Title",
            icon = NextIcons.Title,
            onClick = {},
        )
    }
}

@DayNightPreview
@Composable
private fun MediaPickerNoVideosFoundPreview() {
    OnlyPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderPath = null,
                    folderName = null,
                    mediaDataState = DataState.Success(null),
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}

@DayNightPreview
@Composable
private fun MediaPickerLoadingPreview() {
    OnlyPlayerTheme {
        Surface {
            MediaPickerScreen(
                uiState = MediaPickerUiState(
                    folderPath = null,
                    folderName = null,
                    mediaDataState = DataState.Loading,
                    preferences = ApplicationPreferences(),
                ),
            )
        }
    }
}
