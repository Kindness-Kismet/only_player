package one.only.player.settings.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.extensions.isPipFeatureSupported
import one.only.player.core.common.extensions.round
import one.only.player.core.model.ControlButtonsPosition
import one.only.player.core.model.ControllerAutoHidePreset
import one.only.player.core.model.PlayerControlsStyle
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.isEnabled
import one.only.player.settings.extensions.name

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
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_controller_timeout"),
                    title = stringResource(R.string.controller_timeout),
                    description = uiState.preferences.controllerAutoHideDescription(),
                    icon = NextIcons.Timer,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.ControllerAutoHideDialog))
                    },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_dim_video_controls"),
                    title = stringResource(id = R.string.dim_video_when_controls_visible),
                    description = stringResource(id = R.string.dim_video_when_controls_visible_description),
                    icon = NextIcons.HideSource,
                    isChecked = uiState.preferences.shouldDimVideoWhenControlsVisible,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleDimVideoWhenControlsVisible) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_screen_orientation"),
                    title = stringResource(id = R.string.player_screen_orientation),
                    description = uiState.preferences.playerScreenOrientation.name(),
                    icon = NextIcons.Rotation,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog))
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_orientation"),
                    title = stringResource(id = R.string.remember_player_screen_orientation),
                    description = stringResource(id = R.string.remember_player_screen_orientation_description),
                    icon = NextIcons.History,
                    isChecked = uiState.preferences.shouldRememberPlayerScreenOrientation,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_controls_style"),
                    title = stringResource(id = R.string.player_controls_style),
                    description = uiState.preferences.controlsStyle.name(),
                    icon = NextIcons.Player,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.ControlsStyleDialog)) },
                    isLastItem = uiState.preferences.controlsStyle != PlayerControlsStyle.LEGACY,
                )
                if (uiState.preferences.controlsStyle == PlayerControlsStyle.LEGACY) {
                    ClickablePreferenceItem(
                        modifier = Modifier.testTag("item_settings_player_icon_style"),
                        title = stringResource(id = R.string.player_icon_style),
                        description = uiState.preferences.playerIconStyle.name(),
                        icon = NextIcons.Style,
                        onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PlayerIconStyleDialog)) },
                        isLastItem = true,
                    )
                }
            }

            ListSectionTitle(text = stringResource(id = R.string.playback_behavior))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_resume"),
                    title = stringResource(id = R.string.resume),
                    description = stringResource(id = R.string.resume_description),
                    icon = NextIcons.Resume,
                    isChecked = uiState.preferences.resume.isEnabled,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePlaybackResume) },
                    isFirstItem = true,
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_player_default_speed"),
                    sliderModifier = Modifier.testTag("slider_settings_player_default_speed"),
                    title = stringResource(id = R.string.default_playback_speed),
                    description = uiState.preferences.defaultPlaybackSpeed.toString(),
                    icon = NextIcons.Speed,
                    value = uiState.preferences.defaultPlaybackSpeed,
                    valueRange = 0.2f..4.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(it.round(2))) },
                    trailingContent = {
                        FilledIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_player_default_speed"),
                            onClick = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(1f)) },
                        ) {
                            Icon(
                                imageVector = NextIcons.History,
                                contentDescription = stringResource(id = R.string.reset_default_playback_speed),
                            )
                        }
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_autoplay"),
                    title = stringResource(id = R.string.autoplay_settings),
                    description = stringResource(
                        id = R.string.autoplay_settings_description,
                    ),
                    icon = NextIcons.Player,
                    isChecked = uiState.preferences.shouldAutoPlay,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoplay) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_pause_at_end_of_queue"),
                    title = stringResource(id = R.string.pause_at_end_of_queue),
                    description = stringResource(id = R.string.pause_at_end_of_queue_description),
                    icon = NextIcons.Pause,
                    isChecked = uiState.preferences.shouldPauseAtEndOfQueue,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePauseAtEndOfQueue) },
                )
                if (isPipFeatureSupported) {
                    PreferenceSwitch(
                        modifier = Modifier.testTag("switch_settings_player_auto_pip"),
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
                    modifier = Modifier.testTag("switch_settings_player_background_play"),
                    title = stringResource(id = R.string.background_play),
                    description = stringResource(
                        id = R.string.background_play_description,
                    ),
                    icon = NextIcons.Headset,
                    isChecked = uiState.preferences.shouldAutoPlayInBackground,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_brightness"),
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

            ListSectionTitle(text = stringResource(id = R.string.player_controls))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_control_buttons_position"),
                    title = stringResource(id = R.string.control_buttons_alignment),
                    description = uiState.preferences.controlButtonsPosition.name(),
                    icon = NextIcons.ButtonsPosition,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.ControlButtonsDialog)) },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_control_labels"),
                    title = stringResource(id = R.string.player_control_labels),
                    description = stringResource(id = R.string.player_control_labels_description),
                    icon = NextIcons.Title,
                    isChecked = uiState.preferences.shouldHidePlayerControlLabels,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePlayerControlLabels) },
                    isLastItem = true,
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                PlayerPreferenceDialog.ControllerAutoHideDialog -> {
                    ControllerAutoHideDialog(
                        preferences = uiState.preferences,
                        onDismiss = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                        onPresetSelected = {
                            onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHidePreset(it))
                            onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                        },
                        onCustomConfirm = {
                            onEvent(PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout(it))
                            onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                        },
                    )
                }

                PlayerPreferenceDialog.PlayerScreenOrientationDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_screen_orientation),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(ScreenOrientation.entries.toTypedArray()) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_player_screen_orientation_${it.name.lowercase()}"),
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
                                modifier = Modifier.testTag("option_settings_player_control_buttons_position_${it.name.lowercase()}"),
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

                PlayerPreferenceDialog.PlayerIconStyleDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_icon_style),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(PlayerIconStyle.entries.toTypedArray()) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_player_icon_style_${it.name.lowercase()}"),
                                text = it.name(),
                                isSelected = it == uiState.preferences.playerIconStyle,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePlayerIconStyle(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                PlayerPreferenceDialog.ControlsStyleDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.player_controls_style),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(PlayerControlsStyle.entries.toTypedArray()) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_player_controls_style_${it.name.lowercase()}"),
                                text = it.name(),
                                isSelected = it == uiState.preferences.controlsStyle,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdateControlsStyle(it))
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

@Composable
private fun ControllerAutoHideDialog(
    preferences: PlayerPreferences,
    onDismiss: () -> Unit,
    onPresetSelected: (ControllerAutoHidePreset) -> Unit,
    onCustomConfirm: (Int) -> Unit,
) {
    var isCustomSelected by rememberSaveable {
        mutableStateOf(preferences.controllerAutoHidePreset == ControllerAutoHidePreset.CUSTOM)
    }
    var value by rememberSaveable {
        mutableStateOf(preferences.controllerAutoHideTimeout.coerceAtLeast(1).toString())
    }
    val seconds = value.toIntOrNull()?.coerceAtLeast(1)

    NextDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.controller_timeout_select)) },
        content = {
            HorizontalDivider()
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                items(ControllerAutoHidePreset.entries.toTypedArray()) {
                    RadioTextButton(
                        modifier = Modifier.testTag("option_settings_player_controller_timeout_${it.name.lowercase()}"),
                        text = it.description(preferences),
                        isSelected = when (it) {
                            ControllerAutoHidePreset.CUSTOM -> isCustomSelected
                            else -> !isCustomSelected && it == preferences.controllerAutoHidePreset
                        },
                        onClick = {
                            if (it == ControllerAutoHidePreset.CUSTOM) {
                                isCustomSelected = true
                            } else {
                                onPresetSelected(it)
                            }
                        },
                    )
                }
                if (isCustomSelected) {
                    item {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { input -> value = input.filter(Char::isDigit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("input_settings_player_controller_timeout_custom"),
                            singleLine = true,
                            label = { Text(stringResource(R.string.enter_seconds)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            HorizontalDivider()
        },
        confirmButton = {
            if (isCustomSelected) {
                TextButton(
                    modifier = Modifier.testTag("btn_settings_player_controller_timeout_custom_confirm"),
                    enabled = seconds != null,
                    onClick = { seconds?.let(onCustomConfirm) },
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun PlayerPreferences.controllerAutoHideDescription(): String = controllerAutoHidePreset.description(this)

@Composable
private fun ControllerAutoHidePreset.description(preferences: PlayerPreferences): String = when (this) {
    ControllerAutoHidePreset.DISABLED -> stringResource(R.string.controller_timeout_disabled)
    ControllerAutoHidePreset.FIFTEEN_SECONDS -> stringResource(R.string.controller_timeout_15_seconds)
    ControllerAutoHidePreset.ONE_MINUTE -> stringResource(R.string.controller_timeout_1_minute)
    ControllerAutoHidePreset.CUSTOM -> stringResource(R.string.controller_timeout_custom_value, preferences.controllerAutoHideTimeout)
}

@DayNightPreview
@Composable
private fun PlayerPreferencesScreenPreview() {
    OnlyPlayerTheme {
        PlayerPreferencesContent(
            uiState = PlayerPreferencesUiState(),
            onEvent = {},
        )
    }
}
