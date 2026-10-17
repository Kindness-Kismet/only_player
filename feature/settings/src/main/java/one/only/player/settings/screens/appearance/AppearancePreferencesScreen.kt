package one.only.player.settings.screens.appearance

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.PredictiveBackSupport
import one.only.player.core.model.ThemeConfig
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.supportsDynamicTheming
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import one.only.player.settings.utils.LocalesHelper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppearancePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AppearancePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val onPredictiveBackToggle: (Boolean) -> Unit = onPredictiveBackToggle@{ isEnabled ->
        if (!PredictiveBackSupport.setEnabled(context.applicationInfo, isEnabled)) {
            return@onPredictiveBackToggle
        }
        viewModel.onEvent(
            AppearancePreferencesEvent.ToggleEnablePredictiveBack(
                isEnabled = isEnabled,
                onApplied = {
                    activity?.recreate()
                },
            ),
        )
    }

    AppearancePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onPredictiveBackToggle = onPredictiveBackToggle,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun AppearancePreferencesContent(
    uiState: AppearancePreferencesUiState,
    onEvent: (AppearancePreferencesEvent) -> Unit,
    onPredictiveBackToggle: (Boolean) -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val preferences = uiState.preferences
    val appLanguages = remember { LocalesHelper.appSupportedLocales }
    val shouldShowPredictiveBack = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val shouldShowNavigationBarBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val shouldShowTopBarBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // 语言下拉：首项为系统默认，其后为受支持语言
    val languageTags = remember(appLanguages) { listOf("") + appLanguages.map { it.second } }
    val languageLabelSystem = stringResource(id = R.string.system_default)
    val languageLabels = remember(appLanguages, languageLabelSystem) {
        listOf(languageLabelSystem) + appLanguages.map { it.first }
    }
    val languageIndex = languageTags.indexOf(preferences.appLanguage).coerceAtLeast(0)

    val themeConfigs = remember { ThemeConfig.entries }

    AppScaffold(
        topBar = {
            AppTopAppBar(
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
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    modifier = Modifier.testTag("item_settings_appearance_language"),
                    title = stringResource(id = R.string.app_language),
                    summary = languageLabels[languageIndex],
                    startAction = { PrefIcon(AppIcons.Language) },
                    onClick = {
                        onEvent(AppearancePreferencesEvent.ShowDialog(AppearancePreferenceDialog.AppLanguage))
                    },
                )
                ArrowPreference(
                    modifier = Modifier.testTag("item_settings_appearance_theme"),
                    title = stringResource(id = R.string.theme_mode),
                    summary = preferences.themeConfig.name(),
                    startAction = { PrefIcon(AppIcons.DarkMode) },
                    onClick = {
                        onEvent(AppearancePreferencesEvent.ShowDialog(AppearancePreferenceDialog.Theme))
                    },
                )
                if (supportsDynamicTheming()) {
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_dynamic_colors"),
                        title = stringResource(id = R.string.dynamic_theme),
                        startAction = { PrefIcon(AppIcons.Appearance) },
                        checked = preferences.shouldUseDynamicColors,
                        onCheckedChange = { onEvent(AppearancePreferencesEvent.ToggleUseDynamicColors) },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    modifier = Modifier.testTag("switch_settings_appearance_show_cloud_tab"),
                    title = stringResource(id = R.string.show_cloud_tab),
                    summary = stringResource(id = R.string.show_cloud_tab_description),
                    startAction = { PrefIcon(AppIcons.Cloud) },
                    checked = preferences.shouldShowCloudTab,
                    onCheckedChange = { onEvent(AppearancePreferencesEvent.ToggleShowCloudTab) },
                )
                SwitchPreference(
                    modifier = Modifier.testTag("switch_settings_appearance_title_long_press_home"),
                    title = stringResource(id = R.string.home_title_long_press_to_root),
                    summary = stringResource(id = R.string.home_title_long_press_to_root_description),
                    startAction = { PrefIcon(AppIcons.Title) },
                    checked = preferences.shouldNavigateHomeOnTitleLongPress,
                    onCheckedChange = {
                        onEvent(AppearancePreferencesEvent.ToggleNavigateHomeOnTitleLongPress)
                    },
                )
                SwitchPreference(
                    modifier = Modifier.testTag("switch_settings_appearance_floating_navigation_bar"),
                    title = stringResource(id = R.string.floating_navigation_bar),
                    summary = stringResource(id = R.string.floating_navigation_bar_description),
                    startAction = { PrefIcon(AppIcons.SmartButton) },
                    checked = preferences.shouldUseFloatingNavigationBar,
                    onCheckedChange = {
                        onEvent(AppearancePreferencesEvent.ToggleUseFloatingNavigationBar)
                    },
                )
                if (shouldShowTopBarBlur) {
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_top_bar_blur"),
                        title = stringResource(id = R.string.top_bar_blur),
                        summary = stringResource(id = R.string.top_bar_blur_description),
                        startAction = { PrefIcon(AppIcons.BlurOn) },
                        checked = preferences.shouldBlurTopBar,
                        onCheckedChange = {
                            onEvent(AppearancePreferencesEvent.ToggleBlurTopBar)
                        },
                    )
                }
                if (shouldShowNavigationBarBlur) {
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_floating_navigation_bar_blur"),
                        title = stringResource(id = R.string.floating_navigation_bar_blur),
                        summary = stringResource(id = R.string.floating_navigation_bar_blur_description),
                        startAction = { PrefIcon(AppIcons.BlurOn) },
                        checked = preferences.shouldBlurFloatingNavigationBar,
                        onCheckedChange = {
                            onEvent(AppearancePreferencesEvent.ToggleBlurFloatingNavigationBar)
                        },
                    )
                }
                if (shouldShowPredictiveBack) {
                    SwitchPreference(
                        modifier = Modifier.testTag("switch_settings_appearance_predictive_back"),
                        title = stringResource(id = R.string.predictive_back_gesture),
                        summary = stringResource(id = R.string.predictive_back_gesture_description),
                        startAction = { PrefIcon(AppIcons.SwipeHorizontal) },
                        checked = preferences.shouldEnablePredictiveBack,
                        onCheckedChange = onPredictiveBackToggle,
                    )
                }
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AppearancePreferenceDialog.AppLanguage -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.app_language),
                        onDismissClick = { onEvent(AppearancePreferencesEvent.ShowDialog(null)) },
                    ) {
                        itemsIndexed(languageLabels, key = { index, _ -> languageTags[index] }) { index, label ->
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_appearance_language_$index"),
                                text = label,
                                isSelected = index == languageIndex,
                                onClick = {
                                    onEvent(AppearancePreferencesEvent.UpdateAppLanguage(languageTags[index]))
                                    onEvent(AppearancePreferencesEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                AppearancePreferenceDialog.Theme -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.theme_mode),
                        onDismissClick = { onEvent(AppearancePreferencesEvent.ShowDialog(null)) },
                    ) {
                        items(themeConfigs, key = { it.name }) { themeConfig ->
                            RadioTextButton(
                                modifier = Modifier.testTag(
                                    "option_settings_appearance_theme_${themeConfig.name.lowercase()}",
                                ),
                                text = themeConfig.name(),
                                isSelected = themeConfig == preferences.themeConfig,
                                onClick = {
                                    onEvent(AppearancePreferencesEvent.UpdateThemeConfig(themeConfig))
                                    onEvent(AppearancePreferencesEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(end = 12.dp).size(24.dp),
    )
}
