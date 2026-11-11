package one.only.player.feature.videopicker.screens.favorites

import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import java.io.File
import one.only.player.core.media.extensions.isStorageRoot
import one.only.player.core.media.extensions.storageRootLabelOf
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.FavoriteItem
import one.only.player.core.model.FavoriteTargetType
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SearchTopAppBar
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.subtractBottomPadding
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.navigation.MiuixBackHandler
import one.only.player.feature.videopicker.composables.FolderThumbnail
import one.only.player.feature.videopicker.composables.LibraryEntryItem
import one.only.player.feature.videopicker.composables.LibraryIconThumb
import one.only.player.feature.videopicker.composables.MediaItemContentPadding
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.VideoThumbnail
import one.only.player.feature.videopicker.composables.libraryListThumbWidth
import one.only.player.feature.videopicker.composables.metaParts
import one.only.player.feature.videopicker.composables.rememberStorageRootLabels
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
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

    MiuixBackHandler(enabled = uiState.currentParentId != null) {
        onEvent(FavoritesUiEvent.NavigateParent)
    }

    AppScaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "favorites_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    SearchTopAppBar(
                        query = uiState.searchQuery,
                        placeholder = stringResource(R.string.search_favorites),
                        searchFieldTestTag = "input_favorites_search",
                        clearButtonTestTag = "btn_favorites_search_clear",
                        isActive = isSearchActive,
                        onQueryChange = { onEvent(FavoritesUiEvent.UpdateSearchQuery(it)) },
                        onClose = {
                            isSearchActive = false
                            onEvent(FavoritesUiEvent.UpdateSearchQuery(""))
                        },
                    )
                } else {
                    AppTopAppBar(
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
                                        imageVector = AppIcons.ArrowBack,
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
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            MiuixIconButton(
                                onClick = { shouldShowAddFolderDialog = true },
                                modifier = Modifier.testTag("btn_favorites_add_folder"),
                            ) {
                                MiuixIcon(
                                    imageVector = AppIcons.Add,
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
                    contentPadding = innerPadding.copy(top = PageContentTopPadding, start = 0.dp).withBottomFallback(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = innerPadding.copy(top = PageContentTopPadding, start = 0.dp)
                        .withBottomFallback()
                        .subtractBottomPadding(MediaItemContentPadding),
                    verticalArrangement = Arrangement.spacedBy(CardItemGap),
                ) {
                    itemsIndexed(
                        uiState.visibleItems,
                        key = { _, item -> item.id },
                    ) { _, item ->
                        FavoriteListItem(
                            item = item,
                            allItems = uiState.allItems,
                            preferences = uiState.preferences,
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
private fun EmptyFavoritesContent(contentPadding: PaddingValues) {
    MediaMessageState(
        icon = AppIcons.FavoritesLine,
        title = stringResource(R.string.no_favorites),
        contentPadding = contentPadding,
    )
}

@Composable
private fun FavoriteListItem(
    item: FavoriteItem,
    allItems: List<FavoriteItem>,
    preferences: ApplicationPreferences,
    onClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val chips = item.libraryChips(allItems, preferences)

    LibraryEntryItem(
        title = item.displayTitle(preferences.shouldShowExtensionField),
        chips = chips,
        testTag = "favorite_item_${item.id}",
        onClick = onClick,
        leadingContent = {
            FavoriteLeading(
                item = item,
                preferences = preferences,
            )
        },
        overflowActions = listOf(
            MenuAction(
                text = stringResource(R.string.move),
                icon = AppIcons.DriveFileMove,
                testTag = "item_favorite_move_${item.id}",
                onClick = onMoveClick,
            ),
            MenuAction(
                text = stringResource(R.string.delete),
                icon = AppIcons.Delete,
                testTag = "item_favorite_delete_${item.id}",
                onClick = onDeleteClick,
            ),
        ),
    )
}

@Composable
private fun FavoriteLeading(
    item: FavoriteItem,
    preferences: ApplicationPreferences,
) {
    when (item.targetType) {
        FavoriteTargetType.LOCAL_VIDEO -> {
            val video = item.video ?: Video.sample.copy(
                uriString = item.localUri.orEmpty(),
                path = item.localPath.orEmpty(),
                nameWithExtension = item.title,
                formattedDuration = "",
                playbackPosition = 0,
            )
            VideoThumbnail(
                video = video,
                preferences = if (item.video != null) {
                    preferences
                } else {
                    preferences.copy(
                        shouldShowDurationField = false,
                        shouldShowPlayedProgress = false,
                    )
                },
                modifier = Modifier.width(libraryListThumbWidth()),
            )
        }
        FavoriteTargetType.LOCAL_FOLDER,
        FavoriteTargetType.FAVORITE_FOLDER,
        -> FolderThumbnail()
        FavoriteTargetType.REMOTE_FILE,
        FavoriteTargetType.REMOTE_DIRECTORY,
        FavoriteTargetType.REMOTE_SERVER_ROOT,
        -> LibraryIconThumb(icon = AppIcons.Cloud)
    }
}

@Composable
private fun AddFavoriteFolderDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    AppDialog(
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.move_favorite),
        content = {
            androidx.compose.foundation.layout.Column {
                RadioTextButton(
                    text = stringResource(R.string.favorites_root),
                    isSelected = item.parentId == null,
                    onClick = { onMove(null) },
                    modifier = Modifier.testTag("option_favorite_move_root"),
                )
                folderItems.forEach { folder ->
                    RadioTextButton(
                        text = folder.title,
                        isSelected = item.parentId == folder.id,
                        onClick = { onMove(folder.id) },
                        modifier = Modifier.testTag("option_favorite_move_${folder.id}"),
                    )
                }
            }
        },
        confirmButton = null,
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun DeleteFavoriteDialog(
    item: FavoriteItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AppDialog(
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

@Composable
private fun FavoriteItem.displayTitle(shouldShowExtension: Boolean): String {
    val storageRootLabels = rememberStorageRootLabels()
    return when (targetType) {
        FavoriteTargetType.LOCAL_VIDEO -> video?.let { current ->
            if (shouldShowExtension) current.nameWithExtension else current.displayName
        } ?: title.substringBeforeLast('.')
        FavoriteTargetType.LOCAL_FOLDER -> {
            val storageRootLabel = remember(localPath, storageRootLabels) {
                localPath?.let(storageRootLabels::storageRootLabelOf)
            }
            storageRootLabel ?: title
        }
        FavoriteTargetType.FAVORITE_FOLDER,
        FavoriteTargetType.REMOTE_FILE,
        FavoriteTargetType.REMOTE_DIRECTORY,
        FavoriteTargetType.REMOTE_SERVER_ROOT,
        -> title
    }
}

@Composable
private fun FavoriteItem.libraryChips(
    allItems: List<FavoriteItem>,
    preferences: ApplicationPreferences,
): List<String> {
    if (targetType == FavoriteTargetType.FAVORITE_FOLDER) {
        val children = allItems.filter { child -> child.parentId == id }
        val videoCount = children.count { child -> child.isVideoFavorite() }
        val folderCount = children.count { child -> child.isFolderFavorite() }
        return buildList {
            if (videoCount > 0) {
                add(
                    "$videoCount " + stringResource(
                        id = R.string.video.takeIf { videoCount == 1 } ?: R.string.videos,
                    ),
                )
            }
            if (folderCount > 0) {
                add(
                    "$folderCount " + stringResource(
                        id = R.string.folder.takeIf { folderCount == 1 } ?: R.string.folders,
                    ),
                )
            }
        }
    }

    return buildList {
        addAll(video?.metaParts(preferences).orEmpty())
        locationLabel()?.let(::add)
    }
}

private fun FavoriteItem.isVideoFavorite(): Boolean = when (targetType) {
    FavoriteTargetType.LOCAL_VIDEO,
    FavoriteTargetType.REMOTE_FILE,
    -> true
    FavoriteTargetType.FAVORITE_FOLDER,
    FavoriteTargetType.LOCAL_FOLDER,
    FavoriteTargetType.REMOTE_DIRECTORY,
    FavoriteTargetType.REMOTE_SERVER_ROOT,
    -> false
}

private fun FavoriteItem.isFolderFavorite(): Boolean = !isVideoFavorite()

@Composable
private fun FavoriteItem.locationLabel(): String? {
    val storageRootLabels = rememberStorageRootLabels()
    return when (targetType) {
        FavoriteTargetType.FAVORITE_FOLDER,
        FavoriteTargetType.LOCAL_VIDEO,
        -> null
        FavoriteTargetType.LOCAL_FOLDER -> remember(localPath, storageRootLabels) {
            localPath
                ?.takeUnless(storageRootLabels::isStorageRoot)
                ?.let { path -> File(path).parentFile }
                ?.let { parent -> storageRootLabels.storageRootLabelOf(parent.path) ?: parent.name }
        }
        FavoriteTargetType.REMOTE_FILE,
        FavoriteTargetType.REMOTE_DIRECTORY,
        FavoriteTargetType.REMOTE_SERVER_ROOT,
        -> remoteServerName?.takeIf { it.isNotBlank() }
    }
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
