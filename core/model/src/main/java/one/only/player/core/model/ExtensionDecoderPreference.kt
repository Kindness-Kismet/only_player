package one.only.player.core.model

import kotlinx.serialization.Serializable

/**
 * 按文件扩展名配置默认解码方式。
 * [extension] 不带点，统一小写，例如 "mp4"。
 */
@Serializable
data class ExtensionDecoderPreference(
    val extension: String,
    val decoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,
    val isBuiltIn: Boolean = false,
) {
    fun normalized(): ExtensionDecoderPreference = copy(
        extension = extension.trim().removePrefix(".").lowercase(),
    )

    companion object {
        val DEFAULT_EXTENSIONS: List<String> = listOf(
            "3gp",
            "asf",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "mts",
            "rmvb",
            "ts",
            "webm",
            "wmv",
        )

        fun defaults(): List<ExtensionDecoderPreference> = DEFAULT_EXTENSIONS.map { extension ->
            ExtensionDecoderPreference(
                extension = extension,
                // 默认：自动检测（硬件优先）
                decoderPriority = DecoderPriority.AUTOMATIC,
                isBuiltIn = true,
            )
        }

        fun normalizeExtension(raw: String): String = raw.trim().removePrefix(".").lowercase()
    }
}

fun List<ExtensionDecoderPreference>.normalized(): List<ExtensionDecoderPreference> {
    val seen = linkedSetOf<String>()
    val result = mutableListOf<ExtensionDecoderPreference>()
    for (item in this) {
        val normalized = item.normalized()
        if (normalized.extension.isBlank()) continue
        if (!seen.add(normalized.extension)) continue
        result += normalized
    }
    // 允许空列表：用户可删除全部内置扩展名；新装默认值在 ApplicationPreferences 字段上
    return result
}

fun List<ExtensionDecoderPreference>.decoderPriorityForExtension(extension: String): DecoderPriority? {
    val key = ExtensionDecoderPreference.normalizeExtension(extension)
    if (key.isBlank()) return null
    return firstOrNull { it.extension == key }?.decoderPriority
}

fun List<ExtensionDecoderPreference>.decoderPriorityForPath(path: String): DecoderPriority? {
    val extension = path.substringAfterLast('.', missingDelimiterValue = "")
    return decoderPriorityForExtension(extension)
}

fun List<ExtensionDecoderPreference>.knownExtensions(): Set<String> =
    map { it.extension }.filter { it.isNotBlank() }.toSet()
