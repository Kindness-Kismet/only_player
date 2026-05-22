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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.extensions.canonicalPathOrSelf
import one.only.player.core.common.extensions.prettyName
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.data.repository.MediaMoveSummary
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.domain.GetSortedMediaUseCase
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaInfoSynchronizer
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.Video
import one.only.player.core.ui.base.DataState
import one.only.player.feature.videopicker.navigation.FolderArgs
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedMediaUseCase: GetSortedMediaUseCase,
    savedStateHandle: SavedStateHandle,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val mediaSynchronizer: MediaSynchronizer,
    private val snapshotCache: MediaPickerSnapshotCache,
    private val moveSelectionStore: MediaPickerMoveSelectionStore,
) : ViewModel() {

    private val folderArgs = FolderArgs(savedStateHandle)

    val folderPath = folderArgs.folderId
    private val screenMode = folderArgs.screenMode

    private var shouldCancelMoveSelection = false

    private val initialPreferences = preferencesRepository.applicationPreferences.value
    private val initialMediaDataState: DataState<Folder?> = snapshotCache.get(
        folderPath = folderPath,
        preferences = initialPreferences,
        hasAllFilesAccess = hasManageExternalStorageAccess(),
    )
        ?.takeIf { screenMode == MediaPickerScreenMode.LIBRARY }
        ?.let { folder -> DataState.Success(folder) }
        ?: DataState.Loading

    private val uiStateInternal = MutableStateFlow(
        MediaPickerUiState(
            folderPath = folderPath,
            folderName = folderPath?.let { File(folderPath).prettyName },
            mediaDataState = initialMediaDataState,
            preferences = initialPreferences,
            screenMode = screenMode,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
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
                }
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        mediaDataState = DataState.Success(folder),
                    )
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
            moveSelectionStore.selection.collect { selection ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        moveSelection = selection,
                    )
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
            is MediaPickerUiEvent.ExcludeFolders -> excludeFolders(event.paths)
            is MediaPickerUiEvent.Refresh -> refresh()
            is MediaPickerUiEvent.RenameVideo -> renameVideo(event.uri, event.to)
            is MediaPickerUiEvent.AddToSync -> addToMediaInfoSynchronizer(event.uri)
            is MediaPickerUiEvent.UpdateMenu -> updateMenu(event.preferences)
            is MediaPickerUiEvent.CacheFolderSnapshot -> cacheFolderSnapshot(event.folder)
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
        }
    }

    private fun permanentlyDeleteVideos(uris: List<String>) {
        viewModelScope.launch {
            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.refresh()
            }
        }
    }

    private fun moveVideosToRecycleBin(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.moveVideosToRecycleBin(uris)
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
                folderParentPaths = rootFolder?.folderList
                    ?.filter { folder -> folder.path in folderPaths }
                    ?.mapNotNull(Folder::parentPath)
                    .orEmpty(),
            ),
        )
    }

    private fun cancelMoveSelection() {
        if (uiStateInternal.value.isMovingSelection) return
        moveSelectionStore.clear()
    }

    private fun cancelRemainingMoveSelection() {
        shouldCancelMoveSelection = true
    }

    private fun clearMoveResult() {
        uiStateInternal.update { currentState ->
            currentState.copy(moveResult = null)
        }
    }

    private fun updateMoveProgress(completedCount: Int) {
        uiStateInternal.update { currentState ->
            currentState.copy(
                moveProgress = currentState.moveProgress?.copy(completedCount = completedCount),
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
                    moveProgress = MediaPickerMoveProgress(totalCount = selection.totalCount),
                    moveResult = null,
                )
            }
            val videoSummary = mediaRepository.moveVideosToFolder(
                uris = selection.videoUris,
                targetFolderPath = targetFolderPath,
                shouldCancel = { shouldCancelMoveSelection },
                onProgress = ::updateMoveProgress,
            )
            val folderSummary = if (videoSummary.canceledCount > 0) {
                MediaMoveSummary(canceledCount = selection.folderPaths.distinct().size)
            } else {
                mediaRepository.moveFoldersToFolder(
                    folderPaths = selection.folderPaths,
                    targetFolderPath = targetFolderPath,
                    shouldCancel = { shouldCancelMoveSelection },
                    onProgress = { completedCount ->
                        updateMoveProgress(selection.videoUris.distinct().size + completedCount)
                    },
                )
            }
            val summary = videoSummary + folderSummary
            if (summary.movedCount > 0) {
                moveSelectionStore.clear()
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

    private fun addToMediaInfoSynchronizer(uri: Uri) {
        mediaInfoSynchronizer.sync(uri)
    }

    private fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaService.renameMedia(uri, to)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(isRefreshing = true) }
            mediaSynchronizer.refresh()
            uiStateInternal.update { it.copy(isRefreshing = false) }
        }
    }

    private fun updateMenu(preferences: ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences }
        }
    }

    private fun excludeFolders(paths: List<String>) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(excludeFolders = it.excludeFolders + paths.filter { path -> path !in it.excludeFolders })
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
}

@Stable
data class MediaPickerUiState(
    val folderPath: String?,
    val folderName: String?,
    val mediaDataState: DataState<Folder?> = DataState.Loading,
    val isRefreshing: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
    val moveSelection: MediaPickerMoveSelection? = null,
    val isMovingSelection: Boolean = false,
    val moveProgress: MediaPickerMoveProgress? = null,
    val moveResult: MediaMoveSummary? = null,
)

sealed interface MediaPickerUiEvent {
    data class DeleteVideos(val videos: List<String>) : MediaPickerUiEvent
    data class DeleteFolders(val folders: List<Folder>) : MediaPickerUiEvent
    data class MoveVideosToRecycleBin(val videos: List<String>) : MediaPickerUiEvent
    data class StartMoveSelection(
        val videoUris: List<String>,
        val folderPaths: List<String>,
    ) : MediaPickerUiEvent
    data class MoveSelectionToFolder(val targetFolderPath: String) : MediaPickerUiEvent
    data object CancelMoveSelection : MediaPickerUiEvent
    data object CancelRemainingMoveSelection : MediaPickerUiEvent
    data object ClearMoveResult : MediaPickerUiEvent
    data class RestoreVideos(val videos: List<String>) : MediaPickerUiEvent
    data class PermanentlyDeleteVideos(val videos: List<String>) : MediaPickerUiEvent
    data class ShareVideos(val videos: List<String>) : MediaPickerUiEvent
    data class ExcludeFolders(val paths: List<String>) : MediaPickerUiEvent
    data object Refresh : MediaPickerUiEvent
    data class RenameVideo(val uri: Uri, val to: String) : MediaPickerUiEvent
    data class AddToSync(val uri: Uri) : MediaPickerUiEvent
    data class UpdateMenu(val preferences: ApplicationPreferences) : MediaPickerUiEvent
    data class CacheFolderSnapshot(val folder: Folder) : MediaPickerUiEvent
}

@Stable
data class MediaPickerMoveProgress(
    val completedCount: Int = 0,
    val totalCount: Int = 0,
)

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
        val targetPath = targetFolderPath.canonicalPathOrSelf()
        if (targetPath in videoParentPaths.map(String::canonicalPathOrSelf)) return false
        if (targetPath in folderParentPaths.map(String::canonicalPathOrSelf)) return false
        return folderPaths.map(String::canonicalPathOrSelf).none { folderPath ->
            targetPath == folderPath || targetPath.startsWith("$folderPath/")
        }
    }
}

@Singleton
class MediaPickerMoveSelectionStore @Inject constructor() {
    private val selectionInternal = MutableStateFlow<MediaPickerMoveSelection?>(null)
    val selection = selectionInternal.asStateFlow()

    fun set(selection: MediaPickerMoveSelection) {
        selectionInternal.value = selection.takeUnless(MediaPickerMoveSelection::isEmpty)
    }

    fun clear() {
        selectionInternal.value = null
    }
}
