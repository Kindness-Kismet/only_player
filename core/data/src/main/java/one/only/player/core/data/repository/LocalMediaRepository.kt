package one.only.player.core.data.repository

import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.extensions.toCanonicalFilePathOrNull
import one.only.player.core.data.mappers.toFolder
import one.only.player.core.data.mappers.toSubtitleCalibration
import one.only.player.core.data.mappers.toVideo
import one.only.player.core.data.mappers.toVideoState
import one.only.player.core.data.models.RemotePlaybackInfo
import one.only.player.core.data.models.VideoState
import one.only.player.core.database.converter.UriListConverter
import one.only.player.core.database.dao.DirectoryDao
import one.only.player.core.database.dao.MediumDao
import one.only.player.core.database.dao.MediumStateDao
import one.only.player.core.database.dao.SubtitleCalibrationDao
import one.only.player.core.database.entities.MediumStateEntity
import one.only.player.core.database.entities.SubtitleCalibrationEntity
import one.only.player.core.database.relations.DirectoryWithMedia
import one.only.player.core.database.relations.MediumWithInfo
import one.only.player.core.media.services.MediaMoveResult
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.Folder
import one.only.player.core.model.StoragePath
import one.only.player.core.model.SubtitleCalibration
import one.only.player.core.model.Video
import one.only.player.core.model.moveDirectoryLayouts

class LocalMediaRepository @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
    private val subtitleCalibrationDao: SubtitleCalibrationDao,
    private val directoryDao: DirectoryDao,
    private val mediaService: MediaService,
    private val mediaSynchronizer: MediaSynchronizer,
    private val favoriteRepository: FavoriteRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackMarkRepository: PlaybackMarkRepository,
) : MediaRepository {

    override fun getVideosFlow(): Flow<List<Video>> = mediumDao.getAllWithInfo().map { media ->
        media.map(MediumWithInfo::toVideo)
    }

    override fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>> = mediumDao
        .getAllWithInfoFromDirectory(folderPath)
        .map { media ->
            media.map(MediumWithInfo::toVideo)
        }

    override fun getRecycleBinVideosFlow(): Flow<List<Video>> = mediumDao.getAllWithInfo().map { media ->
        media.filter { it.isMarkedInRecycleBin() }.map(MediumWithInfo::toVideo)
    }

    override fun getFoldersFlow(): Flow<List<Folder>> = directoryDao.getAllWithMedia().map { it.map(DirectoryWithMedia::toFolder) }

    override suspend fun getVideoByUri(uri: String): Video? = findMediumWithInfo(uri)?.toVideo()

    override suspend fun getVideoState(uri: String): VideoState? = mediumStateDao.get(resolveCanonicalMediaUri(uri))?.toVideoState()

    override suspend fun getVideoState(uris: List<String>): VideoState? {
        val canonicalUris = uris.map { candidateUri -> resolveCanonicalMediaUri(candidateUri) }.distinct()
        if (canonicalUris.isEmpty()) return null
        val stateByUri = mediumStateDao.getAll(canonicalUris).associateBy(MediumStateEntity::uriString)
        return canonicalUris.firstNotNullOfOrNull { canonicalUri ->
            stateByUri[canonicalUri]?.toVideoState()
        }
    }

    override suspend fun getCanonicalMediaUri(uri: String): String = resolveCanonicalMediaUri(uri)

    override suspend fun getRemotePlaybackStates(stateKeys: List<String>): Map<String, RemotePlaybackInfo> {
        if (stateKeys.isEmpty()) return emptyMap()
        return mediumStateDao.getAll(stateKeys).associate { entity ->
            entity.uriString to RemotePlaybackInfo(
                playbackPosition = entity.playbackPosition,
                lastPlayedTime = entity.lastPlayedTime,
            )
        }
    }

    override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                lastPlayedTime = lastPlayedTime,
            ),
        )
    }

    override suspend fun clearMediumLastPlayedTime(uri: String) {
        mediumStateDao.clearLastPlayedTime(resolveCanonicalMediaUri(uri))
    }

    override suspend fun clearAllLastPlayedTimes() {
        mediumStateDao.clearAllLastPlayedTimes()
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackPosition = position,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun markVideosAsPlayed(uris: List<String>) {
        updatePlaybackPositions(uris, PLAYED_PLAYBACK_POSITION)
    }

    override suspend fun markVideosAsUnplayed(uris: List<String>) {
        updatePlaybackPositions(uris, 0L)
    }

    private suspend fun updatePlaybackPositions(
        uris: List<String>,
        position: Long,
    ) {
        uris.distinct().forEach { uri ->
            val canonicalMediaUri = resolveCanonicalMediaUri(uri)
            val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
            mediumStateDao.upsert(
                mediumState = stateEntity.copy(playbackPosition = position),
            )
        }
    }

    override suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackSpeed = playbackSpeed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                audioTrackIndex = audioTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleTrackIndex = subtitleTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                videoScale = zoom,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        val currentExternalSubs = UriListConverter.fromStringToList(stateEntity.externalSubs)
        val newSubtitleCanonicalPath = subtitleUri.toCanonicalFilePathOrNull()
        val hasSameSubtitle = currentExternalSubs.any { existingSubtitleUri ->
            when {
                existingSubtitleUri == subtitleUri -> true
                newSubtitleCanonicalPath == null -> false
                else -> existingSubtitleUri.toCanonicalFilePathOrNull() == newSubtitleCanonicalPath
            }
        }
        if (hasSameSubtitle) return
        val newExternalSubs = UriListConverter.fromListToString(urlList = currentExternalSubs + subtitleUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                externalSubs = newExternalSubs,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateExternalSubs(uri: String, externalSubs: List<Uri>) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                externalSubs = UriListConverter.fromListToString(externalSubs),
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleDelayMilliseconds = delay,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleSpeed = speed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun getOrCreateSubtitleCalibration(
        uri: String,
        subtitleKey: String,
        trackIndex: Int,
    ): SubtitleCalibration = try {
        subtitleCalibrationDao.getOrCreate(
            mediaUri = resolveCanonicalMediaUri(uri),
            subtitleKey = subtitleKey,
            trackIndex = trackIndex,
        ).toSubtitleCalibration()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Logger.error(TAG, "字幕校准读取失败", exception)
        throw exception
    }

    override suspend fun saveSubtitleCalibration(
        uri: String,
        subtitleKey: String,
        calibration: SubtitleCalibration,
    ) {
        try {
            subtitleCalibrationDao.save(
                SubtitleCalibrationEntity(
                    mediaUri = resolveCanonicalMediaUri(uri),
                    subtitleKey = subtitleKey,
                    delayMilliseconds = calibration.delayMilliseconds,
                    speed = calibration.speed,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Logger.error(TAG, "字幕校准保存失败", exception)
            throw exception
        }
    }

    // media_state 主键变更会级联删除校准行，调用方需在删除前取出，写入新父行后重建
    private suspend fun restoreSubtitleCalibrations(
        calibrations: List<SubtitleCalibrationEntity>,
        newMediaUri: String,
    ) {
        if (calibrations.isEmpty()) return
        subtitleCalibrationDao.upsertAll(calibrations.map { it.copy(mediaUri = newMediaUri) })
    }

    override suspend fun moveVideosToRecycleBin(uris: List<String>): List<String> {
        if (uris.isEmpty()) return emptyList()

        val movedUris = mutableListOf<String>()
        val movedPaths = mutableListOf<StoragePath>()
        uris.distinct().forEach { uriString ->
            val medium = mediumDao.get(uriString) ?: return@forEach
            val currentState = mediumStateDao.get(uriString) ?: MediumStateEntity(uriString = uriString)
            val calibrations = subtitleCalibrationDao.getByMediaUri(uriString)
            val moved = mediaService.moveMediaToRecycleBin(uriString.toUri()) ?: return@forEach
            val movedUriString = moved.uri.toString()

            if (movedUriString != uriString) {
                mediumDao.delete(listOf(uriString))
                mediumStateDao.delete(listOf(uriString))
            }

            mediumDao.upsert(
                medium.copy(
                    uriString = movedUriString,
                    path = moved.path,
                    parentPath = moved.parentPath,
                    name = moved.fileName,
                ),
            )

            mediumStateDao.upsert(
                currentState.copy(
                    uriString = movedUriString,
                    isInRecycleBin = true,
                    originalPath = currentState.originalPath ?: medium.path,
                    originalParentPath = currentState.originalParentPath ?: medium.parentPath,
                    originalFileName = currentState.originalFileName ?: medium.name,
                ),
            )
            restoreSubtitleCalibrations(calibrations, movedUriString)
            playbackMarkRepository.updateMediaUri(
                oldMediaUri = uriString,
                newMediaUri = movedUriString,
            )
            favoriteRepository.updateLocalVideoTarget(
                oldLocalUri = uriString,
                newLocalUri = movedUriString,
                newLocalPath = moved.path,
                newTitle = moved.fileName,
                newMediaStoreId = medium.mediaStoreId,
            )
            playlistRepository.updateLocalVideoTarget(
                oldLocalUri = uriString,
                newLocalUri = movedUriString,
                newLocalPath = moved.path,
                newTitle = moved.fileName,
            )
            movedPaths += StoragePath.of(moved.path.canonicalPathOrSelf())
            movedUris += movedUriString
        }
        if (movedPaths.isNotEmpty()) {
            mediaSynchronizer.refreshMovedPaths(movedPaths)
        }
        return movedUris
    }

    override suspend fun moveVideosToFolder(
        uris: List<String>,
        targetFolderPath: String,
        shouldCancel: () -> Boolean,
        onProgress: (MediaMoveProgress) -> Unit,
    ): MediaMoveSummary {
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return MediaMoveSummary()
        if (targetFolderPath.isBlank()) return MediaMoveSummary(failedCount = distinctUris.size)

        var movedCount = 0
        var failedCount = 0
        val movedPaths = mutableListOf<StoragePath>()
        for (uriString in distinctUris) {
            if (shouldCancel()) break

            val medium = mediumDao.get(uriString)
            val currentName = medium?.name ?: uriString
            val totalBytes = medium?.size ?: 0L
            onProgress(
                MediaMoveProgress(
                    completedCount = movedCount + failedCount,
                    totalCount = distinctUris.size,
                    currentName = currentName,
                    totalBytes = totalBytes,
                ),
            )
            val moved = mediaService.moveMediaToFolder(
                uri = uriString.toUri(),
                targetFolderPath = targetFolderPath,
                shouldCancel = shouldCancel,
                onProgress = { copyProgress ->
                    onProgress(
                        MediaMoveProgress(
                            completedCount = movedCount + failedCount,
                            totalCount = distinctUris.size,
                            currentName = currentName,
                            copiedBytes = copyProgress.copiedBytes,
                            totalBytes = copyProgress.totalBytes,
                        ),
                    )
                },
            )
            if (moved == null) {
                if (shouldCancel()) break
                failedCount++
                onProgress(
                    MediaMoveProgress(
                        completedCount = movedCount + failedCount,
                        totalCount = distinctUris.size,
                        currentName = currentName,
                        copiedBytes = totalBytes,
                        totalBytes = totalBytes,
                    ),
                )
                continue
            }
            updateMovedMedium(uriString, moved)
            movedPaths += StoragePath.of(moved.path.canonicalPathOrSelf())
            movedCount++
            onProgress(
                MediaMoveProgress(
                    completedCount = movedCount + failedCount,
                    totalCount = distinctUris.size,
                    currentName = currentName,
                    copiedBytes = totalBytes,
                    totalBytes = totalBytes,
                ),
            )
        }
        mediaSynchronizer.refreshMovedPaths(movedPaths)
        return MediaMoveSummary(
            movedCount = movedCount,
            failedCount = failedCount,
            canceledCount = distinctUris.size - movedCount - failedCount,
        )
    }

    override suspend fun moveFoldersToFolder(
        folderPaths: List<String>,
        targetFolderPath: String,
        shouldCancel: () -> Boolean,
        onProgress: (MediaMoveProgress) -> Unit,
    ): MediaMoveSummary {
        val distinctFolderPaths = folderPaths.distinct()
        if (distinctFolderPaths.isEmpty()) return MediaMoveSummary()
        if (targetFolderPath.isBlank()) return MediaMoveSummary(failedCount = distinctFolderPaths.size)

        var movedCount = 0
        var partiallyMovedCount = 0
        var failedCount = 0
        var processedCount = 0
        val movedPaths = mutableListOf<StoragePath>()
        for (folderPath in distinctFolderPaths) {
            if (shouldCancel()) break

            val folder = File(folderPath)
            val files = folder.walkTopDown().filter(File::isFile).toList()
            val totalBytes = files.sumOf(File::length)
            val uriStringByOriginalPath = files
                .map(File::getPath)
                .chunked(SQLITE_VARIABLE_LIMIT)
                .flatMap { paths -> mediumDao.getAllByPaths(paths) }
                .associate { medium -> StoragePath.of(medium.path) to medium.uriString }
            onProgress(
                MediaMoveProgress(
                    completedCount = processedCount,
                    totalCount = distinctFolderPaths.size,
                    currentName = folder.name,
                    totalBytes = totalBytes,
                ),
            )
            val result = mediaService.moveFolderToFolder(folderPath, targetFolderPath)

            val movedFolderPath = File(targetFolderPath, folder.name).path
            result.movedMedia.forEach { moved ->
                movedPaths += StoragePath.of(moved.path.canonicalPathOrSelf())
                val originalPath = moved.originalPath ?: return@forEach
                val uriString = uriStringByOriginalPath[StoragePath.of(originalPath)] ?: return@forEach
                updateMovedMedium(uriString, moved)
            }
            when {
                result.isComplete -> {
                    preferencesRepository.updateApplicationPreferences { preferences ->
                        preferences.moveDirectoryLayouts(
                            from = StoragePath.of(folderPath.canonicalPathOrSelf()),
                            to = StoragePath.of(movedFolderPath.canonicalPathOrSelf()),
                        )
                    }
                    favoriteRepository.updateLocalFolderPath(
                        oldPath = folderPath,
                        newPath = movedFolderPath,
                    )
                    playlistRepository.updateLocalFolderPath(
                        oldPath = folderPath,
                        newPath = movedFolderPath,
                    )
                    movedCount++
                }
                result.movedMedia.isNotEmpty() -> partiallyMovedCount++
                else -> failedCount++
            }
            processedCount++
            onProgress(
                MediaMoveProgress(
                    completedCount = processedCount,
                    totalCount = distinctFolderPaths.size,
                    currentName = folder.name,
                    copiedBytes = totalBytes.takeIf { result.isComplete } ?: 0L,
                    totalBytes = totalBytes,
                ),
            )
        }
        mediaSynchronizer.refreshMovedPaths(movedPaths)
        return MediaMoveSummary(
            movedCount = movedCount,
            partiallyMovedCount = partiallyMovedCount,
            failedCount = failedCount,
            canceledCount = distinctFolderPaths.size - processedCount,
        )
    }

    override suspend fun restoreVideosFromRecycleBin(uris: List<String>): List<String> {
        if (uris.isEmpty()) return emptyList()

        val restoredUris = mutableListOf<String>()
        val restoredPaths = mutableListOf<StoragePath>()
        uris.distinct().forEach { uriString ->
            val currentState = mediumStateDao.get(uriString) ?: return@forEach
            val medium = mediumDao.get(uriString) ?: return@forEach
            val originalPath = currentState.originalPath ?: return@forEach
            val originalFileName = currentState.originalFileName ?: return@forEach
            val calibrations = subtitleCalibrationDao.getByMediaUri(uriString)
            val restored = mediaService.restoreMediaFromRecycleBin(
                uri = uriString.toUri(),
                originalPath = originalPath,
                originalFileName = originalFileName,
            ) ?: return@forEach
            val restoredUriString = restored.uri.toString()

            if (restoredUriString != uriString) {
                mediumDao.delete(listOf(uriString))
                mediumStateDao.delete(listOf(uriString))
            }

            mediumDao.upsert(
                medium.copy(
                    uriString = restoredUriString,
                    path = restored.path,
                    parentPath = restored.parentPath,
                    name = restored.fileName,
                ),
            )

            mediumStateDao.upsert(
                currentState.copy(
                    uriString = restoredUriString,
                    isInRecycleBin = false,
                    originalPath = null,
                    originalParentPath = null,
                    originalFileName = null,
                ),
            )
            restoreSubtitleCalibrations(calibrations, restoredUriString)
            playbackMarkRepository.updateMediaUri(
                oldMediaUri = uriString,
                newMediaUri = restoredUriString,
            )
            favoriteRepository.updateLocalVideoTarget(
                oldLocalUri = uriString,
                newLocalUri = restoredUriString,
                newLocalPath = restored.path,
                newTitle = restored.fileName,
                newMediaStoreId = medium.mediaStoreId,
            )
            playlistRepository.updateLocalVideoTarget(
                oldLocalUri = uriString,
                newLocalUri = restoredUriString,
                newLocalPath = restored.path,
                newTitle = restored.fileName,
            )
            restoredPaths += StoragePath.of(restored.path.canonicalPathOrSelf())
            restoredUris += restoredUriString
        }
        if (restoredPaths.isNotEmpty()) {
            mediaSynchronizer.refreshMovedPaths(restoredPaths)
        }
        return restoredUris
    }

    private suspend fun updateMovedMedium(
        uriString: String,
        moved: MediaMoveResult,
    ) {
        val medium = mediumDao.get(uriString) ?: return
        val currentState = mediumStateDao.get(uriString)
        val calibrations = subtitleCalibrationDao.getByMediaUri(uriString)
        val movedUriString = moved.uri.toString()

        if (movedUriString != uriString) {
            mediumDao.delete(listOf(uriString))
            mediumStateDao.delete(listOf(uriString))
        }

        mediumDao.upsert(
            medium.copy(
                uriString = movedUriString,
                path = moved.path,
                parentPath = moved.parentPath,
                name = moved.fileName,
            ),
        )

        currentState?.let { state ->
            mediumStateDao.upsert(state.copy(uriString = movedUriString))
            restoreSubtitleCalibrations(calibrations, movedUriString)
        }
        playbackMarkRepository.updateMediaUri(
            oldMediaUri = uriString,
            newMediaUri = movedUriString,
        )
        favoriteRepository.updateLocalVideoTarget(
            oldLocalUri = uriString,
            newLocalUri = movedUriString,
            newLocalPath = moved.path,
            newTitle = moved.fileName,
            newMediaStoreId = medium.mediaStoreId,
        )
        playlistRepository.updateLocalVideoTarget(
            oldLocalUri = uriString,
            newLocalUri = movedUriString,
            newLocalPath = moved.path,
            newTitle = moved.fileName,
        )
    }

    private suspend fun findMediumWithInfo(uri: String): MediumWithInfo? {
        mediumDao.getWithInfo(uri)?.let { return it }
        val path = uri.toPathOrNull() ?: return null
        val canonicalPath = path.canonicalPathOrSelf()
        return mediumDao.getByPath(canonicalPath)?.let { medium ->
            mediumDao.getWithInfo(medium.uriString)
        }
    }

    private suspend fun resolveCanonicalMediaUri(uri: String): String {
        if (uri.isRemotePlaybackStateKey()) return uri
        val medium = findMediumWithInfo(uri) ?: return uri
        return medium.mediumEntity.uriString
    }

    private fun String.toPathOrNull(): String? {
        val parsed = toUri()
        val rawPath = when (parsed.scheme) {
            "file" -> parsed.path
            null -> takeIf { it.startsWith(File.separator) }
            else -> null
        } ?: return null
        return File(rawPath).path
    }

    private fun MediumWithInfo.isMarkedInRecycleBin(): Boolean = mediumStateEntity?.isInRecycleBin == true

    companion object {
        private const val TAG = "LocalMediaRepository"

        // 与 Media3 C.TIME_UNSET 数值一致；负位置在 Video.playedPercentage 中按已播完处理。
        private const val PLAYED_PLAYBACK_POSITION = Long.MIN_VALUE + 1

        // SQLite 单条语句的绑定变量上限是 999，留出余量
        private const val SQLITE_VARIABLE_LIMIT = 900
    }
}
