package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ThemeColorSpec
import one.only.player.core.ui.R

@Composable
fun ThemeColorSpec.name(): String {
    val stringRes = when (this) {
        ThemeColorSpec.SPEC_2021 -> R.string.color_spec_2021
        ThemeColorSpec.SPEC_2025 -> R.string.color_spec_2025
    }

    return stringResource(id = stringRes)
}
