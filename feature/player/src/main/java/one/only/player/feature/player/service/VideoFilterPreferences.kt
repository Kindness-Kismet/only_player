package one.only.player.feature.player.service

import one.only.player.core.model.PlayerPreferences

data class VideoFilterPreferences(
    val shouldApply: Boolean,
    val isAmbienceModeEnabled: Boolean,
    val screenAspectRatio: Float,
    val isBrightnessEnabled: Boolean,
    val brightness: Float,
    val isContrastEnabled: Boolean,
    val contrast: Float,
    val isSaturationEnabled: Boolean,
    val saturation: Float,
    val isHueEnabled: Boolean,
    val hue: Float,
    val isGammaEnabled: Boolean,
    val gamma: Float,
    val isSharpeningEnabled: Boolean,
    val sharpening: Float,
    val videoScaleX: Float = 1f,
    val videoScaleY: Float = 1f,
    val videoOffsetX: Float = 0f,
    val videoOffsetY: Float = 0f,
) {
    fun interpolateTo(
        target: VideoFilterPreferences,
        fraction: Float,
    ): VideoFilterPreferences {
        if (fraction >= 1f) return target

        return VideoFilterPreferences(
            shouldApply = shouldApply || target.shouldApply,
            isAmbienceModeEnabled = target.isAmbienceModeEnabled,
            screenAspectRatio = target.screenAspectRatio,
            isBrightnessEnabled = isBrightnessEnabled || target.isBrightnessEnabled,
            brightness = brightness.interpolate(target.brightness, fraction),
            isContrastEnabled = isContrastEnabled || target.isContrastEnabled,
            contrast = contrast.interpolate(target.contrast, fraction),
            isSaturationEnabled = isSaturationEnabled || target.isSaturationEnabled,
            saturation = saturation.interpolate(target.saturation, fraction),
            isHueEnabled = isHueEnabled || target.isHueEnabled,
            hue = hue.interpolate(target.hue, fraction),
            isGammaEnabled = isGammaEnabled || target.isGammaEnabled,
            gamma = gamma.interpolate(target.gamma, fraction),
            isSharpeningEnabled = isSharpeningEnabled || target.isSharpeningEnabled,
            sharpening = sharpening.interpolate(target.sharpening, fraction),
            videoScaleX = videoScaleX.interpolate(target.videoScaleX, fraction),
            videoScaleY = videoScaleY.interpolate(target.videoScaleY, fraction),
            videoOffsetX = videoOffsetX.interpolate(target.videoOffsetX, fraction),
            videoOffsetY = videoOffsetY.interpolate(target.videoOffsetY, fraction),
        )
    }

    fun shouldCreateEffect(): Boolean = (shouldApply &&
        (
            isAmbienceModeEnabled ||
            isBrightnessEnabled &&
                brightness != PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS ||
                isContrastEnabled &&
                contrast != PlayerPreferences.DEFAULT_VIDEO_CONTRAST ||
                isSaturationEnabled &&
                saturation != PlayerPreferences.DEFAULT_VIDEO_SATURATION ||
                isHueEnabled &&
                hue != PlayerPreferences.DEFAULT_VIDEO_HUE ||
                isGammaEnabled &&
                gamma != PlayerPreferences.DEFAULT_VIDEO_GAMMA ||
                isSharpeningEnabled &&
                sharpening != PlayerPreferences.DEFAULT_VIDEO_SHARPENING
            )) || videoScaleX != 1f || videoScaleY != 1f || videoOffsetX != 0f || videoOffsetY != 0f

    companion object {
        fun default(): VideoFilterPreferences = VideoFilterPreferences(
            shouldApply = false,
            isAmbienceModeEnabled = false,
            screenAspectRatio = 0f,
            isBrightnessEnabled = false,
            brightness = PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS,
            isContrastEnabled = false,
            contrast = PlayerPreferences.DEFAULT_VIDEO_CONTRAST,
            isSaturationEnabled = false,
            saturation = PlayerPreferences.DEFAULT_VIDEO_SATURATION,
            isHueEnabled = false,
            hue = PlayerPreferences.DEFAULT_VIDEO_HUE,
            isGammaEnabled = false,
            gamma = PlayerPreferences.DEFAULT_VIDEO_GAMMA,
            isSharpeningEnabled = false,
            sharpening = PlayerPreferences.DEFAULT_VIDEO_SHARPENING,
        )
    }
}

internal fun Float.interpolate(
    target: Float,
    fraction: Float,
): Float = this + (target - this) * fraction
