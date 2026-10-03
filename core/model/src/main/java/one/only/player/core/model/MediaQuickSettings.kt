package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaSortSettings(
    val by: Sort.By = Sort.By.TITLE,
    val order: Sort.Order = Sort.Order.ASCENDING,
) {
    fun toSort(): Sort = Sort(by, order)
}

@Serializable
data class MediaDisplayFields(
    val shouldShowDurationField: Boolean = true,
    val shouldShowExtensionField: Boolean = false,
    val shouldShowPathField: Boolean = true,
    val shouldShowResolutionField: Boolean = false,
    val shouldShowSizeField: Boolean = false,
    val shouldShowThumbnailField: Boolean = true,
    val shouldShowPlayedProgress: Boolean = true,
)

// 浏览模式属于媒体库导航，不参与目录配置和继承。
data class MediaQuickSettings(
    val folders: MediaItemLayout,
    val videos: MediaItemLayout,
    val sort: MediaSortSettings,
    val fields: MediaDisplayFields,
) {
    fun toOverrides(): MediaQuickSettingsOverrides = MediaQuickSettingsOverrides(
        folders = folders.normalized(),
        videos = videos.normalized(),
        sort = sort,
        fields = fields,
    )
}

fun ApplicationPreferences.resolveQuickSettings(directory: StoragePath? = null): MediaQuickSettings {
    val ancestors = generateSequence(directory) { it.parent }.mapNotNull { directoryQuickSettings[it] }
    val layouts = resolveMediaLayouts(directory)
    return MediaQuickSettings(
        folders = layouts.folders.layout,
        videos = layouts.videos.layout,
        sort = ancestors.firstNotNullOfOrNull { it.sort } ?: MediaSortSettings(sortBy, sortOrder),
        fields = ancestors.firstNotNullOfOrNull { it.fields } ?: MediaDisplayFields(
            shouldShowDurationField = shouldShowDurationField,
            shouldShowExtensionField = shouldShowExtensionField,
            shouldShowPathField = shouldShowPathField,
            shouldShowResolutionField = shouldShowResolutionField,
            shouldShowSizeField = shouldShowSizeField,
            shouldShowThumbnailField = shouldShowThumbnailField,
            shouldShowPlayedProgress = shouldShowPlayedProgress,
        ),
    )
}

fun CloudQuickSettings.resolveQuickSettings(directory: String? = null): MediaQuickSettings {
    val ancestors = cloudDirectoryAncestors(directory).mapNotNull { directoryQuickSettings[it] }
    val layouts = resolveMediaLayouts(directory)
    val sort = ancestors.firstNotNullOfOrNull { it.sort } ?: MediaSortSettings(sortBy, sortOrder)
    return MediaQuickSettings(
        folders = layouts.folders.layout,
        videos = layouts.videos.layout,
        sort = sort.copy(by = sort.by.takeIf { it in CloudQuickSettings.SUPPORTED_SORT_OPTIONS } ?: Sort.By.TITLE),
        fields = ancestors.firstNotNullOfOrNull { it.fields } ?: MediaDisplayFields(
            shouldShowExtensionField = shouldShowExtensionField,
            shouldShowPathField = shouldShowPathField,
            shouldShowSizeField = shouldShowSizeField,
            shouldShowThumbnailField = shouldShowThumbnailField,
            shouldShowPlayedProgress = shouldShowPlayedProgress,
        ),
    )
}

internal fun cloudDirectoryAncestors(directory: String?): Sequence<String> = generateSequence(directory?.trimEnd('/')?.ifEmpty { "/" }) { path ->
    path.takeUnless { it == "/" }?.substringBeforeLast('/', "")?.ifEmpty { "/" }
}

fun ApplicationPreferences.hasInheritedQuickSettings(directory: StoragePath): Boolean = generateSequence(directory.parent) { it.parent }.any { directoryQuickSettings[it]?.isEmpty == false }

fun CloudQuickSettings.hasInheritedQuickSettings(directory: String): Boolean = cloudDirectoryAncestors(directory).drop(1).any { directoryQuickSettings[it]?.isEmpty == false }

fun ApplicationPreferences.withQuickSettings(
    directory: StoragePath?,
    settings: MediaQuickSettings,
): ApplicationPreferences {
    if (directory != null) {
        return copy(directoryQuickSettings = directoryQuickSettings + (directory to settings.toOverrides()))
    }
    return copy(
        folderLayoutMode = settings.folders.mode,
        folderLayoutScale = settings.folders.normalized().scale,
        videoLayoutMode = settings.videos.mode,
        videoLayoutScale = settings.videos.normalized().scale,
        sortBy = settings.sort.by,
        sortOrder = settings.sort.order,
        shouldShowDurationField = settings.fields.shouldShowDurationField,
        shouldShowExtensionField = settings.fields.shouldShowExtensionField,
        shouldShowPathField = settings.fields.shouldShowPathField,
        shouldShowResolutionField = settings.fields.shouldShowResolutionField,
        shouldShowSizeField = settings.fields.shouldShowSizeField,
        shouldShowThumbnailField = settings.fields.shouldShowThumbnailField,
        shouldShowPlayedProgress = settings.fields.shouldShowPlayedProgress,
    )
}

fun CloudQuickSettings.withQuickSettings(
    directory: String?,
    settings: MediaQuickSettings,
): CloudQuickSettings {
    if (directory != null) {
        val key = directory.trimEnd('/').ifEmpty { "/" }
        return copy(directoryQuickSettings = directoryQuickSettings + (key to settings.toOverrides()))
    }
    return copy(
        folderLayoutMode = settings.folders.mode,
        folderLayoutScale = settings.folders.normalized().scale,
        videoLayoutMode = settings.videos.mode,
        videoLayoutScale = settings.videos.normalized().scale,
        sortBy = settings.sort.by,
        sortOrder = settings.sort.order,
        shouldShowExtensionField = settings.fields.shouldShowExtensionField,
        shouldShowPathField = settings.fields.shouldShowPathField,
        shouldShowSizeField = settings.fields.shouldShowSizeField,
        shouldShowThumbnailField = settings.fields.shouldShowThumbnailField,
        shouldShowPlayedProgress = settings.fields.shouldShowPlayedProgress,
    ).normalized()
}

fun ApplicationPreferences.withIndependentQuickSettings(
    directory: StoragePath,
    isEnabled: Boolean,
): ApplicationPreferences = if (isEnabled) {
    withQuickSettings(directory, resolveQuickSettings(directory))
} else {
    copy(directoryQuickSettings = directoryQuickSettings - directory)
}

fun CloudQuickSettings.withIndependentQuickSettings(
    directory: String,
    isEnabled: Boolean,
): CloudQuickSettings = if (isEnabled) {
    withQuickSettings(directory, resolveQuickSettings(directory))
} else {
    copy(directoryQuickSettings = directoryQuickSettings - directory.trimEnd('/').ifEmpty { "/" })
}

// 快照和目录流使用相同的排序依赖；显示字段改变不触发重新排序。
fun ApplicationPreferences.directorySortOverrides(): Map<String, Pair<Sort.By, Sort.Order>> = directoryQuickSettings.entries.mapNotNull { (path, settings) ->
    settings.sort?.let { path.value to (it.by to it.order) }
}.toMap()
