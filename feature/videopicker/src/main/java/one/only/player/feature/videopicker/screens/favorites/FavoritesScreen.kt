package one.only.player.feature.videopicker.screens.favorites

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.FavoriteItem
import one.only.player.core.model.FavoriteTargetType
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextSearchTopAppBar
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.withBottomFallback

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onPlayLocalVideo: (Uri) -> Unit,
    onOpenLocalFolder: (String) -> Unit,
    onOpenRemoteDirectory: (Long, String) -> Unit,
    onPlayRemoteVideo: (Uri, Map<String, String>, Uri?, List<Uri>, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openTarget) {
        when (val target = uiState.openTarget) {
            null -> Unit
            is FavoriteOpenTarget.LocalVideo -> onPlayLocalVideo(target.uri)
            is FavoriteOpenTarget.LocalFolder -> onOpenLocalFolder(target.path)
            is FavoriteOpenTarget.RemoteDirectory -> onOpenRemoteDirectory(target.serverId, target.path)
            is FavoriteOpenTarget.RemoteVideo -> {
                val initialSubtitleDirectoryUri = target.initialSubtitleDocumentId?.let { documentId ->
                    DocumentsContract.buildDocumentUri("${context.packageName}.documents", documentId)
                }
                onPlayRemoteVideo(target.uri, target.headers, initialSubtitleDirectoryUri, target.playlist, target.playlistRemotePaths)
            }
        }
        if (uiState.openTarget != null) {
            viewModel.onEvent(FavoritesUiEvent.ConsumeOpenTarget)
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onEvent(FavoritesUiEvent.ConsumeMessage)
    }

    FavoritesScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FavoritesScreen(
    uiState: FavoritesUiState,
    onNavigateUp: () -> Unit = {},
    onEvent: (FavoritesUiEvent) -> Unit = {},
) {
    var movingItem by remember { mutableStateOf<FavoriteItem?>(null) }
    var deletingItem by remember { mutableStateOf<FavoriteItem?>(null) }
    var shouldShowAddFolderDialog by rememberSaveable { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    val title = uiState.currentTitle ?: stringResource(R.string.favorites)

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotEmpty()) {
            isSearchActive = true
        }
    }

    BackHandler(enabled = uiState.currentParentId != null) {
        onEvent(FavoritesUiEvent.NavigateParent)
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "favorites_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    NextSearchTopAppBar(
                        query = uiState.searchQuery,
                        placeholder = stringResource(R.string.search_favorites),
                        searchFieldTestTag = "input_favorites_search",
                        clearButtonTestTag = "btn_favorites_search_clear",
                        onQueryChange = { onEvent(FavoritesUiEvent.UpdateSearchQuery(it)) },
                        onClose = {
                            isSearchActive = false
                            onEvent(FavoritesUiEvent.UpdateSearchQuery(""))
                        },
                    )
                } else {
                    NextTopAppBar(
                        title = title,
                        fontWeight = FontWeight.Bold,
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = {
                                    if (uiState.currentParentId == null) {
                                        onNavigateUp()
                                    } else {
                                        onEvent(FavoritesUiEvent.NavigateParent)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = NextIcons.ArrowBack,
                                    contentDescription = stringResource(id = R.string.navigate_up),
                                )
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("btn_favorites_search"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            IconButton(
                                onClick = { shouldShowAddFolderDialog = true },
                                modifier = Modifier.testTag("btn_favorites_add_folder"),
                            ) {
                                Icon(
                                    imageVector = NextIcons.Add,
                                    contentDescription = stringResource(R.string.add_favorite_folder),
                                )
                            }
                        },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.displayCutout,
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    if (uiState.visibleItems.isEmpty()) {
                        EmptyFavoritesContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding.copy(top = 8.dp, start = 0.dp).withBottomFallback()),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = innerPadding.copy(top = 8.dp, start = 0.dp).withBottomFallback(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            itemsIndexed(
                                uiState.visibleItems,
                                key = { _, item -> item.id },
                            ) { index, item ->
                                FavoriteListItem(
                                    item = item,
                                    isFirstItem = index == 0,
                                    isLastItem = index == uiState.visibleItems.lastIndex,
                                    onClick = { onEvent(FavoritesUiEvent.OpenItem(item)) },
                                    onMoveClick = { movingItem = item },
                                    onDeleteClick = { deletingItem = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (shouldShowAddFolderDialog) {
        AddFavoriteFolderDialog(
            onDismiss = { shouldShowAddFolderDialog = false },
            onAdd = { title ->
                onEvent(FavoritesUiEvent.AddFolder(title))
                shouldShowAddFolderDialog = false
            },
        )
    }

    movingItem?.let { item ->
        MoveFavoriteDialog(
            item = item,
            allItems = uiState.allItems,
            onDismiss = { movingItem = null },
            onMove = { parentId ->
                onEvent(FavoritesUiEvent.Move(item, parentId))
                movingItem = null
            },
        )
    }

    deletingItem?.let { item ->
        DeleteFavoriteDialog(
            item = item,
            onDismiss = { deletingItem = null },
            onDelete = {
                onEvent(FavoritesUiEvent.Delete(item))
                deletingItem = null
            },
        )
    }
}

@Composable
private fun EmptyFavoritesContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = NextIcons.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.no_favorites),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteListItem(
    item: FavoriteItem,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    NextSegmentedListItem(
        onClick = onClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        modifier = Modifier.testTag("favorite_item_${item.id}"),
        leadingContent = {
            Icon(
                imageVector = item.icon(),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        supportingContent = item.subtitle.takeIf { it.isNotBlank() }?.let {
            { Text(text = it) }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveClick) {
                    Icon(
                        imageVector = NextIcons.DriveFileMove,
                        contentDescription = stringResource(R.string.move),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = NextIcons.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        },
        content = {
            Text(text = item.title)
        },
    )
}

@Composable
private fun AddFavoriteFolderDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_favorite_folder)) },
        content = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onAdd(title.trim()) },
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun MoveFavoriteDialog(
    item: FavoriteItem,
    allItems: List<FavoriteItem>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    val disabledIds = item.descendantIds(allItems) + item.id
    val folderItems = allItems
        .filter { it.targetType == FavoriteTargetType.FAVORITE_FOLDER && it.id !in disabledIds }
        .sortedWith(compareBy<FavoriteItem> { it.parentId ?: 0L }.thenBy { it.title.lowercase() })

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_favorite)) },
        content = {
            Column {
                RadioTextButton(
                    text = stringResource(R.string.favorites_root),
                    isSelected = item.parentId == null,
                    onClick = { onMove(null) },
                )
                folderItems.forEach { folder ->
                    RadioTextButton(
                        text = folder.title,
                        isSelected = item.parentId == folder.id,
                        onClick = { onMove(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun DeleteFavoriteDialog(
    item: FavoriteItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_favorite)) },
        content = {
            Text(
                text = stringResource(
                    if (item.targetType == FavoriteTargetType.FAVORITE_FOLDER) {
                        R.string.delete_favorite_folder_description
                    } else {
                        R.string.delete_favorite_description
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

private fun FavoriteItem.icon(): ImageVector = when (targetType) {
    FavoriteTargetType.FAVORITE_FOLDER -> NextIcons.LibraryBooks
    FavoriteTargetType.LOCAL_VIDEO,
    FavoriteTargetType.REMOTE_FILE,
    -> NextIcons.Video
    FavoriteTargetType.LOCAL_FOLDER,
    FavoriteTargetType.REMOTE_DIRECTORY,
    FavoriteTargetType.REMOTE_SERVER_ROOT,
    -> NextIcons.Folder
}

private fun FavoriteItem.descendantIds(allItems: List<FavoriteItem>): Set<Long> {
    val childrenByParentId = allItems.groupBy { it.parentId }
    val pendingIds = ArrayDeque(listOf(id))
    val result = mutableSetOf<Long>()
    while (pendingIds.isNotEmpty()) {
        val currentId = pendingIds.removeFirst()
        childrenByParentId[currentId].orEmpty().forEach { child ->
            if (result.add(child.id)) pendingIds.add(child.id)
        }
    }
    return result
}
