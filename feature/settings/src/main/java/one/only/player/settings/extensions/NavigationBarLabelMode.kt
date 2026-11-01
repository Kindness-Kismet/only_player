package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.NavigationBarLabelMode
import one.only.player.core.ui.R

@Composable
fun NavigationBarLabelMode.name(): String = stringResource(
    when (this) {
        NavigationBarLabelMode.ICON_AND_TEXT -> R.string.navigation_bar_label_icon_and_text
        NavigationBarLabelMode.ICON_ONLY -> R.string.navigation_bar_label_icon_only
        NavigationBarLabelMode.ICON_WITH_SELECTED_LABEL -> R.string.navigation_bar_label_selected
    },
)
