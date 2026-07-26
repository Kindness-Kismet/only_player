package one.only.player.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import one.only.player.core.model.ThemeColorSpec as ModelColorSpec
import one.only.player.core.model.ThemePaletteStyle as ModelPaletteStyle
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

// 应用主题入口，miuix 为主，Material3 用同一 seed 兜底，保证迁移期配色一致
@Composable
fun OnlyPlayerTheme(
    shouldUseDarkTheme: Boolean = isSystemInDarkTheme(),
    shouldUseDynamicColor: Boolean = true,
    seedColor: Long = DEFAULT_SEED_COLOR,
    paletteStyle: ModelPaletteStyle = ModelPaletteStyle.TONAL_SPOT,
    colorSpec: ModelColorSpec = ModelColorSpec.SPEC_2025,
    content: @Composable () -> Unit,
) {
    val miuixController = remember(
        shouldUseDarkTheme,
        shouldUseDynamicColor,
        seedColor,
        paletteStyle,
        colorSpec,
    ) {
        ThemeController(
            colorSchemeMode = if (shouldUseDarkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = if (shouldUseDynamicColor) null else Color(seedColor),
            colorSpec = colorSpec.toMiuix(),
            paletteStyle = paletteStyle.toMiuix(),
            isDark = shouldUseDarkTheme,
        )
    }

    // Material3 兜底色板，迁移期未改造页面仍能取到协调配色
    val context = LocalContext.current
    val materialScheme = when {
        // 动态取色且系统支持时，与 miuix platformDynamicColors 对齐取系统色
        shouldUseDynamicColor && supportsDynamicTheming() ->
            if (shouldUseDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> rememberDynamicColorScheme(
            seedColor = Color(seedColor),
            isDark = shouldUseDarkTheme,
            isAmoled = false,
            style = paletteStyle.toMaterialKolor(),
            specVersion = colorSpec.toMaterialKolorSpec(),
        )
    }

    MiuixTheme(controller = miuixController) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun ModelPaletteStyle.toMiuix(): ThemePaletteStyle = when (this) {
    ModelPaletteStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    ModelPaletteStyle.NEUTRAL -> ThemePaletteStyle.Neutral
    ModelPaletteStyle.VIBRANT -> ThemePaletteStyle.Vibrant
    ModelPaletteStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
    ModelPaletteStyle.RAINBOW -> ThemePaletteStyle.Rainbow
    ModelPaletteStyle.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
    ModelPaletteStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    ModelPaletteStyle.FIDELITY -> ThemePaletteStyle.Fidelity
    ModelPaletteStyle.CONTENT -> ThemePaletteStyle.Content
}

private fun ModelColorSpec.toMiuix(): ThemeColorSpec = when (this) {
    ModelColorSpec.SPEC_2021 -> ThemeColorSpec.Spec2021
    ModelColorSpec.SPEC_2025 -> ThemeColorSpec.Spec2025
}

private fun ModelPaletteStyle.toMaterialKolor(): PaletteStyle = when (this) {
    ModelPaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    ModelPaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    ModelPaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    ModelPaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    ModelPaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    ModelPaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
    ModelPaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
    ModelPaletteStyle.FIDELITY -> PaletteStyle.Fidelity
    ModelPaletteStyle.CONTENT -> PaletteStyle.Content
}

private fun ModelColorSpec.toMaterialKolorSpec(): ColorSpec.SpecVersion = when (this) {
    ModelColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    ModelColorSpec.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
}

const val DEFAULT_SEED_COLOR: Long = 0xFF6750A4
