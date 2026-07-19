package one.only.player.feature.videopicker.screens.favorites

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.videopicker.composables.MediaMessageState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val scrollBehavior = MiuixScrollBehavior()

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
                    TopAppBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            // 根目录作为底栏 Tab，不显示返回键；子目录显示返回键回到父目录
                            if (uiState.currentParentId != null) {
                                MiuixIconButton(
                                    onClick = { onEvent(FavoritesUiEvent.NavigateParent) },
                                    modifier = Modifier.padding(start = 12.dp),
                                ) {
                                    MiuixIcon(
                                        imageVector = NextIcons.ArrowBack,
                                        contentDescription = stringResource(id = R.string.navigate_up),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        },
                        actions = {
                            MiuixIconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("btn_favorites_search"),
                            ) {
                                MiuixIcon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            MiuixIconButton(
                                onClick = { shouldShowAddFolderDialog = true },
                                modifier = Modifier.testTag("btn_favorites_add_folder"),
                            ) {
                                MiuixIcon(
                                    imageVector = NextIcons.Add,
                                    contentDescription = stringResource(R.string.add_favorite_folder),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.displayCutout,
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            if (uiState.visibleItems.isEmpty()) {
                EmptyFavoritesContent(
                    contentPadding = innerPadding.copy(top = 8.dp, start = 0.dp).withBottomFallback(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = innerPadding.copy(top = 8.dp, start = 0.dp).withBottomFallback(),
                    verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
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
private fun EmptyFavoritesContent(contentPadding: androidx.compose.foundation.layout.PaddingValues) {
    MediaMessageState(
        icon = NextIcons.LibraryBooks,
        title = stringResource(R.string.no_favorites),
        contentPadding = contentPadding,
    )
}

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
            MiuixIcon(
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
                MiuixIconButton(onClick = onMoveClick) {
                    MiuixIcon(
                        imageVector = NextIcons.DriveFileMove,
                        contentDescription = stringResource(R.string.move),
                    )
                }
                MiuixIconButton(onClick = onDeleteClick) {
                    MiuixIcon(
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
        title = stringResource(R.string.add_favorite_folder),
        content = {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = stringResource(R.string.name),
            )
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_favorite_folder_add_confirm"),
                text = stringResource(R.string.add),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { onAdd(title.trim()) },
            )
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
        title = stringResource(R.string.move_favorite),
        content = {
            androidx.compose.foundation.layout.Column {
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
        title = stringResource(R.string.delete_favorite),
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
            TextButton(
                modifier = Modifier.testTag("btn_favorite_delete_confirm"),
                text = stringResource(R.string.delete),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onDelete,
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

private fun FavoriteItem.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (targetType) {
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
