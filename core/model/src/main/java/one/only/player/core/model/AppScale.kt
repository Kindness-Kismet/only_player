package one.only.player.core.model

import kotlin.math.roundToInt

object AppScale {
    const val DEFAULT = 1f
    const val MIN = 0.8f
    const val MAX = 1.2f
    const val STEP = 0.05f
    const val STEPS = 7

    fun quantize(value: Float): Float {
        val index = ((value.coerceIn(MIN, MAX) - MIN) / STEP).roundToInt()
        return (80 + index * 5) / 100f
    }
}
