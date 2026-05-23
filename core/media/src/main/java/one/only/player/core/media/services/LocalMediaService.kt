package one.only.player.core.media.services

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.VIDEO_COLLECTION_URI
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.extensions.getMediaContentUri
import one.only.player.core.common.extensions.getMediaFileContentUri
import one.only.player.core.common.extensions.getPath
import one.only.player.core.common.extensions.updateMedia
import one.only.player.core.common.hasManageExternalStorageAccess

@Singleton
class LocalMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaService {

    private lateinit var activity: Activity
    private val contentResolver = context.contentResolver
    private val mediaRequestMutex = Mutex()
    private var resultCallback: ((Boolean) -> Unit)? = null
    private var mediaRequestLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    override fun initialize(activity: ComponentActivity) {
        this.activity = activity
        mediaRequestLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            resultCallback?.invoke(result.resultCode == Activity.RESULT_OK)
        }
    }

    override suspend fun deleteMedia(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        val targets = uris.map(::resolveDeleteTarget)
        val mediaUris = targets.mapNotNull(DeleteTarget::mediaUri).distinct()
        val localFiles = targets.mapNotNull(DeleteTarget::localFile).distinctBy { it.path }

        if (mediaUris.isEmpty()) return@withContext deleteLocalFiles(localFiles)

        val isDeleteApproved = launchMediaRequest {
            MediaStore.createDeleteRequest(contentResolver, mediaUris)
        }
        if (!isDeleteApproved) return@withContext false

        deleteLocalFiles(localFiles)
        true
    }

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = withContext(Dispatchers.IO) {
        val validUri = ensureMediaStoreUri(uri) ?: return@withContext false

        val isWriteApproved = launchMediaRequest {
            MediaStore.createWriteRequest(contentResolver, listOf(validUri))
        }
        if (!isWriteApproved) return@withContext false

        contentResolver.updateMedia(
            uri = validUri,
            contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, to)
            },
        )
    }

    override suspend fun moveMediaToRecycleBin(uri: Uri): MediaMoveResult? = withContext(Dispatchers.IO) {
        moveMedia(
            uri = uri,
            displayName = createRecycleBinFileName(context.getPath(uri)?.let(::File)?.name ?: return@withContext null),
            mimeType = RECYCLE_BIN_MIME_TYPE,
            relativePath = "$RECYCLE_BIN_RELATIVE_PATH/",
        )
    }

    override suspend fun moveMediaToFolder(
        uri: Uri,
        targetFolderPath: String,
    ): MediaMoveResult? = withContext(Dispatchers.IO) {
        val currentPath = context.getPath(uri) ?: return@withContext null
        val currentFile = File(currentPath)
        val targetFolder = File(targetFolderPath)
        if (!targetFolder.exists() || !targetFolder.isDirectory) return@withContext null
        if (currentFile.parentFile?.canonicalPath == targetFolder.canonicalPath) return@withContext null

        moveMedia(
            uri = uri,
            displayName = currentFile.name,
            mimeType = resolveMimeType(uri, currentFile.name),
            relativePath = buildRelativePath(targetFolder.path) ?: return@withContext null,
        )
    }

    override suspend fun moveFolderToFolder(
        folderPath: String,
        targetFolderPath: String,
    ): List<MediaMoveResult> = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        val targetFolder = File(targetFolderPath)
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()
        if (!targetFolder.exists() || !targetFolder.isDirectory) return@withContext emptyList()
        if (folder.parentFile?.canonicalPath == targetFolder.canonicalPath) return@withContext emptyList()
        if (targetFolder.canonicalPath.startsWith(folder.canonicalPath + File.separator)) return@withContext emptyList()

        val originalFiles = folder.walkTopDown()
            .filter(File::isFile)
            .map { file -> file.path to file.name }
            .toList()
        val movedFolder = File(targetFolder, folder.name)
        if (movedFolder.exists()) return@withContext emptyList()
        if (!folder.renameTo(movedFolder)) {
            return@withContext moveFolderFilesWithMediaStore(
                folder = folder,
                movedFolder = movedFolder,
                originalFiles = originalFiles,
            )
        }

        buildMovedFolderResults(
            folder = folder,
            movedFolder = movedFolder,
            originalFiles = originalFiles,
        )
    }

    override suspend fun restoreMediaFromRecycleBin(
        uri: Uri,
        originalPath: String,
        originalFileName: String,
    ): MediaMoveResult? = withContext(Dispatchers.IO) {
        val file = File(originalPath)
        val parentPath = file.parent ?: return@withContext null

        moveMedia(
            uri = uri,
            displayName = originalFileName,
            mimeType = resolveMimeType(uri, originalFileName),
            relativePath = buildRelativePath(parentPath) ?: return@withContext null,
        )
    }

    override suspend fun shareMedia(uris: List<Uri>) {
        val intent = Intent.createChooser(
            Intent().apply {
                type = "video/*"
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            },
            null,
        )
        activity.startActivity(intent)
    }

    private suspend fun launchMediaRequest(
        createRequest: () -> PendingIntent,
    ): Boolean = mediaRequestMutex.withLock {
        val launcher = mediaRequestLauncher ?: return@withLock false
        suspendCoroutine { continuation ->
            resultCallback = { isApproved ->
                resultCallback = null
                continuation.resume(isApproved)
            }
            runCatching {
                val intent = createRequest()
                launcher.launch(IntentSenderRequest.Builder(intent).build())
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to launch media request", throwable)
                resultCallback = null
                continuation.resume(false)
            }
        }
    }

    private suspend fun moveFolderFilesWithMediaStore(
        folder: File,
        movedFolder: File,
        originalFiles: List<Pair<String, String>>,
    ): List<MediaMoveResult> {
        val movedMedia = originalFiles.mapNotNull { (originalPath, fileName) ->
            val movedFile = File(movedFolder, originalPath.removePrefix(folder.path).trimStart(File.separatorChar))
            val relativePath = buildRelativePath(movedFile.parent ?: return@mapNotNull null) ?: return@mapNotNull null
            val uri = context.getMediaContentUri(File(originalPath).toUri())
                ?: context.getMediaFileContentUri(originalPath)
                ?: return@mapNotNull null

            moveMedia(
                uri = uri,
                displayName = fileName,
                mimeType = resolveMimeType(uri, fileName),
                relativePath = relativePath,
            )
        }
        return movedMedia.takeIf { it.size == originalFiles.size }.orEmpty()
    }

    private suspend fun buildMovedFolderResults(
        folder: File,
        movedFolder: File,
        originalFiles: List<Pair<String, String>>,
    ): List<MediaMoveResult> = originalFiles.mapNotNull { (originalPath, fileName) ->
        val movedFile = File(movedFolder, originalPath.removePrefix(folder.path).trimStart(File.separatorChar))
        val uri = context.getMediaContentUri(File(originalPath).toUri())
            ?: context.getMediaFileContentUri(originalPath)
        val parentPath = movedFile.parent ?: return@mapNotNull null
        val mimeType = uri?.let { resolveMimeType(it, fileName) }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: "video/*"
        val didUpdateMediaStore = uri?.let { mediaUri ->
            contentResolver.updateMedia(
                uri = mediaUri,
                contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.TITLE, movedFile.nameWithoutExtension)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                    put(MediaStore.Files.FileColumns.DATA, movedFile.path)
                },
            )
        } ?: false
        val resultUri = if (didUpdateMediaStore) {
            resolveResultUri(
                path = movedFile.path,
                mimeType = mimeType,
                fallbackUri = uri,
            )
        } else {
            movedFile.toUri()
        }

        MediaMoveResult(
            uri = resultUri,
            path = movedFile.path,
            parentPath = parentPath,
            fileName = fileName,
            originalPath = originalPath,
        )
    }

    private suspend fun moveMedia(
        uri: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): MediaMoveResult? {
        val currentPath = context.getPath(uri) ?: return null
        val currentFile = File(currentPath)
        val mediaStoreUri = ensureMediaStoreUri(uri)
        moveMediaFile(
            uri = mediaStoreUri?.let(::buildWritableMediaUri) ?: uri,
            currentFile = currentFile,
            displayName = displayName,
            mimeType = mimeType,
            relativePath = relativePath,
        )?.let { return it }

        if (mediaStoreUri == null) return null
        val isWriteApproved = launchMediaRequest {
            MediaStore.createWriteRequest(contentResolver, listOf(mediaStoreUri))
        }
        if (!isWriteApproved) return null

        return moveMediaWithMediaStore(
            uri = mediaStoreUri,
            currentFile = currentFile,
            displayName = displayName,
            mimeType = mimeType,
            relativePath = relativePath,
        )
    }

    private suspend fun moveMediaWithMediaStore(
        uri: Uri,
        currentFile: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): MediaMoveResult? = runCatching {
        val updated = contentResolver.updateMedia(
            uri = uri,
            contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, displayName)
                put(MediaStore.Files.FileColumns.TITLE, displayName.substringBeforeLast('.'))
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
            },
        )
        if (!updated) return@runCatching null

        val expectedFile = File(EXTERNAL_STORAGE_PATH, relativePath.removeSuffix("/")).resolve(displayName)
        val resultUri = resolveMovedMediaUri(
            uri = uri,
            path = expectedFile.path,
            mimeType = mimeType,
        ) ?: return@runCatching null
        val resultPath = context.getPath(resultUri) ?: expectedFile.path
        val resultFile = File(resultPath)
        if (resultFile.canonicalPath != expectedFile.canonicalPath) return@runCatching null
        if (!resultFile.exists()) return@runCatching null

        MediaMoveResult(
            uri = resultUri,
            path = resultPath,
            parentPath = resultFile.parent ?: expectedFile.parent.orEmpty(),
            fileName = resultFile.name,
            originalPath = currentFile.path,
        )
    }.getOrNull()

    private suspend fun moveMediaFile(
        uri: Uri,
        currentFile: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): MediaMoveResult? = runCatching {
        val targetDirectory = File(EXTERNAL_STORAGE_PATH, relativePath.removeSuffix("/"))
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return@runCatching null
        }

        val newFile = File(targetDirectory, displayName)
        if (newFile.exists()) return@runCatching null
        if (!currentFile.renameTo(newFile)) {
            return@runCatching null
        }

        val didUpdateMediaStore = contentResolver.updateMedia(
            uri = uri,
            contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newFile.name)
                put(MediaStore.Files.FileColumns.TITLE, newFile.nameWithoutExtension)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.DATA, newFile.path)
            },
        )
        if (!didUpdateMediaStore) {
            newFile.renameTo(currentFile)
            return@runCatching null
        }

        val resultUri = resolveResultUri(
            path = newFile.path,
            mimeType = mimeType,
            fallbackUri = uri,
        )
        val resultPath = context.getPath(resultUri)?.takeIf { path -> File(path).exists() } ?: newFile.path
        val resultFile = File(resultPath)

        MediaMoveResult(
            uri = resultUri,
            path = resultPath,
            parentPath = resultFile.parent ?: targetDirectory.path,
            fileName = resultFile.name,
            originalPath = currentFile.path,
        )
    }.getOrNull()

    private fun buildRelativePath(parentPath: String): String? {
        val normalizedExternalPath = EXTERNAL_STORAGE_PATH.path.replace('\\', '/').trimEnd('/')
        val normalizedParentPath = parentPath.replace('\\', '/').trimEnd('/')
        if (normalizedParentPath != normalizedExternalPath && !normalizedParentPath.startsWith("$normalizedExternalPath/")) {
            return null
        }

        val relative = normalizedParentPath.removePrefix(normalizedExternalPath).trimStart('/')
        return relative.takeIf(String::isNotBlank)?.plus("/")
    }

    private fun resolveMimeType(
        uri: Uri,
        displayName: String,
    ): String = contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
        ?: "video/*"

    private fun createRecycleBinFileName(
        originalFileName: String,
    ): String = originalFileName.substringBeforeLast('.') + "." + RECYCLE_BIN_EXTENSION

    private fun buildWritableMediaUri(uri: Uri): Uri = runCatching {
        ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            ContentUris.parseId(uri),
        )
    }.getOrElse { uri }

    private fun resolveMovedMediaUri(
        uri: Uri,
        path: String,
        mimeType: String,
    ): Uri? {
        val resolvedUri = resolveResultUri(
            path = path,
            mimeType = mimeType,
            fallbackUri = uri,
        )
        if (context.getPath(resolvedUri) != null) return resolvedUri

        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val collectionUri = if (mimeType.startsWith("video/")) {
            VIDEO_COLLECTION_URI
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(collectionUri, id)
    }

    private fun deleteLocalFiles(files: List<File>): Boolean {
        if (files.isEmpty()) return false
        if (!hasManageExternalStorageAccess()) return false

        return files.any { file ->
            file.exists() && file.isFile && file.delete()
        }
    }

    private fun resolveDeleteTarget(uri: Uri): DeleteTarget {
        val path = context.getPath(uri)?.canonicalPathOrSelf()
        val mediaUri = resolveVideoMediaUri(uri, path)
        val localFile = path
            ?.takeIf { mediaUri == null || uri.scheme == "file" }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile }

        return DeleteTarget(
            mediaUri = mediaUri,
            localFile = localFile,
        )
    }

    private fun resolveVideoMediaUri(
        uri: Uri,
        path: String?,
    ): Uri? {
        val videoUri = path?.let { findVideoMediaUriByPath(it) }
            ?: context.getMediaContentUri(uri)
        if (videoUri != null) return videoUri

        val id = runCatching { ContentUris.parseId(uri) }.getOrNull()
        if (id == null || id <= 0L) return null
        if (!uri.toString().contains("/video/media/")) return null

        return uri
    }

    private fun findVideoMediaUriByPath(path: String): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return runCatching {
            contentResolver.query(
                VIDEO_COLLECTION_URI,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                val index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                ContentUris.withAppendedId(VIDEO_COLLECTION_URI, cursor.getLong(index))
            }
        }.getOrNull()
    }

    // createWriteRequest 要求 content URI 且必须含 numeric ID
    private fun ensureMediaStoreUri(uri: Uri): Uri? {
        if (uri.scheme == "content") {
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull()
            return if (id != null && id > 0) uri else null
        }
        val path = context.getPath(uri) ?: return null
        return context.getMediaContentUri(uri) ?: context.getMediaFileContentUri(path)
    }

    private fun resolveResultUri(
        path: String,
        mimeType: String,
        fallbackUri: Uri,
    ): Uri = if (mimeType.startsWith("video/")) {
        context.getMediaContentUri(File(path).toUri()) ?: fallbackUri
    } else {
        context.getMediaFileContentUri(path) ?: fallbackUri
    }

    private data class DeleteTarget(
        val mediaUri: Uri?,
        val localFile: File?,
    )

    companion object {
        private const val TAG = "LocalMediaService"
        private const val RECYCLE_BIN_FOLDER_NAME = ".only_player"
        private const val RECYCLE_BIN_RELATIVE_PATH = "Movies/$RECYCLE_BIN_FOLDER_NAME"
        private const val RECYCLE_BIN_EXTENSION = "optrash"
        private const val RECYCLE_BIN_MIME_TYPE = "application/octet-stream"
        private val EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory()
    }
}
