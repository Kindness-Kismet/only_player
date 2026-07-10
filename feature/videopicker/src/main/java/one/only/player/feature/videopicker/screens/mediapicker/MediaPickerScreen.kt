package one.only.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
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
import one.only.player.feature.videopicker.composables.TextIconToggleButton
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.state.SelectedFolder
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.rememberSelectionManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences) -> Unit,
    onPlayUri: (uri: Uri) -> Unit,
    onFolderClick: (folderPath: String, screenMode: MediaPickerScreenMode) -> Unit,
    onRecycleBinClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCloudClick: () -> Unit,
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

    val selectedCountTitle = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize)
    val topBarTitle = when {
        selectionManager.isInSelectionMode -> selectedCountTitle
        isMoveMode -> stringResource(R.string.move_here)
        else -> uiState.folderName ?: stringResource(
            if (isRecycleBinMode) R.string.recycle_bin else R.string.app_name,
        )
    }
    val shouldUseLargeTopBar = !selectionManager.isInSelectionMode &&
        !isMoveMode &&
        isLibraryMode &&
        uiState.folderName == null

    Scaffold(
        topBar = {
            MediaPickerTopAppBar(
                title = topBarTitle,
                shouldUseLargeTitle = shouldUseLargeTopBar,
                largeTitlePadding = TopAppBarDefaults.TitlePadding,
                smallTitlePadding = 16.dp,
                isTitleLongPressHomeNavigationEnabled = isTitleLongPressHomeNavigationEnabled,
                onTitleLongPress = onNavigateHome,
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        IconButton(
                            onClick = { selectionManager.exitSelectionMode() },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("btn_selection_exit"),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else if (uiState.folderName != null || isRecycleBinMode) {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("btn_media_picker_back"),
                        ) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                actions = {
                    if (isMoveMode) {
                        TextButton(
                            text = stringResource(
                                if (uiState.isMovingSelection) R.string.moving else R.string.move_here,
                            ),
                            onClick = {
                                uiState.folderPath?.let { folderPath ->
                                    onEvent(MediaPickerUiEvent.MoveSelectionToFolder(folderPath))
                                }
                            },
                            enabled = canMoveToCurrentFolder && !uiState.isMovingSelection,
                            modifier = Modifier.testTag("btn_move_here"),
                        )
                        IconButton(
                            onClick = { onEvent(MediaPickerUiEvent.CancelMoveSelection) },
                            enabled = !uiState.isMovingSelection,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("btn_cancel_move"),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.cancel),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else if (selectionManager.isInSelectionMode) {
                        IconButton(
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
                            modifier = Modifier.testTag("btn_selection_toggle_all"),
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
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { shouldShowSelectionMenu = true },
                                holdDownState = shouldShowSelectionMenu,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .testTag("btn_selection_actions"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Menu,
                                    contentDescription = stringResource(id = R.string.menu),
                                    tint = MiuixTheme.colorScheme.onBackground,
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
                            IconButton(
                                onClick = onSearchClick,
                                modifier = Modifier.testTag("btn_media_picker_search"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(id = R.string.search),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                            if (shouldShowRecycleBinEntry) {
                                IconButton(
                                    onClick = onRecycleBinClick,
                                    modifier = Modifier.testTag("btn_media_picker_recycle_bin"),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.DeleteSweep,
                                        contentDescription = stringResource(id = R.string.recycle_bin),
                                        tint = MiuixTheme.colorScheme.onBackground,
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
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { shouldShowMainMenu = true },
                                    holdDownState = shouldShowMainMenu,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .testTag("btn_main_menu"),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.ExpandMore,
                                        contentDescription = stringResource(id = R.string.menu),
                                        tint = MiuixTheme.colorScheme.onBackground,
                                    )
                                }
                                MainMenuPopup(
                                    expanded = shouldShowMainMenu,
                                    onDismissRequest = { shouldShowMainMenu = false },
                                    onOpenNetworkStream = {
                                        shouldShowMainMenu = false
                                        shouldShowUrlDialog = true
                                    },
                                    onOpenLocalVideo = {
                                        shouldShowMainMenu = false
                                        selectVideoFileLauncher.launch("video/*")
                                    },
                                    onOpenRecentlyPlayed = recentlyPlayedVideo?.let { video ->
                                        {
                                            shouldShowMainMenu = false
                                            onPlayVideo(video, uiState.playerPreferences)
                                        }
                                    },
                                    onExit = {
                                        shouldShowMainMenu = false
                                        onExitAppClick()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.displayCutout,
        containerColor = MiuixTheme.colorScheme.background,
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
                    .background(MiuixTheme.colorScheme.background),
            ) {
                PermissionMissingView(
                    isGranted = permissionState.isGranted,
                    shouldShowRationale = permissionState.shouldShowRationale,
                    permission = permissionState.permission,
                    launchPermissionRequest = { permissionState.launchPermissionRequest() },
                ) {
                    val shouldShowRefreshIndicator = uiState.isRefreshing || uiState.mediaDataState is DataState.Loading
                    val updatedScaffoldPadding = scaffoldPadding.copy(top = 0.dp, start = 0.dp).withBottomFallback()
                    PullToRefresh(
                        modifier = Modifier.fillMaxSize(),
                        isRefreshing = shouldShowRefreshIndicator,
                        onRefresh = { onEvent(MediaPickerUiEvent.Refresh) },
                    ) {
                        when (uiState.mediaDataState) {
                            DataState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize())
                            }

                            is DataState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.background),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.unknown_error),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }

                            is DataState.Success -> {
                                val rootFolder = uiState.mediaDataState.value
                                if (rootFolder == null || rootFolder.folderList.isEmpty() && rootFolder.mediaList.isEmpty()) {
                                    NoVideosFound(contentPadding = updatedScaffoldPadding)
                                } else {
                                    MediaView(
                                        rootFolder = rootFolder,
                                        preferences = uiState.preferences,
                                        onFolderClick = {
                                            onEvent(MediaPickerUiEvent.CacheFolderSnapshot(it))
                                            onFolderClick(it.path, uiState.screenMode)
                                        },
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

@Composable
private fun MediaPickerTopAppBar(
    title: String,
    shouldUseLargeTitle: Boolean,
    largeTitlePadding: Dp,
    smallTitlePadding: Dp,
    isTitleLongPressHomeNavigationEnabled: Boolean,
    onTitleLongPress: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    if (shouldUseLargeTitle) {
        TopAppBar(
            title = title,
            titlePadding = largeTitlePadding,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }

    MediaPickerSmallTitleTopAppBar(
        title = title,
        titlePadding = smallTitlePadding,
        isTitleLongPressHomeNavigationEnabled = isTitleLongPressHomeNavigationEnabled,
        onTitleLongPress = onTitleLongPress,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
private fun MediaPickerSmallTitleTopAppBar(
    title: String,
    titlePadding: Dp,
    isTitleLongPressHomeNavigationEnabled: Boolean,
    onTitleLongPress: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val titleLongPressModifier = if (isTitleLongPressHomeNavigationEnabled) {
        Modifier.pointerInput(onTitleLongPress) {
            detectTapGestures(onLongPress = { onTitleLongPress() })
        }
    } else {
        Modifier
    }

    Surface(
        color = MiuixTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MediaPickerSmallTopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                navigationIcon()
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = titlePadding)
                    .then(titleLongPressModifier)
                    .testTag("title_media_picker"),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

private val MediaPickerSmallTopBarHeight = 52.dp

@Composable
private fun MoveProgressButton(
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        minWidth = 56.dp,
        minHeight = 56.dp,
        cornerRadius = 28.dp,
        modifier = modifier.testTag("btn_move_progress"),
    ) {
        CircularProgressIndicator(
            progress = progress,
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
        title = stringResource(R.string.move_progress_title),
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
            TextButton(
                text = stringResource(R.string.cancel_remaining_move),
                onClick = onCancelRemaining,
                modifier = Modifier.testTag("btn_move_progress_cancel_remaining"),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
        dismissButton = {
            TextButton(
                text = stringResource(R.string.continue_move),
                onClick = onContinue,
                modifier = Modifier.testTag("btn_move_progress_continue"),
            )
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
                text = stringResource(
                    if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
                        R.string.move_to_recycle_bin
                    } else {
                        R.string.delete_permanently
                    },
                ),
                onClick = onConfirm,
                modifier = modifier.testTag("btn_delete_confirm"),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
        dismissButton = {
            CancelButton(
                onClick = onCancel,
                modifier = Modifier.testTag("btn_delete_cancel"),
            )
        },
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
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
                if (allSelectedVideos.isNotEmpty()) {
                    Text(text = stringResource(R.string.delete_summary_count, allSelectedVideos.size))
                    Text(text = stringResource(R.string.delete_summary_size, Utils.formatFileSize(totalSize)))
                    Text(text = stringResource(R.string.delete_summary_duration, Utils.formatDurationMillis(totalDuration)))
                    Text(
                        text = allSelectedVideos.take(5).joinToString(separator = "\n") { it.nameWithExtension },
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                    if (allSelectedVideos.size > 5) {
                        Text(
                            text = stringResource(R.string.delete_summary_more, allSelectedVideos.size - 5),
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
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
    OverlayListPopup(
        show = expanded,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    ) {
        ListPopupColumn {
            if (shouldShowRestoreAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.restore),
                    icon = NextIcons.ArrowUpward,
                    testTag = "item_selection_restore",
                    onClick = onRestoreAction,
                )
            }
            if (shouldShowMoveAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.move),
                    icon = NextIcons.Folder,
                    testTag = "item_selection_move",
                    onClick = onMoveAction,
                )
            }
            if (shouldShowFavoriteAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.add_to_favorites),
                    icon = NextIcons.LibraryBooks,
                    testTag = "item_selection_add_favorites",
                    onClick = onFavoriteAction,
                )
            }
            if (shouldShowRenameAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.rename),
                    icon = NextIcons.Edit,
                    testTag = "item_selection_rename",
                    onClick = onRenameAction,
                )
            }
            if (shouldShowInfoAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.info),
                    icon = NextIcons.Info,
                    testTag = "item_selection_info",
                    onClick = onInfoAction,
                )
            }
            PopupMenuItem(
                text = stringResource(id = R.string.share),
                icon = NextIcons.Share,
                testTag = "item_selection_share",
                onClick = onShareAction,
            )
            if (shouldShowExcludeAction) {
                PopupMenuItem(
                    text = stringResource(id = R.string.exclude),
                    icon = NextIcons.FolderOff,
                    testTag = "item_selection_exclude",
                    onClick = onExcludeAction,
                )
            }
            PopupMenuItem(
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
                isDestructive = true,
            )
        }
    }
}

// 顶栏主菜单，miuix overlay popup 承载本地打开、网络流、最近播放、退出等入口
@Composable
private fun MainMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenNetworkStream: () -> Unit,
    onOpenLocalVideo: () -> Unit,
    onOpenRecentlyPlayed: (() -> Unit)?,
    onExit: () -> Unit,
) {
    OverlayListPopup(
        show = expanded,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
    ) {
        ListPopupColumn {
            PopupMenuItem(
                text = stringResource(id = R.string.open_network_stream),
                icon = NextIcons.Link,
                testTag = "item_main_menu_network_stream",
                onClick = onOpenNetworkStream,
            )
            PopupMenuItem(
                text = stringResource(id = R.string.open_local_video),
                icon = NextIcons.FileOpen,
                testTag = "item_main_menu_local_video",
                onClick = onOpenLocalVideo,
            )
            if (onOpenRecentlyPlayed != null) {
                PopupMenuItem(
                    text = stringResource(id = R.string.recently_played),
                    icon = NextIcons.History,
                    testTag = "item_main_menu_recently_played",
                    onClick = onOpenRecentlyPlayed,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                thickness = 1.dp,
            )
            PopupMenuItem(
                text = stringResource(id = R.string.exit),
                icon = NextIcons.Close,
                testTag = "item_main_menu_exit_app",
                onClick = onExit,
            )
        }
    }
}

// miuix popup 通用行：左图标 + 文本，宽度自适应
@Composable
private fun PopupMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val tint = if (isDestructive) {
        MiuixTheme.colorScheme.onErrorContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            color = tint,
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
        title = stringResource(R.string.network_stream),
        content = {
            Text(text = stringResource(R.string.enter_a_network_url))
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_network_url"),
                label = stringResource(R.string.example_url),
                useLabelAsPlaceholder = true,
            )
        },
        confirmButton = {
            DoneButton(
                isEnabled = url.isNotBlank(),
                onClick = { onDone(url) },
                modifier = Modifier.testTag("btn_network_url_done"),
            )
        },
        dismissButton = {
            CancelButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_network_url_cancel"),
            )
        },
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
