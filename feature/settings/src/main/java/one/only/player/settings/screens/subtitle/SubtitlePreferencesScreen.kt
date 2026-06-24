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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.PreferenceSwitchWithDivider
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SubtitleStylePanel
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import one.only.player.settings.utils.LocalesHelper

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubtitlePreferencesContent(
    uiState: SubtitlePreferencesUiState,
    onEvent: (SubtitlePreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }
    val charsetResource = stringArrayResource(id = R.array.charsets_list)
    val context = LocalContext.current
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
            SubtitlePreferencesResultMessage.ImportSucceeded -> context.getString(R.string.external_subtitle_font_import_success)
            SubtitlePreferencesResultMessage.ImportFailed -> context.getString(R.string.external_subtitle_font_import_failed)
            SubtitlePreferencesResultMessage.ClearSucceeded -> context.getString(R.string.external_subtitle_font_clear_success)
            null -> null
        } ?: return@LaunchedEffect

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(SubtitlePreferencesUiEvent.ClearResultMessage)
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.subtitle),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.subtitle_loading))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_auto_load"),
                    title = stringResource(id = R.string.subtitle_auto_load),
                    description = stringResource(id = R.string.subtitle_auto_load_desc),
                    icon = NextIcons.Subtitle,
                    isChecked = uiState.preferences.isSubtitleAutoLoadEnabled,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleSubtitleAutoLoad) },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_language"),
                    title = stringResource(id = R.string.preferred_subtitle_lang),
                    description = LocalesHelper.getLocaleDisplayLanguage(uiState.preferences.preferredSubtitleLanguage)
                        .takeIf { it.isNotBlank() } ?: stringResource(R.string.preferred_subtitle_lang_description),
                    icon = NextIcons.Language,
                    isEnabled = uiState.preferences.isSubtitleAutoLoadEnabled,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleLanguageDialog)) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_remember_track"),
                    title = stringResource(id = R.string.remember_subtitle_track),
                    icon = NextIcons.Subtitle,
                    isChecked = uiState.preferences.shouldRememberSubtitleTrack,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleRememberSubtitleTrack) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_encoding"),
                    title = stringResource(R.string.subtitle_text_encoding),
                    description = charsetResource.first { it.contains(uiState.preferences.subtitleTextEncoding) },
                    icon = NextIcons.Subtitle,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleEncodingDialog)) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.subtitle_style_source))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitchWithDivider(
                    modifier = Modifier.testTag("item_settings_subtitle_system_caption_style"),
                    switchModifier = Modifier.testTag("switch_settings_subtitle_system_caption_style"),
                    title = stringResource(R.string.system_caption_style),
                    description = stringResource(R.string.system_caption_style_desc),
                    icon = NextIcons.Caption,
                    isChecked = uiState.preferences.shouldUseSystemCaptionStyle,
                    onChecked = { onEvent(SubtitlePreferencesUiEvent.ToggleUseSystemCaptionStyle) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_subtitle_embedded_styles"),
                    title = stringResource(R.string.embedded_styles),
                    description = stringResource(R.string.embedded_styles_desc),
                    icon = NextIcons.Style,
                    isChecked = uiState.preferences.shouldApplyEmbeddedStyles,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ToggleApplyEmbeddedStyles) },
                    isLastItem = uiState.preferences.shouldUseSystemCaptionStyle.not(),
                )
                if (uiState.preferences.shouldUseSystemCaptionStyle) {
                    ClickablePreferenceItem(
                        title = stringResource(id = R.string.external_subtitle_font_notice_title),
                        description = stringResource(id = R.string.external_subtitle_font_system_style_notice),
                        icon = NextIcons.Info,
                        isEnabled = false,
                        isLastItem = true,
                    )
                }
            }

            ListSectionTitle(text = stringResource(id = R.string.subtitle_font_settings))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_font"),
                    title = stringResource(id = R.string.subtitle_font),
                    description = uiState.preferences.subtitleFont.name(),
                    icon = NextIcons.Font,
                    isEnabled = uiState.preferences.shouldUseSystemCaptionStyle.not(),
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(SubtitlePreferenceDialog.SubtitleFontDialog)) },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_import_external_font"),
                    title = stringResource(id = R.string.external_subtitle_font_import),
                    description = stringResource(id = R.string.external_subtitle_font_import_desc),
                    icon = NextIcons.FileOpen,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ImportExternalSubtitleFont) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_current_external_font"),
                    title = stringResource(id = R.string.external_subtitle_font_current),
                    description = uiState.externalFontName.ifBlank { stringResource(id = R.string.external_subtitle_font_not_imported) },
                    icon = NextIcons.Font,
                    isEnabled = false,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_subtitle_clear_external_font"),
                    title = stringResource(id = R.string.external_subtitle_font_clear),
                    description = stringResource(id = R.string.external_subtitle_font_clear_desc),
                    icon = NextIcons.DeleteSweep,
                    isEnabled = uiState.isExternalFontAvailable,
                    onClick = { onEvent(SubtitlePreferencesUiEvent.ClearExternalSubtitleFont) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.subtitle_appearance))
            SubtitleStylePanel(
                preferences = uiState.preferences,
                onPreferencesChange = { onEvent(SubtitlePreferencesUiEvent.UpdateSubtitleStyle(it)) },
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                SubtitlePreferenceDialog.SubtitleLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_subtitle_lang),
                        onDismissClick = { onEvent(SubtitlePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(languages) {
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
                        items(Font.entries.toTypedArray()) {
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
                        items(charsetResource) {
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
