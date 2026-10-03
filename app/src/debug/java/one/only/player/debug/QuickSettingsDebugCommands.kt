package one.only.player.debug

import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaLayoutTarget
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.Sort
import one.only.player.core.model.StoragePath
import one.only.player.core.model.resolveMediaLayouts
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withIndependentQuickSettings
import one.only.player.core.model.withMediaLayout
import one.only.player.core.model.withQuickSettings

internal fun Context.runQuickSettingsCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "quick_settings.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )

    return runCatching {
        runBlocking { entryPoint.runQuickSettingsAction(action, target, extras ?: Bundle.EMPTY) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle quick settings action: $action",
            command = command,
            target = target,
        )
    }
}

private suspend fun DebugCommandEntryPoint.runQuickSettingsAction(
    action: String,
    target: String?,
    extras: Bundle,
): Bundle {
    val command = "quick_settings.$action"
    return when (action) {
        "get" -> {
            val preferences = preferencesRepository().applicationPreferences.value
            debugResult(
                isOk = true,
                message = preferences.debugSummary(extras),
                command = command,
                target = target,
                value = preferences.debugSummary(extras),
            )
        }
        "set" -> {
            val settingTarget = target?.takeIf { it.isNotBlank() }
                ?: extras.getString("target")?.takeIf { it.isNotBlank() }
                ?: error("Missing quick setting target")
            require(settingTarget != "view_mode" || extras.getString("directory") == null) {
                "Media view mode is global; directory is not supported"
            }
            var updatedPreferences = preferencesRepository().applicationPreferences.value
            preferencesRepository().updateApplicationPreferences { preferences ->
                updatedPreferences = preferences.updatedQuickSetting(settingTarget, extras)
                updatedPreferences
            }
            debugResult(
                isOk = true,
                message = updatedPreferences.debugSummary(extras),
                command = command,
                target = settingTarget,
                value = updatedPreferences.debugSummary(extras),
            )
        }
        else -> error("Unknown quick settings action: $action")
    }
}

private fun ApplicationPreferences.updatedQuickSetting(
    target: String,
    extras: Bundle,
): ApplicationPreferences {
    val directory = extras.getString("directory")?.let { StoragePath.of(it.canonicalPathOrSelf()) }
    val layoutTarget = when (target.substringBefore('.')) {
        "folder" -> MediaLayoutTarget.FOLDERS
        "video" -> MediaLayoutTarget.VIDEOS
        else -> null
    }
    if (layoutTarget != null) {
        val current = resolveMediaLayouts(directory)[layoutTarget].layout
        val layout = when (target.substringAfter('.')) {
            "layout_mode" -> current.copy(mode = enumValue<MediaLayoutMode>(extras.requiredString(EXTRA_VALUE)))
            "layout_scale" -> current.copy(scale = extras.requiredFloat(EXTRA_VALUE))
            "inherit" -> {
                require(directory != null) { "Missing directory" }
                null
            }
            else -> error("Unknown layout setting: $target")
        }
        return withMediaLayout(directory, layoutTarget, layout)
    }
    if (target == "independent") {
        require(directory != null) { "Missing directory" }
        return withIndependentQuickSettings(directory, extras.requiredBoolean(EXTRA_ENABLED))
    }
    if (target == "view_mode") {
        return copy(mediaViewMode = enumValue<MediaViewMode>(extras.requiredString(EXTRA_VALUE)))
    }
    if (target == "layout_mode") {
        val mode = enumValue<MediaLayoutMode>(extras.requiredString(EXTRA_VALUE))
        return copy(videoLayoutMode = mode, folderLayoutMode = mode)
    }
    if (target == "layout_scale") {
        return withVideoLayoutScale(extras.requiredFloat(EXTRA_VALUE)).let { it.copy(folderLayoutScale = it.videoLayoutScale) }
    }
    val current = resolveQuickSettings(directory)
    val updated = when (target) {
        "sort_by" -> current.copy(sort = current.sort.copy(by = enumValue<Sort.By>(extras.requiredString(EXTRA_VALUE))))
        "sort_order" -> current.copy(sort = current.sort.copy(order = enumValue<Sort.Order>(extras.requiredString(EXTRA_VALUE))))
        "field.duration" -> current.copy(fields = current.fields.copy(shouldShowDurationField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.extension" -> current.copy(fields = current.fields.copy(shouldShowExtensionField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.path" -> current.copy(fields = current.fields.copy(shouldShowPathField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.played_progress" -> current.copy(fields = current.fields.copy(shouldShowPlayedProgress = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.resolution" -> current.copy(fields = current.fields.copy(shouldShowResolutionField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.size" -> current.copy(fields = current.fields.copy(shouldShowSizeField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.thumbnail" -> current.copy(fields = current.fields.copy(shouldShowThumbnailField = extras.requiredBoolean(EXTRA_ENABLED)))
        else -> error("Unknown quick setting target: $target")
    }
    return withQuickSettings(directory, updated)
}

private fun ApplicationPreferences.debugSummary(extras: Bundle): String {
    val directory = extras.getString("directory")?.let { StoragePath.of(it.canonicalPathOrSelf()) }
    val settings = resolveQuickSettings(directory)
    val layouts = resolveMediaLayouts(directory)
    val layoutSummary = "folder=${layouts.folders.layout.mode}/${layouts.folders.layout.scale} folder_source=${layouts.folders.sourcePath ?: "default"} video=${layouts.videos.layout.mode}/${layouts.videos.layout.scale} video_source=${layouts.videos.sourcePath ?: "default"}"
    val fields = listOf(
        "duration:${settings.fields.shouldShowDurationField}",
        "extension:${settings.fields.shouldShowExtensionField}",
        "path:${settings.fields.shouldShowPathField}",
        "played:${settings.fields.shouldShowPlayedProgress}",
        "resolution:${settings.fields.shouldShowResolutionField}",
        "size:${settings.fields.shouldShowSizeField}",
        "thumbnail:${settings.fields.shouldShowThumbnailField}",
    ).joinToString(separator = ",")
    return "$layoutSummary view=$mediaViewMode layout=$videoLayoutMode scale=${normalizedVideoLayoutScale()} sort=${settings.sort.by}/${settings.sort.order} fields=$fields"
}
