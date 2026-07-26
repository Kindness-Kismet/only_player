package one.only.player.feature.player.service.effects

import androidx.compose.ui.graphics.Color
import one.only.player.core.model.PlayerPreferences
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * SurfaceView 吃不到真正像素滤镜时，用半透明叠加层做“看得见”的近似。
 *
 * 预期观感（单开一项时）：
 * - 亮度 +：发白变亮；亮度 -：发黑变暗
 * - 对比度 +：更“闷/硬”（加深）；对比度 -：发灰发雾
 * - 饱和度 +：遮罩无法“加浓原色”，仅开饱和时轻微加深；配合色相才偏对应色；饱和度 -：发灰
 * - 色相：整圈变色（0°红 → 60°黄 → 120°绿 → 180°青 → 240°蓝 → 300°品红），不是只会蓝
 * - 伽马 +：偏亮；伽马 -：偏暗
 * - 锐化：极轻白边感（叠层做不到真锐化，只给开关反馈）
 */
data class VideoFilterOverlayStyle(
    val color: Color,
    /** 0..1 */
    val alpha: Float,
)

fun VideoFilterPreferences.toOverlayStyleOrNull(): VideoFilterOverlayStyle? {
    if (!shouldCreateEffect()) return null

    val b = if (isBrightnessEnabled) {
        brightness.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS)
    } else {
        0f
    }
    val c = if (isContrastEnabled) {
        contrast.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST)
    } else {
        0f
    }
    val hueDeg = if (isHueEnabled) {
        hue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE)
    } else {
        0f
    }
    val sat = if (isSaturationEnabled) {
        saturation.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION)
    } else {
        0f
    }
    val g = if (isGammaEnabled) {
        gamma.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA)
    } else {
        PlayerPreferences.DEFAULT_VIDEO_GAMMA
    }
    val sharp = if (isSharpeningEnabled) {
        sharpening.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING)
    } else {
        0f
    }

    // 累加 RGB 加权与 alpha，最后归一
    var rAcc = 0f
    var gAcc = 0f
    var bAcc = 0f
    var wAcc = 0f

    fun add(color: Triple<Float, Float, Float>, weight: Float) {
        if (weight <= 0.001f) return
        rAcc += color.first * weight
        gAcc += color.second * weight
        bAcc += color.third * weight
        wAcc += weight
    }

    // —— 亮度：白/黑 ——
    if (b > 0.01f) {
        add(Triple(1f, 1f, 1f), b * 1.0f)
    } else if (b < -0.01f) {
        add(Triple(0f, 0f, 0f), (-b) * 1.15f)
    }

    // —— 对比度：+ 加深（黑），- 变雾（浅灰）—— 加强权重，避免“几乎看不出”
    if (c > 0.05f) {
        add(Triple(0f, 0f, 0f), c * 0.85f)
    } else if (c < -0.05f) {
        add(Triple(0.72f, 0.72f, 0.72f), (-c) * 0.75f)
    }

    // —— 饱和度 / 色相 ——
    // 真饱和度是“原色更浓/更灰”，不是整屏变红。
    // 遮罩做不到像素级提饱和：+饱和且未开色相 → 不叠彩色（避免误导成偏红）；
    // +饱和且开了色相 → 按色相偏色；-饱和 → 叠灰。
    if (sat < -2f) {
        val k = ((-sat) / 100f).coerceIn(0f, 1f)
        add(Triple(0.5f, 0.5f, 0.5f), k * 0.85f)
    } else if (sat > 2f && isHueEnabled) {
        val hueForSat = ((hueDeg % 360f) + 360f) % 360f
        val k = (sat / 100f).coerceIn(0f, 1f)
        add(hueToRgb(hueForSat), k * 0.9f)
    } else if (sat > 2f && !isHueEnabled) {
        // 仅提饱和、无色相：遮罩无法“加浓原色”，用很轻的暗边/对比感代替，绝不单通道变红
        val k = (sat / 100f).coerceIn(0f, 1f)
        add(Triple(0f, 0f, 0f), k * 0.22f)
    }

    if (isHueEnabled && abs(hueDeg) > 1f && abs(sat) <= 2f) {
        // 只调色相：完整色环偏色（红→黄→绿→青→蓝→品红）
        val hueForOnly = ((hueDeg % 360f) + 360f) % 360f
        add(hueToRgb(hueForOnly), 0.5f)
    }

    // —— 伽马 ——
    if (abs(g - 1f) > 0.05f) {
        if (g > 1f) {
            add(Triple(1f, 1f, 1f), ((g - 1f) / 2f).coerceIn(0f, 1f) * 0.7f)
        } else {
            add(Triple(0f, 0f, 0f), ((1f - g) / 0.9f).coerceIn(0f, 1f) * 0.7f)
        }
    }

    // —— 锐化：叠层做不到真锐化，用很轻的高光闪表示“开了” ——
    if (sharp > 0.05f) {
        add(Triple(1f, 1f, 1f), sharp * 0.12f)
    }

    if (wAcc < 0.02f) return null

    val r = (rAcc / wAcc).coerceIn(0f, 1f)
    val gg = (gAcc / wAcc).coerceIn(0f, 1f)
    val bb = (bAcc / wAcc).coerceIn(0f, 1f)
    // 总强度：单项拉满大约 0.45~0.6，多项叠加封顶 0.72
    val alpha = wAcc.coerceIn(0.08f, 0.72f)

    return VideoFilterOverlayStyle(Color(r, gg, bb), alpha)
}

private fun hueToRgb(h: Float): Triple<Float, Float, Float> {
    // h in [0, 360)
    val hh = ((h % 360f) + 360f) % 360f
    val sector = hh / 60f
    val i = sector.toInt() % 6
    val f = sector - i
    // 全饱和、中等明度的色相色
    return when (i) {
        0 -> Triple(1f, f, 0f) // 红 → 黄
        1 -> Triple(1f - f, 1f, 0f) // 黄 → 绿
        2 -> Triple(0f, 1f, f) // 绿 → 青
        3 -> Triple(0f, 1f - f, 1f) // 青 → 蓝
        4 -> Triple(f, 0f, 1f) // 蓝 → 品红
        else -> Triple(1f, 0f, 1f - f) // 品红 → 红
    }
}
