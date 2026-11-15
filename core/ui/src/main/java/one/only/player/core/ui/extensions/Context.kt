package one.only.player.core.ui.extensions

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt
import one.only.player.core.model.AppScale

// 基准 Context 来自系统，Dialog 与 Popup 继承 Activity 的同一密度。
fun scaledAppContext(
    base: Context,
    scale: Float,
): Context {
    val value = AppScale.quantize(scale)
    if (value == AppScale.DEFAULT) return base
    val configuration = Configuration(base.resources.configuration).apply {
        densityDpi = (densityDpi * value).roundToInt()
        screenWidthDp = (screenWidthDp / value).roundToInt()
        screenHeightDp = (screenHeightDp / value).roundToInt()
        smallestScreenWidthDp = (smallestScreenWidthDp / value).roundToInt()
    }
    return base.createConfigurationContext(configuration)
}
