package one.next.player.settings.screens.decoder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.model.DecoderPriority
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.ui.R
import one.next.player.core.ui.components.ClickablePreferenceItem
import one.next.player.core.ui.components.ListSectionTitle
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.components.PreferenceSlider
import one.next.player.core.ui.components.PreferenceSwitch
import one.next.player.core.ui.components.RadioTextButton
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback
import one.next.player.core.ui.theme.OnePlayerTheme
import one.next.player.settings.composables.OptionsDialog
import one.next.player.settings.extensions.name

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DecoderPreferencesContent(
    uiState: DecoderPreferencesUiState,
    onEvent: (DecoderPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val preferences = uiState.preferences

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.video_processing),
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
            ListSectionTitle(text = stringResource(id = R.string.decoder))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    title = stringResource(R.string.decoder_priority),
                    description = preferences.decoderPriority.name(),
                    icon = NextIcons.Priority,
                    onClick = { onEvent(DecoderPreferencesUiEvent.ShowDialog(DecoderPreferenceDialog.DecoderPriorityDialog)) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.video_filters))
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
                        items(DecoderPriority.entries.toTypedArray()) {
                            RadioTextButton(
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoFiltersSettings(
    preferences: PlayerPreferences,
    onEvent: (DecoderPreferencesUiEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_video_filters"),
            title = stringResource(R.string.enable_video_filters),
            description = stringResource(R.string.enable_video_filters_description),
            icon = NextIcons.Sensitivity,
            isChecked = preferences.shouldApplyVideoFilters,
            onClick = { onEvent(DecoderPreferencesUiEvent.ToggleVideoFilters) },
            isFirstItem = true,
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_brightness"),
            sliderModifier = Modifier.testTag("slider_settings_video_brightness"),
            title = stringResource(R.string.video_brightness),
            description = signedPercent(preferences.videoBrightness),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoBrightness,
            valueRange = PlayerPreferences.MIN_VIDEO_BRIGHTNESS..PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoBrightness(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_brightness",
                    contentDescription = stringResource(id = R.string.reset_video_brightness),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoBrightness(PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_contrast"),
            sliderModifier = Modifier.testTag("slider_settings_video_contrast"),
            title = stringResource(R.string.video_contrast),
            description = signedPercent(preferences.videoContrast),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoContrast,
            valueRange = PlayerPreferences.MIN_VIDEO_CONTRAST..PlayerPreferences.MAX_VIDEO_CONTRAST,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoContrast(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_contrast",
                    contentDescription = stringResource(id = R.string.reset_video_contrast),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoContrast(PlayerPreferences.DEFAULT_VIDEO_CONTRAST)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_saturation"),
            sliderModifier = Modifier.testTag("slider_settings_video_saturation"),
            title = stringResource(R.string.video_saturation),
            description = signedInteger(preferences.videoSaturation),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoSaturation,
            valueRange = PlayerPreferences.MIN_VIDEO_SATURATION..PlayerPreferences.MAX_VIDEO_SATURATION,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoSaturation(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_saturation",
                    contentDescription = stringResource(id = R.string.reset_video_saturation),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoSaturation(PlayerPreferences.DEFAULT_VIDEO_SATURATION)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_hue"),
            sliderModifier = Modifier.testTag("slider_settings_video_hue"),
            title = stringResource(R.string.video_hue),
            description = stringResource(R.string.degrees, preferences.videoHue.toInt()),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoHue,
            valueRange = PlayerPreferences.MIN_VIDEO_HUE..PlayerPreferences.MAX_VIDEO_HUE,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoHue(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_hue",
                    contentDescription = stringResource(id = R.string.reset_video_hue),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoHue(PlayerPreferences.DEFAULT_VIDEO_HUE)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_gamma"),
            sliderModifier = Modifier.testTag("slider_settings_video_gamma"),
            title = stringResource(R.string.video_gamma),
            description = String.format("%.2f", preferences.videoGamma),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoGamma,
            valueRange = PlayerPreferences.MIN_VIDEO_GAMMA..PlayerPreferences.MAX_VIDEO_GAMMA,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoGamma(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_gamma",
                    contentDescription = stringResource(id = R.string.reset_video_gamma),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoGamma(PlayerPreferences.DEFAULT_VIDEO_GAMMA)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_sharpening"),
            sliderModifier = Modifier.testTag("slider_settings_video_sharpening"),
            title = stringResource(R.string.video_sharpening),
            description = stringResource(R.string.percent, (preferences.videoSharpening * 100).toInt()),
            icon = NextIcons.Sensitivity,
            isEnabled = preferences.shouldApplyVideoFilters,
            value = preferences.videoSharpening,
            valueRange = PlayerPreferences.DEFAULT_VIDEO_SHARPENING..PlayerPreferences.MAX_VIDEO_SHARPENING,
            onValueChange = { onEvent(DecoderPreferencesUiEvent.UpdateVideoSharpening(it)) },
            isLastItem = true,
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_sharpening",
                    contentDescription = stringResource(id = R.string.reset_video_sharpening),
                    isEnabled = preferences.shouldApplyVideoFilters,
                    onClick = { onEvent(DecoderPreferencesUiEvent.UpdateVideoSharpening(PlayerPreferences.DEFAULT_VIDEO_SHARPENING)) },
                )
            },
        )
    }
}

@Composable
private fun ResetVideoFilterButton(
    testTag: String,
    contentDescription: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    FilledIconButton(
        modifier = Modifier.testTag(testTag),
        enabled = isEnabled,
        onClick = onClick,
    ) {
        Icon(
            imageVector = NextIcons.History,
            contentDescription = contentDescription,
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
    OnePlayerTheme {
        DecoderPreferencesContent(
            uiState = DecoderPreferencesUiState(),
            onEvent = {},
            onNavigateUp = {},
        )
    }
}
