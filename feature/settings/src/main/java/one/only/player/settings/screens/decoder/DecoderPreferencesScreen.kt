package one.only.player.settings.screens.decoder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.ResetIconButton
import one.only.player.core.ui.components.SavePresetNameDialog
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.components.VIDEO_BRIGHTNESS_INT_RANGE
import one.only.player.core.ui.components.VIDEO_CONTRAST_INT_RANGE
import one.only.player.core.ui.components.VIDEO_GAMMA_INT_RANGE
import one.only.player.core.ui.components.VIDEO_HUE_INT_RANGE
import one.only.player.core.ui.components.VIDEO_SATURATION_INT_RANGE
import one.only.player.core.ui.components.VIDEO_SHARPENING_INT_RANGE
import one.only.player.core.ui.components.VideoFilterPresetPickerDialog
import one.only.player.core.ui.components.sliderStepCount
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DecoderPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: DecoderPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DecoderPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun DecoderPreferencesContent(
    uiState: DecoderPreferencesUiState,
    onEvent: (DecoderPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val preferences = uiState.preferences

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.video_processing),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_decoder_back"),
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
                    modifier = Modifier.testTag("item_settings_decoder_priority"),
                    title = stringResource(R.string.decoder_priority),
                    description = preferences.decoderPriority.name(),
                    icon = AppIcons.Priority,
                    onClick = { onEvent(DecoderPreferencesUiEvent.ShowDialog(DecoderPreferenceDialog.DecoderPriorityDialog)) },
                )
            }

            VideoFiltersSettings(
                preferences = preferences,
                onEvent = onEvent,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                DecoderPreferenceDialog.DecoderPriorityDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.decoder_priority),
                        onDismissClick = { onEvent(DecoderPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(DecoderPriority.entries.toTypedArray(), key = { it.name }) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_decoder_priority_${it.name.lowercase()}"),
                                text = it.name(),
                                isSelected = it == preferences.decoderPriority,
                                onClick = {
                                    onEvent(DecoderPreferencesUiEvent.UpdateDecoderPriority(it))
                                    onEvent(DecoderPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }
                DecoderPreferenceDialog.VideoFilterPresets -> {
                    VideoFilterPresetPickerDialog(
                        preferences = preferences,
                        onApplyPreset = {
                            onEvent(DecoderPreferencesUiEvent.ApplyVideoFilterPreset(it))
                            onEvent(DecoderPreferencesUiEvent.ShowDialog(null))
                        },
                        onDeletePreset = { onEvent(DecoderPreferencesUiEvent.DeleteVideoFilterPreset(it)) },
                        onDismissRequest = { onEvent(DecoderPreferencesUiEvent.ShowDialog(null)) },
                    )
                }
                DecoderPreferenceDialog.SaveVideoFilterPreset -> {
                    SavePresetNameDialog(
                        title = stringResource(R.string.save_current_as_video_filter_preset),
                        presetNameLabel = stringResource(R.string.video_filter_preset_name),
                        dialogTestTag = "dialog_save_video_filter_preset",
                        inputTestTag = "input_filter_preset_name",
                        confirmTestTag = "btn_save_filter_preset",
                        onDismissRequest = { onEvent(DecoderPreferencesUiEvent.ShowDialog(null)) },
                        onSavePreset = {
                            onEvent(DecoderPreferencesUiEvent.SaveVideoFilterPreset(it))
                            onEvent(DecoderPreferencesUiEvent.ShowDialog(null))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFiltersSettings(
    preferences: PlayerPreferences,
    onEvent: (DecoderPreferencesUiEvent) -> Unit,
) {
    // 拖动过程中只更新本地草稿，滑动结束才提交，避免逐帧写入
    var draftPreferences by remember(preferences) { mutableStateOf(preferences) }

    PreferenceGroup {
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_video_filters"),
            title = stringResource(R.string.enable_video_filters),
            description = stringResource(R.string.enable_video_filters_description),
            icon = AppIcons.Brightness,
            isChecked = preferences.shouldApplyVideoFilters,
            onClick = { onEvent(DecoderPreferencesUiEvent.ToggleVideoFilters) },
        )
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_video_filter_presets"),
            title = stringResource(R.string.video_filter_presets),
            icon = AppIcons.ColorFilter,
            onClick = { onEvent(DecoderPreferencesUiEvent.ShowDialog(DecoderPreferenceDialog.VideoFilterPresets)) },
        )
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_save_video_filter_preset"),
            title = stringResource(R.string.save_current_as_video_filter_preset),
            icon = AppIcons.Save,
            onClick = { onEvent(DecoderPreferencesUiEvent.ShowDialog(DecoderPreferenceDialog.SaveVideoFilterPreset)) },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_brightness"),
            title = stringResource(R.string.video_brightness),
            description = signedPercent(draftPreferences.videoBrightness),
            icon = AppIcons.Exposure,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoBrightness,
            valueRange = PlayerPreferences.MIN_VIDEO_BRIGHTNESS..PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
            steps = VIDEO_BRIGHTNESS_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoBrightness = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoBrightness(draftPreferences.videoBrightness))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_brightness"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoBrightness(PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_brightness),
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_contrast"),
            title = stringResource(R.string.video_contrast),
            description = signedPercent(draftPreferences.videoContrast),
            icon = AppIcons.Drop,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoContrast,
            valueRange = PlayerPreferences.MIN_VIDEO_CONTRAST..PlayerPreferences.MAX_VIDEO_CONTRAST,
            steps = VIDEO_CONTRAST_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoContrast = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoContrast(draftPreferences.videoContrast))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_contrast"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoContrast(PlayerPreferences.DEFAULT_VIDEO_CONTRAST))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_contrast),
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_saturation"),
            title = stringResource(R.string.video_saturation),
            description = signedInteger(draftPreferences.videoSaturation),
            icon = AppIcons.Rainbow,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoSaturation,
            valueRange = PlayerPreferences.MIN_VIDEO_SATURATION..PlayerPreferences.MAX_VIDEO_SATURATION,
            steps = VIDEO_SATURATION_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoSaturation = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoSaturation(draftPreferences.videoSaturation))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_saturation"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoSaturation(PlayerPreferences.DEFAULT_VIDEO_SATURATION))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_saturation),
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_hue"),
            title = stringResource(R.string.video_hue),
            description = stringResource(R.string.degrees, draftPreferences.videoHue.toInt()),
            icon = AppIcons.ChartLine,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoHue,
            valueRange = PlayerPreferences.MIN_VIDEO_HUE..PlayerPreferences.MAX_VIDEO_HUE,
            steps = VIDEO_HUE_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoHue = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoHue(draftPreferences.videoHue))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_hue"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoHue(PlayerPreferences.DEFAULT_VIDEO_HUE))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_hue),
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_gamma"),
            title = stringResource(R.string.video_gamma),
            description = stringResource(R.string.percent, (draftPreferences.videoGamma * 100).roundToInt()),
            icon = AppIcons.Focus,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoGamma,
            valueRange = PlayerPreferences.MIN_VIDEO_GAMMA..PlayerPreferences.MAX_VIDEO_GAMMA,
            steps = VIDEO_GAMMA_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoGamma = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoGamma(draftPreferences.videoGamma))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_gamma"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoGamma(PlayerPreferences.DEFAULT_VIDEO_GAMMA))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_gamma),
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_sharpening"),
            title = stringResource(R.string.video_sharpening),
            description = stringResource(R.string.percent, (draftPreferences.videoSharpening * 100).toInt()),
            icon = AppIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = draftPreferences.videoSharpening,
            valueRange = PlayerPreferences.DEFAULT_VIDEO_SHARPENING..PlayerPreferences.MAX_VIDEO_SHARPENING,
            steps = VIDEO_SHARPENING_INT_RANGE.sliderStepCount(),
            onValueChange = { draftPreferences = draftPreferences.copy(videoSharpening = it) },
            onValueChangeFinished = {
                onEvent(DecoderPreferencesUiEvent.UpdateVideoSharpening(draftPreferences.videoSharpening))
            },
            trailingContent = {
                ResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_video_sharpening"),
                    enabled = preferences.shouldApplyVideoFilters,
                    onClick = {
                        onEvent(DecoderPreferencesUiEvent.UpdateVideoSharpening(PlayerPreferences.DEFAULT_VIDEO_SHARPENING))
                    },
                    contentDescription = stringResource(id = R.string.reset_video_sharpening),
                )
            },
        )
    }
}

private fun signedPercent(value: Float): String {
    val percent = (value * 100).toInt()
    return if (percent > 0) "+$percent%" else "$percent%"
}

private fun signedInteger(value: Float): String {
    val rounded = value.toInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
}

@PreviewLightDark
@Composable
private fun DecoderPreferencesScreenPreview() {
    OnlyPlayerTheme {
        DecoderPreferencesContent(
            uiState = DecoderPreferencesUiState(),
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
