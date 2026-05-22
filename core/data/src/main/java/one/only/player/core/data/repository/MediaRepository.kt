package one.only.player.core.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import one.only.player.core.data.models.RemotePlaybackInfo
import one.only.player.core.data.models.VideoState
import one.only.player.core.model.Folder
import one.only.player.core.model.Video

interface MediaRepository {
    fun getVideosFlow(): Flow<List<Video>>
    fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>>
    fun getRecycleBinVideosFlow(): Flow<List<Video>>
    fun getFoldersFlow(): Flow<List<Folder>>

    suspend fun getVideoByUri(uri: String): Video?
    suspend fun getVideoState(uri: String): VideoState?
    suspend fun getVideoState(uris: List<String>): VideoState?
    suspend fun getCanonicalMediaUri(uri: String): String
    suspend fun getRemotePlaybackStates(stateKeys: List<String>): Map<String, RemotePlaybackInfo>

    suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long)
    suspend fun updateMediumPosition(uri: String, position: Long)
    suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float)
    suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int)
    suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int)
    suspend fun updateMediumZoom(uri: String, zoom: Float)
    suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri)
    suspend fun updateExternalSubs(uri: String, externalSubs: List<Uri>)
    suspend fun updateSubtitleDelay(uri: String, delay: Long)
    suspend fun updateSubtitleSpeed(uri: String, speed: Float)
    suspend fun moveVideosToRecycleBin(uris: List<String>)
    suspend fun moveVideosToFolder(
        uris: List<String>,
        targetFolderPath: String,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): MediaMoveSummary

    suspend fun moveFoldersToFolder(
        folderPaths: List<String>,
        targetFolderPath: String,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Int) -> Unit = {},
    ): MediaMoveSummary
    suspend fun restoreVideosFromRecycleBin(uris: List<String>)
}

data class MediaMoveSummary(
    val movedCount: Int = 0,
    val failedCount: Int = 0,
    val canceledCount: Int = 0,
) {
    operator fun plus(other: MediaMoveSummary): MediaMoveSummary = MediaMoveSummary(
        movedCount = movedCount + other.movedCount,
        failedCount = failedCount + other.failedCount,
        canceledCount = canceledCount + other.canceledCount,
    )
}
