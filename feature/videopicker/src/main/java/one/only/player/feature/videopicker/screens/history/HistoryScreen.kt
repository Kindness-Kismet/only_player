package one.only.player.feature.videopicker.screens.history

import android.text.format.DateUtils
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
import one.only.player.feature.videopicker.composables.LibraryEntryItem
import one.only.player.feature.videopicker.composables.MediaItemContentPadding
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.MenuAction
import one.only.player.feature.videopicker.composables.VideoThumbnail
import one.only.player.feature.videopicker.composables.libraryListThumbWidth
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onPlayVideo: (Video, PlayerPreferences) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onPlayVideo = onPlayVideo,
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun HistoryScreen(
    uiState: HistoryUiState,
    onNavigateUp: () -> Unit = {},
    onPlayVideo: (Video, PlayerPreferences) -> Unit = { _, _ -> },
    onEvent: (HistoryUiEvent) -> Unit = {},
) {
    var isSearchActive by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotEmpty()) }
    var shouldShowClearDialog by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery.isNotEmpty()) {
            isSearchActive = true
        }
    }

    MiuixBackHandler(enabled = isSearchActive) {
        isSearchActive = false
        onEvent(HistoryUiEvent.UpdateSearchQuery(""))
    }

    AppScaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "history_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    SearchTopAppBar(
                        query = uiState.searchQuery,
                        placeholder = stringResource(R.string.search_watch_history),
                        searchFieldTestTag = "input_history_search",
                        clearButtonTestTag = "btn_history_search_clear",
                        onQueryChange = { onEvent(HistoryUiEvent.UpdateSearchQuery(it)) },
                        onClose = {
                            isSearchActive = false
                            onEvent(HistoryUiEvent.UpdateSearchQuery(""))
                        },
                    )
                } else {
                    AppTopAppBar(
                        title = stringResource(R.string.watch_history),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            MiuixIconButton(
                                onClick = onNavigateUp,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .testTag("btn_history_back"),
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
                                modifier = Modifier.testTag("btn_history_search"),
                            ) {
                                MiuixIcon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            if (uiState.videos.isNotEmpty()) {
                                MiuixIconButton(
                                    onClick = { shouldShowClearDialog = true },
                                    modifier = Modifier.testTag("btn_history_clear"),
                                ) {
                                    MiuixIcon(
                                        imageVector = AppIcons.DeleteSweep,
                                        contentDescription = stringResource(R.string.clear_watch_history),
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
            if (uiState.videos.isEmpty()) {
                MediaMessageState(
                    icon = AppIcons.History,
                    title = stringResource(R.string.no_watch_history),
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
                    items(
                        uiState.videos,
                        key = Video::uriString,
                    ) { video ->
                        HistoryVideoItem(
                            video = video,
                            preferences = uiState.preferences,
                            onClick = { onPlayVideo(video, uiState.playerPreferences) },
                            onRemove = { onEvent(HistoryUiEvent.Remove(video)) },
                        )
                    }
                }
            }
        }
    }

    if (shouldShowClearDialog) {
        AppDialog(
            onDismissRequest = { shouldShowClearDialog = false },
            title = stringResource(R.string.clear_watch_history),
            content = {
                Text(text = stringResource(R.string.clear_watch_history_description))
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_history_clear_confirm"),
                    text = stringResource(R.string.clear_watch_history),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        onEvent(HistoryUiEvent.Clear)
                        shouldShowClearDialog = false
                    },
                )
            },
            dismissButton = { CancelButton(onClick = { shouldShowClearDialog = false }) },
        )
    }
}

@Composable
private fun HistoryVideoItem(
    video: Video,
    preferences: ApplicationPreferences,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val relativeTime = video.lastPlayedAt?.time?.let { playedAt ->
        DateUtils.getRelativeTimeSpanString(
            playedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    val chips = buildList {
        relativeTime?.let(::add)
        if (preferences.shouldShowSizeField) {
            add(video.formattedFileSize)
        }
        if (preferences.shouldShowResolutionField && video.height > 0) {
            add("${video.height}p")
        }
    }

    LibraryEntryItem(
        title = if (preferences.shouldShowExtensionField) video.nameWithExtension else video.displayName,
        chips = chips,
        testTag = "history_item_${video.displayName}",
        onClick = onClick,
        leadingContent = {
            VideoThumbnail(
                video = video,
                preferences = preferences,
                modifier = Modifier.width(libraryListThumbWidth()),
            )
        },
        overflowActions = listOf(
            MenuAction(
                text = stringResource(R.string.remove_from_history),
                icon = AppIcons.Delete,
                testTag = "item_history_remove_${video.displayName}",
                onClick = onRemove,
            ),
        ),
    )
}
