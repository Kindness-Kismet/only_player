package one.only.player.core.model

import kotlinx.serialization.Serializable

/**
 * 按文件名（不含路径）记住的播放配置：解码方式 / 启动方向 / 视频缩放。
 * 权威数据在 app_preferences.json；files/data/per_file_playback_preferences.json 为可读镜像。
 */
@Serializable
data class PerFilePlaybackPreference(
    val fileName: String,
    val decoderPriority: DecoderPriority? = null,
    val screenOrientation: LastPlayerScreenOrientation? = null,
    val videoContentScale: VideoContentScale? = null,
) {
    fun normalized(): PerFilePlaybackPreference = copy(
        fileName = normalizeFileName(fileName),
    )

    companion object {
        fun normalizeFileName(raw: String): String {
            val name = raw
                .trim()
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .substringBefore('?')
                .substringBefore('#')
            return name
        }

        fun fromPathOrName(pathOrName: String?): String? {
            if (pathOrName.isNullOrBlank()) return null
            val name = normalizeFileName(pathOrName)
            return name.takeIf { it.isNotBlank() }
        }
    }
}

fun List<PerFilePlaybackPreference>.normalized(): List<PerFilePlaybackPreference> {
    val seen = linkedSetOf<String>()
    val result = mutableListOf<PerFilePlaybackPreference>()
    for (item in this) {
        val normalized = item.normalized()
        if (normalized.fileName.isBlank()) continue
        if (!seen.add(normalized.fileName)) continue
        // 至少有一项配置才保留
        if (
            normalized.decoderPriority == null &&
            normalized.screenOrientation == null &&
            normalized.videoContentScale == null
        ) {
            continue
        }
        result += normalized
    }
    return result
}

fun List<PerFilePlaybackPreference>.forFileName(fileName: String?): PerFilePlaybackPreference? {
    val key = PerFilePlaybackPreference.fromPathOrName(fileName) ?: return null
    return firstOrNull { it.fileName.equals(key, ignoreCase = true) }
}

fun List<PerFilePlaybackPreference>.upsert(preference: PerFilePlaybackPreference): List<PerFilePlaybackPreference> {
    val normalized = preference.normalized()
    if (normalized.fileName.isBlank()) return this.normalized()
    val without = filterNot { it.fileName.equals(normalized.fileName, ignoreCase = true) }
    if (
        normalized.decoderPriority == null &&
        normalized.screenOrientation == null &&
        normalized.videoContentScale == null
    ) {
        return without.normalized()
    }
    return (without + normalized).normalized()
}

fun List<PerFilePlaybackPreference>.removeByFileNames(fileNames: Collection<String>): List<PerFilePlaybackPreference> {
    val keys = fileNames.mapNotNull(PerFilePlaybackPreference::fromPathOrName)
        .map { it.lowercase() }
        .toSet()
    if (keys.isEmpty()) return this
    return filterNot { it.fileName.lowercase() in keys }.normalized()
}
