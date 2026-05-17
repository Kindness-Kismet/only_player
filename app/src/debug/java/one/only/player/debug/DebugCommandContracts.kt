package one.only.player.debug

import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import one.only.player.core.data.repository.MediaRepository
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

internal val MEDIA_METHODS = setOf(
    "media.list",
    "media.move_to_recycle_bin",
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
    "player.toggle_customize_controls",
    "player.back",
)

internal val PLAYER_GET_METHODS = setOf(
    "player.state",
    "player.position",
    "player.duration",
)

internal val UI_PLAYER_ACTIONS = setOf(
    PlayerDebugCommandBridge.ACTION_BACK,
    PlayerDebugCommandBridge.ACTION_ROTATE,
    PlayerDebugCommandBridge.ACTION_TOGGLE_AMBIENCE,
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
    PlayerDebugCommandBridge.ACTION_TOGGLE_CUSTOMIZE_CONTROLS,
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
    getString(key)?.let { return it.toFloatOrNull() ?: error("Invalid float extra: $key") }
    return getFloat(key)
}

internal fun Bundle.requiredInt(key: String): Int {
    if (!containsKey(key)) error("Missing int extra: $key")
    getString(key)?.let { return it.toIntOrNull() ?: error("Invalid int extra: $key") }
    return getInt(key)
}

internal fun Bundle.optionalInt(key: String): Int? {
    if (!containsKey(key)) return null
    getString(key)?.let { return it.toIntOrNull() ?: error("Invalid int extra: $key") }
    return getInt(key)
}

internal fun Bundle.requiredLong(key: String): Long {
    if (!containsKey(key)) error("Missing long extra: $key")
    getString(key)?.let { return it.toLongOrNull() ?: error("Invalid long extra: $key") }
    return getLong(key).takeIf { it != 0L } ?: getInt(key).toLong()
}

internal fun Bundle.requiredLongMillis(key: String): Long {
    if (!containsKey(key)) error("Missing time extra: $key")
    getString(key)?.let { return it.parseTimeMillisOrNull() ?: error("Invalid time extra: $key") }
    return getLong(key).takeIf { it != 0L } ?: getInt(key).toLong()
}

internal fun Bundle.debugValue(): String? = when {
    containsKey(EXTRA_VALUE) -> getString(EXTRA_VALUE) ?: getInt(EXTRA_VALUE).takeIf { it != 0 }?.toString() ?: getFloat(EXTRA_VALUE).toString()
    containsKey(EXTRA_ENABLED) -> getBoolean(EXTRA_ENABLED).toString()
    else -> null
}

internal inline fun <reified T : Enum<T>> enumValue(rawValue: String): T {
    val normalizedValue = rawValue.trim().replace('-', '_').uppercase()
    return enumValues<T>().firstOrNull { it.name == normalizedValue } ?: error("Unknown ${T::class.simpleName}: $rawValue")
}

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

    fun mediaService(): MediaService

    fun mediaSynchronizer(): MediaSynchronizer

    fun mediaInfoSynchronizer(): MediaInfoSynchronizer

    fun subtitleFontRepository(): SubtitleFontRepository
}
