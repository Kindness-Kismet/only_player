package one.only.player.feature.videopicker.screens.search

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.data.repository.FavoriteRepository
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.SearchHistoryRepository
import one.only.player.core.data.repository.toFavoriteItem
import one.only.player.core.domain.GetPopularFoldersUseCase
import one.only.player.core.domain.SearchMediaUseCase
import one.only.player.core.domain.SearchResults
import one.only.player.core.domain.asRootFolder
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaInfoSynchronizer
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.feature.videopicker.screens.mediapicker.MediaPickerMoveSelection
import one.only.player.feature.videopicker.screens.mediapicker.MediaPickerMoveSelectionStore
import one.only.player.feature.videopicker.screens.mediapicker.MediaPickerSnapshotCache

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMediaUseCase: SearchMediaUseCase,
    private val getPopularFoldersUseCase: GetPopularFoldersUseCase,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val mediaSynchronizer: MediaSynchronizer,
    private val snapshotCache: MediaPickerSnapshotCache,
    private val moveSelectionStore: MediaPickerMoveSelectionStore,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(SearchUiState())
    val uiState = uiStateInternal.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        collectSearchHistory()
        collectPopularFolders()
        collectPreferences()
        collectPlayerPreferences()
        collectSearchResults()
    }

    private fun collectSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.searchHistory.collect { history ->
                uiStateInternal.update { it.copy(searchHistory = history) }
            }
        }
    }

    private fun collectPopularFolders() {
        viewModelScope.launch {
            getPopularFoldersUseCase(limit = 5).collect { folders ->
                uiStateInternal.update { it.copy(popularFolders = folders) }
            }
        }
    }

    private fun collectPreferences() {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update { it.copy(preferences = prefs) }
            }
        }
    }

    private fun collectPlayerPreferences() {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                uiStateInternal.update { it.copy(playerPreferences = prefs) }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun collectSearchResults() {
        viewModelScope.launch {
            searchQuery
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    searchMediaUseCase(query)
                }
                .collect { results ->
                    uiStateInternal.update {
                        it.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                    }
                }
        }
    }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.OnQueryChange -> onQueryChange(event.query)
            is SearchUiEvent.OnSearch -> onSearch(event.query)
            is SearchUiEvent.OnHistoryItemClick -> onHistoryItemClick(event.query)
            is SearchUiEvent.OnRemoveHistoryItem -> removeHistoryItem(event.query)
            is SearchUiEvent.OnClearHistory -> clearHistory()
            is SearchUiEvent.AddToSync -> addToMediaInfoSynchronizer(event.uri)
            is SearchUiEvent.CacheFolderSnapshot -> cacheFolderSnapshot(event.folder)
            is SearchUiEvent.StartMoveSelection -> startMoveSelection(event.videoUris, event.folderPaths)
            is SearchUiEvent.AddFavorites -> addFavorites(event.videos, event.folders)
            is SearchUiEvent.ShareVideos -> shareVideos(event.uris)
            is SearchUiEvent.MoveVideosToRecycleBin -> moveVideosToRecycleBin(event.uris)
            is SearchUiEvent.PermanentlyDeleteVideos -> permanentlyDeleteVideos(event.uris)
            is SearchUiEvent.RenameVideo -> renameVideo(event.uri, event.to)
        }
    }

    private fun onQueryChange(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = query.isNotBlank()) }
        searchQuery.value = query
    }

    private fun onSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchQuery(query)
        }
    }

    private fun onHistoryItemClick(query: String) {
        uiStateInternal.update { it.copy(query = query, isSearching = true) }
        searchQuery.value = query
        onSearch(query)
    }

    private fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeSearchQuery(query)
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearHistory()
        }
    }

    private fun addToMediaInfoSynchronizer(uri: Uri) {
        mediaInfoSynchronizer.sync(uri)
    }

    private fun startMoveSelection(
        videoUris: List<String>,
        folderPaths: List<String>,
    ) {
        val rootFolder = uiStateInternal.value.searchResults.asRootFolder()
        moveSelectionStore.set(
            MediaPickerMoveSelection(
                videoUris = videoUris,
                videoParentPaths = rootFolder.allMediaList
                    .filter { video -> video.uriString in videoUris }
                    .map(Video::parentPath),
                folderPaths = folderPaths,
                folderParentPaths = rootFolder.folderList
                    .filter { folder -> folder.path in folderPaths }
                    .mapNotNull(Folder::parentPath),
            ),
        )
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

    private fun shareVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaService.shareMedia(uris.map { it.toUri() })
        }
    }

    private fun moveVideosToRecycleBin(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.moveVideosToRecycleBin(uris)
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

    private fun renameVideo(
        uri: Uri,
        to: String,
    ) {
        viewModelScope.launch {
            mediaService.renameMedia(uri, to)
        }
    }

    private fun cacheFolderSnapshot(folder: Folder) {
        val preferences = uiStateInternal.value.preferences
        if (preferences.mediaViewMode == MediaViewMode.FOLDER_TREE) return

        snapshotCache.put(
            folderPath = folder.path,
            folder = folder,
            preferences = preferences,
            hasAllFilesAccess = hasManageExternalStorageAccess(),
        )
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

@Stable
data class SearchUiState(
    val query: String = "",
    val searchHistory: List<String> = emptyList(),
    val popularFolders: List<Folder> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val playerPreferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface SearchUiEvent {
    data class OnQueryChange(val query: String) : SearchUiEvent
    data class OnSearch(val query: String) : SearchUiEvent
    data class OnHistoryItemClick(val query: String) : SearchUiEvent
    data class OnRemoveHistoryItem(val query: String) : SearchUiEvent
    data object OnClearHistory : SearchUiEvent
    data class AddToSync(val uri: Uri) : SearchUiEvent
    data class CacheFolderSnapshot(val folder: Folder) : SearchUiEvent
    data class StartMoveSelection(
        val videoUris: List<String>,
        val folderPaths: List<String>,
    ) : SearchUiEvent
    data class AddFavorites(
        val videos: List<Video>,
        val folders: List<Folder>,
    ) : SearchUiEvent
    data class ShareVideos(val uris: List<String>) : SearchUiEvent
    data class MoveVideosToRecycleBin(val uris: List<String>) : SearchUiEvent
    data class PermanentlyDeleteVideos(val uris: List<String>) : SearchUiEvent
    data class RenameVideo(val uri: Uri, val to: String) : SearchUiEvent
}
