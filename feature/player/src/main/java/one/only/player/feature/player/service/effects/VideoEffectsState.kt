package one.only.player.feature.player.service.effects

import one.only.player.core.model.DecoderPriority

internal data class VideoEffectsState(
    val filters: VideoFilterPreferences,
    val decoderPriority: DecoderPriority,
    val isAmbientEnabled: Boolean = false,
    val ambientTargetAspectRatio: Float = 0f,
    val isPipelineInitialized: Boolean = false,
)
