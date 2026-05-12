package one.next.player.feature.player.service

import one.next.player.core.model.PlayerPreferences

data class VideoFilterPreferences(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val hue: Float,
    val gamma: Float,
    val sharpening: Float,
) {
    fun interpolateTo(
        target: VideoFilterPreferences,
        fraction: Float,
    ): VideoFilterPreferences = VideoFilterPreferences(
        brightness = brightness.interpolate(target.brightness, fraction),
        contrast = contrast.interpolate(target.contrast, fraction),
        saturation = saturation.interpolate(target.saturation, fraction),
        hue = hue.interpolate(target.hue, fraction),
        gamma = gamma.interpolate(target.gamma, fraction),
        sharpening = sharpening.interpolate(target.sharpening, fraction),
    )

    companion object {
        fun default(): VideoFilterPreferences = VideoFilterPreferences(
            brightness = PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS,
            contrast = PlayerPreferences.DEFAULT_VIDEO_CONTRAST,
            saturation = PlayerPreferences.DEFAULT_VIDEO_SATURATION,
            hue = PlayerPreferences.DEFAULT_VIDEO_HUE,
            gamma = PlayerPreferences.DEFAULT_VIDEO_GAMMA,
            sharpening = PlayerPreferences.DEFAULT_VIDEO_SHARPENING,
        )
    }
}

internal fun Float.interpolate(
    target: Float,
    fraction: Float,
): Float = this + (target - this) * fraction
