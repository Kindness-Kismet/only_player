package one.only.player.feature.videopicker.screens.search

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.Utils
import one.only.player.core.domain.SearchResults
import one.only.player.core.domain.asRootFolder
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppSmallTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.CardListItem
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.SearchTopAppBar
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.subtractBottomPadding
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.videopicker.composables.AddToPlaylistDialog
import one.only.player.feature.videopicker.composables.FolderItem
import one.only.player.feature.videopicker.composables.MediaItemContentPadding
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MediaView
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.MenuActionsPopup
import one.only.player.feature.videopicker.composables.RenameDialog
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.SelectionManager
import one.only.player.feature.videopicker.state.rememberSelectionManager
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences, playlist: List<Video>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onNavigateUp: () -> Unit,
    onMoveSelectionStarted: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFolderClick = { folder -> onFolderClick(folder.path) },
        onVideoClick = { video, playlist -> onPlayVideo(video, uiState.playerPreferences, playlist) },
        onMoveSelectionStarted = onMoveSelectionStarted,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun SearchScreen(
    uiState: SearchUiState,
    onNavigateUp: () -> Unit = {},
    onFolderClick: (Folder) -> Unit = {},
    onVideoClick: (Video, List<Video>) -> Unit = { _, _ -> },
    onMoveSelectionStarted: () -> Unit = {},
    onEvent: (SearchUiEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val selectionManager = rememberSelectionManager()
    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var shouldShowDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var shouldShowSelectionMenu by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistSelection by remember { mutableStateOf<Pair<List<Video>, List<Folder>>?>(null) }
    val rootFolder = uiState.searchResults.asRootFolder()
    val selectedVideos = remember(selectionManager.selectedVideos, rootFolder) {
        selectionManager.selectedVideos.mapNotNull { selectedVideo ->
            rootFolder.allMediaList.firstOrNull { video -> video.uriString == selectedVideo.uriString }
        }
    }
    val selectedFolders = remember(selectionManager.selectedFolders, rootFolder) {
        selectionManager.selectedFolders.mapNotNull { selectedFolder ->
            rootFolder.folderList.firstOrNull { folder -> folder.path == selectedFolder.path }
        }
    }
    val selectedVideoUris = selectionManager.allSelectedVideos.map { it.uriString }.distinct()
    val selectedItemsSize = selectionManager.selectedFolders.size + selectionManager.selectedVideos.size
    val totalItemsSize = rootFolder.folderList.size + rootFolder.mediaList.size
    val deleteResultMessage = when (uiState.deleteResult) {
        SearchDeleteResult.Deleted -> stringResource(R.string.delete_success)
        SearchDeleteResult.MovedToRecycleBin -> stringResource(R.string.move_to_recycle_bin_success)
        SearchDeleteResult.DeleteFailed -> stringResource(R.string.delete_failed)
        null -> null
    }
    val cacheAndOpenFolder: (Folder) -> Unit = { folder ->
        onEvent(SearchUiEvent.CacheFolderSnapshot(folder))
        onFolderClick(folder)
    }

    LaunchedEffect(deleteResultMessage) {
        val message = deleteResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(SearchUiEvent.ClearDeleteResult)
    }

    AppScaffold(
        topBar = {
            if (!selectionManager.isInSelectionMode) {
                SearchTopAppBar(
                    query = uiState.query,
                    placeholder = stringResource(R.string.search_videos_and_folders),
                    searchFieldTestTag = "input_search_query",
                    clearButtonTestTag = "btn_search_clear",
                    closeButtonTestTag = "btn_search_close",
                    onQueryChange = { onEvent(SearchUiEvent.OnQueryChange(it)) },
                    onSearch = { onEvent(SearchUiEvent.OnSearch(uiState.query)) },
                    onClose = onNavigateUp,
                )
            } else {
                AppSmallTopAppBar(
                    title = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                    navigationIcon = {
                        IconButton(
                            onClick = { selectionManager.exitSelectionMode() },
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("btn_search_selection_close"),
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    rootFolder.folderList.forEach { selectionManager.selectFolder(it) }
                                    rootFolder.mediaList.forEach { selectionManager.selectVideo(it) }
                                } else {
                                    selectionManager.exitSelectionMode()
                                }
                            },
                            modifier = Modifier.testTag("btn_search_selection_toggle_all"),
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
                        if (selectionManager.isSingleVideoSelected) {
                            IconButton(
                                onClick = {
                                    val video = selectedVideos.firstOrNull() ?: return@IconButton
                                    showInfoActionFor = video
                                    selectionManager.exitSelectionMode()
                                },
                                modifier = Modifier.testTag("btn_search_selection_info"),
                            ) {
                                Icon(
                                    imageVector = AppIcons.Info,
                                    contentDescription = stringResource(id = R.string.info),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        val primaryActions = searchSelectionPrimaryActions(
                            selectionManager = selectionManager,
                            selectedVideos = selectedVideos,
                            selectedFolders = selectedFolders,
                            selectedVideoUris = selectedVideoUris,
                            onEvent = onEvent,
                            onMoveSelectionStarted = onMoveSelectionStarted,
                            onAddToPlaylist = { videos, folders ->
                                pendingPlaylistSelection = videos to folders
                            },
                        )
                        val overflowActions = searchSelectionOverflowActions(
                            selectionManager = selectionManager,
                            selectedVideos = selectedVideos,
                            selectedVideoUris = selectedVideoUris,
                            onEvent = onEvent,
                            onRenameRequest = { video -> showRenameActionFor = video },
                        )
                        val deleteMenuAction = searchSelectionDeleteAction(
                            onDeleteRequest = { shouldShowDeleteConfirmation = true },
                        )
                        IconButton(
                            onClick = { shouldShowSelectionMenu = true },
                            holdDownState = shouldShowSelectionMenu,
                            modifier = Modifier.testTag("btn_search_selection_more"),
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
                    },
                )
            }
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
                modifier = Modifier.fillMaxSize(),
            ) {
                val updatedScaffoldPadding = scaffoldPadding.copy(top = 0.dp, start = 0.dp).withBottomFallback()
                if (uiState.query.isBlank()) {
                    SuggestionsContent(
                        searchHistory = uiState.searchHistory,
                        popularFolders = uiState.popularFolders,
                        preferences = uiState.preferences,
                        contentPadding = updatedScaffoldPadding,
                        onHistoryItemClick = { onEvent(SearchUiEvent.OnHistoryItemClick(it)) },
                        onRemoveHistoryItem = { onEvent(SearchUiEvent.OnRemoveHistoryItem(it)) },
                        onClearHistory = { onEvent(SearchUiEvent.OnClearHistory) },
                        onFolderClick = cacheAndOpenFolder,
                    )
                } else {
                    SearchResultsContent(
                        searchResults = uiState.searchResults,
                        preferences = uiState.preferences,
                        isSearching = uiState.isSearching,
                        contentPadding = updatedScaffoldPadding,
                        onFolderClick = cacheAndOpenFolder,
                        onVideoClick = onVideoClick,
                        onVideoLoaded = { onEvent(SearchUiEvent.AddToSync(it)) },
                        selectionManager = selectionManager,
                    )
                }
            }
        }
    }

    BackHandler(enabled = selectionManager.isInSelectionMode) {
        selectionManager.exitSelectionMode()
    }

    LaunchedEffect(selectionManager.isInSelectionMode) {
        if (!selectionManager.isInSelectionMode) shouldShowSelectionMenu = false
    }

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(SearchUiEvent.RenameVideo(video.uriString.toUri(), it))
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

    pendingPlaylistSelection?.let { (videos, folders) ->
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { pendingPlaylistSelection = null },
            onSelectPlaylist = { playlistId ->
                onEvent(SearchUiEvent.AddToPlaylist(playlistId, videos, folders))
                pendingPlaylistSelection = null
                selectionManager.exitSelectionMode()
            },
            onCreatePlaylist = { title ->
                onEvent(SearchUiEvent.CreatePlaylistAndAdd(title, videos, folders))
                pendingPlaylistSelection = null
                selectionManager.exitSelectionMode()
            },
        )
    }

    if (shouldShowDeleteConfirmation) {
        SearchDeleteConfirmationDialog(
            selectedVideos = selectionManager.allSelectedVideos,
            isRecycleBinEnabled = uiState.preferences.isRecycleBinEnabled,
            onConfirm = {
                if (uiState.preferences.isRecycleBinEnabled) {
                    onEvent(SearchUiEvent.MoveVideosToRecycleBin(selectedVideoUris))
                } else {
                    onEvent(SearchUiEvent.PermanentlyDeleteVideos(selectedVideoUris))
                }
                selectionManager.exitSelectionMode()
                shouldShowDeleteConfirmation = false
            },
            onCancel = { shouldShowDeleteConfirmation = false },
        )
    }
}

@Composable
private fun SuggestionsContent(
    searchHistory: List<String>,
    popularFolders: List<Folder>,
    preferences: ApplicationPreferences,
    contentPadding: PaddingValues = PaddingValues(),
    onHistoryItemClick: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFolderClick: (Folder) -> Unit,
) {
    if (searchHistory.isEmpty() && popularFolders.isEmpty()) {
        MediaMessageState(
            icon = AppIcons.Search,
            title = stringResource(R.string.search_videos_and_folders),
            contentPadding = contentPadding,
        )
        return
    }

    val listContentPadding = if (popularFolders.isNotEmpty()) {
        contentPadding.subtractBottomPadding(MediaItemContentPadding)
    } else {
        contentPadding
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp) + listContentPadding,
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListSectionTitle(
                        text = stringResource(R.string.recent_searches),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp),
                    )
                    TextButton(
                        text = stringResource(R.string.clear_history),
                        onClick = onClearHistory,
                    )
                }
            }
            items(
                items = searchHistory,
                key = { "history_$it" },
            ) { query ->
                SearchHistoryItem(
                    query = query,
                    onClick = { onHistoryItemClick(query) },
                    onRemove = { onRemoveHistoryItem(query) },
                )
            }
        }

        if (popularFolders.isNotEmpty()) {
            item {
                ListSectionTitle(
                    text = stringResource(R.string.popular_folders),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = if (searchHistory.isNotEmpty()) 12.dp else 6.dp,
                        bottom = 8.dp,
                    ),
                )
            }
            itemsIndexed(
                items = popularFolders,
                key = { _, folder -> "popular_${folder.path}" },
            ) { index, folder ->
                FolderItem(
                    folder = folder,
                    isRecentlyPlayedFolder = false,
                    preferences = preferences.copy(folderLayoutMode = MediaLayoutMode.LIST),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = { onFolderClick(folder) },
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    CardListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = AppIcons.History,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        trailingContent = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        content = {
            Text(
                text = query,
                style = MiuixTheme.textStyles.main,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SearchResultsContent(
    searchResults: SearchResults,
    preferences: ApplicationPreferences,
    isSearching: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onFolderClick: (Folder) -> Unit,
    onVideoClick: (Video, List<Video>) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
    selectionManager: one.only.player.feature.videopicker.state.SelectionManager,
) {
    AnimatedVisibility(
        visible = isSearching,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 100.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CircularProgressIndicator()
        }
    }

    AnimatedVisibility(
        visible = !isSearching,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (searchResults.isEmpty) {
            MediaMessageState(
                icon = AppIcons.Search,
                title = stringResource(R.string.no_results_found),
                contentPadding = contentPadding,
            )
        } else {
            val rootFolder = searchResults.asRootFolder()
            MediaView(
                rootFolder = rootFolder,
                preferences = preferences,
                onFolderClick = onFolderClick,
                onVideoClick = { video -> onVideoClick(video, rootFolder.mediaList) },
                onVideoLoaded = onVideoLoaded,
                shouldShowHeaders = true,
                selectionManager = selectionManager,
                contentPadding = contentPadding,
            )
        }
    }
}

// 搜索页选中模式菜单的高频操作组：分享/收藏/移动；删除单独成组置底。
@Composable
private fun searchSelectionPrimaryActions(
    selectionManager: SelectionManager,
    selectedVideos: List<Video>,
    selectedFolders: List<Folder>,
    selectedVideoUris: List<String>,
    onEvent: (SearchUiEvent) -> Unit,
    onMoveSelectionStarted: () -> Unit,
    onAddToPlaylist: (List<Video>, List<Folder>) -> Unit,
): List<MenuAction> = listOf(
    MenuAction(
        text = stringResource(id = R.string.share),
        icon = AppIcons.Share,
        testTag = "item_search_selection_share",
        onClick = {
            onEvent(SearchUiEvent.ShareVideos(selectedVideoUris))
        },
    ),
    MenuAction(
        text = stringResource(id = R.string.favorites),
        icon = AppIcons.LibraryBooks,
        testTag = "item_search_selection_add_favorites",
        onClick = {
            onEvent(SearchUiEvent.AddFavorites(selectedVideos, selectedFolders))
            selectionManager.exitSelectionMode()
        },
    ),
    MenuAction(
        text = stringResource(id = R.string.add_to_playlist),
        icon = AppIcons.PlaylistPlay,
        testTag = "item_search_selection_add_playlist",
        onClick = {
            onAddToPlaylist(selectedVideos, selectedFolders)
        },
    ),
    MenuAction(
        text = stringResource(id = R.string.move),
        icon = AppIcons.DriveFileMove,
        testTag = "item_search_selection_move",
        onClick = {
            onEvent(
                SearchUiEvent.StartMoveSelection(
                    videoUris = selectionManager.selectedVideos.map { it.uriString },
                    folderPaths = selectionManager.selectedFolders.map { it.path },
                ),
            )
            selectionManager.exitSelectionMode()
            onMoveSelectionStarted()
        },
    ),
)

// 删除入口单独成组，置于菜单末尾，用分隔线与其它操作区隔。
@Composable
private fun searchSelectionDeleteAction(onDeleteRequest: () -> Unit): MenuAction = MenuAction(
    text = stringResource(id = R.string.delete),
    icon = AppIcons.Delete,
    testTag = "item_search_selection_delete",
    onClick = onDeleteRequest,
)

// 搜索页选中模式顶栏溢出菜单的低频操作。
@Composable
private fun searchSelectionOverflowActions(
    selectionManager: SelectionManager,
    selectedVideos: List<Video>,
    selectedVideoUris: List<String>,
    onEvent: (SearchUiEvent) -> Unit,
    onRenameRequest: (Video) -> Unit,
): List<MenuAction> {
    val actions = mutableListOf<MenuAction>()
    if (selectionManager.isSingleVideoSelected) {
        actions += MenuAction(
            text = stringResource(id = R.string.rename),
            icon = AppIcons.Edit,
            testTag = "item_search_selection_rename",
            onClick = {
                selectedVideos.firstOrNull()?.let(onRenameRequest)
            },
        )
    }
    actions += MenuAction(
        text = stringResource(id = R.string.mark_as_played),
        icon = AppIcons.CheckBox,
        testTag = "item_search_selection_mark_played",
        onClick = {
            onEvent(SearchUiEvent.MarkVideosPlayed(selectedVideoUris))
            selectionManager.exitSelectionMode()
        },
    )
    actions += MenuAction(
        text = stringResource(id = R.string.mark_as_unplayed),
        icon = AppIcons.CheckBoxOutline,
        testTag = "item_search_selection_mark_unplayed",
        onClick = {
            onEvent(SearchUiEvent.MarkVideosUnplayed(selectedVideoUris))
            selectionManager.exitSelectionMode()
        },
    )
    return actions
}

@Composable
private fun SearchDeleteConfirmationDialog(
    selectedVideos: Collection<SelectedVideo>,
    isRecycleBinEnabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val selectedVideoList = selectedVideos.toList()
    val totalDuration = selectedVideoList.sumOf(SelectedVideo::duration)
    val totalSize = selectedVideoList.sumOf(SelectedVideo::size)
    AppDialog(
        onDismissRequest = onCancel,
        title = if (isRecycleBinEnabled) {
            stringResource(R.string.move_to_recycle_bin)
        } else {
            stringResource(R.string.delete_videos, selectedVideoList.size)
        },
        content = {
            val warningText = stringResource(
                if (isRecycleBinEnabled) {
                    R.string.move_to_recycle_bin_info
                } else {
                    R.string.delete_items_info
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = warningText,
                    style = MiuixTheme.textStyles.title4,
                )
                Text(
                    text = stringResource(R.string.delete_summary_count, selectedVideoList.size),
                    style = MiuixTheme.textStyles.main,
                )
                Text(
                    text = stringResource(R.string.delete_summary_size, Utils.formatFileSize(totalSize)),
                    style = MiuixTheme.textStyles.main,
                )
                Text(
                    text = stringResource(R.string.delete_summary_duration, Utils.formatDurationMillis(totalDuration)),
                    style = MiuixTheme.textStyles.main,
                )
                Text(
                    text = selectedVideoList.take(5).joinToString(separator = "\n") { it.nameWithExtension },
                    style = MiuixTheme.textStyles.body2,
                )
                if (selectedVideoList.size > 5) {
                    Text(
                        text = stringResource(R.string.delete_summary_more, selectedVideoList.size - 5),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        },
        confirmButton = {
            DoneButton(onClick = onConfirm)
        },
        dismissButton = {
            CancelButton(onClick = onCancel)
        },
    )
}

@PreviewLightDark
@Composable
private fun SearchScreenEmptyPreview() {
    OnlyPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenWithHistoryPreview() {
    OnlyPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                searchHistory = listOf("avengers", "movie", "trailer"),
                popularFolders = listOf(
                    Folder(
                        name = "Movies",
                        path = "/storage/Movies",
                        dateModified = System.currentTimeMillis(),
                        mediaList = listOf(Video.sample, Video.sample),
                    ),
                    Folder(
                        name = "Downloads",
                        path = "/storage/Downloads",
                        dateModified = System.currentTimeMillis(),
                        mediaList = listOf(Video.sample),
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenWithResultsPreview() {
    OnlyPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "movie",
                searchResults = SearchResults(
                    folders = listOf(
                        Folder(
                            name = "Movies",
                            path = "/storage/Movies",
                            dateModified = System.currentTimeMillis(),
                        ),
                    ),
                    videos = listOf(
                        Video.sample.copy(nameWithExtension = "Movie_Clip.mp4", uriString = "content://sample/movie_clip.mp4"),
                        Video.sample.copy(nameWithExtension = "My_Movie.mp4", uriString = "content://sample/my_movie.mp4"),
                    ),
                ),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenNoResultsPreview() {
    OnlyPlayerTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "xyz123",
                searchResults = SearchResults(),
            ),
        )
    }
}
