package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.NavigationBarStyle
import one.only.player.core.ui.R

@Composable
fun NavigationBarStyle.name(): String = stringResource(
    when (this) {
        NavigationBarStyle.STANDARD -> R.string.navigation_bar_style_standard
        NavigationBarStyle.FLOATING -> R.string.navigation_bar_style_floating
        NavigationBarStyle.MIUIX -> R.string.navigation_bar_style_miuix
    },
)
