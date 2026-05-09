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
