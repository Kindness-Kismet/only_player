package one.only.player.feature.videopicker.screens.playlists

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Playlist
import one.only.player.core.model.PlaylistItem
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.SearchTopAppBar
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.subtractBottomPadding
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.navigation.MiuixBackHandler
import one.only.player.feature.videopicker.composables.CreatePlaylistDialog
import one.only.player.feature.videopicker.composables.FolderThumbnail
import one.only.player.feature.videopicker.composables.LibraryEntryItem
import one.only.player.feature.videopicker.composables.LibraryIconThumb
import one.only.player.feature.videopicker.composables.MediaItemContentPadding
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.VideoThumbnail
import one.only.player.feature.videopicker.composables.libraryListThumbWidth
import one.only.player.feature.videopicker.composables.metaParts
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlaylistsRoute(
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onPlayVideos: (Video, PlayerPreferences, List<Uri>) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openTarget) {
        val target = uiState.openTarget ?: return@LaunchedEffect
        onPlayVideos(target.video, uiState.playerPreferences, target.playlist)
        viewModel.onEvent(PlaylistsUiEvent.ConsumeOpenTarget)
    }

    PlaylistsScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun PlaylistsScreen(
    uiState: PlaylistsUiState,
    onNavigateUp: () -> Unit = {},
    onEvent: (PlaylistsUiEvent) -> Unit = {},
) {
    var isSearchActive by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    var shouldShowCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val isDetail = uiState.currentPlaylistId != null
    val title = uiState.currentTitle ?: stringResource(R.string.playlists)
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotEmpty()) {
            isSearchActive = true
        }
    }

    MiuixBackHandler(enabled = isDetail || isSearchActive) {
        if (isSearchActive) {
            isSearchActive = false
            onEvent(PlaylistsUiEvent.UpdateSearchQuery(""))
            return@MiuixBackHandler
        }
        onEvent(PlaylistsUiEvent.NavigateParent)
    }

    AppScaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "playlists_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    SearchTopAppBar(
                        query = uiState.searchQuery,
                        placeholder = stringResource(
                            if (isDetail) R.string.search else R.string.search_playlists,
                        ),
                        searchFieldTestTag = "input_playlists_search",
                        clearButtonTestTag = "btn_playlists_search_clear",
                        onQueryChange = { onEvent(PlaylistsUiEvent.UpdateSearchQuery(it)) },
                        onClose = {
                            isSearchActive = false
                            onEvent(PlaylistsUiEvent.UpdateSearchQuery(""))
                        },
                    )
                } else {
                    AppTopAppBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            MiuixIconButton(
                                onClick = {
                                    if (isDetail) {
                                        onEvent(PlaylistsUiEvent.NavigateParent)
                                    } else {
                                        onNavigateUp()
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .testTag("btn_playlists_back"),
                            ) {
                                MiuixIcon(
                                    imageVector = AppIcons.ArrowBack,
                                    contentDescription = stringResource(id = R.string.navigate_up),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        actions = {
                            MiuixIconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("btn_playlists_search"),
                            ) {
                                MiuixIcon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            if (isDetail) {
                                if (uiState.items.any { it.video != null }) {
                                    MiuixIconButton(
                                        onClick = { onEvent(PlaylistsUiEvent.PlayPlaylist()) },
                                        modifier = Modifier.testTag("btn_playlist_play_all"),
                                    ) {
                                        MiuixIcon(
                                            imageVector = AppIcons.Play,
                                            contentDescription = stringResource(R.string.play_all),
                                            tint = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            } else {
                                MiuixIconButton(
                                    onClick = { shouldShowCreateDialog = true },
                                    modifier = Modifier.testTag("btn_playlists_add"),
                                ) {
                                    MiuixIcon(
                                        imageVector = AppIcons.Add,
                                        contentDescription = stringResource(R.string.create_playlist),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.displayCutout,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            val isEmpty = if (isDetail) uiState.items.isEmpty() else uiState.playlists.isEmpty()
            if (isEmpty) {
                MediaMessageState(
                    icon = AppIcons.PlaylistPlay,
                    title = stringResource(if (isDetail) R.string.empty_playlist else R.string.no_playlists),
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
                    if (isDetail) {
                        items(
                            uiState.items,
                            key = PlaylistItem::id,
                        ) { item ->
                            PlaylistMediaItem(
                                item = item,
                                preferences = uiState.preferences,
                                onClick = {
                                    if (item.video != null) {
                                        onEvent(PlaylistsUiEvent.PlayPlaylist(item))
                                    }
                                },
                                onRemove = { onEvent(PlaylistsUiEvent.RemoveItem(item)) },
                            )
                        }
                    } else {
                        items(
                            uiState.playlists,
                            key = Playlist::id,
                        ) { playlist ->
                            PlaylistCollectionItem(
                                playlist = playlist,
                                onClick = { onEvent(PlaylistsUiEvent.OpenPlaylist(playlist.id)) },
                                onRename = { renamingPlaylist = playlist },
                                onDelete = { deletingPlaylist = playlist },
                            )
                        }
                    }
                }
            }
        }
    }

    if (shouldShowCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { shouldShowCreateDialog = false },
            onCreate = { title ->
                onEvent(PlaylistsUiEvent.Create(title))
                shouldShowCreateDialog = false
            },
        )
    }

    renamingPlaylist?.let { playlist ->
        CreatePlaylistDialog(
            title = stringResource(R.string.rename_playlist),
            confirmText = stringResource(R.string.save),
            initialName = playlist.title,
            onDismiss = { renamingPlaylist = null },
            onCreate = { title ->
                onEvent(PlaylistsUiEvent.Rename(playlist.id, title))
                renamingPlaylist = null
            },
        )
    }

    deletingPlaylist?.let { playlist ->
        AppDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = stringResource(R.string.delete_playlist),
            content = {
                Text(text = stringResource(R.string.delete_playlist_description))
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_playlist_delete_confirm"),
                    text = stringResource(R.string.delete),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        onEvent(PlaylistsUiEvent.Delete(playlist.id))
                        deletingPlaylist = null
                    },
                )
            },
            dismissButton = { CancelButton(onClick = { deletingPlaylist = null }) },
        )
    }
}

@Composable
private fun PlaylistCollectionItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val chips = buildList {
        if (playlist.itemCount > 0) {
            add(
                "${playlist.itemCount} " + stringResource(
                    id = R.string.video.takeIf { playlist.itemCount == 1 } ?: R.string.videos,
                ),
            )
        }
    }

    LibraryEntryItem(
        title = playlist.title,
        chips = chips,
        testTag = "playlist_item_${playlist.id}",
        onClick = onClick,
        leadingContent = { FolderThumbnail() },
        overflowActions = listOf(
            MenuAction(
                text = stringResource(R.string.rename),
                icon = AppIcons.Edit,
                testTag = "item_playlist_rename_${playlist.id}",
                onClick = onRename,
            ),
            MenuAction(
                text = stringResource(R.string.delete),
                icon = AppIcons.Delete,
                testTag = "item_playlist_delete_${playlist.id}",
                onClick = onDelete,
            ),
        ),
    )
}

@Composable
private fun PlaylistMediaItem(
    item: PlaylistItem,
    preferences: ApplicationPreferences,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val video = item.video
    val title = video?.let { current ->
        if (preferences.shouldShowExtensionField) current.nameWithExtension else current.displayName
    } ?: item.title.substringBeforeLast('.')
    val chips = video?.metaParts(preferences).orEmpty()

    LibraryEntryItem(
        title = title,
        chips = chips,
        testTag = "playlist_media_${item.id}",
        onClick = onClick,
        leadingContent = {
            if (video != null) {
                VideoThumbnail(
                    video = video,
                    preferences = preferences,
                    modifier = Modifier.width(libraryListThumbWidth()),
                )
            } else {
                LibraryIconThumb(icon = AppIcons.Video)
            }
        },
        overflowActions = listOf(
            MenuAction(
                text = stringResource(R.string.remove_from_playlist),
                icon = AppIcons.Delete,
                testTag = "item_playlist_remove_${item.id}",
                onClick = onRemove,
            ),
        ),
    )
}
