package one.only.player.core.media.services

import android.net.Uri
import androidx.activity.ComponentActivity

interface MediaService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
    suspend fun moveMediaToRecycleBin(uri: Uri): MediaMoveResult?
    suspend fun moveMediaToFolder(
        uri: Uri,
        targetFolderPath: String,
        shouldCancel: () -> Boolean = { false },
        onProgress: (MediaCopyProgress) -> Unit = {},
    ): MediaMoveResult?
    suspend fun moveFolderToFolder(folderPath: String, targetFolderPath: String): List<MediaMoveResult>
    suspend fun restoreMediaFromRecycleBin(
        uri: Uri,
        originalPath: String,
        originalFileName: String,
    ): MediaMoveResult?
    suspend fun shareMedia(uris: List<Uri>)

    companion object {
        fun shouldAskSystemForDeleteConfirmation(): Boolean = true
    }
}

data class MediaMoveResult(
    val uri: Uri,
    val path: String,
    val parentPath: String,
    val fileName: String,
    val originalPath: String? = null,
)

data class MediaCopyProgress(
    val copiedBytes: Long,
    val totalBytes: Long,
)
