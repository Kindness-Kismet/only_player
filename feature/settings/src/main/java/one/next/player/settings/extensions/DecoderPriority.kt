package one.next.player.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import one.next.player.core.model.DecoderPriority
import one.next.player.core.ui.R

@Composable
fun DecoderPriority.name(): String {
    val stringRes = when (this) {
        DecoderPriority.AUTOMATIC -> R.string.auto_hw_decoder
        DecoderPriority.AUTOMATIC_PREFER_DEVICE -> R.string.auto_hw_plus_decoder
        DecoderPriority.DEVICE_ONLY -> R.string.hw_decoder
        DecoderPriority.PREFER_DEVICE -> R.string.hw_plus_decoder
        DecoderPriority.PREFER_APP -> R.string.sw_decoder
    }

    return stringResource(id = stringRes)
}
