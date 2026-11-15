package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import one.only.player.core.model.AppScale
import one.only.player.core.ui.R
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
fun AppScalePreference(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(scale) { mutableFloatStateOf(AppScale.quantize(scale)) }
    var committed by remember(scale) { mutableFloatStateOf(AppScale.quantize(scale)) }
    var hasPointerGesture by remember { mutableStateOf(false) }

    fun commit() {
        if (draft == committed) return
        committed = draft
        onScaleChange(draft)
    }

    val pointerObserver = Modifier.pointerInput(Unit) {
        try {
            awaitPointerEventScope {
                while (true) {
                    hasPointerGesture = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                }
            }
        } finally {
            hasPointerGesture = false
        }
    }
    val valueText = if (draft == AppScale.DEFAULT) {
        stringResource(R.string.default_name)
    } else {
        String.format(Locale.ROOT, "%.2f", draft).trimEnd('0').trimEnd('.') + "x"
    }
    SliderPreference(
        title = stringResource(R.string.app_scale),
        value = draft,
        valueText = valueText,
        valueRange = AppScale.MIN..AppScale.MAX,
        steps = AppScale.STEPS,
        modifier = modifier.squircleClip(16.dp).then(pointerObserver).testTag("app_scale"),
        onValueChange = {
            draft = AppScale.quantize(it)
            if (!hasPointerGesture) commit()
        },
        onValueChangeFinished = { commit() },
    )
}
