package one.only.player.feature.player.service.effects

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import one.only.player.core.model.PlayerPreferences
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 把播放器滤镜参数转成 Android [ColorMatrixColorFilter]。
 * 必须设在 TextureView 上（SurfaceView 独立图层吃不到 colorFilter）。
 */
fun VideoFilterPreferences.toAndroidColorFilterOrNull(): ColorMatrixColorFilter? {
    if (!shouldCreateEffect()) return null
    return ColorMatrixColorFilter(toAndroidColorMatrix())
}

fun VideoFilterPreferences.toAndroidColorMatrix(): ColorMatrix {
    val matrix = ColorMatrix()
    if (!shouldCreateEffect()) return matrix

    // 对比度：绕 0.5 拉伸（与自定义 shader 一致： (c-0.5)*(1+contrast)+0.5）
    if (isContrastEnabled && contrast != PlayerPreferences.DEFAULT_VIDEO_CONTRAST) {
        val c = (1f + contrast.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST))
            .coerceAtLeast(0.01f)
        val translate = 128f * (1f - c)
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        matrix.postConcat(contrastMatrix)
    }

    // 亮度：RGB 加常量（-1..1 → 约 -255..255）
    if (isBrightnessEnabled && brightness != PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS) {
        val b = brightness.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS) * 255f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        matrix.postConcat(brightnessMatrix)
    }

    // 饱和度：-100..100 → 0..2 倍率（0 灰、1 原色、2 过饱和）
    if (isSaturationEnabled && saturation != PlayerPreferences.DEFAULT_VIDEO_SATURATION) {
        val sat = (1f + saturation.coerceIn(
            PlayerPreferences.MIN_VIDEO_SATURATION,
            PlayerPreferences.MAX_VIDEO_SATURATION,
        ) / 100f).coerceAtLeast(0f)
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(sat)
        matrix.postConcat(satMatrix)
    }

    // 色相：绕亮度轴旋转
    if (isHueEnabled && hue != PlayerPreferences.DEFAULT_VIDEO_HUE) {
        val degrees = hue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE)
        matrix.postConcat(hueRotateMatrix(degrees))
    }

    // 伽马：ColorMatrix 线性，用增益近似 pow(c, 1/gamma) 方向
    if (isGammaEnabled && gamma != PlayerPreferences.DEFAULT_VIDEO_GAMMA) {
        val g = gamma.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA)
        val gain = (1f / g.coerceAtLeast(0.1f)).toDouble().pow(0.5).toFloat().coerceIn(0.3f, 2.5f)
        val gammaMatrix = ColorMatrix(
            floatArrayOf(
                gain, 0f, 0f, 0f, 0f,
                0f, gain, 0f, 0f, 0f,
                0f, 0f, gain, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        matrix.postConcat(gammaMatrix)
    }

    // 锐化无法用 ColorMatrix 可靠实现，忽略
    return matrix
}

private fun hueRotateMatrix(degrees: Float): ColorMatrix {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = cos(rad).toFloat()
    val sin = sin(rad).toFloat()
    // 标准 hue rotation matrix (RGB)
    val a00 = 0.213f + cos * 0.787f - sin * 0.213f
    val a01 = 0.715f - cos * 0.715f - sin * 0.715f
    val a02 = 0.072f - cos * 0.072f + sin * 0.928f
    val a10 = 0.213f - cos * 0.213f + sin * 0.143f
    val a11 = 0.715f + cos * 0.285f + sin * 0.140f
    val a12 = 0.072f - cos * 0.072f - sin * 0.283f
    val a20 = 0.213f - cos * 0.213f - sin * 0.787f
    val a21 = 0.715f - cos * 0.715f + sin * 0.715f
    val a22 = 0.072f + cos * 0.928f + sin * 0.072f
    return ColorMatrix(
        floatArrayOf(
            a00, a01, a02, 0f, 0f,
            a10, a11, a12, 0f, 0f,
            a20, a21, a22, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}
