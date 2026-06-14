package one.only.player.debug

import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.extensions.prettyName
import one.only.player.core.data.repository.toFavoriteItem
import one.only.player.core.data.repository.toFavoriteRootItem
import one.only.player.core.data.repository.toRemoteFavoriteItem
import one.only.player.core.model.FavoriteItem
import one.only.player.core.model.FavoriteTargetType
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.Video

internal fun Context.runFavoriteCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "favorite.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )
    val value = extras.withTarget(target)

    return runCatching {
        runBlocking { entryPoint.runFavoriteAction(action, value) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle favorite action: $action",
            command = command,
            target = action,
        )
    }
}

private suspend fun DebugCommandEntryPoint.runFavoriteAction(
    action: String,
    extras: Bundle,
): Bundle {
    val command = "favorite.$action"
    return when (action) {
        "add" -> {
            val itemId = addFavorite(extras)
            debugResult(
                isOk = true,
                message = "Added favorite: $itemId",
                command = command,
                target = action,
                value = itemId.toString(),
            )
        }
        "list" -> {
            val items = favoriteRepository().observeAll().first()
            debugResult(
                isOk = true,
                message = items.joinToString(separator = "; ") { item -> item.debugSummary() },
                command = command,
                target = action,
                value = items.size.toString(),
            )
        }
        "delete" -> {
            val id = extras.requiredTargetLong(EXTRA_ID)
            favoriteRepository().delete(listOf(id))
            debugResult(
                isOk = true,
                message = "Deleted favorite: $id",
                command = command,
                target = action,
                value = id.toString(),
            )
        }
        "move" -> {
            val id = extras.requiredTargetLong(EXTRA_ID)
            val parentId = extras.optionalLong("parent_id")
            favoriteRepository().move(listOf(id), parentId)
            debugResult(
                isOk = true,
                message = "Moved favorite: $id parent=$parentId",
                command = command,
                target = action,
                value = id.toString(),
            )
        }
        "clear" -> {
            favoriteRepository().clear()
            debugResult(
                isOk = true,
                message = "Cleared favorites",
                command = command,
                target = action,
            )
        }
        else -> error("Unknown favorite action: $action")
    }
}

private suspend fun DebugCommandEntryPoint.addFavorite(extras: Bundle): Long {
    val type = enumValue<FavoriteTargetType>(extras.requiredString("type"))
    val parentId = extras.optionalLong("parent_id")
    return when (type) {
        FavoriteTargetType.FAVORITE_FOLDER -> favoriteRepository().addFolder(
            title = extras.getString(EXTRA_NAME).orEmpty().ifBlank { "收藏夹" },
            parentId = parentId,
        )
        FavoriteTargetType.LOCAL_VIDEO -> {
            val video = requireDebugVideo(extras.requiredMediaTarget())
            favoriteRepository().upsert(video.toFavoriteItem(parentId))
        }
        FavoriteTargetType.LOCAL_FOLDER -> {
            val path = extras.requiredString(EXTRA_PATH).canonicalPathOrSelf()
            favoriteRepository().upsert(
                FavoriteItem(
                    parentId = parentId,
                    targetType = FavoriteTargetType.LOCAL_FOLDER,
                    targetKey = "local:folder:$path",
                    title = File(path).prettyName,
                    subtitle = path,
                    localPath = path,
                ),
            )
        }
        FavoriteTargetType.REMOTE_SERVER_ROOT -> {
            val server = remoteServerRepository().getById(extras.requiredFavoriteServerId()) ?: error("Cloud server not found")
            favoriteRepository().upsert(server.toFavoriteRootItem(parentId))
        }
        FavoriteTargetType.REMOTE_DIRECTORY,
        FavoriteTargetType.REMOTE_FILE,
        -> {
            val server = remoteServerRepository().getById(extras.requiredFavoriteServerId()) ?: error("Cloud server not found")
            val path = extras.requiredString(EXTRA_PATH)
            val file = RemoteFile(
                name = extras.getString(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: File(path).name.ifBlank { path },
                path = path,
                isDirectory = type == FavoriteTargetType.REMOTE_DIRECTORY,
            )
            favoriteRepository().upsert(file.toRemoteFavoriteItem(server, parentId))
        }
    }
}

private fun Bundle.requiredFavoriteServerId(): Long {
    getString("server_id")?.toLongOrNull()?.let { return it }
    getString(EXTRA_ID)?.toLongOrNull()?.let { return it }
    getLong("server_id", 0L).takeIf { it > 0L }?.let { return it }
    getLong(EXTRA_ID, 0L).takeIf { it > 0L }?.let { return it }
    getInt("server_id", 0).takeIf { it > 0 }?.let { return it.toLong() }
    return requiredLong(EXTRA_ID)
}

private fun Bundle.optionalLong(key: String): Long? {
    if (!containsKey(key)) return null
    getString(key)?.toLongOrNull()?.let { return it }
    return getLong(key, 0L).takeIf { it > 0L } ?: getInt(key, 0).toLong().takeIf { it > 0L }
}

private suspend fun DebugCommandEntryPoint.requireDebugVideo(target: String): Video {
    mediaRepository().getVideoByUri(target)?.let { return it }
    val videos = mediaRepository().getVideosFlow().first().distinctBy(Video::uriString)
    val exactMatches = videos.filter { video ->
        video.uriString == target ||
            video.path == target ||
            video.nameWithExtension == target ||
            video.displayName == target
    }
    if (exactMatches.size == 1) return exactMatches.single()
    if (exactMatches.size > 1) error("Ambiguous media target: $target")
    val partialMatches = videos.filter { video ->
        video.path.contains(target, ignoreCase = true) ||
            video.nameWithExtension.contains(target, ignoreCase = true) ||
            video.displayName.contains(target, ignoreCase = true)
    }
    if (partialMatches.size == 1) return partialMatches.single()
    if (partialMatches.size > 1) error("Ambiguous media target: $target")
    error("Media not found: $target")
}

private fun FavoriteItem.debugSummary(): String = "id=$id type=$targetType title=$title path=${localPath ?: remotePath.orEmpty()} parent=${parentId ?: ""}"
