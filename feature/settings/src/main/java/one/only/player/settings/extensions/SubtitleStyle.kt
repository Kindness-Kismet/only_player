package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.SubtitleColor
import one.only.player.core.model.SubtitleEdgeStyle
import one.only.player.core.ui.R

@Composable
fun SubtitleColor.name(): String {
    val stringRes = when (this) {
        SubtitleColor.WHITE -> R.string.subtitle_color_white
        SubtitleColor.YELLOW -> R.string.subtitle_color_yellow
        SubtitleColor.CYAN -> R.string.subtitle_color_cyan
        SubtitleColor.GREEN -> R.string.subtitle_color_green
    }

    return stringResource(id = stringRes)
}

@Composable
fun SubtitleEdgeStyle.name(): String {
    val stringRes = when (this) {
        SubtitleEdgeStyle.NONE -> R.string.subtitle_edge_none
        SubtitleEdgeStyle.OUTLINE -> R.string.subtitle_edge_outline
        SubtitleEdgeStyle.DROP_SHADOW -> R.string.subtitle_edge_shadow
        SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW -> R.string.subtitle_edge_outline_shadow
    }

    return stringResource(id = stringRes)
}
