package one.only.player.feature.videopicker.screens.cloud

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.security.MessageDigest
import one.only.player.core.common.needsLocalNetworkPermission
import one.only.player.core.data.models.RemotePlaybackInfo
import one.only.player.core.model.CloudQuickSettings
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.resolveMediaLayouts
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withQuickSettings
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppSmallTopAppBar
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.CardListItem
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.subtractBottomPadding
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.videopicker.composables.FolderGridThumbnail
import one.only.player.feature.videopicker.composables.FolderThumbnail
import one.only.player.feature.videopicker.composables.LocalNetworkPermissionMissingScreen
import one.only.player.feature.videopicker.composables.MediaItemContentPadding
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MediaMetaText
import one.only.player.feature.videopicker.composables.MediaSectionTitleStartPadding
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.MenuActionsPopup
import one.only.player.feature.videopicker.composables.PreserveMediaLayoutScroll
import one.only.player.feature.videopicker.composables.QuickSettingsDialog
import one.only.player.feature.videopicker.composables.QuickSettingsTarget
import one.only.player.feature.videopicker.composables.RequestLocalNetworkPermissionIfNeeded
import one.only.player.feature.videopicker.composables.SelectionCheckIndicator
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.composables.libraryListThumbWidth
import one.only.player.feature.videopicker.composables.mediaGridGeometry
import one.only.player.feature.videopicker.composables.rememberLocalNetworkPermissionState
import one.only.player.feature.videopicker.composables.rememberPullToRefreshTexts
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CloudBrowseRoute(
    viewModel: CloudBrowseViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onDirectoryClick: (serverId: Long, path: String) -> Unit,
    onPlayVideo: (uri: Uri, headers: Map<String, String>, initialSubtitleDirectoryUri: Uri?, playlist: List<Uri>, playlistRemotePaths: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val localNetworkPermissionState = rememberLocalNetworkPermissionState()
    val isLocalNetworkReady = !needsLocalNetworkPermission() || localNetworkPermissionState.isGranted

    RequestLocalNetworkPermissionIfNeeded(permissionState = localNetworkPermissionState)

    LaunchedEffect(isLocalNetworkReady) {
        if (!isLocalNetworkReady) return@LaunchedEffect
        viewModel.onEvent(CloudBrowseEvent.Retry)
    }

    // 从播放器返回时刷新播放状态
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(CloudBrowseEvent.RefreshPlaybackStates)
        onPauseOrDispose {}
    }

    if (!isLocalNetworkReady) {
        LocalNetworkPermissionMissingScreen(
            shouldShowRationale = localNetworkPermissionState.shouldShowRationale,
            onGrantClick = localNetworkPermissionState::launchPermissionRequest,
            onNavigateUp = onNavigateUp,
        )
        return
    }

    CloudBrowseScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
        onDirectoryClick = { path ->
            val serverId = uiState.server?.id ?: return@CloudBrowseScreen
            onDirectoryClick(serverId, path)
        },
        onFileClick = { file ->
            val url = viewModel.buildPlayUrl(file) ?: return@CloudBrowseScreen
            val headers = viewModel.buildAuthHeaders(file)
            val initialSubtitleDirectoryUri = viewModel.buildCurrentDirectoryDocumentId()
                ?.let { documentId ->
                    DocumentsContract.buildDocumentUri("${context.packageName}.documents", documentId)
                }
            val playlist = viewModel.buildAllVideoPlayUrls()
            val playlistRemotePaths = viewModel.buildAllVideoRemotePaths()
            onPlayVideo(Uri.parse(url), headers, initialSubtitleDirectoryUri, playlist, playlistRemotePaths)
        },
        onFileInfoClick = { file ->
            val documentUri = viewModel.buildFileDocumentId(file)
                ?.let { documentId ->
                    DocumentsContract.buildDocumentUri("${context.packageName}.documents", documentId)
                } ?: return@CloudBrowseScreen
            viewModel.onEvent(CloudBrowseEvent.LoadFileInfo(file, documentUri))
        },
        buildFileThumbnailUri = { file ->
            val server = uiState.server ?: return@CloudBrowseScreen null
            viewModel.buildFileDocumentId(file)
                ?.let { documentId ->
                    DocumentsContract.buildDocumentUri("${context.packageName}.documents", documentId)
                        .withRemoteThumbnailCacheIdentity(server)
                }
        },
    )
}

@Composable
internal fun CloudBrowseScreen(
    uiState: CloudBrowseUiState,
    onNavigateUp: () -> Unit = {},
    onEvent: (CloudBrowseEvent) -> Unit = {},
    onDirectoryClick: (String) -> Unit = {},
    onFileClick: (RemoteFile) -> Unit = {},
    onFileInfoClick: (RemoteFile) -> Unit = {},
    buildFileThumbnailUri: (RemoteFile) -> Uri? = { null },
) {
    val serverName = uiState.server?.name?.takeIf { it.isNotBlank() }
        ?: uiState.server?.host
        ?: stringResource(R.string.browsing)
    val haptic = LocalHapticFeedback.current
    val lazyGridState = rememberLazyGridState()
    val layoutDirectoryPath = uiState.settingsDirectoryPath
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shouldShowSelectionMenu by remember { mutableStateOf(false) }
    var shouldShowQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var restoredDirectoryPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItemsSize = selectedFilePaths.size
    val totalItemsSize = uiState.files.size
    val isInSelectionMode = selectedFilePaths.isNotEmpty()

    fun clearSelection() {
        selectedFilePaths = emptySet()
        shouldShowSelectionMenu = false
    }

    fun toggleFileSelection(file: RemoteFile) {
        selectedFilePaths = if (file.path in selectedFilePaths) {
            selectedFilePaths - file.path
        } else {
            selectedFilePaths + file.path
        }
        if (selectedFilePaths.isEmpty()) {
            shouldShowSelectionMenu = false
        }
    }

    LaunchedEffect(uiState.currentPath) {
        restoredDirectoryPath = null
        clearSelection()
    }

    LaunchedEffect(uiState.files) {
        selectedFilePaths = selectedFilePaths
            .filter { path -> uiState.files.any { file -> file.path == path } }
            .toSet()
    }

    LaunchedEffect(
        uiState.currentPath,
        uiState.restoreTargetFilePath,
        uiState.files,
    ) {
        if (!uiState.preferences.shouldRestoreLastPlayedMediaInFolders) return@LaunchedEffect
        if (restoredDirectoryPath == uiState.currentPath) return@LaunchedEffect
        val restoreTargetFilePath = uiState.restoreTargetFilePath ?: return@LaunchedEffect
        val targetIndex = resolveCloudRestoreScrollIndex(uiState.files, restoreTargetFilePath) ?: return@LaunchedEffect
        lazyGridState.scrollToItem(targetIndex)
        restoredDirectoryPath = uiState.currentPath
    }

    // 出错时直接允许返回上级页面，不再反复重试 PROPFIND
    BackHandler(enabled = isInSelectionMode) {
        clearSelection()
    }

    BackHandler(enabled = !isInSelectionMode && !uiState.isAtRoot && !uiState.isError) {
        onNavigateUp()
    }

    val scrollBehavior = MiuixScrollBehavior()
    val shouldUseLargeTopBar = !isInSelectionMode && uiState.isAtRoot

    AppScaffold(
        topBar = {
            if (isInSelectionMode) {
                AppSmallTopAppBar(
                    title = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                    navigationIcon = {
                        MiuixIconButton(
                            onClick = { clearSelection() },
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        MiuixIconButton(
                            onClick = {
                                selectedFilePaths = if (selectedItemsSize != totalItemsSize) {
                                    uiState.files
                                        .map { it.path }
                                        .toSet()
                                } else {
                                    emptySet()
                                }
                                if (selectedFilePaths.isEmpty()) {
                                    shouldShowSelectionMenu = false
                                }
                            },
                        ) {
                            MiuixIcon(
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
                        MiuixIconButton(
                            onClick = { shouldShowSelectionMenu = true },
                            holdDownState = shouldShowSelectionMenu,
                            modifier = Modifier.testTag("btn_cloud_selection_actions"),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(id = R.string.more_actions),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        val selectedFiles = uiState.files.filter { it.path in selectedFilePaths }
                        val selectedFile = selectedFiles.singleOrNull()
                        MenuActionsPopup(
                            expanded = shouldShowSelectionMenu,
                            onDismissRequest = { shouldShowSelectionMenu = false },
                            groups = listOf(
                                cloudSelectionActions(
                                    shouldShowInfoAction = selectedFile?.isDirectory == false,
                                    onFavoriteAction = {
                                        if (selectedFiles.isEmpty()) return@cloudSelectionActions
                                        onEvent(CloudBrowseEvent.AddFavorites(selectedFiles))
                                        clearSelection()
                                    },
                                    onInfoAction = {
                                        val file = selectedFile?.takeUnless { it.isDirectory } ?: return@cloudSelectionActions
                                        onFileInfoClick(file)
                                        clearSelection()
                                    },
                                ),
                            ),
                        )
                    },
                )
            } else if (shouldUseLargeTopBar) {
                AppTopAppBar(
                    title = serverName,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        MiuixIconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        MiuixIconButton(
                            onClick = { shouldShowQuickSettingsDialog = true },
                            modifier = Modifier.testTag("btn_cloud_quick_settings"),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.DashBoard,
                                contentDescription = stringResource(R.string.cloud_quick_settings),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            } else {
                AppSmallTopAppBar(
                    title = serverName,
                    navigationIcon = {
                        MiuixIconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        MiuixIconButton(
                            onClick = { shouldShowQuickSettingsDialog = true },
                            modifier = Modifier.testTag("btn_cloud_quick_settings"),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.DashBoard,
                                contentDescription = stringResource(R.string.cloud_quick_settings),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                val contentPadding = innerPadding.copy(
                    top = if (shouldUseLargeTopBar) PageContentTopPadding else 0.dp,
                    start = 0.dp,
                ).withBottomFallback()
                val refreshTexts = rememberPullToRefreshTexts()
                PullToRefresh(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { onEvent(CloudBrowseEvent.Retry) },
                    topAppBarScrollBehavior = scrollBehavior.takeIf { !isInSelectionMode },
                    refreshTexts = refreshTexts,
                ) {
                    when {
                        uiState.isLoading && uiState.files.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        uiState.isError -> {
                            CloudBrowseMessageState(
                                contentPadding = contentPadding,
                                icon = AppIcons.Cloud,
                                title = stringResource(R.string.connection_failed),
                                message = uiState.errorMessage,
                                actionText = stringResource(R.string.retry),
                                onActionClick = { onEvent(CloudBrowseEvent.Retry) },
                            )
                        }

                        uiState.files.isEmpty() -> {
                            CloudBrowseMessageState(
                                contentPadding = contentPadding,
                                icon = AppIcons.Folder,
                                title = stringResource(R.string.empty_directory),
                            )
                        }

                        else -> {
                            val mostRecentFilePath = uiState.playbackStates.entries
                                .maxByOrNull { it.value.lastPlayedTime ?: 0L }
                                ?.takeIf { (it.value.lastPlayedTime ?: 0L) > 0L }
                                ?.key

                            CloudRemoteMediaView(
                                files = uiState.files,
                                directoryPath = layoutDirectoryPath,
                                settings = uiState.preferences.cloudQuickSettings(uiState.server?.id),
                                shouldMarkLastPlayedMedia = uiState.preferences.shouldMarkLastPlayedMedia,
                                playbackStates = uiState.playbackStates,
                                mostRecentFilePath = mostRecentFilePath,
                                selectedFilePaths = selectedFilePaths,
                                isInSelectionMode = isInSelectionMode,
                                lazyGridState = lazyGridState,
                                contentPadding = contentPadding,
                                onDirectoryClick = onDirectoryClick,
                                onFileClick = onFileClick,
                                buildFileThumbnailUri = buildFileThumbnailUri,
                                onToggleFileSelection = { file ->
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    toggleFileSelection(file)
                                },
                                onLongClickFile = { file ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toggleFileSelection(file)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isLoadingFileInfo) {
        RemoteFileInfoLoadingDialog(
            onDismiss = { onEvent(CloudBrowseEvent.DismissFileInfo) },
        )
    }

    uiState.infoVideo?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismiss = { onEvent(CloudBrowseEvent.DismissFileInfo) },
        )
    }

    if (shouldShowQuickSettingsDialog) {
        QuickSettingsDialog(
            applicationPreferences = uiState.preferences,
            target = QuickSettingsTarget.CLOUD,
            cloudServerId = uiState.server?.id,
            isRoot = uiState.isAtRoot,
            directoryPath = layoutDirectoryPath,
            onDismiss = { shouldShowQuickSettingsDialog = false },
            updatePreferences = { onEvent(CloudBrowseEvent.UpdateQuickSettings(it)) },
        )
    }
}

@Composable
private fun CloudRemoteMediaView(
    directoryPath: String,
    files: List<RemoteFile>,
    settings: CloudQuickSettings,
    shouldMarkLastPlayedMedia: Boolean,
    playbackStates: Map<String, RemotePlaybackInfo>,
    mostRecentFilePath: String?,
    selectedFilePaths: Set<String>,
    isInSelectionMode: Boolean,
    lazyGridState: LazyGridState,
    contentPadding: PaddingValues,
    onDirectoryClick: (String) -> Unit,
    onFileClick: (RemoteFile) -> Unit,
    buildFileThumbnailUri: (RemoteFile) -> Uri?,
    onToggleFileSelection: (RemoteFile) -> Unit,
    onLongClickFile: (RemoteFile) -> Unit,
) {
    val folders = files.filter(RemoteFile::isDirectory)
    val videos = files.filterNot(RemoteFile::isDirectory)
    val layouts = remember(settings, directoryPath) { settings.resolveMediaLayouts(directoryPath) }
    val displaySettings = remember(settings, directoryPath) {
        settings.withQuickSettings(null, settings.resolveQuickSettings(directoryPath))
    }
    PreserveMediaLayoutScroll(directoryPath, layouts, lazyGridState)

    BoxWithConstraints {
        val itemSpacing = CardItemGap
        val contentHorizontalPadding = 8.dp - itemSpacing / 2
        val sectionTitlePadding = PaddingValues(
            start = MediaSectionTitleStartPadding + itemSpacing / 2,
            top = 4.dp,
            bottom = 4.dp,
        )
        val geometry = mediaGridGeometry(
            availableWidth = maxWidth - 16.dp,
            layouts = layouts,
            hasFolders = folders.isNotEmpty(),
            hasVideos = videos.isNotEmpty(),
        )

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            columns = GridCells.Fixed(geometry.slots),
            contentPadding = contentPadding
                .subtractBottomPadding(MediaItemContentPadding) + PaddingValues(horizontal = contentHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(
                        text = stringResource(id = R.string.folders) + " (${folders.size})",
                        contentPadding = sectionTitlePadding,
                    )
                }
            }
            itemsIndexed(
                items = folders,
                key = { _, file -> file.path },
                span = { _, _ -> GridItemSpan(geometry.folderSpan) },
            ) { index, file ->
                RemoteFileItem(
                    file = file,
                    layoutMode = if (file.isDirectory) layouts.folders.layout.mode else layouts.videos.layout.mode,
                    settings = displaySettings,
                    thumbnailUri = null,
                    shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                    isRecentlyPlayed = false,
                    hasBeenPlayed = false,
                    isSelected = file.path in selectedFilePaths,
                    onClick = {
                        if (isInSelectionMode) {
                            onToggleFileSelection(file)
                        } else {
                            onDirectoryClick(file.path)
                        }
                    },
                    onLongClick = { onLongClickFile(file) },
                )
            }

            if (videos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(
                        text = stringResource(id = R.string.videos) + " (${videos.size})",
                        contentPadding = sectionTitlePadding,
                    )
                }
            }
            itemsIndexed(
                items = videos,
                key = { _, file -> file.path },
                span = { _, _ -> GridItemSpan(geometry.videoSpan) },
            ) { index, file ->
                val playbackInfo = playbackStates[file.path]
                val isRecentlyPlayed = file.path == mostRecentFilePath
                val hasBeenPlayed = playbackInfo != null && playbackInfo.playbackPosition > 0
                val isSelected = file.path in selectedFilePaths
                RemoteFileItem(
                    file = file,
                    layoutMode = if (file.isDirectory) layouts.folders.layout.mode else layouts.videos.layout.mode,
                    settings = displaySettings,
                    thumbnailUri = if (displaySettings.shouldShowThumbnailField) buildFileThumbnailUri(file) else null,
                    shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                    isRecentlyPlayed = isRecentlyPlayed,
                    hasBeenPlayed = hasBeenPlayed,
                    isSelected = isSelected,
                    onClick = {
                        if (isInSelectionMode) {
                            onToggleFileSelection(file)
                        } else {
                            onFileClick(file)
                        }
                    },
                    onLongClick = { onLongClickFile(file) },
                )
            }
        }
    }
}

@Composable
private fun CloudBrowseMessageState(
    contentPadding: PaddingValues,
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    MediaMessageState(
        icon = icon,
        title = title,
        contentPadding = contentPadding,
        message = message,
        action = if (!actionText.isNullOrBlank() && onActionClick != null) {
            { TextButton(text = actionText, onClick = onActionClick) }
        } else {
            null
        },
    )
}

@Composable
private fun RemoteFileItem(
    layoutMode: MediaLayoutMode,
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isRecentlyPlayed: Boolean = false,
    hasBeenPlayed: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = CardItemGap / 2)) {
        when (layoutMode) {
            MediaLayoutMode.LIST -> RemoteFileListItem(
                file = file,
                settings = settings,
                thumbnailUri = thumbnailUri,
                shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                isRecentlyPlayed = isRecentlyPlayed,
                hasBeenPlayed = hasBeenPlayed,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            MediaLayoutMode.GRID -> RemoteFileGridItem(
                file = file,
                settings = settings,
                thumbnailUri = thumbnailUri,
                shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                isRecentlyPlayed = isRecentlyPlayed,
                hasBeenPlayed = hasBeenPlayed,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun RemoteFileListItem(
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isRecentlyPlayed: Boolean,
    hasBeenPlayed: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shouldHighlight = isRecentlyPlayed && shouldMarkLastPlayedMedia
    val highlightColor = MiuixTheme.colorScheme.primary
    val shouldShowSize = !file.isDirectory && settings.shouldShowSizeField && file.size > 0
    val shouldShowPlayedDot = !file.isDirectory && settings.shouldShowPlayedProgress && hasBeenPlayed
    CardListItem(
        modifier = Modifier.testTag("remote_file_${file.name}"),
        isSelected = false,
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(MediaItemContentPadding),
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (file.isDirectory) {
                FolderThumbnail()
            } else {
                RemoteThumbnailView(
                    file = file,
                    thumbnailUri = thumbnailUri,
                    shouldShowThumbnail = settings.shouldShowThumbnailField,
                    modifier = Modifier.width(libraryListThumbWidth()),
                )
            }
        },
        trailingContent = {
            SelectionCheckIndicator(isSelected = isSelected)
        },
        content = {
            Text(
                text = file.displayName(settings),
                maxLines = 2,
                style = MiuixTheme.textStyles.title4,
                color = if (shouldHighlight) highlightColor else MiuixTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (file.isDirectory && settings.shouldShowPathField) {
                    Text(
                        text = file.parentDirectoryPath(),
                        maxLines = 1,
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Normal),
                        color = if (shouldHighlight) highlightColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                if (shouldShowSize || shouldShowPlayedDot) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (shouldShowSize) {
                            MediaMetaText(parts = listOf(formatFileSize(file.size)))
                        }
                        if (shouldShowPlayedDot) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(highlightColor, CircleShape),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun RemoteFileGridItem(
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isRecentlyPlayed: Boolean,
    hasBeenPlayed: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shouldHighlight = isRecentlyPlayed && shouldMarkLastPlayedMedia
    val highlightColor = MiuixTheme.colorScheme.primary
    CardListItem(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("remote_file_${file.name}"),
        isSelected = false,
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(MediaItemContentPadding),
        onClick = onClick,
        onLongClick = onLongClick,
        trailingContent = {
            SelectionCheckIndicator(isSelected = isSelected)
        },
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (file.isDirectory) {
                    FolderGridThumbnail()
                } else {
                    RemoteThumbnailView(
                        file = file,
                        thumbnailUri = thumbnailUri,
                        shouldShowThumbnail = settings.shouldShowThumbnailField,
                    )
                }
                Text(
                    text = file.displayName(settings),
                    maxLines = 2,
                    style = MiuixTheme.textStyles.title4,
                    color = if (shouldHighlight) highlightColor else MiuixTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (!file.isDirectory && settings.shouldShowPlayedProgress && hasBeenPlayed) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(highlightColor, CircleShape),
                    )
                }
            }
        },
    )
}

@Composable
private fun RemoteThumbnailView(
    file: RemoteFile,
    thumbnailUri: Uri?,
    shouldShowThumbnail: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cacheKey = thumbnailUri?.let(file::remoteThumbnailCacheKey)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .testTag("remote_thumbnail_${thumbnailUri?.scheme ?: "none"}")
            .aspectRatio(16f / 10f),
    ) {
        MiuixIcon(
            imageVector = AppIcons.Video,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.5f),
        )
        if (shouldShowThumbnail && thumbnailUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUri)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun RemoteFile.remoteThumbnailCacheKey(thumbnailUri: Uri): String = "$thumbnailUri#remoteSize=$size#remoteType=$contentType"

private fun Uri.withRemoteThumbnailCacheIdentity(server: RemoteServer): Uri = buildUpon()
    .appendQueryParameter("remoteKey", server.remoteThumbnailCacheIdentity())
    .build()

private fun RemoteServer.remoteThumbnailCacheIdentity(): String {
    val value = listOf(
        protocol.name,
        host,
        port?.toString().orEmpty(),
        path,
        username,
        isProxyEnabled.toString(),
        proxyHost,
        proxyPort?.toString().orEmpty(),
    ).joinToString(separator = "\u0000")
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

@Composable
private fun cloudSelectionActions(
    shouldShowInfoAction: Boolean,
    onFavoriteAction: () -> Unit,
    onInfoAction: () -> Unit,
): List<MenuAction> = buildList {
    add(
        MenuAction(
            text = stringResource(id = R.string.add_to_favorites),
            icon = AppIcons.LibraryBooks,
            testTag = "item_cloud_selection_add_favorites",
            onClick = onFavoriteAction,
        ),
    )
    if (shouldShowInfoAction) {
        add(
            MenuAction(
                text = stringResource(id = R.string.info),
                icon = AppIcons.Info,
                testTag = "item_cloud_selection_info",
                onClick = onInfoAction,
            ),
        )
    }
}

private fun resolveCloudRestoreScrollIndex(
    files: List<RemoteFile>,
    targetPath: String,
): Int? {
    val folders = files.filter(RemoteFile::isDirectory)
    val videos = files.filterNot(RemoteFile::isDirectory)
    val folderIndex = folders.indexOfFirst { it.path == targetPath }
    if (folderIndex >= 0) {
        return 1 + folderIndex
    }

    val videoIndex = videos.indexOfFirst { it.path == targetPath }
    if (videoIndex < 0) return null

    val folderSectionSize = if (folders.isEmpty()) 0 else 1 + folders.size
    val videoHeaderSize = if (videos.isEmpty()) 0 else 1
    return folderSectionSize + videoHeaderSize + videoIndex
}

@Composable
private fun RemoteFileInfoLoadingDialog(
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = R.string.info),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = null,
        modifier = Modifier.testTag("remote_file_info_loading_dialog"),
    )
}

private fun RemoteFile.displayName(settings: CloudQuickSettings): String {
    if (isDirectory || settings.shouldShowExtensionField) return name
    return name.substringBeforeLast(".", missingDelimiterValue = name)
}

private fun RemoteFile.parentDirectoryPath(): String = path
    .trimEnd('/')
    .substringBeforeLast("/", missingDelimiterValue = "/")
    .ifBlank { "/" }
    .let(Uri::decode)

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
