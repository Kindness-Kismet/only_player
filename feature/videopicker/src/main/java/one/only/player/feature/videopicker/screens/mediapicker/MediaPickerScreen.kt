package one.only.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures as detectGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import one.only.player.core.common.Logger
import one.only.player.core.common.Utils
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.storagePermission
import one.only.player.core.data.repository.MediaMoveProgress
import one.only.player.core.media.extensions.storageRootLabelOf
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.StoragePath
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.LocalTopBarBackdrop
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.surfaceBlur
import one.only.player.core.ui.composables.PermissionMissingView
import one.only.player.core.ui.composables.rememberRuntimePermissionState
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.preview.VideoPickerPreviewParameterProvider
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.videopicker.composables.AddToPlaylistDialog
import one.only.player.feature.videopicker.composables.MediaPickerPathPanel
import one.only.player.feature.videopicker.composables.MediaPickerPathScope
import one.only.player.feature.videopicker.composables.MediaView
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.MenuActionsPopup
import one.only.player.feature.videopicker.composables.MoveTargetView
import one.only.player.feature.videopicker.composables.NoVideosFound
import one.only.player.feature.videopicker.composables.QuickSettingsDialog
import one.only.player.feature.videopicker.composables.RenameDialog
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.composables.buildMediaPickerPathEntries
import one.only.player.feature.videopicker.composables.rememberPathPanelBackdrop
import one.only.player.feature.videopicker.composables.rememberPullToRefreshTexts
import one.only.player.feature.videopicker.composables.rememberStorageRootLabels
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.state.SelectedFolder
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.SelectionManager
import one.only.player.feature.videopicker.state.rememberSelectionManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaPickerRoute(
    viewModel: MediaPickerViewModel = hiltViewModel(),
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
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onMoveSelectionStarted: () -> Unit,
    onMoveSelectionClosed: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MediaPickerScreen(
        uiState = uiState,
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
    onAncestorFolderClick: (String, MediaPickerScreenMode) -> Unit = { _, _ -> },
    onRecycleBinClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    onCloudClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onExitAppClick: () -> Unit = {},
    onMoveSelectionStarted: () -> Unit = {},
    onMoveSelectionClosed: () -> Unit = {},
    onEvent: (MediaPickerUiEvent) -> Unit = {},
) {
    val selectionManager = rememberSelectionManager()
    val permissionState = rememberRuntimePermissionState(permission = storagePermission)
    val lazyGridState = rememberLazyGridState()
    val scrollBehavior = MiuixScrollBehavior()
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
    var pendingPlaylistSelection by remember { mutableStateOf<Pair<List<Video>, List<Folder>>?>(null) }

    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var shouldShowDeleteVideosConfirmation by rememberSaveable { mutableStateOf(false) }
    var shouldShowMoveProgressDialog by rememberSaveable { mutableStateOf(false) }
    var shouldShowPathPanel by rememberSaveable { mutableStateOf(false) }

    val isLibraryMode = uiState.screenMode == MediaPickerScreenMode.LIBRARY
    val layoutDirectoryPath = if (isLibraryMode) {
        uiState.folderPath ?: (uiState.mediaDataState as? DataState.Success)?.value?.path?.takeUnless { it == "/" }
    } else {
        null
    }
    val isMoveMode = uiState.moveSelection != null && isLibraryMode
    val pathRootLabel = stringResource(R.string.tab_home)
    val storageRootLabels = rememberStorageRootLabels()
    val pathEntries = remember(
        uiState.folderPath,
        pathRootLabel,
        storageRootLabels,
        uiState.homeLandingPath,
        uiState.preferences.mediaViewMode,
        uiState.mediaBearingFolderPaths,
    ) {
        buildMediaPickerPathEntries(
            folderPath = uiState.folderPath,
            rootLabel = pathRootLabel,
            storageRootLabels = storageRootLabels,
            scope = MediaPickerPathScope.of(
                mediaViewMode = uiState.preferences.mediaViewMode,
                homeLandingPath = uiState.homeLandingPath,
                mediaBearingFolderPaths = uiState.mediaBearingFolderPaths,
            ),
        )
    }
    val canOpenPathPanel = isLibraryMode &&
        !selectionManager.isInSelectionMode &&
        !isMoveMode &&
        pathEntries.size > 1
    val isPathPanelExpanded = shouldShowPathPanel && canOpenPathPanel
    val pathPanelBackdrop = rememberPathPanelBackdrop()
    val isTitleLongPressHomeNavigationEnabled = shouldEnableTitleLongPressHomeNavigation(
        isInSelectionMode = selectionManager.isInSelectionMode || isMoveMode,
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
    val moveResult = uiState.moveResult
    val moveResultMessage = when {
        moveResult == null -> null
        moveResult.canceledCount > 0 -> stringResource(R.string.move_cancelled, moveResult.movedCount, moveResult.failedCount)
        moveResult.partiallyMovedCount > 0 -> stringResource(
            R.string.move_incomplete,
            moveResult.movedCount,
            moveResult.partiallyMovedCount,
            moveResult.failedCount,
        )
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
        uiState.folderPath == null -> false
        (uiState.moveTargetDataState as? DataState.Success)?.value?.canMoveHere != true -> false
        uiState.moveSpaceCheck?.hasEnoughSpace != true -> false
        else -> uiState.moveSelection.canMoveTo(uiState.folderPath)
    }
    val moveProgress = uiState.moveProgress

    val selectedCountTitle = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize)
    val topBarTitle = when {
        selectionManager.isInSelectionMode -> selectedCountTitle
        isMoveMode -> stringResource(R.string.move)
        else -> uiState.folderPath?.let(storageRootLabels::storageRootLabelOf)
            ?: uiState.folderName
            ?: stringResource(if (isRecycleBinMode) R.string.recycle_bin else R.string.app_name)
    }
    val shouldUseLargeTopBar = !selectionManager.isInSelectionMode &&
        !isMoveMode &&
        isLibraryMode &&
        uiState.folderName == null

    AppScaffold(
        topBar = {
            MediaPickerTopAppBar(
                title = topBarTitle,
                shouldUseLargeTitle = shouldUseLargeTopBar,
                largeTitlePadding = TopAppBarDefaults.TitlePadding,
                smallTitlePadding = 16.dp,
                scrollBehavior = scrollBehavior,
                isTitleLongPressHomeNavigationEnabled = isTitleLongPressHomeNavigationEnabled,
                onTitleLongPress = onNavigateHome,
                canOpenPathPanel = canOpenPathPanel,
                onTitleClick = { shouldShowPathPanel = !shouldShowPathPanel },
                navigationIcon = {
                    if (selectionManager.isInSelectionMode) {
                        IconButton(
                            onClick = { selectionManager.exitSelectionMode() },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("btn_selection_exit"),
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else if (uiState.folderName != null || isRecycleBinMode) {
                        IconButton(
                            onClick = {
                                if (isMoveMode && uiState.isMovingSelection) {
                                    shouldShowMoveProgressDialog = true
                                } else {
                                    onNavigateUp()
                                }
                            },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("btn_media_picker_back"),
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                actions = {
                    if (isMoveMode) {
                        IconButton(
                            onClick = {
                                onEvent(MediaPickerUiEvent.CancelMoveSelection)
                                onMoveSelectionClosed()
                            },
                            enabled = !uiState.isMovingSelection,
                            modifier = Modifier.testTag("btn_cancel_move"),
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
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
                                    selectionManager.exitSelectionMode()
                                }
                            },
                            modifier = Modifier.testTag("btn_selection_toggle_all"),
                        ) {
                            Icon(
                                imageVector = if (selectedItemsSize != totalItemsSize) {
                                    AppIcons.SelectAll
                                } else {
                                    AppIcons.DeselectAll
                                },
                                contentDescription = if (selectedItemsSize != totalItemsSize) {
                                    stringResource(R.string.select_all)
                                } else {
                                    stringResource(R.string.deselect_all)
                                },
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        if (selectionManager.isSingleVideoSelected && !isRecycleBinMode) {
                            IconButton(
                                onClick = {
                                    val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@IconButton
                                    val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                                        ?.find { it.uriString == selectedVideo.uriString } ?: return@IconButton
                                    showInfoActionFor = video
                                    selectionManager.exitSelectionMode()
                                },
                                modifier = Modifier.testTag("btn_selection_info"),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Info,
                                    contentDescription = stringResource(id = R.string.info),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        val primaryActions = selectionPrimaryActions(
                            isLibraryMode = isLibraryMode,
                            isRecycleBinMode = isRecycleBinMode,
                            selectionManager = selectionManager,
                            uiState = uiState,
                            onEvent = onEvent,
                            onMoveSelectionStarted = onMoveSelectionStarted,
                            onAddToPlaylist = { videos, folders ->
                                pendingPlaylistSelection = videos to folders
                            },
                        )
                        val overflowActions = selectionOverflowActions(
                            isLibraryMode = isLibraryMode,
                            isRecycleBinMode = isRecycleBinMode,
                            selectionManager = selectionManager,
                            uiState = uiState,
                            onEvent = onEvent,
                            onRenameRequest = { video -> showRenameActionFor = video },
                        )
                        val deleteMenuAction = selectionDeleteAction(
                            isRecycleBinMode = isRecycleBinMode,
                            deleteAction = deleteAction,
                            onDeleteRequest = { shouldShowDeleteVideosConfirmation = true },
                        )
                        IconButton(
                            onClick = { shouldShowSelectionMenu = true },
                            holdDownState = shouldShowSelectionMenu,
                            modifier = Modifier.testTag("btn_selection_more"),
                        ) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(id = R.string.more_actions),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        MenuActionsPopup(
                            expanded = shouldShowSelectionMenu,
                            onDismissRequest = { shouldShowSelectionMenu = false },
                            groups = listOf(primaryActions, overflowActions, listOf(deleteMenuAction)),
                        )
                    } else {
                        if (isLibraryMode) {
                            IconButton(
                                onClick = onSearchClick,
                                modifier = Modifier.testTag("btn_media_picker_search"),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Search,
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
                                        imageVector = AppIcons.DeleteSweep,
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
                                    imageVector = AppIcons.DashBoard,
                                    contentDescription = stringResource(id = R.string.quick_settings),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                            IconButton(
                                onClick = { shouldShowMainMenu = true },
                                holdDownState = shouldShowMainMenu,
                                modifier = Modifier.testTag("btn_main_menu"),
                            ) {
                                Icon(
                                    imageVector = AppIcons.ExpandMore,
                                    contentDescription = stringResource(id = R.string.menu),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                            MenuActionsPopup(
                                expanded = shouldShowMainMenu,
                                onDismissRequest = { shouldShowMainMenu = false },
                                groups = mainMenuActionGroups(
                                    onHistoryClick = onHistoryClick,
                                    onPlaylistsClick = onPlaylistsClick,
                                    onOpenNetworkStream = { shouldShowUrlDialog = true },
                                    onOpenLocalVideo = { selectVideoFileLauncher.launch("video/*") },
                                    onExit = onExitAppClick,
                                ),
                            )
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.displayCutout,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding())
                .padding(start = scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                // 模糊源只含内容区，面板自身必须留在外层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (pathPanelBackdrop != null) {
                                Modifier.layerBackdrop(pathPanelBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    PermissionMissingView(
                        isGranted = permissionState.isGranted,
                        shouldShowRationale = permissionState.shouldShowRationale,
                        permission = permissionState.permission,
                        launchPermissionRequest = { permissionState.launchPermissionRequest() },
                    ) {
                        val activeDataState = if (isMoveMode) uiState.moveTargetDataState else uiState.mediaDataState
                        var shouldShowRefreshIndicator by remember(uiState.isRefreshing) {
                            mutableStateOf(uiState.isRefreshing)
                        }
                        LaunchedEffect(uiState.isRefreshing) {
                            if (!uiState.isRefreshing) return@LaunchedEffect
                            // 隐藏指示器后扫描继续执行，实际刷新状态仍用于阻止重复扫描。
                            delay(REFRESH_INDICATOR_TIMEOUT_MILLIS)
                            shouldShowRefreshIndicator = false
                        }
                        val updatedScaffoldPadding = scaffoldPadding.copy(
                            top = if (shouldUseLargeTopBar) PageContentTopPadding else 0.dp,
                            start = 0.dp,
                        ).withBottomFallback()
                        val refreshTexts = rememberPullToRefreshTexts()
                        PullToRefresh(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("media_picker_pull_refresh"),
                            isRefreshing = shouldShowRefreshIndicator,
                            onRefresh = { onEvent(MediaPickerUiEvent.Refresh) },
                            topAppBarScrollBehavior = scrollBehavior.takeIf { shouldUseLargeTopBar },
                            refreshTexts = if (uiState.isRefreshing) {
                                refreshTexts.dropLast(1) + refreshTexts[2]
                            } else {
                                refreshTexts
                            },
                        ) {
                            when (activeDataState) {
                                DataState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .testTag("media_picker_loading"),
                                    )
                                }
                                is DataState.Error -> Box(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.unknown_error),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                                is DataState.Success -> if (isMoveMode) {
                                    val moveTargetContent = (uiState.moveTargetDataState as DataState.Success).value
                                    MoveTargetView(
                                        content = moveTargetContent,
                                        spaceCheck = uiState.moveSpaceCheck,
                                        canMoveHere = canMoveToCurrentFolder,
                                        isMoving = uiState.isMovingSelection,
                                        contentPadding = updatedScaffoldPadding,
                                        onDirectoryClick = { directory ->
                                            onFolderClick(directory.path, MediaPickerScreenMode.LIBRARY)
                                        },
                                        onMoveHere = {
                                            uiState.folderPath?.let { folderPath ->
                                                onEvent(MediaPickerUiEvent.MoveSelectionToFolder(folderPath))
                                            }
                                        },
                                    )
                                } else {
                                    val rootFolder = (uiState.mediaDataState as DataState.Success).value
                                    if (rootFolder == null || rootFolder.folderList.isEmpty() && rootFolder.mediaList.isEmpty()) {
                                        NoVideosFound(contentPadding = updatedScaffoldPadding)
                                    } else {
                                        MediaView(
                                            rootFolder = rootFolder,
                                            preferences = uiState.preferences,
                                            layoutDirectory = layoutDirectoryPath?.let(StoragePath::of),
                                            onFolderClick = {
                                                onEvent(MediaPickerUiEvent.CacheFolderSnapshot(it))
                                                onFolderClick(it.path, uiState.screenMode)
                                            },
                                            onVideoClick = { video -> onPlayVideo(video, uiState.playerPreferences) },
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
                                .padding(end = 21.dp),
                        )
                    }
                }

                MediaPickerPathPanel(
                    isExpanded = isPathPanelExpanded,
                    entries = pathEntries,
                    backdrop = pathPanelBackdrop,
                    onDismissRequest = { shouldShowPathPanel = false },
                    onPathSelected = { path ->
                        shouldShowPathPanel = false
                        if (path == null) {
                            onNavigateHome()
                        } else if (path != uiState.folderPath?.canonicalPathOrSelf()) {
                            onAncestorFolderClick(path, uiState.screenMode)
                        }
                    },
                )
            }
        }
    }

    LaunchedEffect(moveResultMessage) {
        val message = moveResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(MediaPickerUiEvent.ClearMoveResult)
        if (uiState.moveSelection == null) onMoveSelectionClosed()
    }

    LaunchedEffect(moveProgress != null) {
        if (moveProgress != null) shouldShowMoveProgressDialog = true
    }

    LaunchedEffect(deleteResultMessage) {
        val message = deleteResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(MediaPickerUiEvent.ClearDeleteResult)
    }

    LaunchedEffect(uiState.folderPath) {
        restoredPlaybackAnchor = null
        shouldShowPathPanel = false
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
            shouldShowPathPanel = false
        }
        if (!selectionManager.isInSelectionMode) {
            shouldShowSelectionMenu = false
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    BackHandler(enabled = shouldShowPathPanel) {
        shouldShowPathPanel = false
    }

    BackHandler(enabled = isMoveMode && uiState.isMovingSelection) {
        shouldShowMoveProgressDialog = true
    }

    BackHandler(
        enabled = isMoveMode && uiState.folderPath == null && !uiState.isMovingSelection,
    ) {
        onEvent(MediaPickerUiEvent.CancelMoveSelection)
        onMoveSelectionClosed()
    }

    LaunchedEffect(uiState.moveSelectionResolution) {
        when (uiState.moveSelectionResolution) {
            MediaPickerMoveSelectionResolution.Canceled -> {
                if (selectionManager.selectedVideos.isNotEmpty() || selectionManager.selectedFolders.isNotEmpty()) {
                    selectionManager.enterSelectionMode()
                }
            }
            MediaPickerMoveSelectionResolution.Completed -> selectionManager.exitSelectionMode()
            null -> Unit
        }
    }

    if (shouldShowQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            isRoot = uiState.folderPath == null,
            directoryPath = layoutDirectoryPath,
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

    pendingPlaylistSelection?.let { (videos, folders) ->
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { pendingPlaylistSelection = null },
            onSelectPlaylist = { playlistId ->
                onEvent(MediaPickerUiEvent.AddToPlaylist(playlistId, videos, folders))
                pendingPlaylistSelection = null
                selectionManager.exitSelectionMode()
            },
            onCreatePlaylist = { title ->
                onEvent(MediaPickerUiEvent.CreatePlaylistAndAdd(title, videos, folders))
                pendingPlaylistSelection = null
                selectionManager.exitSelectionMode()
            },
        )
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(MediaPickerUiEvent.RenameVideo(video.uriString.toUri(), it))
                showRenameActionFor = null
                selectionManager.exitSelectionMode()
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
            progress = moveProgress,
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
    scrollBehavior: ScrollBehavior,
    isTitleLongPressHomeNavigationEnabled: Boolean,
    onTitleLongPress: () -> Unit,
    canOpenPathPanel: Boolean,
    onTitleClick: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    if (shouldUseLargeTitle) {
        AppTopAppBar(
            title = title,
            titlePadding = largeTitlePadding,
            scrollBehavior = scrollBehavior,
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
        canOpenPathPanel = canOpenPathPanel,
        onTitleClick = onTitleClick,
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
    canOpenPathPanel: Boolean,
    onTitleClick: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val titleGestureModifier = if (isTitleLongPressHomeNavigationEnabled || canOpenPathPanel) {
        Modifier.pointerInput(
            isTitleLongPressHomeNavigationEnabled,
            canOpenPathPanel,
            onTitleLongPress,
            onTitleClick,
        ) {
            detectGestures(
                onTap = { if (canOpenPathPanel) onTitleClick() },
                onLongPress = { if (isTitleLongPressHomeNavigationEnabled) onTitleLongPress() },
            )
        }
    } else {
        Modifier
    }

    val topBarBackdrop = LocalTopBarBackdrop.current
    Surface(
        color = if (topBarBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .surfaceBlur(topBarBackdrop),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MediaPickerSmallTopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(start = TopAppBarDefaults.NavigationIconPadding)) {
                navigationIcon()
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = titlePadding)
                    .then(titleGestureModifier)
                    .testTag("title_media_picker"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
            Row(
                modifier = Modifier.padding(end = TopAppBarDefaults.ActionIconPadding),
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
    progress: MediaMoveProgress,
    onCancelRemaining: () -> Unit,
    onContinue: () -> Unit,
) {
    val fileProgress = if (progress.totalBytes > 0L) {
        progress.copiedBytes.toFloat() / progress.totalBytes
    } else {
        null
    }
    val progressPercent = ((fileProgress ?: 0f) * 100).toInt().coerceIn(0, 100)

    AppDialog(
        onDismissRequest = onContinue,
        title = stringResource(R.string.move_progress_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.move_progress_message,
                        progress.completedCount,
                        progress.totalCount,
                    ),
                )
                progress.currentName?.let { currentName ->
                    Text(
                        text = stringResource(R.string.move_progress_current_file, currentName),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fileProgress != null) {
                        CircularProgressIndicator(
                            progress = fileProgress,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = stringResource(R.string.move_progress_percent, progressPercent))
                        if (progress.totalBytes > 0L) {
                            Text(
                                text = stringResource(
                                    R.string.move_progress_size,
                                    Utils.formatFileSize(progress.copiedBytes),
                                    Utils.formatFileSize(progress.totalBytes),
                                ),
                            )
                        }
                    }
                }
            }
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
    AppDialog(
        onDismissRequest = onCancel,
        title = if (deleteAction == MediaPickerDeleteAction.MoveToRecycleBin) {
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
private const val REFRESH_INDICATOR_TIMEOUT_MILLIS = 1_500L

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

// 选中模式菜单的高频操作组：回收站为恢复+分享，媒体库为分享/收藏/移动；删除单独成组置底。
@Composable
private fun selectionPrimaryActions(
    isLibraryMode: Boolean,
    isRecycleBinMode: Boolean,
    selectionManager: SelectionManager,
    uiState: MediaPickerUiState,
    onEvent: (MediaPickerUiEvent) -> Unit,
    onMoveSelectionStarted: () -> Unit,
    onAddToPlaylist: (List<Video>, List<Folder>) -> Unit,
): List<MenuAction> {
    val actions = mutableListOf<MenuAction>()
    if (isRecycleBinMode) {
        actions += MenuAction(
            text = stringResource(id = R.string.restore),
            icon = AppIcons.ArrowUpward,
            testTag = "item_selection_restore",
            onClick = {
                onEvent(MediaPickerUiEvent.RestoreVideos(selectionManager.allSelectedVideos.map { it.uriString }))
                selectionManager.exitSelectionMode()
            },
        )
    }
    actions += MenuAction(
        text = stringResource(id = R.string.share),
        icon = AppIcons.Share,
        testTag = "item_selection_share",
        onClick = {
            onEvent(MediaPickerUiEvent.ShareVideos(selectionManager.allSelectedVideos.map { it.uriString }))
        },
    )
    if (isLibraryMode) {
        actions += MenuAction(
            text = stringResource(id = R.string.favorites),
            icon = AppIcons.LibraryBooks,
            testTag = "item_selection_add_favorites",
            onClick = {
                val rootFolder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@MenuAction
                val selectedVideos = selectionManager.selectedVideos.mapNotNull { selectedVideo ->
                    rootFolder.allMediaList.firstOrNull { video -> video.uriString == selectedVideo.uriString }
                }
                val selectedFolders = selectionManager.selectedFolders.mapNotNull { selectedFolder ->
                    rootFolder.folderList.firstOrNull { folder -> folder.path == selectedFolder.path }
                }
                onEvent(MediaPickerUiEvent.AddFavorites(selectedVideos, selectedFolders))
                selectionManager.exitSelectionMode()
            },
        )
        actions += MenuAction(
            text = stringResource(id = R.string.add_to_playlist),
            icon = AppIcons.PlaylistPlay,
            testTag = "item_selection_add_playlist",
            onClick = {
                val rootFolder = (uiState.mediaDataState as? DataState.Success)?.value ?: return@MenuAction
                val selectedVideos = selectionManager.selectedVideos.mapNotNull { selectedVideo ->
                    rootFolder.allMediaList.firstOrNull { video -> video.uriString == selectedVideo.uriString }
                }
                val selectedFolders = selectionManager.selectedFolders.mapNotNull { selectedFolder ->
                    rootFolder.folderList.firstOrNull { folder -> folder.path == selectedFolder.path }
                }
                onAddToPlaylist(selectedVideos, selectedFolders)
            },
        )
        actions += MenuAction(
            text = stringResource(id = R.string.move),
            icon = AppIcons.DriveFileMove,
            testTag = "item_selection_move",
            onClick = {
                onEvent(
                    MediaPickerUiEvent.StartMoveSelection(
                        videoUris = selectionManager.selectedVideos.map { it.uriString },
                        folderPaths = selectionManager.selectedFolders.map { it.path },
                    ),
                )
                onMoveSelectionStarted()
            },
        )
    }
    return actions
}

// 删除入口单独成组，置于菜单末尾，用分隔线与其它操作区隔。
@Composable
private fun selectionDeleteAction(
    isRecycleBinMode: Boolean,
    deleteAction: MediaPickerDeleteAction,
    onDeleteRequest: () -> Unit,
): MenuAction = MenuAction(
    text = stringResource(
        id = when (deleteAction) {
            MediaPickerDeleteAction.MoveToRecycleBin -> R.string.delete
            MediaPickerDeleteAction.PermanentlyDelete -> {
                if (isRecycleBinMode) R.string.delete_permanently else R.string.delete
            }
        },
    ),
    icon = AppIcons.Delete,
    testTag = "item_selection_delete",
    onClick = onDeleteRequest,
)

// 选中模式顶栏溢出菜单的低频操作：重命名仅单选视频可用，排除仅选中文件夹时可用。
@Composable
private fun selectionOverflowActions(
    isLibraryMode: Boolean,
    isRecycleBinMode: Boolean,
    selectionManager: SelectionManager,
    uiState: MediaPickerUiState,
    onEvent: (MediaPickerUiEvent) -> Unit,
    onRenameRequest: (Video) -> Unit,
): List<MenuAction> {
    if (isRecycleBinMode) return emptyList()
    val actions = mutableListOf<MenuAction>()
    if (selectionManager.isSingleVideoSelected && isLibraryMode) {
        actions += MenuAction(
            text = stringResource(id = R.string.rename),
            icon = AppIcons.Edit,
            testTag = "item_selection_rename",
            onClick = {
                val selectedVideo = selectionManager.selectedVideos.firstOrNull() ?: return@MenuAction
                val video = (uiState.mediaDataState as? DataState.Success)?.value?.mediaList
                    ?.find { it.uriString == selectedVideo.uriString } ?: return@MenuAction
                onRenameRequest(video)
            },
        )
    }
    if (isLibraryMode) {
        actions += MenuAction(
            text = stringResource(id = R.string.mark_as_played),
            icon = AppIcons.CheckBox,
            testTag = "item_selection_mark_played",
            onClick = {
                onEvent(MediaPickerUiEvent.MarkVideosPlayed(selectionManager.allSelectedVideos.map { it.uriString }))
                selectionManager.exitSelectionMode()
            },
        )
        actions += MenuAction(
            text = stringResource(id = R.string.mark_as_unplayed),
            icon = AppIcons.CheckBoxOutline,
            testTag = "item_selection_mark_unplayed",
            onClick = {
                onEvent(MediaPickerUiEvent.MarkVideosUnplayed(selectionManager.allSelectedVideos.map { it.uriString }))
                selectionManager.exitSelectionMode()
            },
        )
    }
    if (selectionManager.selectedFolders.isNotEmpty() && isLibraryMode) {
        actions += MenuAction(
            text = stringResource(id = R.string.exclude),
            icon = AppIcons.FolderOff,
            testTag = "item_selection_exclude",
            onClick = {
                val paths = selectionManager.selectedFolders.map { it.path }
                onEvent(MediaPickerUiEvent.ExcludeFolders(paths))
                selectionManager.exitSelectionMode()
            },
        )
    }
    return actions
}

// 顶栏主菜单：打开入口为一组，退出单独一组，靠 miuix 分组分隔线区隔。
@Composable
private fun mainMenuActionGroups(
    onHistoryClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onOpenNetworkStream: () -> Unit,
    onOpenLocalVideo: () -> Unit,
    onExit: () -> Unit,
): List<List<MenuAction>> {
    val libraryActions = listOf(
        MenuAction(
            text = stringResource(id = R.string.watch_history),
            icon = AppIcons.History,
            testTag = "item_main_menu_history",
            onClick = onHistoryClick,
        ),
        MenuAction(
            text = stringResource(id = R.string.playlists),
            icon = AppIcons.PlaylistPlay,
            testTag = "item_main_menu_playlists",
            onClick = onPlaylistsClick,
        ),
    )
    val openActions = listOf(
        MenuAction(
            text = stringResource(id = R.string.open_network_stream),
            icon = AppIcons.Link,
            testTag = "item_main_menu_network_stream",
            onClick = onOpenNetworkStream,
        ),
        MenuAction(
            text = stringResource(id = R.string.open_local_video),
            icon = AppIcons.FileOpen,
            testTag = "item_main_menu_local_video",
            onClick = onOpenLocalVideo,
        ),
    )
    val exitActions = listOf(
        MenuAction(
            text = stringResource(id = R.string.exit),
            icon = AppIcons.Close,
            testTag = "item_main_menu_exit_app",
            onClick = onExit,
        ),
    )
    return listOf(libraryActions, openActions, exitActions)
}

@Composable
private fun NetworkUrlDialog(
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AppDialog(
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
                    videoLayoutMode = MediaLayoutMode.GRID,
                ),
            ),
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
