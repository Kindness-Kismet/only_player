package one.next.player.feature.player.service

import androidx.media3.exoplayer.DefaultRenderersFactory
import one.next.player.core.model.DecoderPriority

internal fun DecoderPriority.extensionRendererMode(): Int = when (this) {
    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
}

internal fun DecoderPriority.shouldUseAudioExtensionFallback(): Boolean = this == DecoderPriority.PREFER_DEVICE
