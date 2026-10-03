package one.only.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.core.common.di.ApplicationScope
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.data.repository.FavoriteRepository
import one.only.player.core.data.repository.MediaMoveProgress
import one.only.player.core.data.repository.MediaMoveSummary
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PlaylistRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.toFavoriteItem
import one.only.player.core.domain.GetMediaBearingFolderPathsUseCase
import one.only.player.core.domain.GetSortedMediaUseCase
import one.only.player.core.media.services.MediaMoveSpaceCheck
import one.only.player.core.media.services.MediaMoveTargetDirectoryContent
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaInfoSynchronizer
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Playlist
import one.only.player.core.model.StoragePath
import one.only.player.core.model.Video
import one.only.player.core.ui.base.DataState
import one.only.player.feature.videopicker.navigation.FolderArgs
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.state.SelectedVideo

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedMediaUseCase: GetSortedMediaUseCase,
    getMediaBearingFolderPathsUseCase: GetMediaBearingFolderPathsUseCase,
    savedStateHandle: SavedStateHandle,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
    private val playlistRepository: PlaylistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val mediaSynchronizer: MediaSynchronizer,
    private val snapshotCache: MediaPickerSnapshotCache,
    private val moveSelectionStore: MediaPickerMoveSelectionStore,
    private val homeStore: MediaPickerHomeStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val folderArgs = FolderArgs(savedStateHandle)

    val folderPath = folderArgs.folderId
    private val screenMode = folderArgs.screenMode

    private var shouldCancelMoveSelection = false

    private val initialPreferences = preferencesRepository.applicationPreferences.value
    private val initialPlayerPreferences = preferencesRepository.playerPreferences.value
    private val initialHasAllFilesAccess = hasManageExternalStorageAccess()
    private val initialMediaDataState: DataState<Folder?> = snapshotCache.get(
        folderPath = folderPath,
        preferences = initialPreferences,
        hasAllFilesAccess = initialHasAllFilesAccess,
    )
        ?.takeIf { screenMode == MediaPickerScreenMode.LIBRARY }
        ?.let { folder -> DataState.Success(folder) }
        ?: DataState.Loading

    private val uiStateInternal = MutableStateFlow(
        MediaPickerUiState(
            folderPath = folderPath,
            folderName = folderPath?.let { File(folderPath).name },
            mediaDataState = initialMediaDataState,
            preferences = initialPreferences,
            playerPreferences = initialPlayerPreferences,
            screenMode = screenMode,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        if (initialMediaDataState is DataState.Loading && screenMode == MediaPickerScreenMode.LIBRARY) {
            viewModelScope.launch {
                val folder = snapshotCache.awaitGet(
                    folderPath = folderPath,
                    preferences = initialPreferences,
                    hasAllFilesAccess = initialHasAllFilesAccess,
                ) ?: return@launch
                uiStateInternal.update { currentState ->
                    if (currentState.mediaDataState !is DataState.Loading) return@update currentState
                    currentState.copy(mediaDataState = DataState.Success(folder))
                }
            }
        }

        viewModelScope.launch {
            getSortedMediaUseCase.invoke(
                folderPath = folderPath,
                isRecycleBinOnly = screenMode == MediaPickerScreenMode.RECYCLE_BIN,
            ).collect { folder ->
                if (screenMode == MediaPickerScreenMode.LIBRARY) {
                    snapshotCache.put(
                        folderPath = folderPath,
                        folder = folder,
                        preferences = uiStateInternal.value.preferences,
                        hasAllFilesAccess = hasManageExternalStorageAccess(),
                    )
                    // 主页会下钻到首个有内容的目录，落点供子目录页的路径面板去重；扁平视图停在根上，没有落点
                    if (folderPath == null) {
                        folder?.let { StoragePath.of(it.path) }
                            ?.takeUnless(StoragePath::isRoot)
                            ?.let(homeStore::set)
                    }
                }
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        mediaDataState = DataState.Success(folder),
                    )
                }
            }
        }

        // 只有子目录页会展示路径面板，主页自身不需要落点
        if (folderPath != null) {
            viewModelScope.launch {
                // 进程被回收后主页可能没有重建过，用它留下的快照补上落点
                if (homeStore.landingPath.value == null) {
                    snapshotCache.awaitGet(
                        folderPath = null,
                        preferences = initialPreferences,
                        hasAllFilesAccess = initialHasAllFilesAccess,
                    )
                        ?.let { homeFolder -> StoragePath.of(homeFolder.path) }
                        ?.takeUnless(StoragePath::isRoot)
                        ?.let(homeStore::set)
                }
                homeStore.landingPath.collect { landingPath ->
                    uiStateInternal.update { currentState ->
                        currentState.copy(homeLandingPath = landingPath)
                    }
                }
            }

            // 扁平视图下中间层级不可浏览，路径面板据此只保留自身含媒体的祖先
            viewModelScope.launch {
                getMediaBearingFolderPathsUseCase().collect { paths ->
                    uiStateInternal.update { currentState ->
                        currentState.copy(mediaBearingFolderPaths = paths)
                    }
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        preferences = it,
                    )
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        playerPreferences = it,
                    )
                }
            }
        }

        viewModelScope.launch {
            playlistRepository.observePlaylists().collect { playlists ->
                uiStateInternal.update { currentState ->
                    currentState.copy(playlists = playlists)
                }
            }
        }

        viewModelScope.launch {
            moveSelectionStore.selection.collectLatest { selection ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        moveSelection = selection,
                        moveTargetDataState = DataState.Loading,
                        moveSpaceCheck = null,
                    )
                }
                if (selection != null && screenMode == MediaPickerScreenMode.LIBRARY) {
                    loadMoveTargetDirectory(selection)
                }
            }
        }

        viewModelScope.launch {
            moveSelectionStore.resolution.collect { resolution ->
                uiStateInternal.update { currentState ->
                    currentState.copy(moveSelectionResolution = resolution)
                }
            }
        }
    }

    fun onEvent(event: MediaPickerUiEvent) {
        when (event) {
            is MediaPickerUiEvent.DeleteFolders -> permanentlyDeleteFolders(event.folders)
            is MediaPickerUiEvent.DeleteVideos -> permanentlyDeleteVideos(event.videos)
            is MediaPickerUiEvent.MoveVideosToRecycleBin -> moveVideosToRecycleBin(event.videos)
            is MediaPickerUiEvent.StartMoveSelection -> startMoveSelection(event.videoUris, event.folderPaths)
            is MediaPickerUiEvent.MoveSelectionToFolder -> moveSelectionToFolder(event.targetFolderPath)
            is MediaPickerUiEvent.CancelMoveSelection -> cancelMoveSelection()
            is MediaPickerUiEvent.CancelRemainingMoveSelection -> cancelRemainingMoveSelection()
            is MediaPickerUiEvent.ClearMoveResult -> clearMoveResult()
            is MediaPickerUiEvent.RestoreVideos -> restoreVideos(event.videos)
            is MediaPickerUiEvent.PermanentlyDeleteVideos -> permanentlyDeleteVideos(event.videos)
            is MediaPickerUiEvent.ShareVideos -> shareVideos(event.videos)
            is MediaPickerUiEvent.AddFavorites -> addFavorites(event.videos, event.folders)
            is MediaPickerUiEvent.AddToPlaylist -> addToPlaylist(event.playlistId, event.videos, event.folders)
            is MediaPickerUiEvent.CreatePlaylistAndAdd -> createPlaylistAndAdd(event.title, event.videos, event.folders)
            is MediaPickerUiEvent.ExcludeFolders -> excludeFolders(event.paths)
            is MediaPickerUiEvent.MarkVideosPlayed -> markVideosPlayed(event.videos)
            is MediaPickerUiEvent.MarkVideosUnplayed -> markVideosUnplayed(event.videos)
            is MediaPickerUiEvent.Refresh -> refresh()
            is MediaPickerUiEvent.RenameVideo -> renameVideo(event.uri, event.to)
            is MediaPickerUiEvent.AddToSync -> addToMediaInfoSynchronizer(event.uri)
            is MediaPickerUiEvent.UpdateMenu -> updateMenu(event.transform)
            is MediaPickerUiEvent.CacheFolderSnapshot -> cacheFolderSnapshot(event.folder)
            MediaPickerUiEvent.ClearDeleteResult -> clearDeleteResult()
        }
    }

    private fun permanentlyDeleteFolders(folders: List<Folder>) {
        viewModelScope.launch {
            val uris = folders.flatMap { folder ->
                folder.allMediaList.map { video ->
                    video.uriString.toUri()
                }
            }
            val isDeletionSuccessful = mediaService.deleteMedia(uris)
            if (isDeletionSuccessful) {
                mediaSynchronizer.refresh()
            }
            uiStateInternal.update { currentState ->
                currentState.copy(
                    deleteResult = if (isDeletionSuccessful) {
                        MediaPickerDeleteResult.Deleted
                    } else {
                        MediaPickerDeleteResult.DeleteFailed
                    },
                )
            }
        }
    }

    private fun permanentlyDeleteVideos(videos: List<SelectedVideo>) {
        viewModelScope.launch {
            val uris = videos.map(SelectedVideo::uriString)
            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.removeDeleted(uris)
                refreshDeletedPathsAsync(videos.map(SelectedVideo::path))
            }
            uiStateInternal.update { currentState ->
                currentState.copy(
                    deleteResult = if (isDeletionSuccessful) {
                        MediaPickerDeleteResult.Deleted
                    } else {
                        MediaPickerDeleteResult.DeleteFailed
                    },
                )
            }
        }
    }

    private fun moveVideosToRecycleBin(videos: List<SelectedVideo>) {
        viewModelScope.launch {
            runCatching {
                mediaRepository.moveVideosToRecycleBin(videos.map(SelectedVideo::uriString))
            }.onSuccess {
                uiStateInternal.update { currentState ->
                    currentState.copy(deleteResult = MediaPickerDeleteResult.MovedToRecycleBin)
                }
            }.onFailure {
                uiStateInternal.update { currentState ->
                    currentState.copy(deleteResult = MediaPickerDeleteResult.DeleteFailed)
                }
            }
        }
    }

    private fun startMoveSelection(
        videoUris: List<String>,
        folderPaths: List<String>,
    ) {
        val rootFolder = (uiStateInternal.value.mediaDataState as? DataState.Success)?.value
        moveSelectionStore.set(
            MediaPickerMoveSelection(
                videoUris = videoUris,
                videoParentPaths = rootFolder?.allMediaList
                    ?.filter { video -> video.uriString in videoUris }
                    ?.map(Video::parentPath)
                    .orEmpty(),
                folderPaths = folderPaths,
                folderParentPaths = folderPaths.mapNotNull { folderPath ->
                    File(folderPath).parent?.let(String::normalizedMovePath)
                },
            ),
        )
    }

    private fun cancelMoveSelection() {
        if (uiStateInternal.value.isMovingSelection) return
        moveSelectionStore.cancel()
    }

    private fun cancelRemainingMoveSelection() {
        shouldCancelMoveSelection = true
    }

    private fun clearMoveResult() {
        uiStateInternal.update { currentState ->
            currentState.copy(moveResult = null)
        }
    }

    private fun clearDeleteResult() {
        uiStateInternal.update { currentState ->
            currentState.copy(deleteResult = null)
        }
    }

    private fun updateMoveProgress(progress: MediaMoveProgress) {
        uiStateInternal.update { currentState ->
            currentState.copy(
                moveProgress = progress,
            )
        }
    }

    private fun moveSelectionToFolder(targetFolderPath: String) {
        val selection = uiStateInternal.value.moveSelection ?: return
        if (uiStateInternal.value.isMovingSelection) return
        viewModelScope.launch {
            shouldCancelMoveSelection = false
            uiStateInternal.update { currentState ->
                currentState.copy(
                    isMovingSelection = true,
                    moveProgress = MediaMoveProgress(totalCount = selection.totalCount),
                    moveResult = null,
                )
            }
            val videoSummary = mediaRepository.moveVideosToFolder(
                uris = selection.videoUris,
                targetFolderPath = targetFolderPath,
                shouldCancel = { shouldCancelMoveSelection },
                onProgress = { progress ->
                    updateMoveProgress(progress.copy(totalCount = selection.totalCount))
                },
            )
            val folderSummary = if (videoSummary.canceledCount > 0) {
                MediaMoveSummary(canceledCount = selection.folderPaths.distinct().size)
            } else {
                mediaRepository.moveFoldersToFolder(
                    folderPaths = selection.folderPaths,
                    targetFolderPath = targetFolderPath,
                    shouldCancel = { shouldCancelMoveSelection },
                    onProgress = { progress ->
                        updateMoveProgress(
                            progress.copy(
                                completedCount = selection.videoUris.distinct().size + progress.completedCount,
                                totalCount = selection.totalCount,
                            ),
                        )
                    },
                )
            }
            val summary = videoSummary + folderSummary
            when {
                summary.movedCount > 0 || summary.partiallyMovedCount > 0 -> moveSelectionStore.complete()
                summary.canceledCount > 0 -> moveSelectionStore.cancel()
            }
            uiStateInternal.update { currentState ->
                currentState.copy(
                    isMovingSelection = false,
                    moveProgress = null,
                    moveResult = summary,
                )
            }
        }
    }

    private fun restoreVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.restoreVideosFromRecycleBin(uris)
        }
    }

    private fun shareVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaService.shareMedia(uris.map { it.toUri() })
        }
    }

    private fun addFavorites(
        videos: List<Video>,
        folders: List<Folder>,
    ) {
        viewModelScope.launch {
            folders.forEach { folder -> favoriteRepository.upsert(folder.toFavoriteItem()) }
            videos.forEach { video -> favoriteRepository.upsert(video.toFavoriteItem()) }
        }
    }

    private fun addToPlaylist(
        playlistId: Long,
        videos: List<Video>,
        folders: List<Folder>,
    ) {
        viewModelScope.launch {
            playlistRepository.addVideos(
                playlistId = playlistId,
                videos = videos + folders.flatMap(Folder::allMediaList),
            )
        }
    }

    private fun createPlaylistAndAdd(
        title: String,
        videos: List<Video>,
        folders: List<Folder>,
    ) {
        viewModelScope.launch {
            val playlistId = playlistRepository.create(title)
            playlistRepository.addVideos(
                playlistId = playlistId,
                videos = videos + folders.flatMap(Folder::allMediaList),
            )
        }
    }

    private fun markVideosPlayed(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.markVideosAsPlayed(uris)
        }
    }

    private fun markVideosUnplayed(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.markVideosAsUnplayed(uris)
        }
    }

    private fun addToMediaInfoSynchronizer(uri: Uri) {
        mediaInfoSynchronizer.sync(uri)
    }

    private fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaService.renameMedia(uri, to)
        }
    }

    private fun refresh() {
        if (uiStateInternal.value.isRefreshing) return
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isRefreshing = true) }
            try {
                val moveSelection = uiStateInternal.value.moveSelection
                if (moveSelection == null) {
                    mediaSynchronizer.refresh()
                } else {
                    loadMoveTargetDirectory(moveSelection)
                }
            } finally {
                uiStateInternal.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun loadMoveTargetDirectory(selection: MediaPickerMoveSelection) {
        uiStateInternal.update { currentState ->
            currentState.copy(
                moveTargetDataState = DataState.Loading,
                moveSpaceCheck = null,
            )
        }
        runCatching {
            val content = mediaService.getMoveTargetDirectory(folderPath)
                ?: throw IllegalStateException("Move target directory is unavailable")
            val spaceCheck = folderPath
                ?.takeIf { targetPath -> content.canMoveHere && selection.canMoveTo(targetPath) }
                ?.let { targetPath ->
                    mediaService.checkMoveSpace(
                        videoUris = selection.videoUris.map(String::toUri),
                        folderPaths = selection.folderPaths,
                        targetFolderPath = targetPath,
                    )
                }
            content to spaceCheck
        }.onSuccess { (content, spaceCheck) ->
            uiStateInternal.update { currentState ->
                currentState.copy(
                    moveTargetDataState = DataState.Success(content),
                    moveSpaceCheck = spaceCheck,
                )
            }
        }.onFailure { throwable ->
            uiStateInternal.update { currentState ->
                currentState.copy(
                    moveTargetDataState = DataState.Error(throwable),
                    moveSpaceCheck = null,
                )
            }
        }
    }

    private fun updateMenu(transform: (ApplicationPreferences) -> ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences(transform)
        }
    }

    private fun excludeFolders(paths: List<String>) {
        val excludedPaths = paths.map(StoragePath::of)
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(excludeFolders = it.excludeFolders + excludedPaths.filterNot { path -> path in it.excludeFolders })
            }
        }
    }

    private fun cacheFolderSnapshot(folder: Folder) {
        snapshotCache.put(
            folderPath = folder.path,
            folder = folder,
            preferences = uiStateInternal.value.preferences,
            hasAllFilesAccess = hasManageExternalStorageAccess(),
        )
    }

    private fun refreshDeletedPathsAsync(paths: List<String>) {
        val distinctPaths = paths.filter(String::isNotBlank).distinct()
        if (distinctPaths.isEmpty()) return

        applicationScope.launch {
            distinctPaths.forEach { path ->
                runCatching {
                    mediaSynchronizer.refresh(path)
                }.onFailure { throwable ->
                    Logger.error(TAG, "Failed to refresh deleted path: $path", throwable)
                }
            }
        }
    }
}

@Stable
data class MediaPickerUiState(
    val folderPath: String?,
    val folderName: String?,
    val mediaDataState: DataState<Folder?> = DataState.Loading,
    val homeLandingPath: StoragePath? = null,
    // 自身直接含媒体的目录，扁平视图下路径面板据此裁掉不可浏览的中间层级
    val mediaBearingFolderPaths: Set<StoragePath> = emptySet(),
    val isRefreshing: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val playerPreferences: PlayerPreferences = PlayerPreferences(),
    val screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
    val moveSelection: MediaPickerMoveSelection? = null,
    val moveTargetDataState: DataState<MediaMoveTargetDirectoryContent> = DataState.Loading,
    val moveSpaceCheck: MediaMoveSpaceCheck? = null,
    val isMovingSelection: Boolean = false,
    val moveProgress: MediaMoveProgress? = null,
    val moveResult: MediaMoveSummary? = null,
    val moveSelectionResolution: MediaPickerMoveSelectionResolution? = null,
    val deleteResult: MediaPickerDeleteResult? = null,
    val playlists: List<Playlist> = emptyList(),
)

sealed interface MediaPickerUiEvent {
    data class DeleteVideos(val videos: List<SelectedVideo>) : MediaPickerUiEvent
    data class DeleteFolders(val folders: List<Folder>) : MediaPickerUiEvent
    data class MoveVideosToRecycleBin(val videos: List<SelectedVideo>) : MediaPickerUiEvent
    data class StartMoveSelection(
        val videoUris: List<String>,
        val folderPaths: List<String>,
    ) : MediaPickerUiEvent
    data class MoveSelectionToFolder(val targetFolderPath: String) : MediaPickerUiEvent
    data object CancelMoveSelection : MediaPickerUiEvent
    data object CancelRemainingMoveSelection : MediaPickerUiEvent
    data object ClearMoveResult : MediaPickerUiEvent
    data class RestoreVideos(val videos: List<String>) : MediaPickerUiEvent
    data class PermanentlyDeleteVideos(val videos: List<SelectedVideo>) : MediaPickerUiEvent
    data class ShareVideos(val videos: List<String>) : MediaPickerUiEvent
    data class AddFavorites(
        val videos: List<Video>,
        val folders: List<Folder>,
    ) : MediaPickerUiEvent
    data class AddToPlaylist(
        val playlistId: Long,
        val videos: List<Video>,
        val folders: List<Folder>,
    ) : MediaPickerUiEvent
    data class CreatePlaylistAndAdd(
        val title: String,
        val videos: List<Video>,
        val folders: List<Folder>,
    ) : MediaPickerUiEvent
    data class ExcludeFolders(val paths: List<String>) : MediaPickerUiEvent
    data class MarkVideosPlayed(val videos: List<String>) : MediaPickerUiEvent
    data class MarkVideosUnplayed(val videos: List<String>) : MediaPickerUiEvent
    data object Refresh : MediaPickerUiEvent
    data class RenameVideo(val uri: Uri, val to: String) : MediaPickerUiEvent
    data class AddToSync(val uri: Uri) : MediaPickerUiEvent
    data class UpdateMenu(val transform: (ApplicationPreferences) -> ApplicationPreferences) : MediaPickerUiEvent
    data class CacheFolderSnapshot(val folder: Folder) : MediaPickerUiEvent
    data object ClearDeleteResult : MediaPickerUiEvent
}

sealed interface MediaPickerDeleteResult {
    data object Deleted : MediaPickerDeleteResult
    data object MovedToRecycleBin : MediaPickerDeleteResult
    data object DeleteFailed : MediaPickerDeleteResult
}

private const val TAG = "MediaPickerViewModel"

@Stable
data class MediaPickerMoveSelection(
    val videoUris: List<String> = emptyList(),
    val videoParentPaths: List<String> = emptyList(),
    val folderPaths: List<String> = emptyList(),
    val folderParentPaths: List<String> = emptyList(),
) {
    val isEmpty: Boolean = videoUris.isEmpty() && folderPaths.isEmpty()
    val totalCount: Int = videoUris.distinct().size + folderPaths.distinct().size

    fun canMoveTo(targetFolderPath: String): Boolean {
        val targetPath = targetFolderPath.normalizedMovePath()
        if (targetPath in videoParentPaths.map(String::normalizedMovePath)) return false
        if (targetPath in folderParentPaths.map(String::normalizedMovePath)) return false
        return folderPaths.map(String::normalizedMovePath).none { folderPath ->
            targetPath == folderPath || targetPath.startsWith("$folderPath/")
        }
    }
}

private fun String.normalizedMovePath(): String = canonicalPathOrSelf().replace(File.separatorChar, '/')

// 主页在文件夹树视图下会下钻到首个有内容的目录，落点由主页写入，子目录页据此裁剪路径层级
@Singleton
class MediaPickerHomeStore @Inject constructor() {
    private val landingPathInternal = MutableStateFlow<StoragePath?>(null)
    val landingPath = landingPathInternal.asStateFlow()

    fun set(path: StoragePath) {
        landingPathInternal.value = path
    }
}

@Singleton
class MediaPickerMoveSelectionStore @Inject constructor() {
    private val selectionInternal = MutableStateFlow<MediaPickerMoveSelection?>(null)
    val selection = selectionInternal.asStateFlow()
    private val resolutionInternal = MutableStateFlow<MediaPickerMoveSelectionResolution?>(null)
    val resolution = resolutionInternal.asStateFlow()

    fun set(selection: MediaPickerMoveSelection) {
        resolutionInternal.value = null
        selectionInternal.value = selection.takeUnless(MediaPickerMoveSelection::isEmpty)
    }

    fun cancel() {
        selectionInternal.value = null
        resolutionInternal.value = MediaPickerMoveSelectionResolution.Canceled
    }

    fun complete() {
        selectionInternal.value = null
        resolutionInternal.value = MediaPickerMoveSelectionResolution.Completed
    }
}

enum class MediaPickerMoveSelectionResolution {
    Canceled,
    Completed,
}
