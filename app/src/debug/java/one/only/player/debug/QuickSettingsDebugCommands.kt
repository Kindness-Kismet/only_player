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
import one.only.player.core.model.withMediaLayout

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
    return when (target) {
        "view_mode" -> copy(mediaViewMode = enumValue<MediaViewMode>(extras.requiredString(EXTRA_VALUE)))
        "layout_mode" -> enumValue<MediaLayoutMode>(extras.requiredString(EXTRA_VALUE)).let { copy(videoLayoutMode = it, folderLayoutMode = it) }
        "layout_scale" -> withVideoLayoutScale(extras.requiredFloat(EXTRA_VALUE)).let { it.copy(folderLayoutScale = it.videoLayoutScale) }
        "sort_by" -> copy(sortBy = enumValue<Sort.By>(extras.requiredString(EXTRA_VALUE)))
        "sort_order" -> copy(sortOrder = enumValue<Sort.Order>(extras.requiredString(EXTRA_VALUE)))
        "field.duration" -> copy(shouldShowDurationField = extras.requiredBoolean(EXTRA_ENABLED))
        "field.extension" -> copy(shouldShowExtensionField = extras.requiredBoolean(EXTRA_ENABLED))
        "field.path" -> copy(shouldShowPathField = extras.requiredBoolean(EXTRA_ENABLED))
        "field.played_progress" -> copy(shouldShowPlayedProgress = extras.requiredBoolean(EXTRA_ENABLED))
        "field.resolution" -> copy(shouldShowResolutionField = extras.requiredBoolean(EXTRA_ENABLED))
        "field.size" -> copy(shouldShowSizeField = extras.requiredBoolean(EXTRA_ENABLED))
        "field.thumbnail" -> copy(shouldShowThumbnailField = extras.requiredBoolean(EXTRA_ENABLED))
        else -> error("Unknown quick setting target: $target")
    }
}

private fun ApplicationPreferences.debugSummary(extras: Bundle): String {
    val directory = extras.getString("directory")?.let { StoragePath.of(it.canonicalPathOrSelf()) }
    val layouts = resolveMediaLayouts(directory)
    val layoutSummary = "folder=${layouts.folders.layout.mode}/${layouts.folders.layout.scale} folder_source=${layouts.folders.sourcePath ?: "default"} video=${layouts.videos.layout.mode}/${layouts.videos.layout.scale} video_source=${layouts.videos.sourcePath ?: "default"}"
    val fields = listOf(
        "duration:$shouldShowDurationField",
        "extension:$shouldShowExtensionField",
        "path:$shouldShowPathField",
        "played:$shouldShowPlayedProgress",
        "resolution:$shouldShowResolutionField",
        "size:$shouldShowSizeField",
        "thumbnail:$shouldShowThumbnailField",
    ).joinToString(separator = ",")
    return "$layoutSummary view=$mediaViewMode layout=$videoLayoutMode scale=${normalizedVideoLayoutScale()} sort=$sortBy/$sortOrder fields=$fields"
}
