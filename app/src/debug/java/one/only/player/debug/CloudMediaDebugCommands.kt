package one.only.player.debug

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import one.only.player.core.model.RemoteFile

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
    val files = remoteMediaResolver().listBrowsableFiles(server, directoryPath, forceRefresh = true).getOrThrow()
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

private fun Bundle.requiredServerId(): Long {
    getString("server_id")?.toLongOrNull()?.let { return it }
    getString(EXTRA_ID)?.toLongOrNull()?.let { return it }
    getLong("server_id", 0L).takeIf { it > 0L }?.let { return it }
    getLong(EXTRA_ID, 0L).takeIf { it > 0L }?.let { return it }
    getInt("server_id", 0).takeIf { it > 0 }?.let { return it.toLong() }
    return requiredLong(EXTRA_ID)
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
