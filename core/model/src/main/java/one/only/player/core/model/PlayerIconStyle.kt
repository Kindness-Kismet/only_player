package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerIconStyle {
    TONAL,
    /** 已移除「经典」；旧配置反序列化时映射为 [TONAL]。 */
    @Deprecated("Removed classic player icon style", replaceWith = ReplaceWith("TONAL"))
    CLASSIC,
    TRANSLUCENT,
    TRANSPARENT,
}

fun PlayerIconStyle.normalized(): PlayerIconStyle = when (this) {
    PlayerIconStyle.CLASSIC -> PlayerIconStyle.TONAL
    PlayerIconStyle.TONAL,
    PlayerIconStyle.TRANSLUCENT,
    PlayerIconStyle.TRANSPARENT,
    -> this
}
