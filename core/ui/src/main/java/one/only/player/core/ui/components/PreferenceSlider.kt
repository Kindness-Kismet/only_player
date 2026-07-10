package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceSlider(
    modifier: Modifier = Modifier,
    sliderModifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isSliderEnabled: Boolean = isEnabled,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        SliderPreference(
            value = value,
            onValueChange = onValueChange,
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            endActions = { trailingContent() },
            enabled = isEnabled && isSliderEnabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}
