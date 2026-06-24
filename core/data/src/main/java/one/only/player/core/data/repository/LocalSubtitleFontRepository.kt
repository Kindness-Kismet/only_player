package one.only.player.core.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.Logger
import one.only.player.core.common.NextDispatchers
import one.only.player.core.common.di.ApplicationScope
import one.only.player.core.common.extensions.externalSubtitleFontDir
import one.only.player.core.common.extensions.externalSubtitleFontFile
import one.only.player.core.common.extensions.externalSubtitleFontFilesDir
import one.only.player.core.common.extensions.externalSubtitleFontMetaFile
import one.only.player.core.common.extensions.externalSubtitleFontTempDir
import one.only.player.core.common.extensions.externalSubtitleFontTempFile
import one.only.player.core.common.extensions.externalSubtitleFontTempMetaFile
import one.only.player.core.data.model.ExternalSubtitleFontMeta

class LocalSubtitleFontRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    private val subtitleFontFileValidator: SubtitleFontFileValidator,
) : SubtitleFontRepository {

    companion object {
        private const val TAG = "LocalSubtitleFontRepository"
        private val SUPPORTED_EXTENSIONS = setOf("ttf", "otf")
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }
    private val writeMutex = Mutex()
    private val stateInternal = MutableStateFlow(ExternalSubtitleFontState())
    private val sourceInternal = MutableStateFlow<ExternalSubtitleFontSource?>(null)

    override val state: StateFlow<ExternalSubtitleFontState> = stateInternal.asStateFlow()
    override val source: StateFlow<ExternalSubtitleFontSource?> = sourceInternal.asStateFlow()

    init {
        applicationScope.launch {
            refreshState()
        }
    }

    override suspend fun importFonts(uris: List<Uri>) {
        writeMutex.withLock {
            withContext(ioDispatcher) {
                importFontsLocked(uris)
                refreshStateLocked()
            }
        }
    }

    override suspend fun clearFont() {
        writeMutex.withLock {
            withContext(ioDispatcher) {
                clearFormalArtifacts()
                clearTempArtifacts()
                refreshStateLocked()
            }
        }
    }

    private suspend fun refreshState() {
        writeMutex.withLock {
            withContext(ioDispatcher) {
                refreshStateLocked()
            }
        }
    }

    private fun importFontsLocked(uris: List<Uri>) {
        val distinctUris = uris.distinct()
        require(distinctUris.isNotEmpty()) { "No subtitle fonts selected" }
        clearTempArtifacts()
        val tempFontDir = context.externalSubtitleFontTempDir
        val tempMetaFile = context.externalSubtitleFontTempMetaFile
        tempFontDir.deleteRecursively()
        tempFontDir.mkdirs()

        runCatching {
            val importedFonts = distinctUris.mapIndexedNotNull { index, uri ->
                val displayName = resolveDisplayName(uri)
                val extension = displayName.substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) return@mapIndexedNotNull null

                val targetFile = File(tempFontDir, "${index.toString().padStart(3, '0')}_${displayName.safeFileName()}")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@mapIndexedNotNull null

                runCatching {
                    validateFontFile(targetFile)
                }.onFailure { throwable ->
                    targetFile.delete()
                    Logger.error(TAG, "Ignored invalid subtitle font: $displayName", throwable)
                }.getOrNull() ?: return@mapIndexedNotNull null

                ImportedFont(
                    displayName = displayName,
                )
            }

            require(importedFonts.isNotEmpty()) { "No supported subtitle fonts selected" }

            val displayName = importedFonts.displayName()
            tempMetaFile.writeText(
                buildJsonObject {
                    put("displayName", displayName)
                    put(
                        "displayNames",
                        buildJsonArray {
                            importedFonts.forEach { font ->
                                add(JsonPrimitive(font.displayName))
                            }
                        },
                    )
                }.toString(),
            )

            commitTempArtifacts(tempFontDir = tempFontDir, tempMetaFile = tempMetaFile)
        }.onFailure { throwable ->
            clearTempArtifacts()
            Logger.error(TAG, "Failed to import subtitle fonts", throwable)
            throw throwable
        }
    }

    private fun refreshStateLocked() {
        clearTempArtifacts()

        val fontDir = context.externalSubtitleFontFilesDir
        val metaFile = context.externalSubtitleFontMetaFile

        if (!fontDir.exists() && !metaFile.exists()) {
            publishEmptyState()
            return
        }

        val meta = readMeta(metaFile)
        val fontFiles = fontDir.listFiles()
            ?.filter { file -> file.isFile && file.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy(File::getName)
            .orEmpty()
        if (meta == null || fontFiles.isEmpty()) {
            clearFormalArtifacts()
            publishEmptyState()
            return
        }

        val validFontFiles = fontFiles.filter { fontFile ->
            runCatching {
                validateFontFile(fontFile)
            }.isSuccess
        }
        if (validFontFiles.isEmpty()) {
            clearFormalArtifacts()
            publishEmptyState()
            return
        }

        stateInternal.value = ExternalSubtitleFontState(
            isAvailable = true,
            displayName = meta.displayName,
        )
        sourceInternal.value = ExternalSubtitleFontSource(
            fonts = validFontFiles.mapIndexed { index, fontFile ->
                ExternalSubtitleFontFile(
                    displayName = meta.displayNames.getOrNull(index) ?: fontFile.name,
                    absolutePath = fontFile.absolutePath,
                )
            },
        )
    }

    private fun readMeta(metaFile: File): ExternalSubtitleFontMeta? {
        if (!metaFile.exists()) return null
        return runCatching {
            val jsonObject = json.parseToJsonElement(metaFile.readText()).jsonObject
            val displayName = jsonObject.getValue("displayName").jsonPrimitive.content
            ExternalSubtitleFontMeta(
                displayName = displayName,
                displayNames = jsonObject["displayNames"]
                    ?.let { element ->
                        runCatching {
                            element.jsonArray.map { it.jsonPrimitive.content }
                        }.getOrNull()
                    }
                    ?.takeIf(List<String>::isNotEmpty)
                    ?: listOf(displayName),
            )
        }.onFailure { throwable ->
            Logger.error(TAG, "Failed to read external subtitle font meta", throwable)
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String {
        val displayName = context.contentResolver.queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: ""
        require(displayName.isNotBlank()) { "Unable to resolve font display name" }
        return displayName
    }

    private fun ContentResolver.queryDisplayName(uri: Uri): String? = query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index == -1) return@use null
        cursor.getString(index)
    }

    private fun validateFontFile(file: File) {
        subtitleFontFileValidator.validate(file)
    }

    private fun commitTempArtifacts(
        tempFontDir: File,
        tempMetaFile: File,
    ) {
        val formalFontFile = context.externalSubtitleFontFile
        val formalFontDir = context.externalSubtitleFontFilesDir
        val formalMetaFile = context.externalSubtitleFontMetaFile
        val fontDirBackupFile = File(context.externalSubtitleFontDir, "fonts.bak")
        val metaBackupFile = File(context.externalSubtitleFontDir, "current.json.bak")

        runCatching {
            backupDirIfExists(formalFontDir, fontDirBackupFile)
            backupIfExists(formalMetaFile, metaBackupFile)

            formalFontFile.delete()
            moveFile(tempFontDir, formalFontDir)
            moveFile(tempMetaFile, formalMetaFile)

            fontDirBackupFile.deleteRecursively()
            metaBackupFile.delete()
        }.onFailure { throwable ->
            restoreDirBackup(formalFontDir, fontDirBackupFile)
            restoreBackup(formalMetaFile, metaBackupFile)
            Logger.error(TAG, "Failed to commit subtitle font artifacts", throwable)
            throw throwable
        }
    }

    private fun backupIfExists(
        sourceFile: File,
        backupFile: File,
    ) {
        if (!sourceFile.exists()) return
        moveFile(sourceFile, backupFile)
    }

    private fun backupDirIfExists(
        sourceDir: File,
        backupDir: File,
    ) {
        if (!sourceDir.exists()) return
        backupDir.deleteRecursively()
        moveFile(sourceDir, backupDir)
    }

    private fun restoreBackup(
        targetFile: File,
        backupFile: File,
    ) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!backupFile.exists()) return
        moveFile(backupFile, targetFile)
    }

    private fun restoreDirBackup(
        targetDir: File,
        backupDir: File,
    ) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        if (!backupDir.exists()) return
        moveFile(backupDir, targetDir)
    }

    private fun moveFile(
        sourceFile: File,
        targetFile: File,
    ) {
        targetFile.parentFile?.mkdirs()
        try {
            Files.move(
                sourceFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                sourceFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: IOException) {
            throw exception
        }
    }

    private fun clearFormalArtifacts() {
        context.externalSubtitleFontFile.delete()
        context.externalSubtitleFontFilesDir.deleteRecursively()
        context.externalSubtitleFontMetaFile.delete()
    }

    private fun clearTempArtifacts() {
        context.externalSubtitleFontTempFile.delete()
        context.externalSubtitleFontTempDir.deleteRecursively()
        context.externalSubtitleFontTempMetaFile.delete()
    }

    private fun publishEmptyState() {
        stateInternal.value = ExternalSubtitleFontState()
        sourceInternal.value = null
    }

    private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun List<ImportedFont>.displayName(): String {
        if (size == 1) return first().displayName
        return "${first().displayName} + ${size - 1}"
    }

    private data class ImportedFont(
        val displayName: String,
    )
}
