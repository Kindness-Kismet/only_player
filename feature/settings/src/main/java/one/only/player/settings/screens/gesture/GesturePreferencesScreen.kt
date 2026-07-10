package one.only.player.settings.screens.gesture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.extensions.round
import one.only.player.core.common.extensions.toString
import one.only.player.core.model.DoubleTapGesture
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialogWithDoneAndCancelButtons
import one.only.player.core.ui.components.NextResetIconButton
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.PreferenceSwitchWithDivider
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GesturePreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: GesturePreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GesturePreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun GesturePreferencesContent(
    uiState: GesturePreferencesUiState,
    onEvent: (GesturePreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.gestures),
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_gesture_back"),
                    ) {
                        MiuixIcon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_seek"),
                    title = stringResource(id = R.string.seek_gesture),
                    description = stringResource(id = R.string.seek_gesture_description),
                    icon = NextIcons.SwipeHorizontal,
                    isChecked = uiState.preferences.shouldUseSeekControls,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleUseSeekControls) },
                    isFirstItem = true,
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_gesture_seek_sensitivity"),
                    sliderModifier = Modifier.testTag("slider_settings_gesture_seek_sensitivity"),
                    title = stringResource(R.string.seek_gesture_sensitivity),
                    description = uiState.preferences.seekSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    isEnabled = uiState.preferences.shouldUseSeekControls,
                    value = uiState.preferences.seekSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateSeekSensitivity(it)) },
                    trailingContent = {
                        NextResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_gesture_seek_sensitivity"),
                            enabled = uiState.preferences.shouldUseSeekControls,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateSeekSensitivity(PlayerPreferences.DEFAULT_SEEK_SENSITIVITY)) },
                            contentDescription = stringResource(id = R.string.reset_seek_sensitivity),
                        )
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_brightness"),
                    title = stringResource(id = R.string.brightness_gesture),
                    description = stringResource(id = R.string.brightness_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.isBrightnessSwipeGestureEnabled,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnableBrightnessSwipeGesture) },
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_gesture_brightness_sensitivity"),
                    sliderModifier = Modifier.testTag("slider_settings_gesture_brightness_sensitivity"),
                    title = stringResource(R.string.brightness_gesture_sensitivity),
                    description = uiState.preferences.brightnessGestureSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    isEnabled = uiState.preferences.isBrightnessSwipeGestureEnabled,
                    value = uiState.preferences.brightnessGestureSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateBrightnessGestureSensitivity(it)) },
                    trailingContent = {
                        NextResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_gesture_brightness_sensitivity"),
                            enabled = uiState.preferences.isBrightnessSwipeGestureEnabled,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateBrightnessGestureSensitivity(PlayerPreferences.DEFAULT_BRIGHTNESS_GESTURE_SENSITIVITY)) },
                            contentDescription = stringResource(id = R.string.reset_brightness_gesture_sensitivity),
                        )
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_volume"),
                    title = stringResource(id = R.string.volume_gesture),
                    description = stringResource(id = R.string.volume_gesture_description),
                    icon = NextIcons.SwipeVertical,
                    isChecked = uiState.preferences.isVolumeSwipeGestureEnabled,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnableVolumeSwipeGesture) },
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_gesture_volume_sensitivity"),
                    sliderModifier = Modifier.testTag("slider_settings_gesture_volume_sensitivity"),
                    title = stringResource(R.string.volume_gesture_sensitivity),
                    description = uiState.preferences.volumeGestureSensitivity.toString(decimalPlaces = 2),
                    icon = NextIcons.Sensitivity,
                    isEnabled = uiState.preferences.isVolumeSwipeGestureEnabled,
                    value = uiState.preferences.volumeGestureSensitivity,
                    valueRange = 0.1f..2.0f,
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateVolumeGestureSensitivity(it)) },
                    isLastItem = true,
                    trailingContent = {
                        NextResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_gesture_volume_sensitivity"),
                            enabled = uiState.preferences.isVolumeSwipeGestureEnabled,
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateVolumeGestureSensitivity(PlayerPreferences.DEFAULT_VOLUME_GESTURE_SENSITIVITY)) },
                            contentDescription = stringResource(id = R.string.reset_volume_gesture_sensitivity),
                        )
                    },
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.tap_gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSwitchWithDivider(
                    modifier = Modifier.testTag("item_settings_gesture_double_tap"),
                    switchModifier = Modifier.testTag("switch_settings_gesture_double_tap"),
                    title = stringResource(id = R.string.double_tap),
                    description = stringResource(id = R.string.double_tap_description),
                    icon = NextIcons.DoubleTap,
                    isChecked = (uiState.preferences.doubleTapGesture != DoubleTapGesture.NONE),
                    onChecked = { onEvent(GesturePreferencesUiEvent.ToggleDoubleTapGesture) },
                    onClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(GesturePreferenceDialog.DoubleTapDialog)) },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.long_press_gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSwitchWithDivider(
                    modifier = Modifier.testTag("item_settings_gesture_long_press"),
                    switchModifier = Modifier.testTag("switch_settings_gesture_long_press"),
                    title = stringResource(id = R.string.long_press_gesture),
                    description = stringResource(id = R.string.long_press_gesture_desc, uiState.preferences.longPressControlsSpeed),
                    icon = NextIcons.Tap,
                    isChecked = uiState.preferences.shouldUseLongPressControls,
                    onChecked = { onEvent(GesturePreferencesUiEvent.ToggleUseLongPressControls) },
                    onClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(GesturePreferenceDialog.LongPressControlsSpeedDialog)) },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_long_press_variable_speed"),
                    title = stringResource(id = R.string.long_press_variable_speed),
                    description = stringResource(id = R.string.long_press_variable_speed_desc),
                    icon = NextIcons.SwipeHorizontal,
                    isEnabled = uiState.preferences.shouldUseLongPressControls,
                    isChecked = uiState.preferences.shouldUseLongPressVariableSpeed,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleUseLongPressVariableSpeed) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.zoom_and_pan_gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_zoom"),
                    title = stringResource(id = R.string.zoom_gesture),
                    description = stringResource(id = R.string.zoom_gesture_description),
                    icon = NextIcons.Pinch,
                    isChecked = uiState.preferences.shouldUseZoomControls,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleUseZoomControls) },
                    isFirstItem = true,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_gesture_pan"),
                    title = stringResource(id = R.string.pan_gesture),
                    description = stringResource(id = R.string.pan_gesture_description),
                    icon = NextIcons.Pan,
                    isEnabled = uiState.preferences.shouldUseZoomControls,
                    isChecked = uiState.preferences.isPanGestureEnabled,
                    onClick = { onEvent(GesturePreferencesUiEvent.ToggleEnablePanGesture) },
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.other_gestures))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_gesture_seek_increment"),
                    sliderModifier = Modifier.testTag("slider_settings_gesture_seek_increment"),
                    title = stringResource(R.string.seek_increment),
                    description = stringResource(R.string.seconds, uiState.preferences.seekIncrement),
                    icon = NextIcons.Replay,
                    value = uiState.preferences.seekIncrement.toFloat(),
                    valueRange = 1.0f..PlayerPreferences.MAX_SEEK_INCREMENT.toFloat(),
                    onValueChange = { onEvent(GesturePreferencesUiEvent.UpdateSeekIncrement(it.toInt())) },
                    isFirstItem = true,
                    isLastItem = true,
                    trailingContent = {
                        NextResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_gesture_seek_increment"),
                            onClick = { onEvent(GesturePreferencesUiEvent.UpdateSeekIncrement(PlayerPreferences.DEFAULT_SEEK_INCREMENT)) },
                            contentDescription = stringResource(id = R.string.reset_seek_increment),
                        )
                    },
                )
            }
        }

        uiState.showDialog?.let { showDialog ->
            when (showDialog) {
                GesturePreferenceDialog.DoubleTapDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.double_tap),
                        onDismissClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(DoubleTapGesture.entries.toTypedArray()) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_gesture_double_tap_${it.name.lowercase()}"),
                                text = it.name(),
                                isSelected = (it == uiState.preferences.doubleTapGesture),
                                onClick = {
                                    onEvent(GesturePreferencesUiEvent.UpdateDoubleTapGesture(it))
                                    onEvent(GesturePreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

                GesturePreferenceDialog.LongPressControlsSpeedDialog -> {
                    var longPressControlsSpeed by remember {
                        mutableFloatStateOf(uiState.preferences.longPressControlsSpeed)
                    }

                    NextDialogWithDoneAndCancelButtons(
                        title = stringResource(R.string.long_press_gesture),
                        onDoneClick = {
                            onEvent(GesturePreferencesUiEvent.UpdateLongPressControlsSpeed(longPressControlsSpeed))
                            onEvent(GesturePreferencesUiEvent.ShowDialog(null))
                        },
                        onDismissClick = { onEvent(GesturePreferencesUiEvent.ShowDialog(null)) },
                        content = {
                            Text(
                                text = "$longPressControlsSpeed",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center,
                                style = MiuixTheme.textStyles.headline1,
                            )
                            Slider(
                                modifier = Modifier.testTag("slider_settings_gesture_long_press_speed"),
                                value = longPressControlsSpeed,
                                onValueChange = { longPressControlsSpeed = it.round(1) },
                                valueRange = PlayerPreferences.MIN_LONG_PRESS_CONTROLS_SPEED..PlayerPreferences.MAX_LONG_PRESS_CONTROLS_SPEED,
                            )
                        },
                    )
                }
            }
        }
    }
}

@DayNightPreview
@Composable
private fun GesturePreferencesScreenPreview() {
    OnlyPlayerTheme {
        GesturePreferencesContent(
            uiState = GesturePreferencesUiState(),
            onEvent = {},
        )
    }
}
