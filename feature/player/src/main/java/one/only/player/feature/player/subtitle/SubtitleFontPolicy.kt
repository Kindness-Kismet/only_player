package one.only.player.feature.player.subtitle

enum class SubtitleFontPolicy {
    SystemCaptionStyle,
    ExternalOrFallback,
}

fun decideSubtitleFontPolicy(
    shouldUseSystemCaptionStyle: Boolean,
): SubtitleFontPolicy {
    if (shouldUseSystemCaptionStyle) return SubtitleFontPolicy.SystemCaptionStyle
    return SubtitleFontPolicy.ExternalOrFallback
}
