package one.only.player.debug

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        runBlocking { entryPoint.runMediaAction(action, value) }
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
        "move_to_recycle_bin" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget())
            repository.moveVideosToRecycleBin(listOf(video.uriString))
            val movedVideo = repository.findDebugVideo(video.displayName, includeRecycleBin = true)
            val didMove = movedVideo?.isInRecycleBin == true
            debugResult(
                isOk = didMove,
                message = movedVideo?.debugSummary() ?: "Failed to move to recycle bin: ${video.nameWithExtension}",
                command = command,
                target = action,
                value = movedVideo?.uriString ?: video.uriString,
            )
        }
        "restore_from_recycle_bin" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget(), includeRecycleBin = true)
            repository.restoreVideosFromRecycleBin(listOf(video.uriString))
            val restoredVideo = repository.findDebugVideo(video.displayName, includeRecycleBin = true)
            val didRestore = restoredVideo != null && !restoredVideo.isInRecycleBin
            debugResult(
                isOk = didRestore,
                message = restoredVideo?.debugSummary() ?: "Failed to restore from recycle bin: ${video.nameWithExtension}",
                command = command,
                target = action,
                value = restoredVideo?.uriString ?: video.uriString,
            )
        }
        "delete_permanently" -> {
            val video = repository.requireDebugVideo(extras.requiredMediaTarget(), includeRecycleBin = true)
            val didDelete = mediaService().deleteMedia(listOf(video.uriString.toUri()))
            if (didDelete) mediaSynchronizer().refresh(video.path)
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
            val didRefresh = mediaSynchronizer().refresh(path)
            debugResult(
                isOk = didRefresh,
                message = "Scanned path ${if (didRefresh) "succeeded" else "failed"}: $path",
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

private suspend fun MediaRepository.requireDebugVideo(
    target: String,
    includeRecycleBin: Boolean = false,
): Video = findDebugVideo(target, includeRecycleBin) ?: error("Media not found: $target")

private fun Video.debugSummary(): String = "name=$nameWithExtension uri=$uriString path=$path recycle=$isInRecycleBin exists=${File(path).exists()}"
