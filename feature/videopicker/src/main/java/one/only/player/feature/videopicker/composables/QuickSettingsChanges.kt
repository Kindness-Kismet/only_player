package one.only.player.feature.videopicker.composables

import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.CloudQuickSettings
import one.only.player.core.model.MediaLayoutOverrides

internal fun ApplicationPreferences.applyQuickSettingsChanges(
    original: ApplicationPreferences,
    edited: ApplicationPreferences,
    target: QuickSettingsTarget,
    serverId: Long?,
): ApplicationPreferences = when (target) {
    QuickSettingsTarget.LOCAL -> copy(
        mediaViewMode = changed(original.mediaViewMode, edited.mediaViewMode, mediaViewMode),
        sortBy = changed(original.sortBy, edited.sortBy, sortBy),
        sortOrder = changed(original.sortOrder, edited.sortOrder, sortOrder),
        folderLayoutMode = changed(original.folderLayoutMode, edited.folderLayoutMode, folderLayoutMode),
        folderLayoutScale = changed(original.folderLayoutScale, edited.folderLayoutScale, folderLayoutScale),
        videoLayoutMode = changed(original.videoLayoutMode, edited.videoLayoutMode, videoLayoutMode),
        videoLayoutScale = changed(original.videoLayoutScale, edited.videoLayoutScale, videoLayoutScale),
        shouldShowDurationField = changed(original.shouldShowDurationField, edited.shouldShowDurationField, shouldShowDurationField),
        shouldShowExtensionField = changed(original.shouldShowExtensionField, edited.shouldShowExtensionField, shouldShowExtensionField),
        shouldShowPathField = changed(original.shouldShowPathField, edited.shouldShowPathField, shouldShowPathField),
        shouldShowResolutionField = changed(original.shouldShowResolutionField, edited.shouldShowResolutionField, shouldShowResolutionField),
        shouldShowSizeField = changed(original.shouldShowSizeField, edited.shouldShowSizeField, shouldShowSizeField),
        shouldShowThumbnailField = changed(original.shouldShowThumbnailField, edited.shouldShowThumbnailField, shouldShowThumbnailField),
        shouldShowPlayedProgress = changed(original.shouldShowPlayedProgress, edited.shouldShowPlayedProgress, shouldShowPlayedProgress),
        directoryLayouts = directoryLayouts.applyChanges(original.directoryLayouts, edited.directoryLayouts),
    )
    QuickSettingsTarget.CLOUD -> withCloudQuickSettings(
        serverId,
        cloudQuickSettings(serverId).applyChanges(original.cloudQuickSettings(serverId), edited.cloudQuickSettings(serverId)),
    )
}

private fun CloudQuickSettings.applyChanges(
    original: CloudQuickSettings,
    edited: CloudQuickSettings,
): CloudQuickSettings = copy(
    sortBy = changed(original.sortBy, edited.sortBy, sortBy),
    sortOrder = changed(original.sortOrder, edited.sortOrder, sortOrder),
    folderLayoutMode = changed(original.folderLayoutMode, edited.folderLayoutMode, folderLayoutMode),
    folderLayoutScale = changed(original.folderLayoutScale, edited.folderLayoutScale, folderLayoutScale),
    videoLayoutMode = changed(original.videoLayoutMode, edited.videoLayoutMode, videoLayoutMode),
    videoLayoutScale = changed(original.videoLayoutScale, edited.videoLayoutScale, videoLayoutScale),
    shouldShowExtensionField = changed(original.shouldShowExtensionField, edited.shouldShowExtensionField, shouldShowExtensionField),
    shouldShowPathField = changed(original.shouldShowPathField, edited.shouldShowPathField, shouldShowPathField),
    shouldShowSizeField = changed(original.shouldShowSizeField, edited.shouldShowSizeField, shouldShowSizeField),
    shouldShowThumbnailField = changed(original.shouldShowThumbnailField, edited.shouldShowThumbnailField, shouldShowThumbnailField),
    shouldShowPlayedProgress = changed(original.shouldShowPlayedProgress, edited.shouldShowPlayedProgress, shouldShowPlayedProgress),
    directoryLayouts = directoryLayouts.applyChanges(original.directoryLayouts, edited.directoryLayouts),
)

private fun <T> changed(original: T, edited: T, current: T): T = if (original == edited) current else edited

private fun <K> Map<K, MediaLayoutOverrides>.applyChanges(
    original: Map<K, MediaLayoutOverrides>,
    edited: Map<K, MediaLayoutOverrides>,
): Map<K, MediaLayoutOverrides> {
    val result = toMutableMap()
    for (key in original.keys + edited.keys) {
        if (original[key] == edited[key]) continue
        val before = original[key] ?: MediaLayoutOverrides()
        val after = edited[key] ?: MediaLayoutOverrides()
        val current = result[key] ?: MediaLayoutOverrides()
        val updated = MediaLayoutOverrides(
            folders = changed(before.folders, after.folders, current.folders),
            videos = changed(before.videos, after.videos, current.videos),
        )
        if (updated.folders == null && updated.videos == null) result.remove(key) else result[key] = updated
    }
    return result
}
