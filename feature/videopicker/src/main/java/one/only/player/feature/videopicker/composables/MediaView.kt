package one.only.player.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.StoragePath
import one.only.player.core.model.Video
import one.only.player.core.model.resolveMediaLayouts
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withQuickSettings
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.extensions.subtractBottomPadding
import one.only.player.feature.videopicker.state.SelectionManager
import one.only.player.feature.videopicker.state.rememberSelectionManager

internal val MediaItemContentPadding = 8.dp

internal val MediaSectionTitleStartPadding = 15.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaView(
    rootFolder: Folder,
    preferences: ApplicationPreferences,
    layoutDirectory: StoragePath? = StoragePath.of(rootFolder.path).takeUnless(StoragePath::isRoot),
    shouldShowHeaders: Boolean = preferences.mediaViewMode == MediaViewMode.FOLDER_TREE,
    contentPadding: PaddingValues = PaddingValues(),
    selectionManager: SelectionManager = rememberSelectionManager(),
    lazyGridState: LazyGridState = rememberLazyGridState(),
    onFolderClick: (Folder) -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val layouts = remember(preferences, layoutDirectory) {
        preferences.resolveMediaLayouts(layoutDirectory)
    }
    val displayPreferences = remember(preferences, layoutDirectory) {
        preferences.withQuickSettings(null, preferences.resolveQuickSettings(layoutDirectory))
    }
    PreserveMediaLayoutScroll(layoutDirectory?.value.orEmpty(), layouts, lazyGridState)
    BoxWithConstraints {
        val layoutDirection = LocalLayoutDirection.current
        val visualContentPadding = contentPadding.subtractBottomPadding(MediaItemContentPadding)
        val itemSpacing = CardItemGap
        val contentHorizontalPadding = 8.dp - itemSpacing / 2
        val sectionTitleStartPadding = if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE) {
            MediaSectionTitleStartPadding + itemSpacing / 2
        } else {
            itemSpacing / 2
        }
        val geometry = mediaGridGeometry(
            availableWidth = maxWidth - 16.dp - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection),
            layouts = layouts,
            hasFolders = rootFolder.folderList.isNotEmpty(),
            hasVideos = rootFolder.mediaList.isNotEmpty(),
        )

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = lazyGridState,
            columns = GridCells.Fixed(geometry.slots),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + contentHorizontalPadding,
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection) + contentHorizontalPadding,
                bottom = visualContentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (shouldShowHeaders && rootFolder.folderList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(
                        text = stringResource(id = R.string.folders) + " (${rootFolder.folderList.size})",
                        contentPadding = PaddingValues(
                            start = sectionTitleStartPadding,
                            top = 4.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
            }
            itemsIndexed(
                items = rootFolder.folderList,
                key = { _, folder -> folder.path },
                span = { _, _ -> GridItemSpan(geometry.folderSpan) },
            ) { index, folder ->
                val isFolderSelected by remember { derivedStateOf { selectionManager.isFolderSelected(folder) } }
                FolderItem(
                    folder = folder,
                    layoutMode = layouts.folders.layout.mode,
                    modifier = Modifier.padding(horizontal = itemSpacing / 2),
                    isRecentlyPlayedFolder = rootFolder.isRecentlyPlayedVideo(folder.recentlyPlayedVideo),
                    preferences = displayPreferences,
                    isSelected = isFolderSelected,
                    onClick = {
                        if (selectionManager.isInSelectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            selectionManager.toggleFolderSelection(folder)
                        } else {
                            onFolderClick(folder)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionManager.toggleFolderSelection(folder)
                    },
                )
            }

            if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE && rootFolder.folderList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }

            if (shouldShowHeaders && rootFolder.mediaList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(
                        text = stringResource(id = R.string.videos) + " (${rootFolder.mediaList.size})",
                        contentPadding = PaddingValues(
                            start = sectionTitleStartPadding,
                            top = 4.dp,
                            bottom = 4.dp,
                        ),
                    )
                }
            }

            itemsIndexed(
                items = rootFolder.mediaList,
                key = { _, video -> video.uriString },
                span = { _, _ -> GridItemSpan(geometry.videoSpan) },
            ) { index, video ->
                val isVideoSelected by remember { derivedStateOf { selectionManager.isVideoSelected(video) } }
                VideoItem(
                    video = video,
                    layoutMode = layouts.videos.layout.mode,
                    preferences = displayPreferences,
                    isRecentlyPlayedVideo = rootFolder.isRecentlyPlayedVideo(video),
                    isSelected = isVideoSelected,
                    onClick = {
                        if (selectionManager.isInSelectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            selectionManager.toggleVideoSelection(video)
                        } else {
                            onVideoClick(video)
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectionManager.toggleVideoSelection(video)
                    },
                    modifier = Modifier.padding(horizontal = itemSpacing / 2).onVideoFirstVisible {
                        if (video.duration <= 0 || video.width <= 0 || video.height <= 0 || video.videoStream == null) {
                            onVideoLoaded(video.uriString.toUri())
                        }
                    },
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Modifier.onVideoFirstVisible(onVisible: () -> Unit): Modifier = onFirstVisible(callback = onVisible)
