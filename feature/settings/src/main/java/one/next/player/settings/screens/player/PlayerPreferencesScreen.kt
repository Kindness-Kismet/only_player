package one.next.player.settings.screens.player

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.next.player.core.common.extensions.isPipFeatureSupported
import one.next.player.core.common.extensions.round
import one.next.player.core.model.ControlButtonsPosition
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.ScreenOrientation
import one.next.player.core.ui.R
import one.next.player.core.ui.components.ClickablePreferenceItem
import one.next.player.core.ui.components.ListSectionTitle
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.components.PreferenceSlider
import one.next.player.core.ui.components.PreferenceSwitch
import one.next.player.core.ui.components.RadioTextButton
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback
import one.next.player.core.ui.preview.DayNightPreview
import one.next.player.core.ui.theme.OnePlayerTheme
import one.next.player.settings.composables.OptionsDialog
import one.next.player.settings.extensions.isEnabled
import one.next.player.settings.extensions.name

@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlayerPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerPreferencesContent(
    uiState: PlayerPreferencesUiState,
    onEvent: (PlayerPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
) {
    val isPipFeatureSupported = LocalContext.current.isPipFeatureSupported

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.player_name),
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
            ListSectionTitle(text = stringResource(id = R.string.interface_name))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSlider(
                    title = stringResource(R.string.controller_timeout),
                    description = stringResource(R.string.seconds, uiState.preferences.controllerAutoHideTimeout),
                    icon = NextIcons.Timer,
                    value = uiState.preferences.controllerAutoHideTimeout.toFloat(),
                    valueRange = 1.0f..60.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout(it.toInt())) },
                    isFirstItem = true,
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout(PlayerPreferences.DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_controller_timeout),
                            )
                        }
                    },
                )
                ClickablePreferenceItem(
                    title = stringResource(id = R.string.player_screen_orientation),
                    description = uiState.preferences.playerScreenOrientation.name(),
                    icon = NextIcons.Rotation,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog))
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("item_settings_classic_player_icons"),
                    title = stringResource(id = R.string.enable_classic_player_icons),
                    description = stringResource(id = R.string.enable_classic_player_icons_description),
                    icon = NextIcons.Style,
                    isChecked = uiState.preferences.shouldUseClassicPlayerIcons,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleUseClassicPlayerIcons) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.playback_behavior))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.resume),
                    description = stringResource(id = R.string.resume_description),
                    icon = NextIcons.Resume,
                    isChecked = uiState.preferences.resume.isEnabled,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePlaybackResume) },
                    isFirstItem = true,
                )
                PreferenceSlider(
                    title = stringResource(id = R.string.default_playback_speed),
                    description = uiState.preferences.defaultPlaybackSpeed.toString(),
                    icon = NextIcons.Speed,
                    value = uiState.preferences.defaultPlaybackSpeed,
                    valueRange = 0.2f..4.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(it.round(2))) },
                    trailingContent = {
                        FilledIconButton(onClick = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(1f)) }) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_default_playback_speed),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.autoplay_settings),
                    description = stringResource(
                        id = R.string.autoplay_settings_description,
                    ),
                    icon = NextIcons.Player,
                    isChecked = uiState.preferences.shouldAutoPlay,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoplay) },
                )
                if (isPipFeatureSupported) {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.pip_settings),
                        description = stringResource(
                            id = R.string.pip_settings_description,
                        ),
                        icon = NextIcons.Pip,
                        isChecked = uiState.preferences.shouldAutoEnterPip,
                        onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoPip) },
                    )
                }
                PreferenceSwitch(
                    title = stringResource(id = R.string.background_play),
                    description = stringResource(
                        id = R.string.background_play_description,
                    ),
                    icon = NextIcons.Headset,
                    isChecked = uiState.preferences.shouldAutoPlayInBackground,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay) },
                )
                PreferenceSwitch(
                    title = stringResource(id = R.string.remember_brightness_level),
                    description = stringResource(
                        id = R.string.remember_brightness_level_description,
                    ),
                    icon = NextIcons.Brightness,
                    isChecked = uiState.preferences.shouldRememberPlayerBrightness,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.video_processing))
            VideoFiltersSettings(
                preferences = uiState.preferences,
                onEvent = onEvent,
            )
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                PlayerPreferenceDialog.PlayerScreenOrientationDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_screen_orientation),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(ScreenOrientation.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                isSelected = it == uiState.preferences.playerScreenOrientation,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.ControlButtonsDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.control_buttons_alignment),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(ControlButtonsPosition.entries.toTypedArray()) {
                            RadioTextButton(
                                text = it.name(),
                                isSelected = it == uiState.preferences.controlButtonsPosition,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
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
    onEvent: (PlayerPreferencesUiEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_brightness"),
            sliderModifier = Modifier.testTag("slider_settings_video_brightness"),
            title = stringResource(R.string.video_brightness),
            description = signedPercent(preferences.videoBrightness),
            icon = NextIcons.Sensitivity,
            value = preferences.videoBrightness,
            valueRange = PlayerPreferences.MIN_VIDEO_BRIGHTNESS..PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoBrightness(it)) },
            isFirstItem = true,
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_brightness",
                    contentDescription = stringResource(id = R.string.reset_video_brightness),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoBrightness(PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_contrast"),
            sliderModifier = Modifier.testTag("slider_settings_video_contrast"),
            title = stringResource(R.string.video_contrast),
            description = signedPercent(preferences.videoContrast),
            icon = NextIcons.Sensitivity,
            value = preferences.videoContrast,
            valueRange = PlayerPreferences.MIN_VIDEO_CONTRAST..PlayerPreferences.MAX_VIDEO_CONTRAST,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoContrast(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_contrast",
                    contentDescription = stringResource(id = R.string.reset_video_contrast),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoContrast(PlayerPreferences.DEFAULT_VIDEO_CONTRAST)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_saturation"),
            sliderModifier = Modifier.testTag("slider_settings_video_saturation"),
            title = stringResource(R.string.video_saturation),
            description = signedInteger(preferences.videoSaturation),
            icon = NextIcons.Sensitivity,
            value = preferences.videoSaturation,
            valueRange = PlayerPreferences.MIN_VIDEO_SATURATION..PlayerPreferences.MAX_VIDEO_SATURATION,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoSaturation(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_saturation",
                    contentDescription = stringResource(id = R.string.reset_video_saturation),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoSaturation(PlayerPreferences.DEFAULT_VIDEO_SATURATION)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_hue"),
            sliderModifier = Modifier.testTag("slider_settings_video_hue"),
            title = stringResource(R.string.video_hue),
            description = stringResource(R.string.degrees, preferences.videoHue.toInt()),
            icon = NextIcons.Sensitivity,
            value = preferences.videoHue,
            valueRange = PlayerPreferences.MIN_VIDEO_HUE..PlayerPreferences.MAX_VIDEO_HUE,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoHue(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_hue",
                    contentDescription = stringResource(id = R.string.reset_video_hue),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoHue(PlayerPreferences.DEFAULT_VIDEO_HUE)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_gamma"),
            sliderModifier = Modifier.testTag("slider_settings_video_gamma"),
            title = stringResource(R.string.video_gamma),
            description = String.format("%.2f", preferences.videoGamma),
            icon = NextIcons.Sensitivity,
            value = preferences.videoGamma,
            valueRange = PlayerPreferences.MIN_VIDEO_GAMMA..PlayerPreferences.MAX_VIDEO_GAMMA,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoGamma(it)) },
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_gamma",
                    contentDescription = stringResource(id = R.string.reset_video_gamma),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoGamma(PlayerPreferences.DEFAULT_VIDEO_GAMMA)) },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_video_sharpening"),
            sliderModifier = Modifier.testTag("slider_settings_video_sharpening"),
            title = stringResource(R.string.video_sharpening),
            description = stringResource(R.string.percent, (preferences.videoSharpening * 100).toInt()),
            icon = NextIcons.Sensitivity,
            value = preferences.videoSharpening,
            valueRange = PlayerPreferences.DEFAULT_VIDEO_SHARPENING..PlayerPreferences.MAX_VIDEO_SHARPENING,
            onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateVideoSharpening(it)) },
            isLastItem = true,
            trailingContent = {
                ResetVideoFilterButton(
                    testTag = "btn_reset_settings_video_sharpening",
                    contentDescription = stringResource(id = R.string.reset_video_sharpening),
                    onClick = { onEvent(PlayerPreferencesUiEvent.UpdateVideoSharpening(PlayerPreferences.DEFAULT_VIDEO_SHARPENING)) },
                )
            },
        )
    }
}

@Composable
private fun ResetVideoFilterButton(
    testTag: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        modifier = Modifier.testTag(testTag),
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

@DayNightPreview
@Composable
private fun PlayerPreferencesScreenPreview() {
    OnePlayerTheme {
        PlayerPreferencesContent(
            uiState = PlayerPreferencesUiState(),
            onEvent = {},
        )
    }
}
