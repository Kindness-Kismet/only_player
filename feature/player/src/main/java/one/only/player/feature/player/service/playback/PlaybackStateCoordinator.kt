package one.only.player.feature.player.service.playback

import androidx.media3.common.MediaItem
import kotlin.math.abs
import one.only.player.core.data.models.VideoState
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.buildPlaybackStateCandidates
import one.only.player.core.data.repository.buildRemotePlaybackStateKey
import one.only.player.core.data.repository.isRemotePlaybackStateKey
import one.only.player.feature.player.extensions.remoteFilePath
import one.only.player.feature.player.extensions.remoteProtocol
import one.only.player.feature.player.extensions.remoteServerId

internal class PlaybackStateCoordinator(
    private val mediaRepository: MediaRepository,
) {

    fun playbackStateCandidates(mediaItem: MediaItem): List<String> = buildPlaybackStateCandidates(
        originalUri = mediaItem.mediaId,
        remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
        remoteServerId = mediaItem.mediaMetadata.remoteServerId,
        remoteFilePath = mediaItem.mediaMetadata.remoteFilePath,
    )

    suspend fun resolvePlaybackStateUri(mediaItem: MediaItem): String = mediaRepository.getCanonicalMediaUri(
        uri = buildRemotePlaybackStateKey(
            remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
            remoteServerId = mediaItem.mediaMetadata.remoteServerId,
            remoteFilePath = mediaItem.mediaMetadata.remoteFilePath,
        ) ?: mediaItem.mediaId,
    )

    suspend fun migrateFallbackStateToPlaybackStateUri(
        playbackStateUri: String,
        primaryVideoState: VideoState?,
        fallbackVideoState: VideoState?,
    ) {
        if (fallbackVideoState == null) return
        if (fallbackVideoState.path == playbackStateUri) return
        if (fallbackVideoState.path.isRemotePlaybackStateKey()) return

        if (primaryVideoState?.position == null) {
            fallbackVideoState.position?.let { position ->
                mediaRepository.updateMediumPosition(
                    uri = playbackStateUri,
                    position = position,
                )
            }
        }
        if (primaryVideoState?.audioTrackIndex == null) {
            fallbackVideoState.audioTrackIndex?.let { audioTrackIndex ->
                mediaRepository.updateMediumAudioTrack(
                    uri = playbackStateUri,
                    audioTrackIndex = audioTrackIndex,
                )
            }
        }
        if (primaryVideoState?.subtitleTrackIndex == null) {
            fallbackVideoState.subtitleTrackIndex?.let { subtitleTrackIndex ->
                mediaRepository.updateMediumSubtitleTrack(
                    uri = playbackStateUri,
                    subtitleTrackIndex = subtitleTrackIndex,
                )
            }
        }
        if (primaryVideoState?.externalSubs.isNullOrEmpty() && fallbackVideoState.externalSubs.isNotEmpty()) {
            mediaRepository.updateExternalSubs(
                uri = playbackStateUri,
                externalSubs = fallbackVideoState.externalSubs,
            )
        }
        if ((primaryVideoState?.subtitleDelayMilliseconds ?: 0L) == 0L && fallbackVideoState.subtitleDelayMilliseconds != 0L) {
            mediaRepository.updateSubtitleDelay(
                uri = playbackStateUri,
                delay = fallbackVideoState.subtitleDelayMilliseconds,
            )
        }
        if (
            abs((primaryVideoState?.subtitleSpeed ?: 1f) - 1f) <= STATE_FLOAT_EPSILON &&
            abs(fallbackVideoState.subtitleSpeed - 1f) > STATE_FLOAT_EPSILON
        ) {
            mediaRepository.updateSubtitleSpeed(
                uri = playbackStateUri,
                speed = fallbackVideoState.subtitleSpeed,
            )
        }
        if (
            abs((primaryVideoState?.videoScale ?: 1f) - 1f) <= STATE_FLOAT_EPSILON &&
            abs(fallbackVideoState.videoScale - 1f) > STATE_FLOAT_EPSILON
        ) {
            mediaRepository.updateMediumZoom(
                uri = playbackStateUri,
                zoom = fallbackVideoState.videoScale,
            )
        }
    }

    fun mergeVideoState(
        primaryVideoState: VideoState?,
        fallbackVideoState: VideoState?,
    ): VideoState? {
        if (primaryVideoState == null) return fallbackVideoState
        if (fallbackVideoState == null) return primaryVideoState

        return primaryVideoState.copy(
            position = primaryVideoState.position ?: fallbackVideoState.position,
            audioTrackIndex = primaryVideoState.audioTrackIndex ?: fallbackVideoState.audioTrackIndex,
            subtitleTrackIndex = primaryVideoState.subtitleTrackIndex ?: fallbackVideoState.subtitleTrackIndex,
            playbackSpeed = primaryVideoState.playbackSpeed ?: fallbackVideoState.playbackSpeed,
            externalSubs = primaryVideoState.externalSubs.ifEmpty { fallbackVideoState.externalSubs },
            videoScale = primaryVideoState.videoScale.takeUnless { abs(it - 1f) <= STATE_FLOAT_EPSILON }
                ?: fallbackVideoState.videoScale,
            subtitleDelayMilliseconds = primaryVideoState.subtitleDelayMilliseconds.takeUnless { it == 0L }
                ?: fallbackVideoState.subtitleDelayMilliseconds,
            subtitleSpeed = primaryVideoState.subtitleSpeed.takeUnless { abs(it - 1f) <= STATE_FLOAT_EPSILON }
                ?: fallbackVideoState.subtitleSpeed,
        )
    }

    private companion object {
        private const val STATE_FLOAT_EPSILON = 0.001f
    }
}
