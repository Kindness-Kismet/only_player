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
import one.only.player.core.model.MediaLayoutTarget
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.ServerProtocol
import one.only.player.core.model.Sort
import one.only.player.core.model.resolveMediaLayouts
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withIndependentQuickSettings
import one.only.player.core.model.withMediaLayout
import one.only.player.core.model.withQuickSettings

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
            directoryPath = if (server.protocol == ServerProtocol.SMB) directoryPath.lowercase() else directoryPath,
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
    val directory = extras.getString("directory")?.let { raw ->
        remoteMediaResolver().normalizeDirectoryPath(server, raw).trimEnd('/').ifEmpty { "/" }.let { path ->
            if (server.protocol == ServerProtocol.SMB) path.lowercase() else path
        }
    }
    return when (action) {
        "get" -> {
            val settings = preferencesRepository().applicationPreferences.value.cloudQuickSettings(server.id)
            debugResult(
                isOk = true,
                message = settings.debugSummary(server.id, directory),
                command = command,
                target = target,
                value = settings.debugSummary(server.id, directory),
            )
        }
        "set" -> {
            val settingTarget = target?.takeIf { it.isNotBlank() }
                ?: extras.getString("target")?.takeIf { it.isNotBlank() }
                ?: error("Missing cloud quick setting target")
            var updatedSettings = CloudQuickSettings()
            preferencesRepository().updateApplicationPreferences { preferences ->
                val current = preferences.cloudQuickSettings(server.id)
                updatedSettings = current.updated(settingTarget, extras, directory).normalized()
                preferences.withCloudQuickSettings(
                    serverId = server.id,
                    settings = updatedSettings,
                )
            }
            debugResult(
                isOk = true,
                message = updatedSettings.debugSummary(server.id, directory),
                command = command,
                target = settingTarget,
                value = updatedSettings.debugSummary(server.id, directory),
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
    directory: String?,
): CloudQuickSettings {
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
    if (target == "layout_mode") {
        val mode = enumValue<MediaLayoutMode>(extras.requiredString(EXTRA_VALUE))
        return copy(videoLayoutMode = mode, folderLayoutMode = mode)
    }
    if (target == "layout_scale") {
        return withVideoLayoutScale(extras.requiredFloat(EXTRA_VALUE)).let { it.copy(folderLayoutScale = it.videoLayoutScale) }
    }
    val current = resolveQuickSettings(directory)
    val updated = when (target) {
        "sort_by" -> {
            val sortBy = enumValue<Sort.By>(extras.requiredString(EXTRA_VALUE))
            require(sortBy in CloudQuickSettings.SUPPORTED_SORT_OPTIONS) { "Unsupported cloud sort option: $sortBy" }
            current.copy(sort = current.sort.copy(by = sortBy))
        }
        "sort_order" -> current.copy(sort = current.sort.copy(order = enumValue<Sort.Order>(extras.requiredString(EXTRA_VALUE))))
        "field.extension" -> current.copy(fields = current.fields.copy(shouldShowExtensionField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.path" -> current.copy(fields = current.fields.copy(shouldShowPathField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.size" -> current.copy(fields = current.fields.copy(shouldShowSizeField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.thumbnail" -> current.copy(fields = current.fields.copy(shouldShowThumbnailField = extras.requiredBoolean(EXTRA_ENABLED)))
        "field.played_progress" -> current.copy(fields = current.fields.copy(shouldShowPlayedProgress = extras.requiredBoolean(EXTRA_ENABLED)))
        else -> error("Unknown quick setting target: $target")
    }
    return withQuickSettings(directory, updated)
}

private fun List<RemoteFile>.sortedForCloud(
    preferences: ApplicationPreferences,
    serverId: Long,
    directoryPath: String,
): List<RemoteFile> {
    val settings = preferences.cloudQuickSettings(serverId).resolveQuickSettings(directoryPath)
    val comparator = settings.sort.toSort().remoteFileComparator()
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

private fun CloudQuickSettings.debugSummary(serverId: Long, directory: String?): String {
    val settings = resolveQuickSettings(directory)
    val layouts = resolveMediaLayouts(directory)
    val layoutSummary = "folder=${layouts.folders.layout.mode}/${layouts.folders.layout.scale} folder_source=${layouts.folders.sourcePath ?: "default"} video=${layouts.videos.layout.mode}/${layouts.videos.layout.scale} video_source=${layouts.videos.sourcePath ?: "default"}"
    val fields = "extension:${settings.fields.shouldShowExtensionField},path:${settings.fields.shouldShowPathField},size:${settings.fields.shouldShowSizeField},thumbnail:${settings.fields.shouldShowThumbnailField},played:${settings.fields.shouldShowPlayedProgress}"
    return "$layoutSummary server_id=$serverId layout=$videoLayoutMode scale=${normalizedVideoLayoutScale()} sort=${settings.sort.by}/${settings.sort.order} fields=$fields"
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
