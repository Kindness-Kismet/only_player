package one.only.player.navigation

internal const val DEBUG_ACTION_OPEN_PAGE = "one.only.player.debug.OPEN_PAGE"
internal const val DEBUG_ACTION_OPEN_PLAYER = "one.only.player.debug.OPEN_PLAYER"
internal const val DEBUG_EXTRA_PAGE = "page"

// Debug 指令的页面白名单，避免外部输入直接拼接路由。
internal enum class DebugPageRoute(val id: String) {
    HOME("home"),
    SEARCH("search"),
    RECYCLE_BIN("recycle_bin"),
    FAVORITES("favorites"),
    CLOUD("cloud"),
    SETTINGS("settings"),
    SETTINGS_APPEARANCE("settings.appearance"),
    SETTINGS_MEDIA_LIBRARY("settings.media_library"),
    SETTINGS_FOLDERS("settings.folders"),
    SETTINGS_THUMBNAILS("settings.thumbnails"),
    SETTINGS_PLAYER("settings.player"),
    SETTINGS_GESTURES("settings.gestures"),
    SETTINGS_DECODER("settings.decoder"),
    SETTINGS_AUDIO("settings.audio"),
    SETTINGS_SUBTITLE("settings.subtitle"),
    SETTINGS_PRIVACY("settings.privacy"),
    SETTINGS_GENERAL("settings.general"),
    SETTINGS_ABOUT("settings.about"),
    SETTINGS_LIBRARIES("settings.libraries"),
    SETTINGS_LOGS("settings.logs"),
    ;

    companion object {
        fun from(rawValue: String?): DebugPageRoute? {
            val normalizedValue = rawValue
                ?.trim()
                ?.lowercase()
                ?.replace('-', '_')
                ?: return null

            return entries.firstOrNull { it.id == normalizedValue || it.name.lowercase() == normalizedValue }
        }
    }
}
