package one.only.player.debug

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.CloudQuickSettings
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.Sort

internal fun Context.runCloudMediaCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "cloud.media.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )
    val value = extras.withTarget(target)

    return runCatching {
        runBlocking { entryPoint.runCloudMediaAction(applicationContext, action, value) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle cloud media action: $action",
            command = command,
            target = action,
        )
    }
}

internal fun Context.runCloudQuickSettingsCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "cloud.quick_settings.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )

    return runCatching {
        runBlocking { entryPoint.runCloudQuickSettingsAction(action, target, extras ?: Bundle.EMPTY) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle cloud quick settings action: $action",
            command = command,
            target = target,
        )
    }
}

private suspend fun DebugCommandEntryPoint.runCloudMediaAction(
    context: Context,
    action: String,
    extras: Bundle,
): Bundle {
    val command = "cloud.media.$action"
    val server = remoteServerRepository().getById(extras.requiredServerId()) ?: error("Cloud server not found")
    val directoryPath = when (action) {
        "list" -> extras.getString(EXTRA_PATH)?.takeIf { it.isNotBlank() } ?: server.path
        "open" -> extras.resolveOpenDirectoryPath(server.path)
        else -> server.path
    }
    val files = remoteMediaResolver()
        .listBrowsableFiles(server, directoryPath, forceRefresh = true)
        .getOrThrow()
        .sortedForCloud(
            preferences = preferencesRepository().applicationPreferences.value,
            serverId = server.id,
        )
    val videos = files.filter { !it.isDirectory }

    return when (action) {
        "list" -> debugResult(
            isOk = true,
            message = videos.joinToString(separator = "; ") { file -> file.debugSummary() },
            command = command,
            target = action,
            value = videos.size.toString(),
        )
        "open" -> {
            val file = videos.requireTargetFile(extras)
            val uri = remoteMediaResolver().buildPlayUrl(server, file).toUri()
            val headers = remoteMediaResolver().buildAuthHeaders(server, file)
            val playlist = remoteMediaResolver().buildVideoPlaylist(server, files)
            val playlistRemotePaths = remoteMediaResolver().buildVideoPlaylistRemotePaths(files)
            val parentPath = file.path.trimEnd('/').substringBeforeLast("/", missingDelimiterValue = "").ifBlank { "/" }
            val initialSubtitleDirectoryUri = DocumentsContract.buildDocumentUri(
                "${context.packageName}.documents",
                remoteMediaResolver().buildDocumentId(server, parentPath),
            )
            context.startDebugPlayerActivity(
                debugPlayerIntent(context) {
                    data = uri
                    if (headers.isNotEmpty()) {
                        putExtra(
                            "headers",
                            Bundle().apply {
                                headers.forEach { (key, value) -> putString(key, value) }
                            },
                        )
                    }
                    if (playlist.size > 1) {
                        putParcelableArrayListExtra("video_list", ArrayList(playlist))
                        putStringArrayListExtra("video_remote_paths", ArrayList(playlistRemotePaths))
                    }
                    putExtra("initial_subtitle_directory_uri", initialSubtitleDirectoryUri)
                },
            )
            debugResult(
                isOk = true,
                message = "Opened cloud media: ${file.debugSummary()}",
                command = command,
                target = action,
                value = uri.toString(),
            )
        }
        else -> error("Unknown cloud media action: $action")
    }
}

private suspend fun DebugCommandEntryPoint.runCloudQuickSettingsAction(
    action: String,
    target: String?,
    extras: Bundle,
): Bundle {
    val command = "cloud.quick_settings.$action"
    val server = remoteServerRepository().getById(extras.requiredServerId()) ?: error("Cloud server not found")
    return when (action) {
        "get" -> {
            val settings = preferencesRepository().applicationPreferences.value.cloudQuickSettings(server.id)
            debugResult(
                isOk = true,
                message = settings.debugSummary(server.id),
                command = command,
                target = target,
                value = settings.debugSummary(server.id),
            )
        }
        "set" -> {
            val settingTarget = target?.takeIf { it.isNotBlank() }
                ?: extras.getString("target")?.takeIf { it.isNotBlank() }
                ?: error("Missing cloud quick setting target")
            var updatedSettings = CloudQuickSettings()
            preferencesRepository().updateApplicationPreferences { preferences ->
                val current = preferences.cloudQuickSettings(server.id)
                updatedSettings = current.updated(settingTarget, extras).normalized()
                preferences.withCloudQuickSettings(
                    serverId = server.id,
                    settings = updatedSettings,
                )
            }
            debugResult(
                isOk = true,
                message = updatedSettings.debugSummary(server.id),
                command = command,
                target = settingTarget,
                value = updatedSettings.debugSummary(server.id),
            )
        }
        else -> error("Unknown cloud quick settings action: $action")
    }
}

private fun Bundle.requiredServerId(): Long {
    optionalLong("server_id")?.takeIf { it > 0L }?.let { return it }
    return requiredLong(EXTRA_ID)
}

private fun CloudQuickSettings.updated(
    target: String,
    extras: Bundle,
): CloudQuickSettings = when (target) {
    "layout_mode" -> copy(mediaLayoutMode = enumValue<MediaLayoutMode>(extras.requiredString(EXTRA_VALUE)))
    "layout_scale" -> withMediaLayoutScale(extras.requiredFloat(EXTRA_VALUE))
    "sort_by" -> {
        val sortBy = enumValue<Sort.By>(extras.requiredString(EXTRA_VALUE))
        if (sortBy !in CloudQuickSettings.SUPPORTED_SORT_OPTIONS) {
            error("Unsupported cloud sort option: $sortBy")
        }
        copy(sortBy = sortBy)
    }
    "sort_order" -> copy(sortOrder = enumValue<Sort.Order>(extras.requiredString(EXTRA_VALUE)))
    "field.extension" -> copy(shouldShowExtensionField = extras.requiredBoolean(EXTRA_ENABLED))
    "field.path" -> copy(shouldShowPathField = extras.requiredBoolean(EXTRA_ENABLED))
    "field.size" -> copy(shouldShowSizeField = extras.requiredBoolean(EXTRA_ENABLED))
    "field.thumbnail" -> copy(shouldShowThumbnailField = extras.requiredBoolean(EXTRA_ENABLED))
    "field.played_progress" -> copy(shouldShowPlayedProgress = extras.requiredBoolean(EXTRA_ENABLED))
    else -> error("Unknown cloud quick setting target: $target")
}

private fun List<RemoteFile>.sortedForCloud(
    preferences: ApplicationPreferences,
    serverId: Long,
): List<RemoteFile> {
    val settings = preferences.cloudQuickSettings(serverId)
    val comparator = Sort(
        by = settings.sortBy,
        order = settings.sortOrder,
    ).remoteFileComparator()
    val (folders, videos) = partition(RemoteFile::isDirectory)
    return folders.sortedWith(comparator) + videos.sortedWith(comparator)
}

private fun List<RemoteFile>.requireTargetFile(extras: Bundle): RemoteFile {
    extras.optionalInt("index")?.let { index ->
        return getOrNull(index) ?: error("Cloud media index out of range: $index")
    }

    val target = extras.getString(EXTRA_VALUE)
        ?: extras.getString(EXTRA_PATH)
        ?: extras.getString(EXTRA_NAME)
        ?: firstOrNull()?.path
        ?: error("No cloud media found")
    val exactMatches = filter { file ->
        file.path == target || file.name == target
    }
    if (exactMatches.size == 1) return exactMatches.single()
    if (exactMatches.size > 1) error("Ambiguous cloud media target: $target")
    val partialMatches = filter { file ->
        file.path.contains(target, ignoreCase = true) ||
            file.name.contains(target, ignoreCase = true)
    }
    if (partialMatches.size == 1) return partialMatches.single()
    if (partialMatches.size > 1) error("Ambiguous cloud media target: $target")
    error("Cloud media not found: $target")
}

private fun RemoteFile.debugSummary(): String = "name=$name path=$path size=$size"

private fun CloudQuickSettings.debugSummary(serverId: Long): String {
    val fields = "extension:$shouldShowExtensionField,path:$shouldShowPathField,size:$shouldShowSizeField,thumbnail:$shouldShowThumbnailField,played:$shouldShowPlayedProgress"
    return "server_id=$serverId layout=$mediaLayoutMode scale=${normalizedMediaLayoutScale()} sort=$sortBy/$sortOrder fields=$fields"
}

private fun Bundle.resolveOpenDirectoryPath(defaultPath: String): String {
    getString(EXTRA_DIRECTORY_PATH)?.takeIf { it.isNotBlank() }?.let { return it }
    getString(EXTRA_PATH)
        ?.takeIf { it.isNotBlank() }
        ?.trimEnd('/')
        ?.substringBeforeLast("/", missingDelimiterValue = "")
        ?.ifBlank { "/" }
        ?.let { return it }
    return defaultPath
}
