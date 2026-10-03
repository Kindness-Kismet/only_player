package one.only.player.core.model

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

enum class MediaLayoutTarget {
    FOLDERS,
    VIDEOS,
}

@Serializable
data class MediaItemLayout(
    val mode: MediaLayoutMode = MediaLayoutMode.LIST,
    val scale: Float = ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE,
) {
    fun normalized(): MediaItemLayout = copy(
        scale = (
            scale.coerceIn(
                ApplicationPreferences.MIN_MEDIA_LAYOUT_SCALE,
                ApplicationPreferences.MAX_MEDIA_LAYOUT_SCALE,
            ) / ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP
            ).roundToInt() * ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP,
    )
}

// 空值表示继承；与父目录相同的显式设置仍然保留。
@Serializable
data class MediaLayoutOverrides(
    val folders: MediaItemLayout? = null,
    val videos: MediaItemLayout? = null,
) {
    fun withLayout(
        target: MediaLayoutTarget,
        layout: MediaItemLayout?,
    ): MediaLayoutOverrides = when (target) {
        MediaLayoutTarget.FOLDERS -> copy(folders = layout?.normalized())
        MediaLayoutTarget.VIDEOS -> copy(videos = layout?.normalized())
    }
}

data class ResolvedMediaLayout(
    val layout: MediaItemLayout,
    val sourcePath: String? = null,
)

data class ResolvedMediaLayouts(
    val folders: ResolvedMediaLayout,
    val videos: ResolvedMediaLayout,
) {
    operator fun get(target: MediaLayoutTarget): ResolvedMediaLayout = when (target) {
        MediaLayoutTarget.FOLDERS -> folders
        MediaLayoutTarget.VIDEOS -> videos
    }
}

fun ApplicationPreferences.resolveMediaLayouts(directory: StoragePath? = null): ResolvedMediaLayouts = resolveMediaLayouts(
    folderDefault = MediaItemLayout(folderLayoutMode, folderLayoutScale),
    videoDefault = MediaItemLayout(videoLayoutMode, videoLayoutScale),
    ancestors = generateSequence(directory) { it.parent }.map { it.value to directoryLayouts[it] },
)

fun ApplicationPreferences.withMediaLayout(
    directory: StoragePath?,
    target: MediaLayoutTarget,
    layout: MediaItemLayout?,
): ApplicationPreferences {
    if (directory != null) {
        return copy(directoryLayouts = directoryLayouts.withLayout(directory, target, layout))
    }
    val value = layout?.normalized() ?: return this
    return when (target) {
        MediaLayoutTarget.FOLDERS -> copy(folderLayoutMode = value.mode, folderLayoutScale = value.scale)
        MediaLayoutTarget.VIDEOS -> copy(videoLayoutMode = value.mode, videoLayoutScale = value.scale)
    }
}

fun CloudQuickSettings.resolveMediaLayouts(directory: String? = null): ResolvedMediaLayouts = resolveMediaLayouts(
    folderDefault = MediaItemLayout(folderLayoutMode, folderLayoutScale),
    videoDefault = MediaItemLayout(videoLayoutMode, videoLayoutScale),
    ancestors = generateSequence(directory?.trimEnd('/')?.ifEmpty { "/" }) { path ->
        path.takeUnless { it == "/" }?.substringBeforeLast('/', "")?.ifEmpty { "/" }
    }.map { it to directoryLayouts[it] },
)

fun CloudQuickSettings.withMediaLayout(
    directory: String?,
    target: MediaLayoutTarget,
    layout: MediaItemLayout?,
): CloudQuickSettings {
    if (directory != null) {
        val key = directory.trimEnd('/').ifEmpty { "/" }
        return copy(directoryLayouts = directoryLayouts.withLayout(key, target, layout))
    }
    val value = layout?.normalized() ?: return this
    return when (target) {
        MediaLayoutTarget.FOLDERS -> copy(folderLayoutMode = value.mode, folderLayoutScale = value.scale)
        MediaLayoutTarget.VIDEOS -> copy(videoLayoutMode = value.mode, videoLayoutScale = value.scale)
    }
}

private fun <T> Map<T, MediaLayoutOverrides>.withLayout(
    directory: T,
    target: MediaLayoutTarget,
    layout: MediaItemLayout?,
): Map<T, MediaLayoutOverrides> {
    val updated = (this[directory] ?: MediaLayoutOverrides()).withLayout(target, layout)
    return if (updated.folders == null && updated.videos == null) this - directory else this + (directory to updated)
}

private fun resolveMediaLayouts(
    folderDefault: MediaItemLayout,
    videoDefault: MediaItemLayout,
    ancestors: Sequence<Pair<String, MediaLayoutOverrides?>>,
): ResolvedMediaLayouts {
    var folders: ResolvedMediaLayout? = null
    var videos: ResolvedMediaLayout? = null
    for ((path, overrides) in ancestors) {
        if (folders == null) folders = overrides?.folders?.let { ResolvedMediaLayout(it.normalized(), path) }
        if (videos == null) videos = overrides?.videos?.let { ResolvedMediaLayout(it.normalized(), path) }
        if (folders != null && videos != null) break
    }
    return ResolvedMediaLayouts(
        folders = folders ?: ResolvedMediaLayout(folderDefault.normalized()),
        videos = videos ?: ResolvedMediaLayout(videoDefault.normalized()),
    )
}

fun ApplicationPreferences.moveDirectoryLayouts(
    from: StoragePath,
    to: StoragePath,
): ApplicationPreferences {
    val moved = directoryLayouts.filterKeys { it.isInside(from) }
    if (moved.isEmpty()) return this
    val updated = directoryLayouts - moved.keys
    return copy(
        directoryLayouts = updated + moved.mapKeys { (path, _) ->
            StoragePath.of(to.value + path.value.substring(from.value.length))
        },
    )
}
