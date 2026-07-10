package one.only.player.settings.screens.thumbnail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.ThumbnailGenerationStrategy
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextResetIconButton
import one.only.player.core.ui.components.PreferenceSlider
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.components.SingleSelectablePreference
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ThumbnailPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: ThumbnailPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ThumbnailPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun ThumbnailPreferencesContent(
    uiState: ThumbnailPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (ThumbnailPreferencesEvent) -> Unit,
) {
    val preferences = uiState.preferences
    var frameSliderValue by rememberSaveable { mutableFloatStateOf(preferences.thumbnailFramePosition * 100f) }
    var pendingChange by remember { mutableStateOf<ThumbnailPreferenceChange?>(null) }

    LaunchedEffect(preferences.thumbnailFramePosition, pendingChange) {
        if (pendingChange == null) {
            frameSliderValue = preferences.thumbnailFramePosition * 100f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.thumbnail_generation),
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_thumbnail_back"),
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
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                SingleSelectablePreference(
                    modifier = Modifier.testTag("option_settings_thumbnail_strategy_first_frame"),
                    title = stringResource(id = R.string.first_frame),
                    description = stringResource(id = R.string.first_frame_desc),
                    isSelected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FIRST_FRAME,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FIRST_FRAME) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.FIRST_FRAME)
                    },
                    isFirstItem = true,
                )
                SingleSelectablePreference(
                    modifier = Modifier.testTag("option_settings_thumbnail_strategy_frame_at_percentage"),
                    title = stringResource(id = R.string.frame_at_position),
                    description = stringResource(id = R.string.frame_at_position_desc),
                    isSelected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE)
                    },
                )
                SingleSelectablePreference(
                    modifier = Modifier.testTag("option_settings_thumbnail_strategy_hybrid"),
                    title = stringResource(id = R.string.hybrid),
                    description = stringResource(id = R.string.hybrid_desc),
                    isSelected = preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.HYBRID,
                    onClick = {
                        if (preferences.thumbnailGenerationStrategy == ThumbnailGenerationStrategy.HYBRID) return@SingleSelectablePreference
                        pendingChange = ThumbnailPreferenceChange.Strategy(ThumbnailGenerationStrategy.HYBRID)
                    },
                    isLastItem = true,
                )
            }

            PreferenceSlider(
                isEnabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .testTag("item_settings_thumbnail_frame_position"),
                sliderModifier = Modifier.testTag("slider_settings_thumbnail_frame_position"),
                title = stringResource(R.string.frame_position),
                description = stringResource(R.string.frame_position_value, frameSliderValue),
                icon = NextIcons.Frame,
                value = frameSliderValue,
                valueRange = 0f..100f,
                isFirstItem = true,
                isLastItem = true,
                onValueChange = { frameSliderValue = it },
                onValueChangeFinished = {
                    val newPosition = frameSliderValue / 100f
                    if (abs(newPosition - preferences.thumbnailFramePosition) > 0.0001f) {
                        pendingChange = ThumbnailPreferenceChange.FramePosition(newPosition)
                    }
                },
                trailingContent = {
                    NextResetIconButton(
                        modifier = Modifier.testTag("btn_reset_settings_thumbnail_frame_position"),
                        enabled = preferences.thumbnailGenerationStrategy != ThumbnailGenerationStrategy.FIRST_FRAME,
                        onClick = {
                            val defaultPosition = ApplicationPreferences.DEFAULT_THUMBNAIL_FRAME_POSITION
                            if (abs(defaultPosition - preferences.thumbnailFramePosition) > 0.0001f) {
                                frameSliderValue = defaultPosition * 100f
                                pendingChange = ThumbnailPreferenceChange.FramePosition(defaultPosition)
                            }
                        },
                        contentDescription = stringResource(id = R.string.reset_seek_sensitivity),
                    )
                },
            )
        }

        pendingChange?.let { change ->
            NextDialog(
                onDismissRequest = {
                    pendingChange = null
                    frameSliderValue = preferences.thumbnailFramePosition * 100f
                },
                title = stringResource(id = R.string.thumbnail_generation),
                confirmButton = {
                    TextButton(
                        text = stringResource(id = R.string.okay),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            when (change) {
                                is ThumbnailPreferenceChange.Strategy -> {
                                    onEvent(ThumbnailPreferencesEvent.UpdateStrategy(change.strategy))
                                }
                                is ThumbnailPreferenceChange.FramePosition -> {
                                    onEvent(ThumbnailPreferencesEvent.UpdateFramePosition(change.position))
                                }
                            }
                            pendingChange = null
                        },
                    )
                },
                dismissButton = {
                    CancelButton(
                        onClick = {
                            pendingChange = null
                            frameSliderValue = preferences.thumbnailFramePosition * 100f
                        },
                    )
                },
                content = {
                    Text(
                        text = stringResource(id = R.string.thumbnail_setting_change_confirmation),
                        style = MiuixTheme.textStyles.body1,
                    )
                },
            )
        }
    }
}

private sealed interface ThumbnailPreferenceChange {
    data class Strategy(val strategy: ThumbnailGenerationStrategy) : ThumbnailPreferenceChange
    data class FramePosition(val position: Float) : ThumbnailPreferenceChange
}

@PreviewLightDark
@Composable
private fun ThumbnailPreferencesScreenPreview() {
    OnlyPlayerTheme {
        ThumbnailPreferencesContent(
            uiState = ThumbnailPreferencesUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
