package one.only.player.feature.videopicker.screens.search

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.videopicker.composables.FolderItem
import one.only.player.feature.videopicker.composables.MediaView
import one.only.player.feature.videopicker.composables.RenameDialog
import one.only.player.feature.videopicker.composables.SelectionMenuItem
import one.only.player.feature.videopicker.composables.VideoInfoDialog
import one.only.player.feature.videopicker.state.SelectedVideo
import one.only.player.feature.videopicker.state.rememberSelectionManager

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    uiState: SearchUiState,
    onNavigateUp: () -> Unit = {},
    onFolderClick: (Folder) -> Unit = {},
    onVideoClick: (Video, List<Video>) -> Unit = { _, _ -> },
    onEvent: (SearchUiEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(deleteResultMessage) {
        val message = deleteResultMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(SearchUiEvent.ClearDeleteResult)
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = {
                    if (selectionManager.isInSelectionMode) {
                        Text(
                            text = stringResource(R.string.m_n_selected, selectedItemsSize, totalItemsSize),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = { onEvent(SearchUiEvent.OnQueryChange(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_videos_and_folders),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { onEvent(SearchUiEvent.OnQueryChange("")) }) {
                                        Icon(
                                            imageVector = NextIcons.Close,
                                            contentDescription = stringResource(R.string.clear_history),
                                        )
                                    }
                                } else if (uiState.isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    onEvent(SearchUiEvent.OnSearch(uiState.query))
                                    keyboardController?.hide()
                                },
                            ),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                errorBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            if (selectionManager.isInSelectionMode) {
                                selectionManager.exitSelectionMode()
                            } else {
                                onNavigateUp()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (selectionManager.isInSelectionMode) NextIcons.Close else NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (selectionManager.isInSelectionMode) {
                        FilledTonalIconButton(
                            onClick = {
                                if (selectedItemsSize != totalItemsSize) {
                                    rootFolder.folderList.forEach { selectionManager.selectFolder(it) }
                                    rootFolder.mediaList.forEach { selectionManager.selectVideo(it) }
                                } else {
                                    selectionManager.exitSelectionMode()
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
                                modifier = Modifier.testTag("btn_search_selection_actions"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Menu,
                                    contentDescription = stringResource(id = R.string.menu),
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
        },
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
                        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                    )
                    TextButton(onClick = onClearHistory) {
                        Text(text = stringResource(R.string.clear_history))
                    }
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
                        top = if (searchHistory.isNotEmpty()) 20.dp else 12.dp,
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

        if (searchHistory.isEmpty() && popularFolders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = NextIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.search_videos_and_folders),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
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
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 100.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = NextIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("menu_search_selection_actions"),
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
            text = stringResource(id = R.string.move),
            icon = NextIcons.Folder,
            testTag = "item_search_selection_move",
            onClick = onMoveAction,
        )
        SelectionMenuItem(
            text = stringResource(id = R.string.add_to_favorites),
            icon = NextIcons.LibraryBooks,
            testTag = "item_search_selection_add_favorites",
            onClick = onFavoriteAction,
        )
        if (shouldShowRenameAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.rename),
                icon = NextIcons.Edit,
                testTag = "item_search_selection_rename",
                onClick = onRenameAction,
            )
        }
        if (shouldShowInfoAction) {
            SelectionMenuItem(
                text = stringResource(id = R.string.info),
                icon = NextIcons.Info,
                testTag = "item_search_selection_info",
                onClick = onInfoAction,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        )
        SelectionMenuItem(
            text = stringResource(id = R.string.share),
            icon = NextIcons.Share,
            testTag = "item_search_selection_share",
            onClick = onShareAction,
        )
        SelectionMenuItem(
            text = stringResource(id = R.string.delete),
            icon = NextIcons.Delete,
            testTag = "item_search_selection_delete",
            onClick = onDeleteAction,
        )
    }
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
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.delete_summary_count, selectedVideoList.size),
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
                    text = selectedVideoList.take(5).joinToString(separator = "\n") { it.nameWithExtension },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selectedVideoList.size > 5) {
                    Text(
                        text = stringResource(R.string.delete_summary_more, selectedVideoList.size - 5),
                        style = MaterialTheme.typography.bodySmall,
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
