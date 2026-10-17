package one.only.player.settings.screens.subtitle

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.nio.charset.Charset
import one.only.player.core.model.Font
import one.only.player.core.model.OnlineSubtitleLanguageFilter
import one.only.player.core.model.OnlineSubtitleProvider
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.PreferenceSwitchWithDivider
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.components.SubtitleStylePanel
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.label
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import one.only.player.settings.utils.LocalesHelper
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SubtitlePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SubtitlePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SubtitlePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun SubtitlePreferencesContent(
    uiState: SubtitlePreferencesUiState,
    onEvent: (SubtitlePreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }
    val charsetResource = stringArrayResource(id = R.array.charsets_list)
    val context = LocalContext.current
    val fontImportSucceededMessage = stringResource(R.string.external_subtitle_font_import_success)
    val fontImportFailedMessage = stringResource(R.string.external_subtitle_font_import_failed)
    val fontClearSucceededMessage = stringResource(R.string.external_subtitle_font_clear_success)
    val importFontLauncher = rememberLauncherForActivityResult(
        contract = OpenMultipleDocuments(),
    ) { uris ->
        onEvent(SubtitlePreferencesUiEvent.OnExternalSubtitleFontsSelected(uris))
    }

    LaunchedEffect(uiState.pendingAction) {
        when (uiState.pendingAction) {
            SubtitlePreferencesPendingAction.OpenExternalSubtitleFontPicker -> {
                importFontLauncher.launch(arrayOf("*/*"))
            }
            null -> Unit
        }
    }

    LaunchedEffect(uiState.resultMessage) {
        val message = when (uiState.resultMessage) {
            SubtitlePreferencesResultMessage.ImportSucceeded -> fontImportSucceededMessage
            SubtitlePreferencesResultMessage.ImportFailed -> fontImportFailedMessage
            SubtitlePreferencesResultMessage.ClearSucceeded -> fontClearSucceededMessage
            null -> null
        } ?: return@LaunchedEffect

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(SubtitlePreferencesUiEvent.ClearResultMessage)
    }

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.subtitle),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_subtitle_back"),
                    ) {
                        MiuixIcon(
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
            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_auto_load"),
                    title = stringResource(id = R.string.subtitle_auto_load),
                    description = stringResource(id = R.string.subtitle_auto_load_desc),
                    icon = AppIcons.Magic,
                    isChecked = uiState.preferences.isSubtitleAutoLoadEnabled,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleSubtitleAutoLoad) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_language"),
                    title = stringResource(id = R.string.preferred_subtitle_lang),
                    description = LocalesHelper.getLocaleDisplayLanguage(uiState.preferences.preferredSubtitleLanguage)
                        .takeIf { it.isNotBlank() } ?: stringResource(R.string.preferred_subtitle_lang_description),
                    icon = AppIcons.Language,
                    isEnabled = uiState.preferences.isSubtitleAutoLoadEnabled,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleLanguageDialog)) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_remember_track"),
                    title = stringResource(id = R.string.remember_subtitle_track),
                    icon = AppIcons.Bookmark,
                    isChecked = uiState.preferences.shouldRememberSubtitleTrack,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleRememberSubtitleTrack) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_encoding"),
                    title = stringResource(R.string.subtitle_text_encoding),
                    description = charsetResource.first { it.contains(uiState.preferences.subtitleTextEncoding) },
                    icon = AppIcons.Code,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleEncodingDialog)) },
                )
            }

            ListSectionTitle(text = stringResource(R.string.online_subtitle_search_settings))
            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_online_subtitle_language"),
                    title = stringResource(R.string.online_subtitle_default_language),
                    description = uiState.preferences.onlineSubtitleSearchPreferences.languageFilter.label(),
                    icon = AppIcons.Translate,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.OnlineSubtitleLanguageDialog)) },
                )
                OnlineSubtitleProvider.entries.forEach { provider ->
                    PreferenceSwitch(
                        modifier = Modifier.testTag("switch_settings_online_subtitle_source_${provider.name.lowercase()}"),
                        title = provider.label(),
                        icon = AppIcons.Server,
                        isChecked = provider in uiState.preferences.onlineSubtitleSearchPreferences.providers,
                        onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleOnlineSubtitleProvider(provider)) },
                    )
                }
            }

            PreferenceGroup {
                PreferenceSwitchWithDivider(
                    modifier = Modifier.testTag("item_settings_subtitle_system_caption_style"),
                    switchModifier = Modifier.testTag("switch_settings_subtitle_system_caption_style"),
                    title = stringResource(R.string.system_caption_style),
                    description = stringResource(R.string.system_caption_style_desc),
                    icon = AppIcons.Caption,
                    isChecked = uiState.preferences.shouldUseSystemCaptionStyle,
                    onChecked = { onEvent(SubtitlePreferencesUiEvent.ToggleUseSystemCaptionStyle) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_embedded_styles"),
                    title = stringResource(R.string.embedded_styles),
                    description = stringResource(R.string.embedded_styles_desc),
                    icon = AppIcons.Brush,
                    isChecked = uiState.preferences.shouldApplyEmbeddedStyles,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleApplyEmbeddedStyles) },
                )
                if (uiState.preferences.shouldUseSystemCaptionStyle) {
                    ClickablePreferenceItem(
                        title = stringResource(id = R.string.external_subtitle_font_notice_title),
                        description = stringResource(id = R.string.external_subtitle_font_system_style_notice),
                        icon = AppIcons.Info,
                        isEnabled = false,
                    )
                }
            }

            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_font"),
                    title = stringResource(id = R.string.subtitle_font),
                    description = uiState.preferences.subtitleFont.name(),
                    icon = AppIcons.Font,
                    isEnabled = uiState.preferences.shouldUseSystemCaptionStyle.not(),
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleFontDialog)) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_import_external_font"),
                    title = stringResource(id = R.string.external_subtitle_font_import),
                    // 已导入时显示字体名，未导入时说明支持的格式
                    description = uiState.externalFontName.ifBlank {
                        stringResource(id = R.string.external_subtitle_font_import_desc)
                    },
                    icon = AppIcons.FileOpen,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ImportExternalSubtitleFont) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_clear_external_font"),
                    title = stringResource(id = R.string.external_subtitle_font_clear),
                    description = stringResource(id = R.string.external_subtitle_font_clear_desc),
                    icon = AppIcons.DeleteSweep,
                    isEnabled = uiState.isExternalFontAvailable,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ClearExternalSubtitleFont) },
                )
            }

            SubtitleStylePanel(
                preferences = uiState.preferences,
                onPreferencesChange = { onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleStyle(it)) },
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                SubtitlePreferenceDialog.OnlineSubtitleLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(R.string.online_subtitle_default_language),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(OnlineSubtitleLanguageFilter.entries, key = { it.name }) { language ->
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_online_subtitle_language_${language.name.lowercase()}"),
                                text = language.label(),
                                isSelected = language == uiState.preferences.onlineSubtitleSearchPreferences.languageFilter,
                                onClick = {
                                    onEvent(SubtitlePreferencesUiEvent.UpdateOnlineSubtitleLanguage(language))
                                    onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }
                SubtitlePreferenceDialog.SubtitleLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_subtitle_lang),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(languages, key = { it.second }) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_subtitle_language_${it.second.ifBlank { "none" }}"),
                                text = it.first,
                                isSelected = it.second == uiState.preferences.preferredSubtitleLanguage,
                                onClick = {
                                    onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleLanguage(it.second))
                                    onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleFontDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_font),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(Font.entries.toTypedArray(), key = { it.name }) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_subtitle_font_${it.name.lowercase()}"),
                                text = it.name(),
                                isSelected = it == uiState.preferences.subtitleFont,
                                onClick = {
                                    onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleFont(it))
                                    onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                SubtitlePreferenceDialog.SubtitleEncodingDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.subtitle_text_encoding),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(charsetResource, key = { it }) {
                            val currentCharset = it.substringAfterLast("(", "").removeSuffix(")")
                            if (currentCharset.isEmpty() || Charset.isSupported(currentCharset)) {
                                RadioTextButton(
                                    modifier = Modifier.testTag("option_settings_subtitle_encoding_$currentCharset"),
                                    text = it,
                                    isSelected = currentCharset == uiState.preferences.subtitleTextEncoding,
                                    onClick = {
                                        onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleEncoding(currentCharset))
                                        onEvent(SubtitlePreferencesUiEvent.ShowDialog(null))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SubtitlePreferencesScreenPreview() {
    OnlyPlayerTheme {
        SubtitlePreferencesContent(
            uiState = SubtitlePreferencesUiState(),
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
