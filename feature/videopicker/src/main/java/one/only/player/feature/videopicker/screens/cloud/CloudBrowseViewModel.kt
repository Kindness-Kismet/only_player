package one.only.player.feature.videopicker.screens.cloud

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.common.Utils
import one.only.player.core.common.hasLocalNetworkPermission
import one.only.player.core.data.models.RemotePlaybackInfo
import one.only.player.core.data.remote.RemoteMediaResolver
import one.only.player.core.data.repository.FavoriteRepository
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.data.repository.buildRemoteFolderPlaybackAnchorKey
import one.only.player.core.data.repository.buildRemotePlaybackStateKey
import one.only.player.core.data.repository.toRemoteFavoriteItem
import one.only.player.core.media.info.RemoteMediaInfo
import one.only.player.core.media.info.RemoteMediaInfoReader
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol
import one.only.player.core.model.Video
import one.only.player.core.model.resolveQuickSettings

@HiltViewModel
class CloudBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: RemoteServerRepository,
    private val mediaRepository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
    private val preferencesRepository: PreferencesRepository,
    private val remoteMediaResolver: RemoteMediaResolver,
    private val remoteMediaInfoReader: RemoteMediaInfoReader,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val serverId: Long = savedStateHandle["serverId"] ?: 0L
    private val initialPath: String = (savedStateHandle["initialPath"] as? String)?.let(Uri::decode) ?: "/"

    private val _uiState = MutableStateFlow(
        CloudBrowseUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = _uiState.asStateFlow()
    private var fileInfoJob: Job? = null
    private var fileInfoRequestId = 0L
    private var hasStarted = false

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                _uiState.update { currentState ->
                    val previousSettings = currentState.preferences.cloudQuickSettings(currentState.server?.id).resolveQuickSettings(currentState.settingsDirectoryPath)
                    val settings = preferences.cloudQuickSettings(currentState.server?.id).resolveQuickSettings(currentState.settingsDirectoryPath)
                    val hasSortChanged = previousSettings.sort != settings.sort
                    currentState.copy(
                        preferences = preferences,
                        files = if (hasSortChanged) {
                            currentState.files.sortedForCloud(
                                preferences = preferences,
                                serverId = currentState.server?.id,
                                directoryPath = currentState.settingsDirectoryPath,
                            )
                        } else {
                            currentState.files
                        },
                    )
                }
            }
        }
        startIfAllowed()
    }

    fun onEvent(event: CloudBrowseEvent) {
        when (event) {
            is CloudBrowseEvent.LoadFileInfo -> loadFileInfo(event.file, event.documentUri)
            CloudBrowseEvent.DismissFileInfo -> dismissFileInfo()
            CloudBrowseEvent.Retry -> {
                if (_uiState.value.server == null) {
                    startIfAllowed()
                } else {
                    loadCurrentDirectory(forceRefresh = true)
                }
            }
            CloudBrowseEvent.RefreshPlaybackStates -> loadPlaybackStates()
            is CloudBrowseEvent.AddFavorites -> addFavorites(event.files)
            is CloudBrowseEvent.UpdateQuickSettings -> updateQuickSettings(event.transform)
        }
    }

    private fun startIfAllowed() {
        if (!hasLocalNetworkPermission(context)) return
        if (hasStarted) return
        hasStarted = true
        loadServer()
    }

    private fun loadServer() {
        viewModelScope.launch {
            val server = repository.getById(serverId)
            if (server == null) {
                _uiState.update { it.copy(isError = true, errorMessage = "Server not found") }
                return@launch
            }
            val serverRootPath = remoteMediaResolver.normalizeDirectoryPath(server, server.path)
            val initialDirectoryPath = remoteMediaResolver.normalizeDirectoryPath(server, initialPath)
            val startPath = initialDirectoryPath.takeIf { it != "/" || serverRootPath == "/" } ?: serverRootPath
            _uiState.update {
                it.copy(
                    server = server,
                    currentPath = startPath,
                    isAtRoot = remoteMediaResolver.isAtServerRoot(startPath, server),
                )
            }
            loadCurrentDirectory()
        }
    }

    private fun loadCurrentDirectory(forceRefresh: Boolean = false) {
        val currentState = _uiState.value
        val server = currentState.server ?: return
        val path = currentState.currentPath

        if (currentState.isLoading) return
        if (!forceRefresh && currentState.hasLoadedDirectory) return

        _uiState.update {
            it.copy(
                isLoading = true,
                isRefreshing = forceRefresh && currentState.hasLoadedDirectory,
                isError = false,
            )
        }

        viewModelScope.launch(ioDispatcher) {
            remoteMediaResolver.listBrowsableFiles(server, path, forceRefresh = forceRefresh)
                .onSuccess { files ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            files = files.sortedForCloud(
                                preferences = it.preferences,
                                serverId = server.id,
                                directoryPath = it.settingsDirectoryPath,
                            ),
                            isError = false,
                            errorMessage = "",
                            playbackStates = emptyMap(),
                            restoreTargetFilePath = null,
                            hasLoadedDirectory = true,
                        )
                    }
                    loadPlaybackStates(
                        server = server,
                        directoryPath = path,
                        files = files,
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isError = true,
                            errorMessage = error.message ?: "Unknown error",
                        )
                    }
                }
        }
    }

    private fun loadPlaybackStates(
        server: RemoteServer? = _uiState.value.server,
        directoryPath: String = _uiState.value.currentPath,
        files: List<RemoteFile> = _uiState.value.files,
    ) {
        val currentServer = server ?: return
        val videoFiles = files.filter { !it.isDirectory }
        if (videoFiles.isEmpty()) {
            _uiState.update {
                it.copy(
                    playbackStates = emptyMap(),
                    restoreTargetFilePath = null,
                )
            }
            return
        }

        val protocol = remoteMediaResolver.protocolKey(currentServer.protocol)

        val pathToKey = videoFiles.mapNotNull { file ->
            val key = buildRemotePlaybackStateKey(
                remoteProtocol = protocol,
                remoteServerId = currentServer.id,
                remoteFilePath = file.path,
            ) ?: return@mapNotNull null
            file.path to key
        }

        viewModelScope.launch(ioDispatcher) {
            val stateKeys = pathToKey.map { it.second }
            val states = mediaRepository.getRemotePlaybackStates(stateKeys)
            val keyToPath = pathToKey.associate { (path, key) -> key to path }
            val playbackStates = states.entries.associate { (key, info) ->
                (keyToPath[key] ?: key) to info
            }
            val restoreTargetFilePath = buildRemoteFolderPlaybackAnchorKey(
                remoteProtocol = protocol,
                remoteServerId = currentServer.id,
                directoryPath = directoryPath,
            )?.let { anchorKey ->
                preferencesRepository.applicationPreferences.value.remoteFolderLastPlayedMediaPaths[anchorKey]
            }
            _uiState.update { currentState ->
                if (currentState.currentPath != directoryPath) {
                    return@update currentState
                }
                currentState.copy(
                    playbackStates = playbackStates,
                    restoreTargetFilePath = restoreTargetFilePath,
                )
            }
        }
    }

    fun buildPlayUrl(file: RemoteFile): String? {
        val server = _uiState.value.server ?: return null
        return remoteMediaResolver.buildPlayUrl(server, file)
    }

    fun buildAllVideoPlayUrls(): List<Uri> {
        val server = _uiState.value.server ?: return emptyList()
        return remoteMediaResolver.buildVideoPlaylist(server, _uiState.value.files)
    }

    fun buildAllVideoRemotePaths(): List<String> = remoteMediaResolver.buildVideoPlaylistRemotePaths(_uiState.value.files)

    fun buildCurrentDirectoryDocumentId(): String? {
        val server = _uiState.value.server ?: return null
        return remoteMediaResolver.buildCurrentDirectoryDocumentId(server, _uiState.value.currentPath)
    }

    fun buildFileDocumentId(file: RemoteFile): String? {
        val server = _uiState.value.server ?: return null
        return remoteMediaResolver.buildDocumentId(server, file.path)
    }

    fun buildAuthHeaders(file: RemoteFile): Map<String, String> {
        val server = _uiState.value.server ?: return emptyMap()
        return remoteMediaResolver.buildAuthHeaders(server, file)
    }

    private fun loadFileInfo(
        file: RemoteFile,
        documentUri: Uri,
    ) {
        val server = _uiState.value.server ?: return
        if (file.isDirectory) return
        if (_uiState.value.isLoadingFileInfo) return

        val requestId = ++fileInfoRequestId
        _uiState.update {
            it.copy(
                isLoadingFileInfo = true,
                infoVideo = null,
            )
        }

        fileInfoJob = viewModelScope.launch {
            val probeUrl = buildProbeUrl(server, file)
            val mediaInfo = remoteMediaInfoReader.read(
                probeUrl = probeUrl,
                documentUri = documentUri,
            ).getOrNull()
            val video = file.toVideo(
                documentUri = documentUri,
                playUrl = buildPlayUrl(file),
                mediaInfo = mediaInfo,
            )
            _uiState.update { currentState ->
                if (requestId != fileInfoRequestId) {
                    return@update currentState
                }
                fileInfoJob = null
                currentState.copy(
                    isLoadingFileInfo = false,
                    infoVideo = video,
                )
            }
        }
    }

    private fun dismissFileInfo() {
        fileInfoRequestId++
        fileInfoJob?.cancel()
        fileInfoJob = null
        _uiState.update {
            it.copy(
                isLoadingFileInfo = false,
                infoVideo = null,
            )
        }
    }

    private fun updateQuickSettings(transform: (ApplicationPreferences) -> ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences(transform)
        }
    }

    private fun addFavorites(files: List<RemoteFile>) {
        val server = _uiState.value.server ?: return
        viewModelScope.launch {
            files.forEach { file -> favoriteRepository.upsert(file.toRemoteFavoriteItem(server)) }
        }
    }

    private fun buildProbeUrl(
        server: RemoteServer,
        file: RemoteFile,
    ): String? {
        val playUrl = when (server.protocol) {
            ServerProtocol.WEBDAV,
            ServerProtocol.FTP,
            -> remoteMediaResolver.buildPlayUrl(server, file)
            ServerProtocol.SMB -> return null
        }
        if (server.username.isBlank()) return playUrl

        val uri = Uri.parse(playUrl)
        val authority = uri.encodedAuthority ?: return playUrl
        val userInfo = "${Uri.encode(server.username)}:${Uri.encode(server.password)}"
        return uri.buildUpon()
            .encodedAuthority("$userInfo@$authority")
            .build()
            .toString()
    }

    private fun RemoteFile.toVideo(
        documentUri: Uri,
        playUrl: String?,
        mediaInfo: RemoteMediaInfo?,
    ): Video {
        val videoStream = mediaInfo?.videoStream
        val duration = mediaInfo?.duration?.takeIf { it > 0 } ?: 0L
        return Video(
            id = path.hashCode().toLong(),
            path = path,
            parentPath = parentPath(),
            duration = duration,
            uriString = playUrl ?: documentUri.toString(),
            nameWithExtension = name,
            width = videoStream?.frameWidth ?: 0,
            height = videoStream?.frameHeight ?: 0,
            size = size,
            formattedDuration = duration.takeIf { it > 0 }?.let(Utils::formatDurationMillis).orEmpty(),
            formattedFileSize = Utils.formatFileSize(size),
            format = mediaInfo?.format ?: contentType.takeIf { it.isNotBlank() },
            videoStream = videoStream,
            audioStreams = mediaInfo?.audioStreams.orEmpty(),
            subtitleStreams = mediaInfo?.subtitleStreams.orEmpty(),
        )
    }

    private fun RemoteFile.parentPath(): String {
        val parentPath = path.trimEnd('/').substringBeforeLast("/", missingDelimiterValue = "")
        return parentPath.ifBlank { "/" }
    }

    private fun List<RemoteFile>.sortedForCloud(
        preferences: ApplicationPreferences,
        serverId: Long?,
        directoryPath: String,
    ): List<RemoteFile> {
        val settings = preferences.cloudQuickSettings(serverId).resolveQuickSettings(directoryPath)
        val comparator = settings.sort.toSort().remoteFileComparator()
        val (folders, videos) = partition(RemoteFile::isDirectory)
        return folders.sortedWith(comparator) + videos.sortedWith(comparator)
    }
}

@Stable
data class CloudBrowseUiState(
    val server: RemoteServer? = null,
    val currentPath: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedDirectory: Boolean = false,
    val isError: Boolean = false,
    val isAtRoot: Boolean = true,
    val errorMessage: String = "",
    val playbackStates: Map<String, RemotePlaybackInfo> = emptyMap(),
    val restoreTargetFilePath: String? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val isLoadingFileInfo: Boolean = false,
    val infoVideo: Video? = null,
)

sealed interface CloudBrowseEvent {
    data class LoadFileInfo(
        val file: RemoteFile,
        val documentUri: Uri,
    ) : CloudBrowseEvent

    data object DismissFileInfo : CloudBrowseEvent
    data object Retry : CloudBrowseEvent
    data object RefreshPlaybackStates : CloudBrowseEvent
    data class AddFavorites(val files: List<RemoteFile>) : CloudBrowseEvent
    data class UpdateQuickSettings(val transform: (ApplicationPreferences) -> ApplicationPreferences) : CloudBrowseEvent
}

internal val CloudBrowseUiState.settingsDirectoryPath: String
    get() = currentPath.trimEnd('/').ifEmpty { "/" }.let { path ->
        if (server?.protocol == ServerProtocol.SMB) path.lowercase() else path
    }
