package one.only.player.feature.player.service.playback

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.buildRemoteFolderPlaybackAnchorKey
import one.only.player.feature.player.extensions.localParentPath
import one.only.player.feature.player.extensions.remoteDirectoryPath
import one.only.player.feature.player.extensions.remoteFilePath
import one.only.player.feature.player.extensions.remoteProtocol
import one.only.player.feature.player.extensions.remoteServerId

internal class FolderPlaybackAnchorUpdater(
    private val scope: CoroutineScope,
    private val preferencesRepository: PreferencesRepository,
    private val mediaRepository: MediaRepository,
    private val resolvePlaybackStateUri: suspend (MediaItem) -> String,
) {

    fun update(mediaItem: MediaItem) {
        val preferences = preferencesRepository.applicationPreferences.value
        if (!preferences.shouldRestoreLastPlayedMediaInFolders) return

        scope.launch {
            val playbackStateUri = resolvePlaybackStateUri(mediaItem)
            val localParentPath = mediaItem.mediaMetadata.localParentPath
                ?: mediaRepository.getVideoByUri(playbackStateUri)?.parentPath
                    ?.takeIf { it.isNotBlank() }
            val remoteAnchorKey = buildRemoteFolderPlaybackAnchorKey(
                remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
                remoteServerId = mediaItem.mediaMetadata.remoteServerId,
                directoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,
            )

            preferencesRepository.updateApplicationPreferences { currentPreferences ->
                var updatedPreferences = currentPreferences

                if (!localParentPath.isNullOrBlank()) {
                    updatedPreferences = updatedPreferences.copy(
                        localFolderLastPlayedMediaUris = updatedPreferences.localFolderLastPlayedMediaUris +
                            (localParentPath to playbackStateUri),
                    )
                }

                if (remoteAnchorKey != null) {
                    val remoteFilePath = mediaItem.mediaMetadata.remoteFilePath ?: return@updateApplicationPreferences updatedPreferences
                    updatedPreferences = updatedPreferences.copy(
                        remoteFolderLastPlayedMediaPaths = updatedPreferences.remoteFolderLastPlayedMediaPaths +
                            (remoteAnchorKey to remoteFilePath),
                    )
                }

                updatedPreferences
            }
        }
    }
}
