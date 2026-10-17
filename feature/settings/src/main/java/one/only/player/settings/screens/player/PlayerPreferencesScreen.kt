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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.extensions.isPipFeatureSupported
import one.only.player.core.common.extensions.round
import one.only.player.core.model.ControllerAutoHidePreset
import one.only.player.core.model.PictureInPictureMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.ResetIconButton
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.isEnabled
import one.only.player.settings.extensions.name
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlayerPreferencesScreen(
    onNavigateUp: () -> Unit,
    onCustomizeControlsClick: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlayerPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
        onCustomizeControlsClick = onCustomizeControlsClick,
    )
}

@Composable
private fun PlayerPreferencesContent(
    uiState: PlayerPreferencesUiState,
    onEvent: (PlayerPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit = {},
    onCustomizeControlsClick: () -> Unit = {},
) {
    val isPipFeatureSupported = LocalContext.current.isPipFeatureSupported

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.player_name),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_player_back"),
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
                    modifier = Modifier.testTag("item_settings_player_controller_timeout"),
                    title = stringResource(R.string.controller_timeout),
                    description = uiState.preferences.controllerAutoHideDescription(),
                    icon = AppIcons.Timer,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.ControllerAutoHideDialog))
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_dim_video_controls"),
                    title = stringResource(id = R.string.dim_video_when_controls_visible),
                    description = stringResource(id = R.string.dim_video_when_controls_visible_description),
                    icon = AppIcons.HideSource,
                    isChecked = uiState.preferences.shouldDimVideoWhenControlsVisible,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleDimVideoWhenControlsVisible) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_customize_controls"),
                    title = stringResource(id = R.string.customize_player_controls),
                    description = stringResource(id = R.string.customize_player_controls_description),
                    icon = AppIcons.Edit,
                    onClick = onCustomizeControlsClick,
                )
            }

            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_screen_orientation"),
                    title = stringResource(id = R.string.player_screen_orientation),
                    description = uiState.preferences.playerScreenOrientation.name(),
                    icon = AppIcons.Rotation,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PlayerScreenOrientationDialog))
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_orientation"),
                    title = stringResource(id = R.string.remember_player_screen_orientation),
                    description = stringResource(id = R.string.remember_player_screen_orientation_description),
                    icon = AppIcons.History,
                    isChecked = uiState.preferences.shouldRememberPlayerScreenOrientation,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_brightness"),
                    title = stringResource(id = R.string.remember_brightness_level),
                    description = stringResource(
                        id = R.string.remember_brightness_level_description,
                    ),
                    icon = AppIcons.Brightness,
                    isChecked = uiState.preferences.shouldRememberPlayerBrightness,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel) },
                )
            }

            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_resume"),
                    title = stringResource(id = R.string.resume),
                    description = stringResource(id = R.string.resume_description),
                    icon = AppIcons.Resume,
                    isChecked = uiState.preferences.resume.isEnabled,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePlaybackResume) },
                )
                PreferenceSlider(
                    modifier = Modifier.testTag("item_settings_player_default_speed"),
                    title = stringResource(id = R.string.default_playback_speed),
                    description = uiState.preferences.defaultPlaybackSpeed.toString(),
                    icon = AppIcons.Speed,
                    value = uiState.preferences.defaultPlaybackSpeed,
                    valueRange = 0.2f..4.0f,
                    onValueChange = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(it.round(2))) },
                    trailingContent = {
                        ResetIconButton(
                            modifier = Modifier.testTag("btn_reset_settings_player_default_speed"),
                            onClick = { onEvent(PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed(1f)) },
                            contentDescription = stringResource(id = R.string.reset_default_playback_speed),
                        )
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_autoplay"),
                    title = stringResource(id = R.string.autoplay_settings),
                    description = stringResource(
                        id = R.string.autoplay_settings_description,
                    ),
                    icon = AppIcons.Player,
                    isChecked = uiState.preferences.shouldAutoPlay,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoplay) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_pause_at_end_of_queue"),
                    title = stringResource(id = R.string.pause_at_end_of_queue),
                    description = stringResource(id = R.string.pause_at_end_of_queue_description),
                    icon = AppIcons.Pause,
                    isChecked = uiState.preferences.shouldPauseAtEndOfQueue,
                    onClick = { onEvent(PlayerPreferencesUiEvent.TogglePauseAtEndOfQueue) },
                )
            }

            PreferenceGroup {
                if (isPipFeatureSupported) {
                    ClickablePreferenceItem(
                        modifier = Modifier.testTag("item_settings_player_pip_mode"),
                        title = stringResource(id = R.string.pip_mode),
                        description = uiState.preferences.pictureInPictureMode.displayName(),
                        icon = AppIcons.Pip,
                        onClick = {
                            onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.PictureInPictureModeDialog))
                        },
                    )
                    PreferenceSwitch(
                        modifier = Modifier.testTag("switch_settings_player_auto_pip"),
                        title = stringResource(id = R.string.pip_auto_enter),
                        description = stringResource(
                            id = R.string.pip_auto_enter_description,
                        ),
                        icon = AppIcons.Pip,
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
                    icon = AppIcons.Headset,
                    isChecked = uiState.preferences.shouldAutoPlayInBackground,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay) },
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
                        items(ScreenOrientation.entries.toTypedArray(), key = { it.name }) {
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

                PlayerPreferenceDialog.PictureInPictureModeDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.pip_mode),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(PictureInPictureMode.entries.toTypedArray(), key = { it.name }) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_player_pip_mode_${it.name.lowercase()}"),
                                text = it.displayName(),
                                isSelected = it == uiState.preferences.pictureInPictureMode,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdatePictureInPictureMode(it))
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
private fun PictureInPictureMode.displayName(): String = when (this) {
    PictureInPictureMode.NATIVE -> stringResource(R.string.pip_mode_native)
    PictureInPictureMode.CUSTOM -> stringResource(R.string.pip_mode_custom)
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(id = R.string.controller_timeout_select),
        content = {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                items(ControllerAutoHidePreset.entries.toTypedArray(), key = { it.name }) {
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
                        TextField(
                            value = value,
                            onValueChange = { input -> value = input.filter(Char::isDigit) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("input_settings_player_controller_timeout_custom"),
                            singleLine = true,
                            label = stringResource(R.string.enter_seconds),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isCustomSelected) {
                TextButton(
                    text = stringResource(R.string.done),
                    modifier = Modifier.testTag("btn_settings_player_controller_timeout_custom_confirm"),
                    enabled = seconds != null,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { seconds?.let(onCustomConfirm) },
                )
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
