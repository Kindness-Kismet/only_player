package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import java.util.Locale
import kotlin.math.roundToInt
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.SubtitleColor
import one.only.player.core.model.SubtitleEdgeStyle
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons

@Composable
fun SubtitleStylePanel(
    preferences: PlayerPreferences,
    onPreferencesChange: (PlayerPreferences) -> Unit,
) {
    val isEnabled = preferences.shouldUseSystemCaptionStyle.not()
    val subtitleBottomPaddingFraction = preferences.subtitleBottomPaddingFraction
        .coerceIn(SUBTITLE_POSITION_RANGE)
        .roundToStep(PlayerPreferences.SUBTITLE_BOTTOM_PADDING_FRACTION_STEP)
    Column(
        verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
    ) {
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_subtitle_bold"),
            title = stringResource(id = R.string.subtitle_text_bold),
            description = stringResource(id = R.string.subtitle_text_bold_desc),
            icon = NextIcons.Bold,
            isEnabled = isEnabled,
            isChecked = preferences.shouldUseBoldSubtitleText,
            onClick = { onPreferencesChange(preferences.copy(shouldUseBoldSubtitleText = !preferences.shouldUseBoldSubtitleText)) },
            isFirstItem = true,
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_subtitle_size"),
            sliderModifier = Modifier.testTag("slider_settings_subtitle_size"),
            title = stringResource(id = R.string.subtitle_text_size),
            description = preferences.subtitleTextSize.toDisplayText(),
            icon = NextIcons.FontSize,
            isEnabled = isEnabled,
            value = preferences.subtitleTextSize,
            valueRange = SUBTITLE_TEXT_SIZE_RANGE,
            onValueChange = { onPreferencesChange(preferences.copy(subtitleTextSize = it.roundToStep(PlayerPreferences.SUBTITLE_TEXT_SIZE_STEP))) },
            trailingContent = {
                NextResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_subtitle_size"),
                    enabled = isEnabled,
                    contentDescription = stringResource(id = R.string.reset_subtitle_text_size),
                    onClick = {
                        onPreferencesChange(
                            preferences.copy(subtitleTextSize = PlayerPreferences.DEFAULT_SUBTITLE_TEXT_SIZE),
                        )
                    },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_subtitle_bottom_padding"),
            sliderModifier = Modifier.testTag("slider_settings_subtitle_bottom_padding"),
            title = stringResource(id = R.string.subtitle_position),
            description = subtitleBottomPaddingFraction.toSubtitlePositionDisplayText(),
            icon = NextIcons.Length,
            isEnabled = isEnabled,
            value = subtitleBottomPaddingFraction,
            valueRange = SUBTITLE_POSITION_RANGE,
            onValueChange = {
                onPreferencesChange(
                    preferences.copy(
                        subtitleBottomPaddingFraction = it
                            .coerceIn(SUBTITLE_POSITION_RANGE)
                            .roundToStep(PlayerPreferences.SUBTITLE_BOTTOM_PADDING_FRACTION_STEP),
                    ),
                )
            },
            trailingContent = {
                NextResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_subtitle_bottom_padding"),
                    enabled = isEnabled,
                    contentDescription = stringResource(id = R.string.reset_subtitle_position),
                    onClick = {
                        onPreferencesChange(
                            preferences.copy(
                                subtitleBottomPaddingFraction = PlayerPreferences.DEFAULT_SUBTITLE_BOTTOM_PADDING_FRACTION,
                            ),
                        )
                    },
                )
            },
        )
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_subtitle_background"),
            title = stringResource(id = R.string.subtitle_background),
            description = stringResource(id = R.string.subtitle_background_desc),
            icon = NextIcons.Background,
            isEnabled = isEnabled,
            isChecked = preferences.shouldShowSubtitleBackground,
            onClick = { onPreferencesChange(preferences.copy(shouldShowSubtitleBackground = !preferences.shouldShowSubtitleBackground)) },
        )
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_subtitle_color"),
            title = stringResource(id = R.string.subtitle_text_color),
            description = preferences.subtitleColor.displayName(),
            icon = NextIcons.Appearance,
            isEnabled = isEnabled,
            onClick = { onPreferencesChange(preferences.copy(subtitleColor = preferences.subtitleColor.next())) },
        )
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_subtitle_edge_style"),
            title = stringResource(id = R.string.subtitle_edge_style),
            description = preferences.subtitleEdgeStyle.displayName(),
            icon = NextIcons.Style,
            isEnabled = isEnabled,
            onClick = { onPreferencesChange(preferences.copy(subtitleEdgeStyle = preferences.subtitleEdgeStyle.next())) },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_subtitle_outline_thickness"),
            sliderModifier = Modifier.testTag("slider_settings_subtitle_outline_thickness"),
            title = stringResource(id = R.string.subtitle_outline_thickness),
            description = preferences.subtitleOutlineThickness.toString(),
            icon = NextIcons.Style,
            isEnabled = isEnabled,
            value = preferences.subtitleOutlineThickness,
            valueRange = SUBTITLE_OUTLINE_THICKNESS_RANGE,
            onValueChange = { onPreferencesChange(preferences.copy(subtitleOutlineThickness = it)) },
            trailingContent = {
                NextResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_subtitle_outline_thickness"),
                    enabled = isEnabled,
                    contentDescription = stringResource(id = R.string.reset_subtitle_outline_thickness),
                    onClick = {
                        onPreferencesChange(
                            preferences.copy(subtitleOutlineThickness = PlayerPreferences.DEFAULT_SUBTITLE_OUTLINE_THICKNESS),
                        )
                    },
                )
            },
        )
        PreferenceSlider(
            modifier = Modifier.testTag("item_settings_subtitle_shadow_strength"),
            sliderModifier = Modifier.testTag("slider_settings_subtitle_shadow_strength"),
            title = stringResource(id = R.string.subtitle_shadow_strength),
            description = preferences.subtitleShadowStrength.toString(),
            icon = NextIcons.Style,
            isEnabled = isEnabled,
            value = preferences.subtitleShadowStrength,
            valueRange = SUBTITLE_SHADOW_STRENGTH_RANGE,
            onValueChange = { onPreferencesChange(preferences.copy(subtitleShadowStrength = it)) },
            isLastItem = true,
            trailingContent = {
                NextResetIconButton(
                    modifier = Modifier.testTag("btn_reset_settings_subtitle_shadow_strength"),
                    enabled = isEnabled,
                    contentDescription = stringResource(id = R.string.reset_subtitle_shadow_strength),
                    onClick = {
                        onPreferencesChange(
                            preferences.copy(subtitleShadowStrength = PlayerPreferences.DEFAULT_SUBTITLE_SHADOW_STRENGTH),
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun SubtitleColor.displayName(): String = when (this) {
    SubtitleColor.WHITE -> stringResource(R.string.subtitle_color_white)
    SubtitleColor.YELLOW -> stringResource(R.string.subtitle_color_yellow)
    SubtitleColor.CYAN -> stringResource(R.string.subtitle_color_cyan)
    SubtitleColor.GREEN -> stringResource(R.string.subtitle_color_green)
}

private fun SubtitleColor.next(): SubtitleColor = when (this) {
    SubtitleColor.WHITE -> SubtitleColor.YELLOW
    SubtitleColor.YELLOW -> SubtitleColor.CYAN
    SubtitleColor.CYAN -> SubtitleColor.GREEN
    SubtitleColor.GREEN -> SubtitleColor.WHITE
}

@Composable
private fun SubtitleEdgeStyle.displayName(): String = when (this) {
    SubtitleEdgeStyle.NONE -> stringResource(R.string.subtitle_edge_none)
    SubtitleEdgeStyle.OUTLINE -> stringResource(R.string.subtitle_edge_outline)
    SubtitleEdgeStyle.DROP_SHADOW -> stringResource(R.string.subtitle_edge_shadow)
    SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW -> stringResource(R.string.subtitle_edge_outline_shadow)
}

private fun SubtitleEdgeStyle.next(): SubtitleEdgeStyle = when (this) {
    SubtitleEdgeStyle.NONE -> SubtitleEdgeStyle.OUTLINE
    SubtitleEdgeStyle.OUTLINE -> SubtitleEdgeStyle.DROP_SHADOW
    SubtitleEdgeStyle.DROP_SHADOW -> SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW
    SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW -> SubtitleEdgeStyle.NONE
}

private fun Float.toDisplayText(): String = String.format(Locale.US, "%.1f", this).removeSuffix(".0")

private fun Float.toSubtitlePositionDisplayText(): String = String.format(Locale.US, "%.1f%%", this * 100)

private fun Float.roundToStep(step: Float): Float {
    val scale = (1f / step).roundToInt().toFloat()
    return (this * scale).roundToInt() / scale
}

private val SUBTITLE_TEXT_SIZE_RANGE = PlayerPreferences.MIN_SUBTITLE_TEXT_SIZE..PlayerPreferences.MAX_SUBTITLE_TEXT_SIZE
private val SUBTITLE_OUTLINE_THICKNESS_RANGE = PlayerPreferences.MIN_SUBTITLE_OUTLINE_THICKNESS..PlayerPreferences.MAX_SUBTITLE_OUTLINE_THICKNESS
private val SUBTITLE_SHADOW_STRENGTH_RANGE = PlayerPreferences.MIN_SUBTITLE_SHADOW_STRENGTH..PlayerPreferences.MAX_SUBTITLE_SHADOW_STRENGTH
private val SUBTITLE_POSITION_RANGE = PlayerPreferences.MIN_SUBTITLE_BOTTOM_PADDING_FRACTION..PlayerPreferences.MAX_SUBTITLE_BOTTOM_PADDING_FRACTION
