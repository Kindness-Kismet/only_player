package one.only.player.settings.screens.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.AudioEqualizerControls
import one.only.player.core.ui.components.AudioEqualizerPresetPickerDialog
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.ResetIconButton
import one.only.player.core.ui.components.SavePresetNameDialog
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.utils.LocalesHelper
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AudioPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: AudioPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AudioPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun AudioPreferencesContent(
    uiState: AudioPreferencesUiState,
    onEvent: (AudioPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val languages = remember { listOf(Pair("None", "")) + LocalesHelper.getAvailableLocales() }
    val initialVolumeLimitRange = PlayerPreferences.MIN_INITIAL_PLAYER_VOLUME_PERCENTAGE.toFloat().rangeTo(
        PlayerPreferences.MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE.toFloat(),
    )

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.audio),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_audio_back"),
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
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_audio_language"),
                    title = stringResource(id = R.string.preferred_audio_lang),
                    description = LocalesHelper.getLocaleDisplayLanguage(uiState.preferences.preferredAudioLanguage)
                        .takeIf { it.isNotBlank() } ?: stringResource(R.string.preferred_audio_lang_description),
                    icon = AppIcons.Language,
                    onClick = { onEvent(AudioPreferencesUiEvent.ShowDialog(AudioPreferenceDialog.AudioLanguageDialog)) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_remember_track"),
                    title = stringResource(id = R.string.remember_audio_track),
                    icon = AppIcons.Audio,
                    isChecked = uiState.preferences.shouldRememberAudioTrack,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleRememberAudioTrack) },
                )
            }

            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_require_focus"),
                    title = stringResource(R.string.require_audio_focus),
                    description = stringResource(R.string.require_audio_focus_desc),
                    icon = AppIcons.Focus,
                    isChecked = uiState.preferences.shouldRequireAudioFocus,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleRequireAudioFocus) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_pause_on_headset_disconnect"),
                    title = stringResource(id = R.string.pause_on_headset_disconnect),
                    description = stringResource(id = R.string.pause_on_headset_disconnect_desc),
                    icon = AppIcons.HeadsetOff,
                    isChecked = uiState.preferences.shouldPauseOnHeadsetDisconnect,
                    onClick = { onEvent(AudioPreferencesUiEvent.TogglePauseOnHeadsetDisconnect) },
                )
            }

            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_system_volume_panel"),
                    title = stringResource(id = R.string.system_volume_panel),
                    description = stringResource(id = R.string.system_volume_panel_desc),
                    icon = AppIcons.Headset,
                    isChecked = uiState.preferences.shouldShowSystemVolumePanel,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleShowSystemVolumePanel) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_remember_volume"),
                    title = stringResource(id = R.string.remember_volume_level),
                    description = stringResource(id = R.string.remember_volume_level_description),
                    icon = AppIcons.VolumeUp,
                    isChecked = uiState.preferences.shouldRememberPlayerVolume,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleRememberPlayerVolume) },
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_audio_initial_volume_limit"),
                    title = stringResource(id = R.string.initial_volume_limit),
                    description = stringResource(id = R.string.percent, uiState.preferences.maxInitialPlayerVolumePercentage),
                    icon = AppIcons.VolumeUp,
                    isEnabled = uiState.preferences.shouldRememberPlayerVolume,
                    value = uiState.preferences.maxInitialPlayerVolumePercentage.toFloat(),
                    valueRange = initialVolumeLimitRange,
                    onValueChange = { onEvent(AudioPreferencesUiEvent.UpdateMaxInitialPlayerVolume(it.toInt())) },
                    trailingContent = {
                        ResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_audio_initial_volume_limit"),
                            enabled = uiState.preferences.shouldRememberPlayerVolume,
                            contentDescription = stringResource(id = R.string.reset_initial_volume_limit),
                            onClick = {
                                onEvent(
                                    AudioPreferencesUiEvent.UpdateMaxInitialPlayerVolume(
                                        PlayerPreferences.DEFAULT_MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE,
                                    ),
                                )
                            },
                        )
                    },
                )
            }

            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_spatial"),
                    title = stringResource(id = R.string.spatial_audio),
                    icon = AppIcons.SurroundSound,
                    isChecked = uiState.preferences.isSpatialAudioEnabled,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleSpatialAudio) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_normalization"),
                    title = stringResource(id = R.string.volume_normalization),
                    description = stringResource(id = R.string.volume_normalization_desc),
                    icon = AppIcons.VolumeUp,
                    isChecked = uiState.preferences.isVolumeNormalizationEnabled,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleVolumeNormalization) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_audio_boost"),
                    title = stringResource(id = R.string.volume_boost),
                    description = stringResource(id = R.string.volume_boost_desc),
                    icon = AppIcons.VolumeUp,
                    isChecked = uiState.preferences.isVolumeBoostEnabled,
                    onClick = { onEvent(AudioPreferencesUiEvent.ToggleVolumeBoost) },
                )
            }

            AudioEqualizerSettings(
                preferences = uiState.preferences,
                onEvent = onEvent,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                AudioPreferenceDialog.AudioLanguageDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.preferred_audio_lang),
                        onDismissClick = { onEvent(AudioPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(languages, key = { it.second }) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_audio_language_${it.second.ifBlank { "none" }}"),
                                text = it.first,
                                isSelected = it.second == uiState.preferences.preferredAudioLanguage,
                                onClick = {
                                    onEvent(AudioPreferencesUiEvent.UpdateAudioLanguage(it.second))
                                    onEvent(AudioPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                AudioPreferenceDialog.AudioEqualizerPresets -> {
                    AudioEqualizerPresetPickerDialog(
                        preferences = uiState.preferences,
                        onApplyBuiltInPreset = {
                            onEvent(AudioPreferencesUiEvent.ApplyAudioEqualizerBuiltInPreset(it))
                            onEvent(AudioPreferencesUiEvent.ShowDialog(null))
                        },
                        onApplyPreset = {
                            onEvent(AudioPreferencesUiEvent.ApplyAudioEqualizerPreset(it))
                            onEvent(AudioPreferencesUiEvent.ShowDialog(null))
                        },
                        onDeletePreset = { onEvent(AudioPreferencesUiEvent.DeleteAudioEqualizerPreset(it)) },
                        onDismissRequest = { onEvent(AudioPreferencesUiEvent.ShowDialog(null)) },
                    )
                }

                AudioPreferenceDialog.SaveAudioEqualizerPreset -> {
                    SavePresetNameDialog(
                        title = stringResource(R.string.save_current_as_audio_equalizer_preset),
                        presetNameLabel = stringResource(R.string.audio_equalizer_preset_name),
                        dialogTestTag = "dialog_save_audio_equalizer_preset",
                        inputTestTag = "input_equalizer_preset_name",
                        confirmTestTag = "btn_save_equalizer_preset",
                        onDismissRequest = { onEvent(AudioPreferencesUiEvent.ShowDialog(null)) },
                        onSavePreset = {
                            onEvent(AudioPreferencesUiEvent.SaveAudioEqualizerPreset(it))
                            onEvent(AudioPreferencesUiEvent.ShowDialog(null))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioEqualizerSettings(
    preferences: PlayerPreferences,
    onEvent: (AudioPreferencesUiEvent) -> Unit,
) {
    AudioEqualizerControls(
        preferences = preferences,
        onEnabledChange = { onEvent(AudioPreferencesUiEvent.ToggleAudioEqualizer) },
        onBandLevelChange = { band, levelDb ->
            onEvent(AudioPreferencesUiEvent.UpdateAudioEqualizerBand(band, levelDb))
        },
        onShowPresets = { onEvent(AudioPreferencesUiEvent.ShowDialog(AudioPreferenceDialog.AudioEqualizerPresets)) },
        onSavePreset = { onEvent(AudioPreferencesUiEvent.ShowDialog(AudioPreferenceDialog.SaveAudioEqualizerPreset)) },
        testTagPrefix = "settings_audio_equalizer",
    )
}

@PreviewLightDark
@Composable
private fun AudioPreferencesScreenPreview() {
    OnlyPlayerTheme {
        AudioPreferencesContent(
            uiState = AudioPreferencesUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
