package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubtitleColor {
    WHITE,
    YELLOW,
    CYAN,
    GREEN,
}

@Serializable
enum class SubtitleEdgeStyle {
    NONE,
    OUTLINE,
    DROP_SHADOW,
    OUTLINE_AND_DROP_SHADOW,
}
