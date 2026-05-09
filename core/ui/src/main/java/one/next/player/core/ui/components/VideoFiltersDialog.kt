package one.next.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.ui.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoFiltersDialog(
    preferences: PlayerPreferences,
    onDismissRequest: () -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onHueChanged: (Float) -> Unit,
    onGammaChanged: (Float) -> Unit,
    onSharpeningChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    NextDialog(
        modifier = modifier.testTag("dialog_video_filters"),
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.video_filters)) },
        confirmButton = { DoneButton(onClick = onDismissRequest) },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoFilterSlider(
                    title = stringResource(R.string.video_brightness),
                    value = preferences.videoBrightness,
                    valueRange = PlayerPreferences.MIN_VIDEO_BRIGHTNESS..PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
                    valueText = signedPercent(preferences.videoBrightness),
                    testTag = "slider_video_brightness",
                    onValueChange = onBrightnessChanged,
                )
                VideoFilterSlider(
                    title = stringResource(R.string.video_contrast),
                    value = preferences.videoContrast,
                    valueRange = PlayerPreferences.MIN_VIDEO_CONTRAST..PlayerPreferences.MAX_VIDEO_CONTRAST,
                    valueText = signedPercent(preferences.videoContrast),
                    testTag = "slider_video_contrast",
                    onValueChange = onContrastChanged,
                )
                VideoFilterSlider(
                    title = stringResource(R.string.video_saturation),
                    value = preferences.videoSaturation,
                    valueRange = PlayerPreferences.MIN_VIDEO_SATURATION..PlayerPreferences.MAX_VIDEO_SATURATION,
                    valueText = signedInteger(preferences.videoSaturation),
                    testTag = "slider_video_saturation",
                    onValueChange = onSaturationChanged,
                )
                VideoFilterSlider(
                    title = stringResource(R.string.video_hue),
                    value = preferences.videoHue,
                    valueRange = PlayerPreferences.MIN_VIDEO_HUE..PlayerPreferences.MAX_VIDEO_HUE,
                    valueText = stringResource(R.string.degrees, preferences.videoHue.toInt()),
                    testTag = "slider_video_hue",
                    onValueChange = onHueChanged,
                )
                VideoFilterSlider(
                    title = stringResource(R.string.video_gamma),
                    value = preferences.videoGamma,
                    valueRange = PlayerPreferences.MIN_VIDEO_GAMMA..PlayerPreferences.MAX_VIDEO_GAMMA,
                    valueText = String.format("%.2f", preferences.videoGamma),
                    testTag = "slider_video_gamma",
                    onValueChange = onGammaChanged,
                )
                VideoFilterSlider(
                    title = stringResource(R.string.video_sharpening),
                    value = preferences.videoSharpening,
                    valueRange = PlayerPreferences.DEFAULT_VIDEO_SHARPENING..PlayerPreferences.MAX_VIDEO_SHARPENING,
                    valueText = stringResource(R.string.percent, (preferences.videoSharpening * 100).toInt()),
                    testTag = "slider_video_sharpening",
                    onValueChange = onSharpeningChanged,
                )
            }
        },
    )
}

@Composable
private fun VideoFilterSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    testTag: String,
    onValueChange: (Float) -> Unit,
) {
    PreferenceSlider(
        modifier = Modifier.testTag(testTag),
        title = title,
        description = valueText,
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
    )
}

private fun signedPercent(value: Float): String {
    val percent = (value * 100).toInt()
    return if (percent > 0) "+$percent%" else "$percent%"
}

private fun signedInteger(value: Float): String {
    val rounded = value.toInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
}
