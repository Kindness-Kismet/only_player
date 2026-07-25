package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.ui.R

@Composable
fun PlayerIconStyle.name(): String {
    val stringRes = when (this) {
        PlayerIconStyle.TONAL,
        PlayerIconStyle.CLASSIC,
        -> R.string.player_icon_style_tonal
        PlayerIconStyle.TRANSLUCENT -> R.string.player_icon_style_translucent
        PlayerIconStyle.TRANSPARENT -> R.string.player_icon_style_transparent
    }
    return stringResource(stringRes)
}
