package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.TopBarStyle
import one.only.player.core.ui.R

@Composable
fun TopBarStyle.name(): String = stringResource(
    when (this) {
        TopBarStyle.STANDARD -> R.string.navigation_bar_style_standard
        TopBarStyle.HYPER_OS_4 -> R.string.bar_style_hyperos4
    },
)
