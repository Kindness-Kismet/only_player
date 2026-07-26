package one.only.player.feature.player.service.effects

import one.only.player.core.model.PlayerPreferences

data class VideoFilterPreferences(
    val shouldApply: Boolean,
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
) {
    fun interpolateTo(
        target: VideoFilterPreferences,
        fraction: Float,
    ): VideoFilterPreferences {
        if (fraction >= 1f) return target

        return VideoFilterPreferences(
            shouldApply = shouldApply || target.shouldApply,
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
        )
    }

    fun shouldCreateEffect(): Boolean {
        if (!shouldApply) return false
        // 显式括号，避免 && / || 优先级误判导致该建 effect 却判定为 false
        val brightnessActive =
            isBrightnessEnabled && brightness != PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS
        val contrastActive =
            isContrastEnabled && contrast != PlayerPreferences.DEFAULT_VIDEO_CONTRAST
        val saturationActive =
            isSaturationEnabled && saturation != PlayerPreferences.DEFAULT_VIDEO_SATURATION
        val hueActive = isHueEnabled && hue != PlayerPreferences.DEFAULT_VIDEO_HUE
        val gammaActive = isGammaEnabled && gamma != PlayerPreferences.DEFAULT_VIDEO_GAMMA
        val sharpeningActive =
            isSharpeningEnabled && sharpening != PlayerPreferences.DEFAULT_VIDEO_SHARPENING
        return brightnessActive ||
            contrastActive ||
            saturationActive ||
            hueActive ||
            gammaActive ||
            sharpeningActive
    }

    companion object {
        fun default(): VideoFilterPreferences = VideoFilterPreferences(
            shouldApply = false,
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
