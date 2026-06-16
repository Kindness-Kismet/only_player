package one.only.player.debug

import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import one.only.player.core.data.remote.RemoteMediaResolver
import one.only.player.core.data.repository.FavoriteRepository
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PlaybackMarkRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.data.repository.SubtitleFontRepository
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaInfoSynchronizer
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.feature.player.PlayerDebugCommandBridge

internal const val METHOD_PAGE_OPEN = "page.open"
internal const val METHOD_SETTINGS_SET = "settings.set"
internal const val METHOD_SETTINGS_TOGGLE = "settings.toggle"
internal const val METHOD_SETTINGS_ACTION = "settings.action"

internal const val EXTRA_VALUE = "value"
internal const val EXTRA_ENABLED = "enabled"
internal const val EXTRA_DURATION_MS = "duration_ms"
internal const val EXTRA_ID = "id"
internal const val EXTRA_NAME = "name"
internal const val EXTRA_PROTOCOL = "protocol"
internal const val EXTRA_HOST = "host"
internal const val EXTRA_PORT = "port"
internal const val EXTRA_PATH = "path"
internal const val EXTRA_USERNAME = "username"
internal const val EXTRA_PASSWORD = "password"
internal const val EXTRA_PROXY_ENABLED = "proxy_enabled"
internal const val EXTRA_PROXY_HOST = "proxy_host"
internal const val EXTRA_PROXY_PORT = "proxy_port"
internal const val EXTRA_DIRECTORY_PATH = "directory_path"

private const val KEY_OK = "ok"
private const val KEY_MESSAGE = "message"
private const val KEY_COMMAND = "command"
private const val KEY_TARGET = "target"
private const val KEY_VALUE = "value"
private const val KEY_DURATION_MS = "duration_ms"
private const val KEY_POSITION_MS = "position_ms"
private const val KEY_IS_PLAYING = "is_playing"
private const val KEY_MEDIA_ITEM_COUNT = "media_item_count"
private const val KEY_MEDIA_ITEM_INDEX = "media_item_index"
private const val KEY_MEDIA_ID = "media_id"

internal val CLOUD_SERVER_METHODS = setOf(
    "cloud.server.add",
    "cloud.server.update",
    "cloud.server.delete",
    "cloud.server.clear",
    "cloud.server.list",
)

internal val CLOUD_MEDIA_METHODS = setOf(
    "cloud.media.list",
    "cloud.media.open",
)

internal val CLOUD_QUICK_SETTINGS_METHODS = setOf(
    "cloud.quick_settings.get",
    "cloud.quick_settings.set",
)

internal val QUICK_SETTINGS_METHODS = setOf(
    "quick_settings.get",
    "quick_settings.set",
)

internal val FAVORITE_METHODS = setOf(
    "favorite.add",
    "favorite.list",
    "favorite.delete",
    "favorite.move",
    "favorite.clear",
)

internal val MEDIA_METHODS = setOf(
    "media.list",
    "media.open",
    "media.move_to_recycle_bin",
    "media.move_to_folder",
    "media.move_folder_to_folder",
    "media.restore_from_recycle_bin",
    "media.delete_permanently",
    "media.refresh",
    "media.scan_path",
    "media.status",
)

internal val PLAYER_ACTION_METHODS = setOf(
    "player.play",
    "player.pause",
    "player.toggle_play_pause",
    "player.previous",
    "player.next",
    "player.seek_to",
    "player.seek_by",
    "player.long_press_speed",
    "player.stop",
    "player.shuffle",
    "player.loop",
    "player.rotate",
    "player.toggle_ambience",
    "player.toggle_mirror",
    "player.show_controls",
    "player.hide_controls",
    "player.show_playlist",
    "player.show_speed",
    "player.show_audio",
    "player.show_subtitle",
    "player.lock",
    "player.unlock",
    "player.toggle_lock",
    "player.cycle_scale",
    "player.show_scale",
    "player.show_decoder",
    "player.show_video_filters",
    "player.pip",
    "player.screenshot",
    "player.background",
    "player.show_sleep_timer",
    "player.show_marks",
    "player.mark.add",
    "player.mark.list",
    "player.mark.seek",
    "player.mark.delete",
    "player.show_menu",
    "player.menu_back",
    "player.toggle_customize_controls",
    "player.stress_pan_zoom",
    "player.back",
)

internal val PLAYER_GET_METHODS = setOf(
    "player.state",
    "player.position",
    "player.duration",
    "player.cues",
    "player.video_format",
)

internal val UI_PLAYER_ACTIONS = setOf(
    PlayerDebugCommandBridge.ACTION_BACK,
    PlayerDebugCommandBridge.ACTION_ROTATE,
    PlayerDebugCommandBridge.ACTION_TOGGLE_AMBIENCE,
    PlayerDebugCommandBridge.ACTION_TOGGLE_MIRROR,
    PlayerDebugCommandBridge.ACTION_SHOW_CONTROLS,
    PlayerDebugCommandBridge.ACTION_HIDE_CONTROLS,
    PlayerDebugCommandBridge.ACTION_SHOW_PLAYLIST,
    PlayerDebugCommandBridge.ACTION_SHOW_SPEED,
    PlayerDebugCommandBridge.ACTION_SHOW_AUDIO,
    PlayerDebugCommandBridge.ACTION_SHOW_SUBTITLE,
    PlayerDebugCommandBridge.ACTION_LOCK,
    PlayerDebugCommandBridge.ACTION_UNLOCK,
    PlayerDebugCommandBridge.ACTION_TOGGLE_LOCK,
    PlayerDebugCommandBridge.ACTION_CYCLE_SCALE,
    PlayerDebugCommandBridge.ACTION_SHOW_SCALE,
    PlayerDebugCommandBridge.ACTION_SHOW_DECODER,
    PlayerDebugCommandBridge.ACTION_SHOW_VIDEO_FILTERS,
    PlayerDebugCommandBridge.ACTION_PIP,
    PlayerDebugCommandBridge.ACTION_SCREENSHOT,
    PlayerDebugCommandBridge.ACTION_BACKGROUND,
    PlayerDebugCommandBridge.ACTION_SHOW_SLEEP_TIMER,
    PlayerDebugCommandBridge.ACTION_SHOW_MARKS,
    PlayerDebugCommandBridge.ACTION_MARK_ADD,
    PlayerDebugCommandBridge.ACTION_MARK_LIST,
    PlayerDebugCommandBridge.ACTION_MARK_SEEK,
    PlayerDebugCommandBridge.ACTION_MARK_DELETE,
    PlayerDebugCommandBridge.ACTION_SHOW_MENU,
    PlayerDebugCommandBridge.ACTION_MENU_BACK,
    PlayerDebugCommandBridge.ACTION_TOGGLE_CUSTOMIZE_CONTROLS,
    PlayerDebugCommandBridge.ACTION_STRESS_PAN_ZOOM,
)

internal fun debugResult(
    isOk: Boolean,
    message: String,
    command: String? = null,
    target: String? = null,
    value: String? = null,
    durationMs: Long? = null,
    positionMs: Long? = null,
    isPlaying: Boolean? = null,
    mediaItemCount: Int? = null,
    mediaItemIndex: Int? = null,
    mediaId: String? = null,
): Bundle = Bundle().apply {
    putBoolean(KEY_OK, isOk)
    putString(KEY_MESSAGE, message)
    putString(KEY_COMMAND, command)
    putString(KEY_TARGET, target)
    putString(KEY_VALUE, value)
    durationMs?.let { putLong(KEY_DURATION_MS, it) }
    positionMs?.let { putLong(KEY_POSITION_MS, it) }
    isPlaying?.let { putBoolean(KEY_IS_PLAYING, it) }
    mediaItemCount?.let { putInt(KEY_MEDIA_ITEM_COUNT, it) }
    mediaItemIndex?.let { putInt(KEY_MEDIA_ITEM_INDEX, it) }
    mediaId?.let { putString(KEY_MEDIA_ID, it) }
}

internal fun Bundle?.withTarget(target: String?): Bundle = Bundle(this ?: Bundle.EMPTY).apply {
    if (!target.isNullOrBlank() && !containsKey(EXTRA_VALUE)) putString(EXTRA_VALUE, target)
}

internal fun Bundle.requiredTargetLong(fallbackKey: String): Long {
    getString(EXTRA_VALUE)?.toLongOrNull()?.let { return it }
    return requiredLong(fallbackKey)
}

internal fun Bundle.requiredMediaTarget(): String = getString(EXTRA_VALUE)?.takeIf { it.isNotBlank() }
    ?: getString(EXTRA_NAME)?.takeIf { it.isNotBlank() }
    ?: getString(EXTRA_PATH)?.takeIf { it.isNotBlank() }
    ?: error("Missing media target: use arg or value/name/path extra")

internal fun Bundle.optionalFilter(): String? = getString(EXTRA_VALUE)?.takeIf { it.isNotBlank() }
    ?: getString(EXTRA_NAME)?.takeIf { it.isNotBlank() }
    ?: getString(EXTRA_PATH)?.takeIf { it.isNotBlank() }

internal fun Bundle.requiredString(key: String): String = getString(key)?.takeIf { it.isNotBlank() } ?: error("Missing string extra: $key")

internal fun Bundle.requiredBoolean(key: String): Boolean {
    if (!containsKey(key)) error("Missing boolean extra: $key")
    return getBoolean(key)
}

internal fun Bundle.requiredFloat(key: String): Float {
    if (!containsKey(key)) error("Missing float extra: $key")
    return when (val value = rawExtra(key)) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is String -> value.toFloatOrNull() ?: error("Invalid float extra: $key")
        else -> error("Invalid float extra: $key")
    }
}

internal fun Bundle.requiredInt(key: String): Int {
    if (!containsKey(key)) error("Missing int extra: $key")
    return when (val value = rawExtra(key)) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.toIntOrNull() ?: error("Invalid int extra: $key")
        else -> error("Invalid int extra: $key")
    }
}

internal fun Bundle.optionalInt(key: String): Int? {
    if (!containsKey(key)) return null
    return when (val value = rawExtra(key)) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.toIntOrNull() ?: error("Invalid int extra: $key")
        else -> error("Invalid int extra: $key")
    }
}

internal fun Bundle.requiredLong(key: String): Long {
    if (!containsKey(key)) error("Missing long extra: $key")
    return optionalLong(key) ?: error("Invalid long extra: $key")
}

internal fun Bundle.optionalLong(key: String): Long? {
    if (!containsKey(key)) return null
    return when (val value = rawExtra(key)) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.toLongOrNull() ?: error("Invalid long extra: $key")
        else -> error("Invalid long extra: $key")
    }
}

internal fun Bundle.requiredLongMillis(key: String): Long {
    if (!containsKey(key)) error("Missing time extra: $key")
    return when (val value = rawExtra(key)) {
        is Long -> value
        is Int -> value.toLong()
        is String -> value.parseTimeMillisOrNull() ?: error("Invalid time extra: $key")
        else -> error("Invalid time extra: $key")
    }
}

internal fun Bundle.debugValue(): String? = when {
    containsKey(EXTRA_VALUE) -> rawExtra(EXTRA_VALUE)?.toString()
    containsKey(EXTRA_ENABLED) -> getBoolean(EXTRA_ENABLED).toString()
    else -> null
}

internal inline fun <reified T : Enum<T>> enumValue(rawValue: String): T {
    val normalizedValue = rawValue.trim().replace('-', '_').uppercase()
    return enumValues<T>().firstOrNull { it.name == normalizedValue } ?: error("Unknown ${T::class.simpleName}: $rawValue")
}

@Suppress("DEPRECATION")
private fun Bundle.rawExtra(key: String): Any? = get(key)

private fun String.parseTimeMillisOrNull(): Long? {
    val rawValue = trim()
    rawValue.toLongOrNull()?.let { return it }
    val unit = rawValue.takeLastWhile { it.isLetter() }.lowercase()
    val number = rawValue.dropLast(unit.length).toLongOrNull() ?: return null
    return when (unit) {
        "ms" -> number
        "s" -> number * 1_000L
        "m" -> number * 60_000L
        else -> null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugCommandEntryPoint {
    fun preferencesRepository(): PreferencesRepository

    fun remoteServerRepository(): RemoteServerRepository

    fun mediaRepository(): MediaRepository

    fun favoriteRepository(): FavoriteRepository

    fun playbackMarkRepository(): PlaybackMarkRepository

    fun remoteMediaResolver(): RemoteMediaResolver

    fun mediaService(): MediaService

    fun mediaSynchronizer(): MediaSynchronizer

    fun mediaInfoSynchronizer(): MediaInfoSynchronizer

    fun subtitleFontRepository(): SubtitleFontRepository
}
