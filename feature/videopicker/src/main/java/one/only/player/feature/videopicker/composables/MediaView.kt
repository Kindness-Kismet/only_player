package one.only.player.feature.videopicker.composables

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.abs
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.Video
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.extensions.plus
import one.only.player.feature.videopicker.state.SelectionManager
import one.only.player.feature.videopicker.state.rememberSelectionManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaView(
    rootFolder: Folder,
    preferences: ApplicationPreferences,
    shouldShowHeaders: Boolean = preferences.mediaViewMode == MediaViewMode.FOLDER_TREE,
    contentPadding: PaddingValues = PaddingValues(),
    selectionManager: SelectionManager = rememberSelectionManager(),
    lazyGridState: LazyGridState = rememberLazyGridState(),
    pinnedServers: List<RemoteServer> = emptyList(),
    onPinnedServerClick: (Long) -> Unit = {},
    onPinnedServerRemove: (Long) -> Unit = {},
    onFolderClick: (Folder) -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoLoaded: (Uri) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val layoutScale = preferences.normalizedMediaLayoutScale()

    val folderMinWidth = 90.dp * layoutScale
    val videoMinWidth = 160.dp * layoutScale
    BoxWithConstraints {
        val contentHorizontalPadding = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 8.dp
            MediaLayoutMode.GRID -> 8.dp
        }
        val itemSpacing = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 2.dp
            MediaLayoutMode.GRID -> 2.dp
        }
        val maxWidth = this.maxWidth - (contentHorizontalPadding * 2) - itemSpacing
        val maxFolders = (maxWidth / folderMinWidth).toInt()
        val maxVideos = (maxWidth / videoMinWidth).toInt()
        val spans = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> lcm(maxFolders.coerceAtLeast(1), maxVideos.coerceAtLeast(1))
        }

        val singleFolderSpan = when (preferences.mediaLayoutMode) {
            MediaLayoutMode.LIST -> 1
            MediaLayoutMode.GRID -> spans / maxFolders.coerceAtLeast(1)
        }
        val singleVideoSpan = when (preferences.mediaLayoutMode) {
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
            if (pinnedServers.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.cloud_servers))
                }
                itemsIndexed(
                    items = pinnedServers,
                    key = { _, server -> "pinned_server_${server.id}" },
                    span = { _, _ -> GridItemSpan(singleFolderSpan) },
                ) { _, server ->
                    FolderItem(
                        folder = Folder(
                            name = server.name.ifBlank { server.host },
                            path = "cloud://${server.id}",
                            dateModified = 0,
                        ),
                        isRecentlyPlayedFolder = false,
                        preferences = preferences,
                        isCloudBadge = true,
                        onClick = { onPinnedServerClick(server.id) },
                    )
                }
            }

            if (shouldShowHeaders && rootFolder.folderList.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListSectionTitle(text = stringResource(id = R.string.folders) + " (${rootFolder.folderList.size})")
                }
            }
            itemsIndexed(
                items = rootFolder.folderList,
                key = { _, folder -> folder.path },
                span = { _, _ -> GridItemSpan(singleFolderSpan) },
            ) { index, folder ->
                val isFolderSelected by remember { derivedStateOf { selectionManager.isFolderSelected(folder) } }
                val hasCloudServers = pinnedServers.isNotEmpty()
                FolderItem(
                    folder = folder,
                    isRecentlyPlayedFolder = rootFolder.isRecentlyPlayedVideo(folder.recentlyPlayedVideo),
                    preferences = preferences,
                    isSelected = isFolderSelected,
                    isLocalBadge = hasCloudServers,
                    isFirstItem = index == 0,
                    isLastItem = index == rootFolder.folderList.lastIndex,
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
                    onThumbnailClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
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
                    ListSectionTitle(text = stringResource(id = R.string.videos) + " (${rootFolder.mediaList.size})")
                }
            }

            itemsIndexed(
                items = rootFolder.mediaList,
                key = { _, video -> video.uriString },
                span = { _, _ -> GridItemSpan(singleVideoSpan) },
            ) { index, video ->
                val isVideoSelected by remember { derivedStateOf { selectionManager.isVideoSelected(video) } }
                VideoItem(
                    video = video,
                    preferences = preferences,
                    isRecentlyPlayedVideo = rootFolder.isRecentlyPlayedVideo(video),
                    isFirstItem = index == 0,
                    isLastItem = index == rootFolder.mediaList.lastIndex,
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
                    onThumbnailClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        selectionManager.toggleVideoSelection(video)
                    },
                    modifier = Modifier.onVideoFirstVisible {
                        if (video.duration <= 0 || video.width <= 0 || video.height <= 0 || video.videoStream == null) {
                            onVideoLoaded(video.uriString.toUri())
                        }
                    },
                )
            }
        }
    }
}

fun lcm(a: Int, b: Int): Int = abs(a * b) / gcd(a, b)

fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

@Suppress("DEPRECATION")
private fun Modifier.onVideoFirstVisible(onVisible: () -> Unit): Modifier = onFirstVisible(callback = onVisible)
