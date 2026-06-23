package one.only.player.debug

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import one.only.player.core.common.Logger
import one.only.player.core.data.repository.MediaMoveSummary
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.model.Video

internal fun Context.runMediaCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "media.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )
    val value = extras.withTarget(target)

    return runCatching {
        runBlocking { entryPoint.runMediaAction(context = applicationContext, action = action, extras = value) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle media action: $action",
            command = command,
            target = target,
        )
    }
}

private suspend fun DebugCommandEntryPoint.runMediaAction(
    context: Context,
    action: String,
    extras: Bundle,
): Bundle {
    val command = "media.$action"
    val repository = mediaRepository()
    return when (action) {
        "list" -> {
            val videos = repository.getVideosForDebug(extras.optionalFilter())
            debugResult(
                isOk = true,
                message = videos.joinToString(separator = "; ") { it.debugSummary() },
                command = command,
                target = action,
                value = videos.size.toString(),
            )
        }
        "open" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget())
            context.startDebugPlayerActivity(
                debugPlayerIntent(context) {
                    data = video.uriString.toUri()
                },
            )
            debugResult(
                isOk = true,
                message = "Opened media: ${video.debugSummary()}",
                command = command,
                target = action,
                value = video.uriString,
            )
        }
        "move_to_folder" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget())
            val targetFolderPath = extras.requiredString("target_folder")
            val summary = repository.moveVideosToFolder(listOf(video.uriString), targetFolderPath)
            val movedVideo = repository.findDebugVideo(video.nameWithExtension)
            val didMove = summary.failedCount == 0 &&
                summary.movedCount == 1 &&
                movedVideo?.parentPath == targetFolderPath &&
                File(movedVideo.path).exists()
            debugResult(
                isOk = didMove,
                message = "summary=${summary.debugSummary()} video=${movedVideo?.debugSummary()}",
                command = command,
                target = action,
                value = movedVideo?.uriString ?: video.uriString,
            )
        }
        "move_folder_to_folder" -> {
            val folderPath = extras.requiredMediaTarget()
            val targetFolderPath = extras.requiredString("target_folder")
            val summary = repository.moveFoldersToFolder(listOf(folderPath), targetFolderPath)
            val movedFolder = File(targetFolderPath, File(folderPath).name)
            debugResult(
                isOk = summary.failedCount == 0 && summary.movedCount == 1 && movedFolder.exists(),
                message = "summary=${summary.debugSummary()} movedFolder=${movedFolder.path} exists=${movedFolder.exists()}",
                command = command,
                target = action,
                value = movedFolder.path,
            )
        }
        "move_to_recycle_bin" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget())
            val movedUri = repository.moveVideosToRecycleBin(listOf(video.uriString)).firstOrNull()
            val movedVideo = movedUri?.let { repository.getVideoByUri(it) }
            val didMove = movedVideo?.isInRecycleBin == true
            debugResult(
                isOk = didMove,
                message = movedVideo?.debugSummary() ?: "Failed to move to recycle bin: ${video.nameWithExtension}",
                command = command,
                target = action,
                value = movedUri ?: video.uriString,
            )
        }
        "restore_from_recycle_bin" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget(), includeRecycleBin = true)
            val restoredUri = repository.restoreVideosFromRecycleBin(listOf(video.uriString)).firstOrNull()
            val restoredVideo = restoredUri?.let { repository.getVideoByUri(it) }
            val didRestore = restoredVideo != null && !restoredVideo.isInRecycleBin
            debugResult(
                isOk = didRestore,
                message = restoredVideo?.debugSummary() ?: "Failed to restore from recycle bin: ${video.nameWithExtension}",
                command = command,
                target = action,
                value = restoredUri ?: video.uriString,
            )
        }
        "delete_permanently" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget(), includeRecycleBin = true)
            val didDelete = mediaService().deleteMedia(listOf(video.uriString.toUri()))
            if (didDelete) {
                mediaSynchronizer().removeDeleted(listOf(video.uriString))
                applicationScope().launch {
                    runCatching {
                        mediaSynchronizer().refresh(video.path)
                    }.onFailure { throwable ->
                        Logger.error("MediaDebugCommands", "Failed to refresh deleted path: ${video.path}", throwable)
                    }
                }
            }
            debugResult(
                isOk = didDelete,
                message = if (didDelete) "Deleted permanently: ${video.nameWithExtension}" else "Failed to delete permanently: ${video.nameWithExtension}",
                command = command,
                target = action,
                value = video.uriString,
            )
        }
        "refresh" -> {
            val path = extras.optionalFilter()
            val didRefresh = mediaSynchronizer().refresh(path)
            debugResult(
                isOk = didRefresh,
                message = "Media refresh ${if (didRefresh) "succeeded" else "failed"}: ${path.orEmpty()}",
                command = command,
                target = action,
                value = path,
            )
        }
        "scan_path" -> {
            val path = extras.requiredMediaTarget()
            if (!File(path).exists()) {
                return debugResult(
                    isOk = false,
                    message = "Path not found: $path",
                    command = command,
                    target = action,
                    value = path,
                )
            }

            val didRefresh = mediaSynchronizer().refresh(path)
            debugResult(
                isOk = didRefresh,
                message = "Scan path refresh ${if (didRefresh) "succeeded" else "failed"}: $path",
                command = command,
                target = action,
                value = path,
            )
        }
        "status" -> {
            val video = repository.findDebugVideo(extras.requiredMediaTarget(), includeRecycleBin = true)
            debugResult(
                isOk = video != null,
                message = video?.debugSummary() ?: "Media not found: ${extras.requiredMediaTarget()}",
                command = command,
                target = action,
                value = video?.uriString,
            )
        }
        else -> error("Unknown media action: $action")
    }
}

private suspend fun MediaRepository.getVideosForDebug(filter: String?): List<Video> {
    val videos = (getVideosFlow().first() + getRecycleBinVideosFlow().first())
        .distinctBy(Video::uriString)
        .sortedBy(Video::nameWithExtension)
    if (filter.isNullOrBlank()) return videos
    return videos.filter { video ->
        video.nameWithExtension.contains(filter, ignoreCase = true) ||
            video.displayName.contains(filter, ignoreCase = true) ||
            video.path.contains(filter, ignoreCase = true) ||
            video.parentPath.contains(filter, ignoreCase = true) ||
            video.uriString == filter
    }
}

private suspend fun MediaRepository.findDebugVideo(
    target: String,
    includeRecycleBin: Boolean = false,
): Video? {
    getVideoByUri(target)?.let { return it }
    if (target.isPreciseMediaTarget()) return null

    val videos = if (includeRecycleBin) {
        getVideosFlow().first() + getRecycleBinVideosFlow().first()
    } else {
        getVideosFlow().first()
    }
    val distinctVideos = videos.distinctBy(Video::uriString)
    val exactMatches = distinctVideos.filter { video ->
        video.uriString == target ||
            video.path == target ||
            video.nameWithExtension == target ||
            video.displayName == target
    }
    if (exactMatches.size == 1) return exactMatches.single()
    if (exactMatches.size > 1) error("Ambiguous media target: $target")

    val partialMatches = distinctVideos.filter { video ->
        video.nameWithExtension.contains(target, ignoreCase = true) ||
            video.displayName.contains(target, ignoreCase = true) ||
            video.path.contains(target, ignoreCase = true)
    }
    if (partialMatches.size > 1) error("Ambiguous media target: $target; matches=${partialMatches.joinToString { it.nameWithExtension }}")
    return partialMatches.singleOrNull()
}

private fun String.isPreciseMediaTarget(): Boolean {
    val uri = toUri()
    return uri.scheme in setOf("content", "file") || startsWith(File.separator)
}

private suspend fun MediaRepository.requireDebugVideo(
    target: String,
    includeRecycleBin: Boolean = false,
): Video = findDebugVideo(target, includeRecycleBin) ?: error("Media not found: $target")

private fun Video.debugSummary(): String = "name=$nameWithExtension uri=$uriString path=$path recycle=$isInRecycleBin exists=${File(path).exists()}"

private fun MediaMoveSummary.debugSummary(): String = "moved=$movedCount failed=$failedCount canceled=$canceledCount"
