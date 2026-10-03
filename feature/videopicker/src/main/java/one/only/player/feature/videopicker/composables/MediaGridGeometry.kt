package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.only.player.core.model.MediaItemLayout
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.ResolvedMediaLayouts
import one.only.player.core.ui.components.CardItemGap

internal data class MediaGridGeometry(
    val slots: Int,
    val folderSpan: Int,
    val videoSpan: Int,
)

internal fun mediaGridGeometry(
    availableWidth: Dp,
    layouts: ResolvedMediaLayouts,
    hasFolders: Boolean,
    hasVideos: Boolean,
): MediaGridGeometry {
    fun columns(layout: MediaItemLayout, minimumWidth: Dp, hasItems: Boolean): Int {
        if (!hasItems || layout.mode == MediaLayoutMode.LIST) return 1
        return ((availableWidth + CardItemGap) / (minimumWidth * layout.scale + CardItemGap)).toInt().coerceAtLeast(1)
    }
    val folders = columns(layouts.folders.layout, 90.dp, hasFolders)
    val videos = columns(layouts.videos.layout, 160.dp, hasVideos)
    var a = folders
    var b = videos
    while (b != 0) {
        val remainder = a % b
        a = b
        b = remainder
    }
    val slots = folders / a * videos
    return MediaGridGeometry(slots, slots / folders, slots / videos)
}

@Composable
internal fun PreserveMediaLayoutScroll(
    directoryKey: String,
    layouts: ResolvedMediaLayouts,
    state: LazyGridState,
) {
    val layoutKey = layouts.folders.layout to layouts.videos.layout
    var previousLayout by remember(directoryKey) { mutableStateOf(layoutKey) }
    SideEffect {
        if (previousLayout != layoutKey) {
            state.requestScrollToItem(state.firstVisibleItemIndex)
            previousLayout = layoutKey
        }
    }
}
