package one.only.player.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.Logger
import one.only.player.core.common.NextDispatchers
import one.only.player.core.common.di.ApplicationScope
import one.only.player.core.common.extensions.VIDEO_COLLECTION_URI
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.extensions.excludeNoMediaPaths
import one.only.player.core.common.extensions.getMediaFileContentUri
import one.only.player.core.common.extensions.getStorageVolumes
import one.only.player.core.common.extensions.isInsideNoMediaDirectory
import one.only.player.core.common.extensions.prettyName
import one.only.player.core.common.extensions.scanPaths
import one.only.player.core.common.extensions.toPrivateLogSummary
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.database.converter.UriListConverter
import one.only.player.core.database.dao.DirectoryDao
import one.only.player.core.database.dao.MediumDao
import one.only.player.core.database.dao.MediumStateDao
import one.only.player.core.database.entities.DirectoryEntity
import one.only.player.core.database.entities.MediumEntity
import one.only.player.core.database.entities.MediumStateEntity
import one.only.player.core.datastore.datasource.AppPreferencesDataSource
import one.only.player.core.media.model.MediaVideo

private val EXCLUDED_DIRECTORY_NAMES = setOf(".globalTrash", "MIUI")
private const val TARGETED_REFRESH_AUTOMATIC_SYNC_SUPPRESS_MILLIS = 8_000L

private fun String.isInsideExcludedSystemDirectory(): Boolean = replace('\\', '/').split('/').any { it in EXCLUDED_DIRECTORY_NAMES }

class LocalMediaSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
    private val directoryDao: DirectoryDao,
    private val imageLoader: ImageLoader,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null
    private var targetedRefreshSuppressUntilMillis: Long = 0L
    private var deferredAutomaticSyncJob: Job? = null
    private val syncMutex = Mutex()

    override suspend fun refresh(path: String?): Boolean = withContext(dispatcher) {
        if (path != null) {
            suppressAutomaticSyncAfterTargetedRefresh()
        }

        syncMutex.withLock {
            val didScan = if (path != null) {
                registerManualVideoPath(path)
                mergePendingManualVideoPaths()
                context.scanPaths(listOf(path))
            } else {
                mergePendingManualVideoPaths()
                pruneHiddenManualVideoPaths()
                val additionalScanTargets = buildRefreshScanTargets()
                if (additionalScanTargets.isNotEmpty()) {
                    registerUnindexedPaths(additionalScanTargets)
                    scanPathsAsync(additionalScanTargets)
                }
                true
            }

            if (path != null) {
                syncPathMedia(path)
            } else {
                syncCurrentMedia()
            }
            didScan
        }
    }

    override suspend fun removeDeleted(uris: List<String>) = withContext(dispatcher) {
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return@withContext
        mediumDao.delete(distinctUris)
        mediumStateDao.delete(distinctUris)
        distinctUris.forEach { uri ->
            runCatching {
                imageLoader.diskCache?.remove(uri)
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to clear thumbnail cache for ${uri.toPrivateLogSummary()}", throwable)
            }
        }
    }

    override suspend fun registerManualVideoPath(path: String) {
        if (path.isBlank()) return

        val canonicalPath = path.canonicalPathOrSelf()
        val preferences = appPreferencesDataSource.preferences.first()
        if (!preferences.shouldIgnoreNoMediaFiles && File(canonicalPath).isInsideNoMediaDirectory()) {
            Logger.info(TAG, "registerManualVideoPath skippedHiddenPath=${canonicalPath.toPrivateLogSummary()}")
            return
        }

        Logger.info(TAG, "registerManualVideoPath path=${canonicalPath.toPrivateLogSummary()}")
        appPreferencesDataSource.update { currentPreferences ->
            val pendingPaths = currentPreferences.pendingExternalVideoPaths + canonicalPath
            currentPreferences.copy(
                pendingExternalVideoPaths = pendingPaths.distinct(),
            )
        }
    }

    // 将文件系统发现但 MediaStore 未收录的路径持久化，确保同步时能兜底
    private suspend fun registerUnindexedPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        Logger.info(TAG, "registerUnindexedPaths count=${paths.size}")
        appPreferencesDataSource.update {
            it.copy(manualVideoPaths = (it.manualVideoPaths + paths).distinct())
        }
    }

    private suspend fun mergePendingManualVideoPaths() {
        val preferences = appPreferencesDataSource.preferences.first()
        if (preferences.pendingExternalVideoPaths.isEmpty()) return

        Logger.info(
            TAG,
            "mergePendingManualVideoPaths pending=${preferences.pendingExternalVideoPaths.size} manual=${preferences.manualVideoPaths.size}",
        )
        appPreferencesDataSource.update {
            val pendingManualPaths = if (it.shouldIgnoreNoMediaFiles) {
                it.pendingExternalVideoPaths
            } else {
                it.pendingExternalVideoPaths.excludeNoMediaPaths()
            }
            it.copy(
                manualVideoPaths = (it.manualVideoPaths + pendingManualPaths).distinct(),
                pendingExternalVideoPaths = emptyList(),
            )
        }
    }

    private suspend fun pruneHiddenManualVideoPaths() {
        val preferences = appPreferencesDataSource.preferences.first()
        if (preferences.shouldIgnoreNoMediaFiles) return

        val visibleManualPaths = preferences.manualVideoPaths.excludeNoMediaPaths()
        if (visibleManualPaths.size == preferences.manualVideoPaths.size) return

        Logger.info(
            TAG,
            "pruneHiddenManualVideoPaths removed=${preferences.manualVideoPaths.size - visibleManualPaths.size}",
        )
        appPreferencesDataSource.update {
            it.copy(manualVideoPaths = visibleManualPaths)
        }
    }

    override fun startSync() {
        if (mediaSyncingJob != null) return

        Logger.info(TAG, "Starting media sync")
        mediaSyncingJob = combine(
            getMediaVideosFlow(),
            appPreferencesDataSource.preferences,
        ) { mediaStoreVideos, preferences ->
            mergeVisibleMedia(
                mediaStoreVideos = mediaStoreVideos,
                manuallyDiscoveredPaths = preferences.manualVideoPaths.toSet(),
                shouldIgnoreNoMediaFiles = preferences.shouldIgnoreNoMediaFiles,
            )
        }.onEach { media ->
            applicationScope.launch {
                syncMutex.withLock {
                    if (shouldSkipAutomaticSyncAfterTargetedRefresh()) {
                        Logger.info(TAG, "Skipped automatic sync after targeted refresh")
                        return@withLock
                    }
                    Logger.info(TAG, "onEach syncing ${media.size} media entries")
                    if (!shouldSkipEmptyMediaSnapshot(media)) {
                        updateDirectories(media)
                        updateMedia(media)
                        scheduleMediaInfoSync()
                    }
                }
            }
        }.launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
        mediaSyncingJob = null
        Logger.info(TAG, "Stopped media sync")
    }

    private fun suppressAutomaticSyncAfterTargetedRefresh() {
        targetedRefreshSuppressUntilMillis = SystemClock.elapsedRealtime() + TARGETED_REFRESH_AUTOMATIC_SYNC_SUPPRESS_MILLIS
        deferredAutomaticSyncJob?.cancel()
        deferredAutomaticSyncJob = null
    }

    private fun shouldSkipAutomaticSyncAfterTargetedRefresh(): Boolean {
        if (SystemClock.elapsedRealtime() > targetedRefreshSuppressUntilMillis) return false
        scheduleDeferredAutomaticSync()
        return true
    }

    private fun scheduleDeferredAutomaticSync() {
        if (deferredAutomaticSyncJob?.isActive == true) return

        deferredAutomaticSyncJob = applicationScope.launch(dispatcher) {
            while (true) {
                val delayMillis = targetedRefreshSuppressUntilMillis - SystemClock.elapsedRealtime()
                if (delayMillis <= 0L) break
                delay(delayMillis)
            }

            syncMutex.withLock {
                Logger.info(TAG, "Running deferred automatic sync after targeted refresh")
                syncCurrentMedia()
            }
        }
    }

    private fun scanPathsAsync(paths: List<String>) {
        applicationScope.launch(dispatcher) {
            runCatching {
                context.scanPaths(paths)
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to scan paths asynchronously", throwable)
            }
        }
    }

    private suspend fun syncCurrentMedia() {
        val startTime = System.currentTimeMillis()
        val preferences = appPreferencesDataSource.preferences.first()
        val media = mergeVisibleMedia(
            mediaStoreVideos = getMediaVideo(selection = null, selectionArgs = null, sortOrder = null),
            manuallyDiscoveredPaths = preferences.manualVideoPaths.toSet(),
            shouldIgnoreNoMediaFiles = preferences.shouldIgnoreNoMediaFiles,
        )
        Logger.info(TAG, "refresh syncing ${media.size} media entries")
        if (shouldSkipEmptyMediaSnapshot(media)) {
            Logger.debug(TAG, "syncCurrentMedia elapsed=${System.currentTimeMillis() - startTime}ms")
            return
        }

        updateDirectories(media)
        updateMedia(media)
        scheduleMediaInfoSync()
        Logger.debug(TAG, "syncCurrentMedia elapsed=${System.currentTimeMillis() - startTime}ms")
    }

    private suspend fun syncPathMedia(path: String) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val canonicalPath = path.canonicalPathOrSelf()
        val targetFile = File(canonicalPath)
        if (targetFile.isDirectory) {
            syncDirectoryMedia(directory = targetFile, startTime = startTime)
            return@withContext
        }

        val existingByPathBeforeScan = mediumDao.getByPath(canonicalPath)
        val existingState = existingByPathBeforeScan?.let { mediumStateDao.get(it.uriString) }
        if (existingState?.isInRecycleBin == true) {
            Logger.debug(TAG, "syncPathMedia path=${canonicalPath.toPrivateLogSummary()} skippedRecycleBin elapsed=${System.currentTimeMillis() - startTime}ms")
            return@withContext
        }

        val mediaVideo = findPathMediaVideo(canonicalPath)
        if (mediaVideo == null) {
            val didDelete = deletePathMedia(canonicalPath)
            Logger.debug(
                TAG,
                "syncPathMedia path=${canonicalPath.toPrivateLogSummary()} missing deleted=$didDelete elapsed=${System.currentTimeMillis() - startTime}ms",
            )
            return@withContext
        }

        val existingByUri = mediumDao.get(mediaVideo.uri.toString())
        val existingByPath = mediumDao.getByPath(canonicalPath)
        if (existingByPath != null && existingByPath.uriString != mediaVideo.uri.toString()) {
            mediumDao.delete(listOf(existingByPath.uriString))
            mediumStateDao.delete(listOf(existingByPath.uriString))
        }

        directoryDao.upsertAll(buildDirectoryEntities(listOf(mediaVideo)))
        val mediumEntity = buildMediumEntity(mediaVideo, existingByUri)
        if (mediumEntity != null) {
            mediumDao.upsert(mediumEntity)
            if (mediumEntity.duration <= 0 || mediumEntity.width <= 0 || mediumEntity.height <= 0) {
                mediaInfoSynchronizer.sync(mediumEntity.uriString.toUri())
            }
        }
        Logger.debug(TAG, "syncPathMedia path=${canonicalPath.toPrivateLogSummary()} elapsed=${System.currentTimeMillis() - startTime}ms")
    }

    private suspend fun syncDirectoryMedia(
        directory: File,
        startTime: Long,
    ) {
        val directoryPath = directory.path
        val directoryPrefix = directoryPath.trimEnd(File.separatorChar) + File.separator
        val mediaStoreVideos = getMediaVideo(
            selection = "${MediaStore.Video.Media.DATA} LIKE ?",
            selectionArgs = arrayOf("$directoryPrefix%"),
            sortOrder = null,
        )
        val indexedPaths = mediaStoreVideos.map { mediaVideo -> mediaVideo.data.canonicalPathOrSelf() }.toSet()
        val manualVideos = if (hasManageExternalStorageAccess()) {
            directory.collectVisibleUnindexedVideoPaths(indexedPaths)
                .asSequence()
                .map(::File)
                .filter(File::exists)
                .filter { it.isVisibleVideoFile() }
                .map { it.toBasicMediaVideo() }
                .toList()
        } else {
            emptyList()
        }
        val media = (mediaStoreVideos + manualVideos)
            .distinctBy { mediaVideo -> mediaVideo.data.canonicalPathOrSelf() }

        val existingMedia = mediumDao.getAll().first()
        val existingMediaByUri = existingMedia.associateBy(MediumEntity::uriString)
        val mediaPaths = media.map { mediaVideo -> mediaVideo.data.canonicalPathOrSelf() }.toSet()
        val removableUris = existingMedia
            .filter { medium -> medium.path.canonicalPathOrSelf().startsWith(directoryPrefix) }
            .filter { medium -> medium.path.canonicalPathOrSelf() !in mediaPaths }
            .filterNot { medium -> mediumStateDao.get(medium.uriString)?.isInRecycleBin == true }
            .map(MediumEntity::uriString)

        if (removableUris.isNotEmpty()) {
            mediumDao.delete(removableUris)
            mediumStateDao.delete(removableUris)
        }

        directoryDao.upsertAll(buildDirectoryEntities(media))
        val mediumEntities = media.mapNotNull { mediaVideo ->
            buildMediumEntity(
                mediaVideo = mediaVideo,
                existingEntity = existingMediaByUri[mediaVideo.uri.toString()],
            )
        }
        mediumDao.upsertAll(mediumEntities)
        mediumEntities
            .filter { medium -> medium.duration <= 0 || medium.width <= 0 || medium.height <= 0 }
            .forEach { medium -> mediaInfoSynchronizer.sync(medium.uriString.toUri()) }

        Logger.debug(
            TAG,
            "syncDirectoryMedia path=${directoryPath.toPrivateLogSummary()} media=${media.size} removed=${removableUris.size} elapsed=${System.currentTimeMillis() - startTime}ms",
        )
    }

    private fun findPathMediaVideo(path: String): MediaVideo? {
        val mediaStoreVideo = getMediaVideo(
            selection = "${MediaStore.Video.Media.DATA} = ?",
            selectionArgs = arrayOf(path),
            sortOrder = null,
        ).firstOrNull()
        if (mediaStoreVideo != null) return mediaStoreVideo
        if (!hasManageExternalStorageAccess()) return null

        val file = File(path)
        if (!file.exists() || !file.isVisibleVideoFile()) return null
        return file.toBasicMediaVideo()
    }

    private suspend fun deletePathMedia(path: String): Boolean {
        val medium = mediumDao.getByPath(path) ?: return false
        val state = mediumStateDao.get(medium.uriString)
        if (state?.isInRecycleBin == true) return false

        mediumDao.delete(listOf(medium.uriString))
        mediumStateDao.delete(listOf(medium.uriString))
        runCatching {
            imageLoader.diskCache?.remove(medium.uriString)
        }.onFailure { throwable ->
            Logger.error(TAG, "Failed to clear thumbnail cache for ${medium.uriString.toPrivateLogSummary()}", throwable)
        }
        return true
    }

    private suspend fun shouldSkipEmptyMediaSnapshot(media: List<MediaVideo>): Boolean {
        if (media.isNotEmpty()) return false

        val existingCount = mediumDao.getAll().first().size
        if (existingCount == 0) return false

        Logger.info(TAG, "Skipped empty media snapshot existing=$existingCount")
        return true
    }

    private suspend fun mergeVisibleMedia(
        mediaStoreVideos: List<MediaVideo>,
        manuallyDiscoveredPaths: Set<String>,
        shouldIgnoreNoMediaFiles: Boolean,
    ): List<MediaVideo> = withContext(dispatcher) {
        val hasAllFilesAccess = hasManageExternalStorageAccess()
        val baseManualPaths = if (shouldIgnoreNoMediaFiles) {
            manuallyDiscoveredPaths
        } else {
            manuallyDiscoveredPaths.excludeNoMediaPaths().toSet()
        }
        val effectiveManualPaths = baseManualPaths
            .filterNot(String::isInsideExcludedSystemDirectory)
            .toSet()
        if (effectiveManualPaths.isNotEmpty()) {
            Logger.info(TAG, "mergeVisibleMedia manualPaths=${effectiveManualPaths.size}")
        }
        val manuallyDiscoveredVideos = if (hasAllFilesAccess) {
            effectiveManualPaths
                .asSequence()
                .map(::File)
                .filter(File::exists)
                .filter { it.isVisibleVideoFile() }
                .map { it.toBasicMediaVideo() }
                .toList()
        } else {
            if (effectiveManualPaths.isNotEmpty()) {
                Logger.info(TAG, "mergeVisibleMedia skippedManualPaths=${effectiveManualPaths.size} noAllFilesAccess=true")
            }
            emptyList()
        }

        val combinedVisibleMedia = mediaStoreVideos + manuallyDiscoveredVideos
        Logger.info(
            TAG,
            "mergeVisibleMedia result mediaStore=${mediaStoreVideos.size} manual=${manuallyDiscoveredVideos.size} combined=${combinedVisibleMedia.size} noMedia=$shouldIgnoreNoMediaFiles manageAccess=$hasAllFilesAccess",
        )
        if (!shouldIgnoreNoMediaFiles || !hasAllFilesAccess) {
            return@withContext combinedVisibleMedia
                .distinctBy { mediaVideo -> mediaVideo.data.canonicalPathOrSelf() }
                .sortedBy(MediaVideo::data)
        }

        val noMediaVideos = context.getStorageVolumes().flatMap { volume ->
            volume.collectNoMediaVideos()
        }
        if (noMediaVideos.isEmpty()) {
            return@withContext combinedVisibleMedia
                .distinctBy(MediaVideo::data)
                .sortedBy(MediaVideo::data)
        }

        Logger.info(TAG, "Found ${noMediaVideos.size} videos inside .nomedia directories")
        return@withContext (combinedVisibleMedia + noMediaVideos)
            .distinctBy { mediaVideo -> mediaVideo.data.canonicalPathOrSelf() }
            .sortedBy(MediaVideo::data)
    }

    private fun buildRefreshScanTargets(): List<String> {
        if (!hasManageExternalStorageAccess()) return emptyList()

        val indexedPaths = getMediaVideo(selection = null, selectionArgs = null, sortOrder = null)
            .map { it.data }
            .toHashSet()

        val targets = context.getStorageVolumes().flatMap { volume ->
            volume.collectVisibleUnindexedVideoPaths(indexedPaths)
        }.distinct()

        if (targets.isNotEmpty()) {
            Logger.info(TAG, "Refreshing ${targets.size} unindexed video files")
        }
        return targets
    }

    private suspend fun updateDirectories(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val directories = buildDirectoryEntities(media)
        directoryDao.upsertAll(directories)

        val currentDirectoryPaths = directories.map { it.path }.toSet()
        val unwantedDirectories = directoryDao.getAll().first()
            .filterNot { it.path in currentDirectoryPaths }
        val unwantedDirectoriesPaths = unwantedDirectories.map { it.path }

        directoryDao.delete(unwantedDirectoriesPaths)
        Logger.debug(TAG, "updateDirectories media=${media.size} directories=${directories.size} elapsed=${System.currentTimeMillis() - startTime}ms")
    }

    private fun buildDirectoryEntities(media: List<MediaVideo>): List<DirectoryEntity> {
        if (media.isEmpty()) return emptyList()

        val storageVolumes = context.getStorageVolumes()
        val volumePaths = storageVolumes.map { it.path }.toSet()
        val directoryPaths = linkedSetOf<String>()

        media.forEach { mediaVideo ->
            var currentPath = File(mediaVideo.data).parent ?: return@forEach
            while (directoryPaths.add(currentPath)) {
                if (currentPath in volumePaths) break
                currentPath = File(currentPath).parent ?: break
            }
        }

        return directoryPaths.map { path ->
            val directory = File(path)
            val parentPath = directory.parent?.takeIf { it in directoryPaths } ?: "/"
            DirectoryEntity(
                path = path,
                name = directory.prettyName,
                modified = directory.lastModified(),
                parentPath = parentPath,
            )
        }
    }

    private fun buildMediumEntity(
        mediaVideo: MediaVideo,
        existingEntity: MediumEntity?,
    ): MediumEntity? {
        val file = File(mediaVideo.data)
        val parentPath = file.parent ?: return null
        return existingEntity?.copy(
            path = file.path,
            name = file.name,
            size = mediaVideo.size,
            width = mediaVideo.width.takeIf { it > 0 } ?: existingEntity.width,
            height = mediaVideo.height.takeIf { it > 0 } ?: existingEntity.height,
            duration = mediaVideo.duration.takeIf { it > 0 } ?: existingEntity.duration,
            mediaStoreId = mediaVideo.id,
            modified = mediaVideo.dateModified,
            parentPath = parentPath,
        ) ?: MediumEntity(
            uriString = mediaVideo.uri.toString(),
            path = mediaVideo.data,
            name = file.name,
            parentPath = parentPath,
            modified = mediaVideo.dateModified,
            size = mediaVideo.size,
            width = mediaVideo.width,
            height = mediaVideo.height,
            duration = mediaVideo.duration,
            mediaStoreId = mediaVideo.id,
        )
    }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val allMedia = mediumDao.getAll().first()
        val existingMediaMap = allMedia.associateBy(MediumEntity::uriString)

        if (media.isEmpty() && allMedia.isNotEmpty()) {
            Logger.info(TAG, "Skipped deleting existing media for empty snapshot existing=${allMedia.size}")
            return@withContext
        }

        val mediumEntities = media.mapNotNull { mediaVideo ->
            buildMediumEntity(
                mediaVideo = mediaVideo,
                existingEntity = existingMediaMap[mediaVideo.uri.toString()],
            )
        }

        val changedMediumEntities = mediumEntities.filter { entity ->
            existingMediaMap[entity.uriString] != entity
        }
        if (changedMediumEntities.isNotEmpty()) {
            mediumDao.upsertAll(changedMediumEntities)
        }

        val currentMediaUris = mediumEntities.map { it.uriString }.toSet()
        val unwantedMedia = allMedia.filterNot { it.uriString in currentMediaUris }

        if (unwantedMedia.isEmpty()) {
            Logger.debug(
                TAG,
                "updateMedia media=${media.size} existing=${allMedia.size} changed=${changedMediumEntities.size} removable=0 elapsed=${System.currentTimeMillis() - startTime}ms",
            )
            return@withContext
        }

        val unwantedMediaUris = unwantedMedia.map(MediumEntity::uriString)
        val stateByUri = mediumStateDao.getAll(unwantedMediaUris).associateBy(MediumStateEntity::uriString)
        val recycleBinUnwantedMediaUris = stateByUri.values
            .filter(MediumStateEntity::isInRecycleBin)
            .map(MediumStateEntity::uriString)
        val removableMediaUris = unwantedMediaUris - recycleBinUnwantedMediaUris.toSet()

        if (removableMediaUris.isEmpty()) {
            Logger.debug(
                TAG,
                "updateMedia media=${media.size} existing=${allMedia.size} changed=${changedMediumEntities.size} removable=0 elapsed=${System.currentTimeMillis() - startTime}ms",
            )
            return@withContext
        }

        mediumDao.delete(removableMediaUris)
        mediumStateDao.delete(removableMediaUris)

        unwantedMedia.filter { it.uriString in removableMediaUris }.forEach { mediumEntity ->
            runCatching {
                imageLoader.diskCache?.remove(mediumEntity.uriString)
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to clear thumbnail cache for ${mediumEntity.uriString.toPrivateLogSummary()}", throwable)
            }
        }

        launch {
            val currentMediaExternalSubs = mediumEntities.flatMap {
                val mediaState = mediumStateDao.get(it.uriString) ?: return@flatMap emptyList<String>()
                UriListConverter.fromStringToList(mediaState.externalSubs)
            }.toSet()

            removableMediaUris.forEach { removableMediaUri ->
                val mediumState = stateByUri[removableMediaUri] ?: return@forEach
                for (sub in UriListConverter.fromStringToList(mediumState.externalSubs)) {
                    if (sub in currentMediaExternalSubs) continue

                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.onFailure { throwable ->
                        Logger.error(TAG, "Failed to release subtitle permission for $sub", throwable)
                    }
                }
            }
        }
        Logger.debug(
            TAG,
            "updateMedia media=${media.size} existing=${allMedia.size} changed=${changedMediumEntities.size} removable=${removableMediaUris.size} elapsed=${System.currentTimeMillis() - startTime}ms",
        )
    }

    // 只补齐列表必需元数据，避免全库 FFmpeg 流信息拖慢扫描。
    private suspend fun scheduleMediaInfoSync() {
        val startTime = System.currentTimeMillis()
        val media = mediumDao.getAll().first()
        val syncUris = media.mapNotNull { entity ->
            val needsMetadata = entity.duration <= 0 || entity.width <= 0 || entity.height <= 0
            if (needsMetadata) {
                entity.uriString.toUri()
            } else {
                null
            }
        }
        if (syncUris.isNotEmpty()) {
            mediaInfoSynchronizer.syncAll(syncUris)
            Logger.info(TAG, "scheduleMediaInfoSync queued ${syncUris.size} items")
        }
        Logger.debug(TAG, "scheduleMediaInfoSync media=${media.size} queued=${syncUris.size} elapsed=${System.currentTimeMillis() - startTime}ms")
    }

    private fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
    ): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(isSelfChange: Boolean) {
                trySend(getMediaVideo(selection, selectionArgs, sortOrder))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        trySend(getMediaVideo(selection, selectionArgs, sortOrder))
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(dispatcher).distinctUntilChanged()

    private fun getMediaVideo(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                mediaVideos.add(
                    MediaVideo(
                        id = id,
                        data = cursor.getString(dataColumn),
                        duration = cursor.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                    ),
                )
            }
        }
        return mediaVideos.filter { File(it.data).exists() }
    }

    private fun File.collectNoMediaVideos(hasNoMediaAncestor: Boolean = false): List<MediaVideo> {
        if (!exists() || !isDirectory) return emptyList()
        if (name in EXCLUDED_DIRECTORY_NAMES) return emptyList()

        val children = runCatching { listFiles()?.toList().orEmpty() }
            .getOrElse { return emptyList() }
        val isNoMediaDirectory = hasNoMediaAncestor ||
            children.any {
                it.isFile && it.name.equals(NO_MEDIA_FILE_NAME, ignoreCase = true)
            }

        val currentDirectoryVideos = if (isNoMediaDirectory) {
            children.filter { it.isVisibleVideoFile() }.mapNotNull { it.toHiddenMediaVideo() }
        } else {
            emptyList()
        }
        val nestedVideos = children.filter(File::isDirectory).flatMap { directory ->
            directory.collectNoMediaVideos(isNoMediaDirectory)
        }

        return currentDirectoryVideos + nestedVideos
    }

    private fun File.collectVisibleUnindexedVideoPaths(indexedPaths: Set<String>): List<String> {
        if (!exists() || !isDirectory) return emptyList()
        if (name.equals("Android", ignoreCase = true)) return emptyList()
        if (name in EXCLUDED_DIRECTORY_NAMES) return emptyList()

        val children = runCatching { listFiles()?.toList().orEmpty() }
            .getOrElse { return emptyList() }
        val hasNoMedia = children.any {
            it.isFile && it.name.equals(NO_MEDIA_FILE_NAME, ignoreCase = true)
        }
        if (hasNoMedia) return emptyList()

        val currentPaths = children
            .filter { it.isVisibleVideoFile() && it.path !in indexedPaths }
            .map { it.path }
        val nestedPaths = children
            .filter(File::isDirectory)
            .flatMap { directory -> directory.collectVisibleUnindexedVideoPaths(indexedPaths) }

        return currentPaths + nestedPaths
    }

    private fun File.isVisibleVideoFile(): Boolean {
        if (!isFile) return false
        if (name.startsWith(".trashed-")) return false

        if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) return true

        val extensionName = extension.lowercase()
        if (extensionName.isBlank()) return false
        if (extensionName in KNOWN_VIDEO_EXTENSIONS) return true

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionName)
        return mimeType?.startsWith("video/") == true
    }

    // 不使用 MediaMetadataRetriever，避免大文件解析阻塞
    private fun File.toBasicMediaVideo(): MediaVideo = MediaVideo(
        id = -path.hashCode().toLong().absoluteValue,
        uri = toUri(),
        size = length(),
        width = 0,
        height = 0,
        data = path,
        duration = 0L,
        dateModified = lastModified(),
    )

    private fun File.toHiddenMediaVideo(): MediaVideo? = toMediaVideo(
        uri = toUri(),
        errorMessage = "Failed to read hidden media metadata for $path",
    )

    private fun File.toMediaVideo(
        uri: Uri,
        errorMessage: String,
    ): MediaVideo? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(path)
            MediaVideo(
                id = -path.hashCode().toLong().absoluteValue,
                uri = if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) {
                    context.getMediaFileContentUri(path) ?: toUri()
                } else {
                    uri
                },
                size = length(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                data = path,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                dateModified = lastModified(),
            )
        }.onFailure { throwable ->
            Logger.error(TAG, errorMessage, throwable)
        }.getOrNull().also {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "LocalMediaSynchronizer"
        private const val NO_MEDIA_FILE_NAME = ".nomedia"
        private const val RECYCLE_BIN_EXTENSION = "optrash"
        private val KNOWN_VIDEO_EXTENSIONS = setOf(
            "3gp",
            "asf",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "mts",
            "rmvb",
            "ts",
            "webm",
            "wmv",
        )

        val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }
}
