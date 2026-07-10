package one.only.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ThemePaletteStyle
import one.only.player.core.ui.R

@Composable
fun ThemePaletteStyle.name(): String {
    val stringRes = when (this) {
        ThemePaletteStyle.TONAL_SPOT -> R.string.palette_style_tonal_spot
        ThemePaletteStyle.NEUTRAL -> R.string.palette_style_neutral
        ThemePaletteStyle.VIBRANT -> R.string.palette_style_vibrant
        ThemePaletteStyle.EXPRESSIVE -> R.string.palette_style_expressive
        ThemePaletteStyle.RAINBOW -> R.string.palette_style_rainbow
        ThemePaletteStyle.FRUIT_SALAD -> R.string.palette_style_fruit_salad
        ThemePaletteStyle.MONOCHROME -> R.string.palette_style_monochrome
        ThemePaletteStyle.FIDELITY -> R.string.palette_style_fidelity
        ThemePaletteStyle.CONTENT -> R.string.palette_style_content
    }

    return stringResource(id = stringRes)
}
