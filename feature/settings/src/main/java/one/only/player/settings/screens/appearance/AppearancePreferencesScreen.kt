package one.only.player.settings.screens.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.ThemeColorSpec
import one.only.player.core.model.ThemeConfig
import one.only.player.core.model.ThemePaletteStyle
import one.only.player.core.ui.R
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.theme.supportsDynamicTheming
import one.only.player.settings.extensions.name
import one.only.player.settings.utils.LocalesHelper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearancePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AppearancePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppearancePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun AppearancePreferencesContent(
    uiState: AppearancePreferencesUiState,
    onEvent: (AppearancePreferencesEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val preferences = uiState.preferences
    val appLanguages = remember { LocalesHelper.appSupportedLocales }

    // 语言下拉：首项为系统默认，其后为受支持语言
    val languageTags = remember(appLanguages) { listOf("") + appLanguages.map { it.second } }
    val languageLabelSystem = stringResource(id = R.string.system_default)
    val languageLabels = remember(appLanguages, languageLabelSystem) {
        listOf(languageLabelSystem) + appLanguages.map { it.first }
    }
    val languageIndex = languageTags.indexOf(preferences.appLanguage).coerceAtLeast(0)

    val themeConfigs = remember { ThemeConfig.entries }
    val paletteStyles = remember { ThemePaletteStyle.entries }
    val colorSpecs = remember { ThemeColorSpec.entries }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.appearance_name),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_appearance_back"),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        modifier = Modifier.testTag("item_settings_appearance_language"),
                        title = stringResource(id = R.string.app_language),
                        startAction = { PrefIcon(NextIcons.Language) },
                        items = languageLabels,
                        selectedIndex = languageIndex,
                        onSelectedIndexChange = { index ->
                            onEvent(AppearancePreferencesEvent.UpdateAppLanguage(languageTags[index]))
                        },
                    )
                    OverlayDropdownPreference(
                        modifier = Modifier.testTag("item_settings_appearance_theme"),
                        title = stringResource(id = R.string.theme_mode),
                        startAction = { PrefIcon(NextIcons.DarkMode) },
                        items = themeConfigs.map { it.name() },
                        selectedIndex = themeConfigs.indexOf(preferences.themeConfig).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            onEvent(AppearancePreferencesEvent.UpdateThemeConfig(themeConfigs[index]))
                        },
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(id = R.string.theme_color))
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (supportsDynamicTheming()) {
                        SwitchPreference(
                            modifier = Modifier.testTag("switch_settings_appearance_dynamic_colors"),
                            title = stringResource(id = R.string.dynamic_theme),
                            summary = stringResource(id = R.string.dynamic_theme_description),
                            startAction = { PrefIcon(NextIcons.Appearance) },
                            checked = preferences.shouldUseDynamicColors,
                            onCheckedChange = { onEvent(AppearancePreferencesEvent.ToggleUseDynamicColors) },
                        )
                    }
                    if (!preferences.shouldUseDynamicColors) {
                        val seedIndex = SeedColorPalette
                            .indexOfFirst { it.value == preferences.themeSeedColor }
                            .coerceAtLeast(0)
                        OverlayDropdownPreference(
                            modifier = Modifier.testTag("item_settings_appearance_theme_color"),
                            title = stringResource(id = R.string.theme_color),
                            summary = stringResource(id = R.string.theme_color_description),
                            startAction = { PrefColorDot(Color(preferences.themeSeedColor)) },
                            items = SeedColorPalette.map { stringResource(id = it.labelRes) },
                            selectedIndex = seedIndex,
                            onSelectedIndexChange = { index ->
                                onEvent(AppearancePreferencesEvent.UpdateThemeSeedColor(SeedColorPalette[index].value))
                            },
                        )
                    }
                    OverlayDropdownPreference(
                        modifier = Modifier.testTag("dropdown_settings_appearance_palette_style"),
                        title = stringResource(id = R.string.theme_palette_style),
                        summary = stringResource(id = R.string.theme_palette_style_description),
                        startAction = { PrefIcon(NextIcons.Style) },
                        items = paletteStyles.map { it.name() },
                        selectedIndex = paletteStyles.indexOf(preferences.themePaletteStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            onEvent(AppearancePreferencesEvent.UpdatePaletteStyle(paletteStyles[index]))
                        },
                    )
                    OverlayDropdownPreference(
                        modifier = Modifier.testTag("dropdown_settings_appearance_color_spec"),
                        title = stringResource(id = R.string.theme_color_spec),
                        summary = stringResource(id = R.string.theme_color_spec_description),
                        startAction = { PrefIcon(NextIcons.Contrast) },
                        items = colorSpecs.map { it.name() },
                        selectedIndex = colorSpecs.indexOf(preferences.themeColorSpec).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            onEvent(AppearancePreferencesEvent.UpdateColorSpec(colorSpecs[index]))
                        },
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(id = R.string.interface_name))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_title_long_press_home"),
                        title = stringResource(id = R.string.home_title_long_press_to_root),
                        summary = stringResource(id = R.string.home_title_long_press_to_root_description),
                        startAction = { PrefIcon(NextIcons.Title) },
                        checked = preferences.shouldNavigateHomeOnTitleLongPress,
                        onCheckedChange = {
                            onEvent(AppearancePreferencesEvent.ToggleNavigateHomeOnTitleLongPress)
                        },
                    )
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_floating_navigation_bar"),
                        title = stringResource(id = R.string.floating_navigation_bar),
                        summary = stringResource(id = R.string.floating_navigation_bar_description),
                        startAction = { PrefIcon(NextIcons.SmartButton) },
                        checked = preferences.shouldUseFloatingNavigationBar,
                        onCheckedChange = {
                            onEvent(AppearancePreferencesEvent.ToggleUseFloatingNavigationBar)
                        },
                    )
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_floating_navigation_bar_blur"),
                        title = stringResource(id = R.string.floating_navigation_bar_blur),
                        summary = stringResource(id = R.string.floating_navigation_bar_blur_description),
                        startAction = { PrefIcon(NextIcons.BlurOn) },
                        checked = preferences.shouldBlurFloatingNavigationBar,
                        enabled = preferences.shouldUseFloatingNavigationBar,
                        onCheckedChange = {
                            onEvent(AppearancePreferencesEvent.ToggleBlurFloatingNavigationBar)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(end = 12.dp).size(24.dp),
    )
}

@Composable
private fun PrefColorDot(color: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(24.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}

// 预设主题色，未开启动态取色时供选择
private data class SeedColor(val labelRes: Int, val value: Long)

private val SeedColorPalette = listOf(
    SeedColor(R.string.seed_color_purple, 0xFF6750A4),
    SeedColor(R.string.seed_color_blue, 0xFF0061A4),
    SeedColor(R.string.seed_color_teal, 0xFF006A67),
    SeedColor(R.string.seed_color_green, 0xFF3A6A1E),
    SeedColor(R.string.seed_color_orange, 0xFF9A4600),
    SeedColor(R.string.seed_color_red, 0xFFA4302A),
    SeedColor(R.string.seed_color_pink, 0xFF9A4058),
)
