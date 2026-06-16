package one.only.player.feature.videopicker.screens.cloud

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.security.MessageDigest
import kotlin.math.abs
import one.only.player.core.data.models.RemotePlaybackInfo
import one.only.player.core.model.CloudQuickSettings
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.videopicker.composables.InfoChip
import one.only.player.feature.videopicker.composables.QuickSettingsDialog
import one.only.player.feature.videopicker.composables.QuickSettingsTarget
import one.only.player.feature.videopicker.composables.VideoInfoDialog

@Composable
fun CloudBrowseRoute(
    viewModel: CloudBrowseViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onDirectoryClick: (serverId: Long, path: String) -> Unit,
    onPlayVideo: (uri: Uri, headers: Map<String, String>, initialSubtitleDirectoryUri: Uri?, playlist: List<Uri>, playlistRemotePaths: List<String>) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 从播放器返回时刷新播放状态
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(CloudBrowseEvent.RefreshPlaybackStates)
        onPauseOrDispose {}
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var selectedFilePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shouldShowSelectionMenu by remember { mutableStateOf(false) }
    var shouldShowQuickSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var restoredDirectoryPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItemsSize = selectedFilePaths.size
    val totalItemsSize = uiState.files.count { !it.isDirectory }
    val isInSelectionMode = selectedFilePaths.isNotEmpty()

    fun clearSelection() {
        selectedFilePaths = emptySet()
        shouldShowSelectionMenu = false
    }

    fun toggleFileSelection(file: RemoteFile) {
        if (file.isDirectory) return
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
            .filter { path -> uiState.files.any { file -> !file.isDirectory && file.path == path } }
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

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = serverName.takeIf { !isInSelectionMode } ?: "",
                navigationIcon = {
                    if (isInSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { clearSelection() }
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
                    } else {
                        FilledTonalIconButton(onClick = {
                            onNavigateUp()
                        }) {
                            Icon(
                                imageVector = NextIcons.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_up),
                            )
                        }
                    }
                },
                actions = {
                    if (isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                selectedFilePaths = if (selectedItemsSize != totalItemsSize) {
                                    uiState.files
                                        .filter { !it.isDirectory }
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
                                enabled = selectedItemsSize == 1,
                                modifier = Modifier.testTag("btn_cloud_selection_actions"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Menu,
                                    contentDescription = stringResource(id = R.string.menu),
                                )
                            }
                            CloudSelectionActionsMenu(
                                expanded = shouldShowSelectionMenu,
                                onDismissRequest = { shouldShowSelectionMenu = false },
                                onInfoAction = {
                                    shouldShowSelectionMenu = false
                                    val selectedPath = selectedFilePaths.firstOrNull() ?: return@CloudSelectionActionsMenu
                                    val file = uiState.files.firstOrNull { it.path == selectedPath } ?: return@CloudSelectionActionsMenu
                                    onFileInfoClick(file)
                                    clearSelection()
                                },
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { shouldShowQuickSettingsDialog = true },
                            modifier = Modifier.testTag("btn_cloud_quick_settings"),
                        ) {
                            Icon(
                                imageVector = NextIcons.DashBoard,
                                contentDescription = stringResource(R.string.cloud_quick_settings),
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                val contentPadding = innerPadding.copy(top = 0.dp, start = 0.dp).withBottomFallback()
                PullToRefreshBox(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { onEvent(CloudBrowseEvent.Retry) },
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
                                icon = NextIcons.Cloud,
                                title = stringResource(R.string.connection_failed),
                                message = uiState.errorMessage,
                                actionText = stringResource(R.string.retry),
                                onActionClick = { onEvent(CloudBrowseEvent.Retry) },
                            )
                        }

                        uiState.files.isEmpty() -> {
                            CloudBrowseMessageState(
                                contentPadding = contentPadding,
                                icon = NextIcons.Folder,
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
            onDismiss = { shouldShowQuickSettingsDialog = false },
            updatePreferences = { onEvent(CloudBrowseEvent.UpdateQuickSettings(it)) },
        )
    }
}

@Composable
private fun CloudRemoteMediaView(
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
    val layoutScale = settings.normalizedMediaLayoutScale()
    val folderMinWidth = 90.dp * layoutScale
    val videoMinWidth = 160.dp * layoutScale

    BoxWithConstraints {
        val contentHorizontalPadding = 8.dp
        val itemSpacing = 2.dp
        val maxWidth = this.maxWidth - (contentHorizontalPadding * 2) - itemSpacing
        val maxFolders = (maxWidth / folderMinWidth).toInt()
        val maxVideos = (maxWidth / videoMinWidth).toInt()
        val spans = when (settings.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> cloudLcm(maxFolders.coerceAtLeast(1), maxVideos.coerceAtLeast(1))
        }
        val singleFolderSpan = when (settings.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxFolders.coerceAtLeast(1)
        }
        val singleVideoSpan = when (settings.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxVideos.coerceAtLeast(1)
        }

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            columns = GridCells.Fixed(spans),
            contentPadding = contentPadding + PaddingValues(horizontal = contentHorizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.folders) + " (${folders.size})")
                }
            }
            itemsIndexed(
                items = folders,
                key = { _, file -> file.path },
                span = { _, _ -> GridItemSpan(singleFolderSpan) },
            ) { index, file ->
                RemoteFileItem(
                    file = file,
                    settings = settings,
                    thumbnailUri = null,
                    shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                    isRecentlyPlayed = false,
                    hasBeenPlayed = false,
                    isSelected = false,
                    isFirstItem = index == 0,
                    isLastItem = index == folders.lastIndex,
                    onClick = {
                        if (!isInSelectionMode) {
                            onDirectoryClick(file.path)
                        }
                    },
                )
            }

            if (videos.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.videos) + " (${videos.size})")
                }
            }
            itemsIndexed(
                items = videos,
                key = { _, file -> file.path },
                span = { _, _ -> GridItemSpan(singleVideoSpan) },
            ) { index, file ->
                val playbackInfo = playbackStates[file.path]
                val isRecentlyPlayed = file.path == mostRecentFilePath
                val hasBeenPlayed = playbackInfo != null && playbackInfo.playbackPosition > 0
                val isSelected = file.path in selectedFilePaths
                RemoteFileItem(
                    file = file,
                    settings = settings,
                    thumbnailUri = if (settings.shouldShowThumbnailField) buildFileThumbnailUri(file) else null,
                    shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
                    isRecentlyPlayed = isRecentlyPlayed,
                    hasBeenPlayed = hasBeenPlayed,
                    isSelected = isSelected,
                    isFirstItem = index == 0,
                    isLastItem = index == videos.lastIndex,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(text = actionText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteFileItem(
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isRecentlyPlayed: Boolean = false,
    hasBeenPlayed: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    when (settings.mediaLayoutMode) {
        MediaLayoutMode.LIST -> RemoteFileListItem(
            file = file,
            settings = settings,
            thumbnailUri = thumbnailUri,
            shouldMarkLastPlayedMedia = shouldMarkLastPlayedMedia,
            isFirstItem = isFirstItem,
            isLastItem = isLastItem,
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
            isFirstItem = isFirstItem,
            isLastItem = isLastItem,
            isRecentlyPlayed = isRecentlyPlayed,
            hasBeenPlayed = hasBeenPlayed,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteFileListItem(
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isRecentlyPlayed: Boolean,
    hasBeenPlayed: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shouldHighlight = isRecentlyPlayed && shouldMarkLastPlayedMedia
    val highlightColor = MaterialTheme.colorScheme.primary
    NextSegmentedListItem(
        modifier = Modifier.testTag("remote_file_${file.name}"),
        isSelected = isSelected,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        contentPadding = PaddingValues(8.dp),
        colors = ListItemDefaults.segmentedColors(
            contentColor = if (shouldHighlight) {
                highlightColor
            } else {
                ListItemDefaults.segmentedColors().contentColor
            },
            supportingContentColor = if (shouldHighlight) {
                highlightColor
            } else {
                ListItemDefaults.colors().supportingContentColor
            },
            selectedContainerColor = selectedRemoteMediaContainerColor(),
        ),
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            if (file.isDirectory) {
                RemoteFolderThumbnail(
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                RemoteThumbnailView(
                    file = file,
                    thumbnailUri = thumbnailUri,
                    shouldShowThumbnail = settings.shouldShowThumbnailField,
                    modifier = Modifier.width(min(150.dp, LocalConfiguration.current.screenWidthDp.dp * 0.35f)),
                )
            }
        },
        content = {
            Text(
                text = file.displayName(settings),
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (settings.shouldShowPathField) {
                    Text(
                        text = file.parentDirectoryPath(),
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (!file.isDirectory && settings.shouldShowSizeField && file.size > 0) {
                        InfoChip(text = formatFileSize(file.size))
                    }
                    if (!file.isDirectory && settings.shouldShowPlayedProgress && hasBeenPlayed) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(highlightColor, CircleShape),
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoteFileGridItem(
    file: RemoteFile,
    settings: CloudQuickSettings,
    thumbnailUri: Uri?,
    shouldMarkLastPlayedMedia: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    isRecentlyPlayed: Boolean,
    hasBeenPlayed: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val shouldHighlight = isRecentlyPlayed && shouldMarkLastPlayedMedia
    val highlightColor = MaterialTheme.colorScheme.primary
    NextSegmentedListItem(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .testTag("remote_file_${file.name}"),
        isSelected = isSelected,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        contentPadding = PaddingValues(8.dp),
        colors = ListItemDefaults.segmentedColors(
            contentColor = if (shouldHighlight) {
                highlightColor
            } else {
                ListItemDefaults.segmentedColors().contentColor
            },
            supportingContentColor = if (shouldHighlight) {
                highlightColor
            } else {
                ListItemDefaults.colors().supportingContentColor
            },
            selectedContainerColor = selectedRemoteMediaContainerColor(),
        ),
        onClick = onClick,
        onLongClick = onLongClick,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (file.isDirectory) {
                    RemoteFolderThumbnail()
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
                    style = MaterialTheme.typography.titleMedium,
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
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .testTag("remote_thumbnail_${thumbnailUri?.scheme ?: "none"}")
            .aspectRatio(16f / 10f),
    ) {
        Icon(
            imageVector = NextIcons.Video,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceColorAtElevation(100.dp),
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
private fun RemoteFolderThumbnail(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(min(90.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f))
            .aspectRatio(20 / 17f),
    )
}

@Composable
private fun selectedRemoteMediaContainerColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Color(0xFFFFFFFF).copy(alpha = 0.20f)
} else {
    Color(0xFF212121).copy(alpha = 0.30f)
}

@Composable
private fun CloudSelectionActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onInfoAction: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("menu_cloud_selection_actions"),
        shape = RoundedCornerShape(10.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        ),
    ) {
        CloudSelectionMenuItem(
            text = stringResource(id = R.string.info),
            icon = NextIcons.Info,
            testTag = "item_cloud_selection_info",
            onClick = onInfoAction,
        )
    }
}

@Composable
private fun CloudSelectionMenuItem(
    text: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = text) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
        modifier = Modifier.testTag(testTag),
        onClick = onClick,
    )
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
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.info)) },
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
        confirmButton = {},
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

private fun cloudLcm(a: Int, b: Int): Int = abs(a * b) / cloudGcd(a, b)

private fun cloudGcd(a: Int, b: Int): Int = if (b == 0) a else cloudGcd(b, a % b)
