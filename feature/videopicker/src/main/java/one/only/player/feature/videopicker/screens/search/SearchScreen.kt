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
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextSearchTopAppBar
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.videopicker.composables.FolderItem
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MediaView
import one.only.player.feature.videopicker.composables.RenameDialog
import one.only.player.feature.videopicker.composables.SelectionActionsPopup
import one.only.player.feature.videopicker.composables.SelectionMenuAction
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.rememberSelectionManager
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchRoute(
    viewModel: SearchViewModel = hiltViewModel(),
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences, playlist: List<Video>) -> Unit,
    onFolderClick: (folderPath: String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onFolderClick = { folder -> onFolderClick(folder.path) },
        onVideoClick = { video, playlist -> onPlayVideo(video, uiState.playerPreferences, playlist) },
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun SearchScreen(
    uiState: SearchUiState,
    onNavigateUp: () -> Unit = {},
    onFolderClick: (Folder) -> Unit = {},
    onVideoClick: (Video, List<Video>) -> Unit = { _, _ -> },
    onEvent: (SearchUiEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val selectionManager = rememberSelectionManager()
    var shouldShowSelectionMenu by rememberSaveable { mutableStateOf(false) }
    var showRenameActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var showInfoActionFor: Video? by rememberSaveable { mutableStateOf(null) }
    var shouldShowDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            if (!selectionManager.isInSelectionMode) {
                NextSearchTopAppBar(
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
                SmallTopAppBar(
                    title = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                    navigationIcon = {
                        IconButton(
                            onClick = { selectionManager.exitSelectionMode() },
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Icon(
                                imageVector = NextIcons.Close,
                                contentDescription = stringResource(id = R.string.navigate_up),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        if (selectionManager.isInSelectionMode) {
                            IconButton(
                                onClick = {
                                    if (selectedItemsSize != totalItemsSize) {
                                        rootFolder.folderList.forEach { selectionManager.selectFolder(it) }
                                        rootFolder.mediaList.forEach { selectionManager.selectVideo(it) }
                                    } else {
                                        selectionManager.exitSelectionMode()
                                    }
                                },
                                modifier = Modifier,
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
                                    modifier = Modifier.testTag("btn_search_selection_actions"),
                                ) {
                                    Icon(
                                        imageVector = NextIcons.Menu,
                                        contentDescription = stringResource(id = R.string.menu),
                                        tint = MiuixTheme.colorScheme.onBackground,
                                    )
                                }
                                SearchSelectionActionsMenu(
                                    expanded = shouldShowSelectionMenu,
                                    onDismissRequest = { shouldShowSelectionMenu = false },
                                    shouldShowRenameAction = selectionManager.isSingleVideoSelected,
                                    shouldShowInfoAction = selectionManager.isSingleVideoSelected,
                                    onMoveAction = {
                                        shouldShowSelectionMenu = false
                                        onEvent(
                                            SearchUiEvent.StartMoveSelection(
                                                videoUris = selectionManager.selectedVideos.map { it.uriString },
                                                folderPaths = selectionManager.selectedFolders.map { it.path },
                                            ),
                                        )
                                        selectionManager.exitSelectionMode()
                                        onNavigateUp()
                                    },
                                    onFavoriteAction = {
                                        shouldShowSelectionMenu = false
                                        onEvent(SearchUiEvent.AddFavorites(selectedVideos, selectedFolders))
                                        selectionManager.exitSelectionMode()
                                    },
                                    onRenameAction = {
                                        shouldShowSelectionMenu = false
                                        showRenameActionFor = selectedVideos.firstOrNull()
                                    },
                                    onInfoAction = {
                                        shouldShowSelectionMenu = false
                                        showInfoActionFor = selectedVideos.firstOrNull()
                                        selectionManager.exitSelectionMode()
                                    },
                                    onShareAction = {
                                        shouldShowSelectionMenu = false
                                        onEvent(SearchUiEvent.ShareVideos(selectedVideoUris))
                                    },
                                    onDeleteAction = {
                                        shouldShowSelectionMenu = false
                                        shouldShowDeleteConfirmation = true
                                    },
                                )
                            }
                        }
                    },
                )
            }
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

    showRenameActionFor?.let { video ->
        RenameDialog(
            name = video.displayName,
            onDismiss = { showRenameActionFor = null },
            onDone = {
                onEvent(SearchUiEvent.RenameVideo(video.uriString.toUri(), it))
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
            icon = NextIcons.Search,
            title = stringResource(R.string.search_videos_and_folders),
            contentPadding = contentPadding,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp) + contentPadding,
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
                    preferences = preferences.copy(mediaLayoutMode = MediaLayoutMode.LIST),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    isFirstItem = index == 0,
                    isLastItem = index == popularFolders.lastIndex,
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
    NextSegmentedListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = NextIcons.History,
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
                    imageVector = NextIcons.Close,
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
                icon = NextIcons.Search,
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

@Composable
private fun SearchSelectionActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    shouldShowRenameAction: Boolean,
    shouldShowInfoAction: Boolean,
    onMoveAction: () -> Unit,
    onFavoriteAction: () -> Unit,
    onRenameAction: () -> Unit,
    onInfoAction: () -> Unit,
    onShareAction: () -> Unit,
    onDeleteAction: () -> Unit,
) {
    val primaryActions = buildList {
        add(
            SelectionMenuAction(
                text = stringResource(id = R.string.move),
                icon = NextIcons.Folder,
                testTag = "item_search_selection_move",
                onClick = onMoveAction,
            ),
        )
        add(
            SelectionMenuAction(
                text = stringResource(id = R.string.add_to_favorites),
                icon = NextIcons.LibraryBooks,
                testTag = "item_search_selection_add_favorites",
                onClick = onFavoriteAction,
            ),
        )
        if (shouldShowRenameAction) {
            add(
                SelectionMenuAction(
                    text = stringResource(id = R.string.rename),
                    icon = NextIcons.Edit,
                    testTag = "item_search_selection_rename",
                    onClick = onRenameAction,
                ),
            )
        }
        if (shouldShowInfoAction) {
            add(
                SelectionMenuAction(
                    text = stringResource(id = R.string.info),
                    icon = NextIcons.Info,
                    testTag = "item_search_selection_info",
                    onClick = onInfoAction,
                ),
            )
        }
    }
    SelectionActionsPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        groups = listOf(
            primaryActions,
            listOf(
                SelectionMenuAction(
                    text = stringResource(id = R.string.share),
                    icon = NextIcons.Share,
                    testTag = "item_search_selection_share",
                    onClick = onShareAction,
                ),
                SelectionMenuAction(
                    text = stringResource(id = R.string.delete),
                    icon = NextIcons.Delete,
                    testTag = "item_search_selection_delete",
                    onClick = onDeleteAction,
                ),
            ),
        ),
    )
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
    NextDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = if (isRecycleBinEnabled) {
                    stringResource(R.string.move_to_recycle_bin)
                } else {
                    stringResource(R.string.delete_videos, selectedVideoList.size)
                },
            )
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
