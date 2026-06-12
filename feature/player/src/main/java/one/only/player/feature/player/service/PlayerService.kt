package one.only.player.feature.player.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.deleteFiles
import one.only.player.core.common.extensions.getFilenameFromUri
import one.only.player.core.common.extensions.getPath
import one.only.player.core.common.extensions.isMpegTsStream
import one.only.player.core.common.extensions.subtitleCacheDir
import one.only.player.core.common.media.MpegTsProgramMapPidFix
import one.only.player.core.common.media.detectMpegTsProgramMapPidFix
import one.only.player.core.common.media.patchMpegTsProgramMapPid
import one.only.player.core.common.media.toMpegTsPidHex
import one.only.player.core.data.remote.FtpClient
import one.only.player.core.data.remote.SmbClient
import one.only.player.core.data.remote.WebDavClient
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.LoopMode
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Resume
import one.only.player.core.ui.R as coreUiR
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.player.datasource.FtpDataSource
import one.only.player.feature.player.datasource.SmbDataSource
import one.only.player.feature.player.engine.media3.SeekMapInjectingExtractor
import one.only.player.feature.player.extensions.addAdditionalSubtitleConfiguration
import one.only.player.feature.player.extensions.audioTrackIndex
import one.only.player.feature.player.extensions.copy
import one.only.player.feature.player.extensions.getManuallySelectedTrackIndex
import one.only.player.feature.player.extensions.isApproximateSeekEnabled
import one.only.player.feature.player.extensions.isVideoEffectsAvailable
import one.only.player.feature.player.extensions.positionMs
import one.only.player.feature.player.extensions.remoteFilePath
import one.only.player.feature.player.extensions.remoteProtocol
import one.only.player.feature.player.extensions.remoteServerId
import one.only.player.feature.player.extensions.requestHeaders
import one.only.player.feature.player.extensions.setExtras
import one.only.player.feature.player.extensions.setIsScrubbingModeEnabled
import one.only.player.feature.player.extensions.subtitleDelayMilliseconds
import one.only.player.feature.player.extensions.subtitleSpeed
import one.only.player.feature.player.extensions.subtitleTrackIndex
import one.only.player.feature.player.extensions.switchTrack
import one.only.player.feature.player.extensions.uriToSubtitleConfiguration
import one.only.player.feature.player.extensions.videoZoom
import one.only.player.feature.player.service.artwork.PlaybackArtworkLoader
import one.only.player.feature.player.service.audio.AudioEffectsCoordinator
import one.only.player.feature.player.service.decoder.NormalizingRenderersFactory
import one.only.player.feature.player.service.decoder.extensionRendererMode
import one.only.player.feature.player.service.decoder.logName
import one.only.player.feature.player.service.decoder.shouldEnableDecoderFallback
import one.only.player.feature.player.service.decoder.shouldRetryWithSoftwareDecoder
import one.only.player.feature.player.service.decoder.shouldUseAudioExtensionFallback
import one.only.player.feature.player.service.effects.VideoEffectsCoordinator
import one.only.player.feature.player.service.effects.isHdrVideoFormat
import one.only.player.feature.player.service.effects.shouldApplyVideoEffects
import one.only.player.feature.player.service.effects.toVideoFilterPreferences
import one.only.player.feature.player.service.playback.FolderPlaybackAnchorUpdater
import one.only.player.feature.player.service.playback.PlaybackStartupAnalyticsListener
import one.only.player.feature.player.service.playback.PlaybackStateCoordinator
import one.only.player.feature.player.service.seek.PreciseSeekCoordinator
import one.only.player.feature.player.service.subtitle.ExternalSubtitleLoader
import one.only.player.feature.player.service.subtitle.SubtitleTrackSelector
import one.only.player.feature.player.subtitle.AssHandlerRegistry
import one.only.player.feature.player.subtitle.NormalizingAssMatroskaExtractor
import one.only.player.feature.player.subtitle.OnlineSubtitleRepository

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "PlayerService"
        private const val LOCAL_MIN_BUFFER_MS = 250
        private const val LOCAL_MAX_BUFFER_MS = 30_000
        private const val LOCAL_BUFFER_FOR_PLAYBACK_MS = 150
        private const val LOCAL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 150
        private val FAST_SEEK_PARAMETERS = SeekParameters.CLOSEST_SYNC
        private val EXACT_SEEK_PARAMETERS = SeekParameters.DEFAULT
        private val REMOTE_SOURCE_URI_SCHEMES = setOf("smb", "ftp")
    }

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var onlineSubtitleRepository: OnlineSubtitleRepository

    @Inject
    lateinit var webDavClient: WebDavClient

    @Inject
    lateinit var smbClient: SmbClient

    @Inject
    lateinit var ftpClient: FtpClient

    @Inject
    lateinit var imageLoader: ImageLoader

    private val playerPreferences: PlayerPreferences
        get() = preferencesRepository.playerPreferences.value

    private val artworkLoader by lazy {
        PlaybackArtworkLoader(
            context = applicationContext,
            imageLoader = imageLoader,
            scope = serviceScope,
            findMediaItem = ::findMediaItemInSession,
        )
    }

    private val playbackStateCoordinator by lazy {
        PlaybackStateCoordinator(mediaRepository)
    }

    private val folderPlaybackAnchorUpdater by lazy {
        FolderPlaybackAnchorUpdater(
            scope = serviceScope,
            preferencesRepository = preferencesRepository,
            mediaRepository = mediaRepository,
            resolvePlaybackStateUri = playbackStateCoordinator::resolvePlaybackStateUri,
        )
    }

    private val customCommands = CustomCommands.asSessionCommands()

    private var isMediaItemReady = false

    private val audioEffectsCoordinator = AudioEffectsCoordinator()
    private val videoEffectsCoordinator = VideoEffectsCoordinator(
        scope = serviceScope,
        currentPreferencesProvider = ::playerPreferences,
        currentPlayerProvider = { mediaSession?.player as? ExoPlayer },
    )
    private val subtitleTrackSelector = SubtitleTrackSelector { playerPreferences.preferredSubtitleLanguage }
    private val externalSubtitleLoader by lazy {
        ExternalSubtitleLoader(
            context = applicationContext,
            mediaRepository = mediaRepository,
            webDavClient = webDavClient,
            smbClient = smbClient,
            ftpClient = ftpClient,
        )
    }
    private val mediaParserRetried = mutableSetOf<String>()
    private val softwareDecoderRetried = mutableSetOf<String>()
    private var isPendingExternalSubAutoSelect = false
    private var pendingRememberedSubtitleSelection: PendingSubtitleSelection? = null
    private var assHandler: AssHandler? = null
    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC
    private lateinit var fastStartMediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var preciseSeekMediaSourceFactory: DefaultMediaSourceFactory
    private var sessionLoadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null
    private var sessionDrmSessionManagerProvider: DrmSessionManagerProvider? = null
    private lateinit var sessionMediaSourceFactory: MediaSource.Factory
    private lateinit var assSubtitleParserFactory: AssSubtitleParserFactory
    private val preciseSeekCoordinator by lazy {
        PreciseSeekCoordinator(
            context = applicationContext,
            scope = serviceScope,
            currentPlayerProvider = { mediaSession?.player as? ExoPlayer },
            createMediaSource = ::createMediaSource,
            resolvePlaybackStateUri = playbackStateCoordinator::resolvePlaybackStateUri,
            updatePlaybackPosition = { uri, position ->
                mediaRepository.updateMediumPosition(
                    uri = uri,
                    position = position,
                )
            },
            mediaLogSummary = { mediaId -> mediaId.toPrivateMediaLogSummary() },
            shouldUseFastSeek = { mediaItem -> mediaItem.shouldUseFastSeek() },
        )
    }

    private val startupAnalyticsListener by lazy {
        PlaybackStartupAnalyticsListener(
            tag = TAG,
            currentPlayerProvider = { mediaSession?.player as? ExoPlayer },
            videoEffectsCoordinator = videoEffectsCoordinator,
        )
    }

    private fun resolveTransitionPlaybackSpeed(
        transitionReason: Int,
        currentPlaybackSpeed: Float,
    ): Float = when (transitionReason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        -> currentPlaybackSpeed

        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        -> playerPreferences.defaultPlaybackSpeed

        else -> playerPreferences.defaultPlaybackSpeed
    }

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                handleRepeatedPlayback(mediaSession?.player ?: return)
                return
            }
            (mediaSession?.player as? ExoPlayer)?.let { player ->
                videoEffectsCoordinator.resetForMediaItem(player)
                applySeekParameters(player)
            }
            preciseSeekCoordinator.resetForMediaItem(mediaItem?.mediaId)
            isMediaItemReady = false
            isPendingExternalSubAutoSelect = false
            pendingRememberedSubtitleSelection = null
            if (mediaItem != null) {
                serviceScope.launch {
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)
                    mediaRepository.updateMediumLastPlayedTime(
                        uri = playbackStateUri,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                }
            }
            mediaItem?.mediaMetadata?.let { metadata ->
                mediaSession?.player?.run {
                    setPlaybackSpeed(
                        resolveTransitionPlaybackSpeed(
                            transitionReason = reason,
                            currentPlaybackSpeed = playbackParameters.speed,
                        ),
                    )
                    playerSpecificSubtitleDelayMilliseconds = metadata.subtitleDelayMilliseconds ?: 0L
                    playerSpecificSubtitleSpeed = metadata.subtitleSpeed ?: 1f
                }

                val resumePositionMs = metadata.positionMs?.takeIf { playerPreferences.resume == Resume.YES }
                if (metadata.isApproximateSeekEnabled) {
                    serviceScope.launch(Dispatchers.IO) {
                        val seekMap = preciseSeekCoordinator.awaitSeekMapForStartup(mediaItem)
                        val resumePosition = resumePositionMs?.takeIf(preciseSeekCoordinator::shouldUsePreciseStartupResume)
                        if (seekMap == null && resumePosition != null) {
                            Logger.info(TAG, "Resume deferred precise-seek media=${mediaItem.mediaId.toPrivateMediaLogSummary()} position=$resumePosition")
                            preciseSeekCoordinator.deferStartupResume(
                                mediaId = mediaItem.mediaId,
                                positionMs = resumePosition,
                            )
                            return@launch
                        }

                        withContext(Dispatchers.Main) {
                            val player = mediaSession?.player as? ExoPlayer ?: return@withContext
                            val currentItem = player.currentMediaItem ?: return@withContext
                            if (currentItem.mediaId != mediaItem.mediaId) return@withContext
                            val currentIndex = player.currentMediaItemIndex
                            val currentPosition = resumePosition
                                ?: player.currentPosition.takeIf { it != C.TIME_UNSET }
                                ?: 0L
                            val updatedMediaItem = currentItem.copy(
                                positionMs = currentPosition,
                                isApproximateSeekEnabled = false,
                            )
                            preciseSeekCoordinator.markPrecise(mediaItem.mediaId)
                            val shouldPlayWhenReady = player.playWhenReady
                            player.addMediaSource(currentIndex + 1, createMediaSource(updatedMediaItem))
                            player.seekTo(currentIndex + 1, currentPosition)
                            player.removeMediaItem(currentIndex)
                            player.prepare()
                            player.playWhenReady = shouldPlayWhenReady
                            applySeekParameters(player)
                            resumePosition?.let {
                                Logger.info(TAG, "Resume cached precise-seek media=${mediaItem.mediaId.toPrivateMediaLogSummary()} position=$it")
                            }
                        }
                    }
                    return
                }

                resumePositionMs?.let {
                    mediaSession?.player?.seekTo(it)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            val oldMediaItem = oldPosition.mediaItem ?: return

            when (reason) {
                DISCONTINUITY_REASON_SEEK,
                DISCONTINUITY_REASON_AUTO_TRANSITION,
                -> {
                    if (newPosition.mediaItem == null || oldMediaItem == newPosition.mediaItem) return

                    val player = mediaSession?.player ?: return
                    val updatedPosition = oldPosition.positionMs.takeIf { reason == DISCONTINUITY_REASON_SEEK } ?: C.TIME_UNSET
                    val mediaItemToUpdate = player.getMediaItemAt(oldPosition.mediaItemIndex)
                        .takeIf { it.mediaId == oldMediaItem.mediaId }
                        ?: oldMediaItem

                    player.replaceMediaItem(
                        oldPosition.mediaItemIndex,
                        mediaItemToUpdate.copy(positionMs = updatedPosition),
                    )
                    serviceScope.launch {
                        val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(oldMediaItem)
                        mediaRepository.updateMediumPosition(
                            uri = playbackStateUri,
                            position = updatedPosition,
                        )
                    }
                }

                DISCONTINUITY_REASON_REMOVE -> {
                    serviceScope.launch {
                        val durationMs = oldMediaItem.mediaMetadata.durationMs
                        val isAtEnd = durationMs != null && oldPosition.positionMs >= durationMs - 1000
                        val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(oldMediaItem)
                        mediaRepository.updateMediumPosition(
                            uri = playbackStateUri,
                            position = if (isAtEnd) C.TIME_UNSET else oldPosition.positionMs,
                        )
                    }
                }

                else -> return
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            if (tracks.groups.isEmpty()) return

            if (isPendingExternalSubAutoSelect) {
                isPendingExternalSubAutoSelect = false
                if (!playerPreferences.isSubtitleAutoLoadEnabled) return
                val player = mediaSession?.player ?: return
                player.restorePendingOrBestSubtitleTrack(
                    tracks = tracks,
                    shouldFallbackToBest = true,
                )
                return
            }

            pendingRememberedSubtitleSelection?.let { pendingSelection ->
                val player = mediaSession?.player ?: return
                if (player.currentMediaItem?.mediaId != pendingSelection.mediaId) {
                    pendingRememberedSubtitleSelection = null
                    return
                }
                val textTracks = subtitleTrackSelector.supportedTextTracks(tracks)
                if (textTracks.isEmpty()) return
                if (pendingSelection.subtitleTrackIndex !in textTracks.indices && !pendingSelection.canFallbackToBest) return

                pendingRememberedSubtitleSelection = null
                subtitleTrackSelector.switchToRememberedOrBestSubtitleTrack(
                    player = player,
                    textTracks = textTracks,
                    rememberedSubtitleTrackIndex = pendingSelection.subtitleTrackIndex,
                    shouldFallbackToBest = pendingSelection.canFallbackToBest,
                )
                return
            }

            if (isMediaItemReady) return
            isMediaItemReady = true

            val player = mediaSession?.player ?: return
            val metadata = player.mediaMetadata
            if (playerPreferences.shouldRememberAudioTrack) {
                metadata.audioTrackIndex?.let { player.switchTrack(C.TRACK_TYPE_AUDIO, it) }
            }

            if (!playerPreferences.isSubtitleAutoLoadEnabled) {
                player.switchTrack(C.TRACK_TYPE_TEXT, -1)
                return
            }

            val textTracks = subtitleTrackSelector.supportedTextTracks(tracks)
            // 与内置字幕并存：同目录外部字幕（如 ass）仍需扫描合并
            val currentMediaItem = player.currentMediaItem ?: return
            val rememberedSubtitleTrackIndex = metadata.subtitleTrackIndex.takeIf { playerPreferences.shouldRememberSubtitleTrack }
            val shouldWaitForExternalSubtitles = rememberedSubtitleTrackIndex != null && rememberedSubtitleTrackIndex >= textTracks.size
            if (textTracks.isNotEmpty() && !shouldWaitForExternalSubtitles) {
                subtitleTrackSelector.switchToRememberedOrBestSubtitleTrack(
                    player = player,
                    textTracks = textTracks,
                    rememberedSubtitleTrackIndex = rememberedSubtitleTrackIndex,
                    shouldFallbackToBest = true,
                )
            }
            if (shouldWaitForExternalSubtitles) {
                pendingRememberedSubtitleSelection = PendingSubtitleSelection(
                    mediaId = currentMediaItem.mediaId,
                    subtitleTrackIndex = rememberedSubtitleTrackIndex,
                    canFallbackToBest = false,
                )
            }
            loadExternalSubtitlesForCurrentItem(
                mediaId = currentMediaItem.mediaId,
                requestHeaders = currentMediaItem.mediaMetadata.requestHeaders,
                onNoNewSubtitles = {
                    if (!shouldWaitForExternalSubtitles) return@loadExternalSubtitlesForCurrentItem

                    val currentPlayer = mediaSession?.player ?: return@loadExternalSubtitlesForCurrentItem
                    if (currentPlayer.currentMediaItem?.mediaId != currentMediaItem.mediaId) return@loadExternalSubtitlesForCurrentItem

                    val pendingSelection = pendingRememberedSubtitleSelection ?: return@loadExternalSubtitlesForCurrentItem
                    val currentTextTracks = subtitleTrackSelector.supportedTextTracks(currentPlayer.currentTracks)
                    if (currentTextTracks.isEmpty()) return@loadExternalSubtitlesForCurrentItem
                    if (pendingSelection.subtitleTrackIndex in currentTextTracks.indices) return@loadExternalSubtitlesForCurrentItem

                    pendingRememberedSubtitleSelection = null
                    subtitleTrackSelector.switchToRememberedOrBestSubtitleTrack(
                        player = currentPlayer,
                        textTracks = currentTextTracks,
                        rememberedSubtitleTrackIndex = pendingSelection.subtitleTrackIndex,
                        shouldFallbackToBest = true,
                    )
                },
            )
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            super.onTrackSelectionParametersChanged(parameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            val audioTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO)
            val subtitleTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_TEXT)

            serviceScope.launch {
                val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                if (playerPreferences.shouldRememberAudioTrack && audioTrackIndex != null) {
                    mediaRepository.updateMediumAudioTrack(
                        uri = playbackStateUri,
                        audioTrackIndex = audioTrackIndex,
                    )
                }
                if (playerPreferences.shouldRememberSubtitleTrack && subtitleTrackIndex != null) {
                    mediaRepository.updateMediumSubtitleTrack(
                        uri = playbackStateUri,
                        subtitleTrackIndex = subtitleTrackIndex,
                    )
                }
            }

            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    audioTrackIndex = audioTrackIndex.takeIf { playerPreferences.shouldRememberAudioTrack },
                    subtitleTrackIndex = subtitleTrackIndex.takeIf { playerPreferences.shouldRememberSubtitleTrack },
                ),
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            if (playbackState == Player.STATE_IDLE) {
                val player = mediaSession?.player ?: return
                player.trackSelectionParameters = TrackSelectionParameters.DEFAULT
                player.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
                return
            }

            if (playbackState == Player.STATE_ENDED) {
                val player = mediaSession?.player ?: return
                player.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
                return
            }

            if (playbackState == Player.STATE_READY) {
                val player = mediaSession?.player ?: return
                val currentMediaItem = player.currentMediaItem ?: return
                serviceScope.launch {
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                    mediaRepository.updateMediumLastPlayedTime(
                        uri = playbackStateUri,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                }
                folderPlaybackAnchorUpdater.update(currentMediaItem)
            }
        }

        override fun onPlayWhenReadyChanged(shouldPlayWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(shouldPlayWhenReady, reason)
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return

            val player = mediaSession?.player ?: return
            if (player.repeatMode != Player.REPEAT_MODE_OFF) {
                player.seekTo(0)
                handleRepeatedPlayback(player)
                player.play()
                return
            }
            player.clearMediaItems()
            player.stop()
            stopSelf()
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            // 从 track format 读取视频尺寸，再通过 metadata extras 传给 MediaController
            val trackFormat = player.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                ?.getTrackFormat(0)
            val format = videoEffectsCoordinator.currentFormat ?: trackFormat
            val width = format?.width ?: 0
            val height = format?.height ?: 0
            val rotation = format?.rotationDegrees ?: 0
            val transfer = format?.colorInfo?.colorTransfer
            val isVideoHdr = format?.isHdrVideoFormat() == true
            Logger.info(
                TAG,
                "startup firstFrameReady format=${width}x$height rot=$rotation duration=${player.duration} seekable=${player.isCurrentMediaItemSeekable} transfer=$transfer hdr=$isVideoHdr",
            )

            val duration = player.duration.takeIf { it != C.TIME_UNSET }
            val currentPosition = player.currentPosition.takeIf { it != C.TIME_UNSET }
            val updatedMediaItem = currentMediaItem.copy(
                positionMs = currentPosition,
                durationMs = duration,
                videoWidth = width,
                videoHeight = height,
                videoRotation = rotation,
                hasRenderedFirstFrame = true,
                isVideoEffectsAvailable = shouldApplyVideoEffects(activeDecoderPriority),
            )
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                updatedMediaItem,
            )
            preciseSeekCoordinator.continueDeferredStartupResume(updatedMediaItem)
            (player as? ExoPlayer)?.let {
                applySeekParameters(it)
                videoEffectsCoordinator.markFirstFrameRendered(
                    player = it,
                    format = format,
                    preferences = playerPreferences,
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            mediaSession?.run {
                serviceScope.launch {
                    val currentMediaItem = player.currentMediaItem ?: return@launch
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                    mediaRepository.updateMediumPosition(
                        uri = playbackStateUri,
                        position = player.currentPosition,
                    )
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(
                        loopMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> LoopMode.OFF
                            Player.REPEAT_MODE_ONE -> LoopMode.ONE
                            Player.REPEAT_MODE_ALL -> LoopMode.ALL
                            else -> LoopMode.OFF
                        },
                    )
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) {
                audioEffectsCoordinator.releaseLoudnessEnhancer()
                return
            }
            audioEffectsCoordinator.initializeLoudnessEnhancer(
                audioSessionId = audioSessionId,
                preferences = playerPreferences,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Logger.error(TAG, "Player error: code=${error.errorCode} name=${error.errorCodeName}", error)
            if (retryWithSoftwareDecoder(error)) return
            retryWithFixedSource(error)
        }
    }

    private fun retryWithSoftwareDecoder(error: PlaybackException): Boolean {
        if (!isHardwareVideoDecoderError(error)) return false
        val session = mediaSession ?: return false
        val failedPlayer = session.player as? ExoPlayer ?: return false
        val mediaId = failedPlayer.currentMediaItem?.mediaId ?: return false
        if (!softwareDecoderRetried.add(mediaId)) return false
        val mediaItems = (0 until failedPlayer.mediaItemCount).map { failedPlayer.getMediaItemAt(it) }
        if (mediaItems.isEmpty()) return false

        val currentIndex = failedPlayer.currentMediaItemIndex.coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = failedPlayer.currentPosition.coerceAtLeast(0L)
        val shouldPlayWhenReady = failedPlayer.playWhenReady
        val playbackParameters = failedPlayer.playbackParameters
        val trackSelectionParameters = failedPlayer.trackSelectionParameters
        val shuffleModeEnabled = failedPlayer.shuffleModeEnabled
        val repeatMode = failedPlayer.repeatMode
        val isSkipSilenceEnabled = failedPlayer.isSkipSilenceEnabledForPlayer
        val subtitleDelayMilliseconds = failedPlayer.playerSpecificSubtitleDelayMilliseconds
        val subtitleSpeed = failedPlayer.playerSpecificSubtitleSpeed
        val retryPlayer = createPlayer(
            decoderPriority = DecoderPriority.PREFER_APP,
            assHandler = assHandler ?: return false,
        )
        Logger.debug(TAG, "Retrying playback with software decoder: ${mediaId.toPrivateMediaLogSummary()}")

        retryPlayer.setMediaItems(mediaItems, currentIndex, playbackPosition)
        retryPlayer.restoreRuntimeState(
            trackSelectionParameters = trackSelectionParameters,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            isSkipSilenceEnabled = isSkipSilenceEnabled,
            subtitleDelayMilliseconds = subtitleDelayMilliseconds,
            subtitleSpeed = subtitleSpeed,
            playbackParameters = playbackParameters,
            mediaItemIndex = currentIndex,
            positionMs = playbackPosition,
        )
        retryPlayer.playWhenReady = shouldPlayWhenReady

        audioEffectsCoordinator.releaseLoudnessEnhancer()
        failedPlayer.removeListener(playbackStateListener)
        failedPlayer.removeAnalyticsListener(startupAnalyticsListener)
        session.player = retryPlayer
        retryPlayer.prepare()
        failedPlayer.clearMediaItems()
        failedPlayer.stop()
        failedPlayer.release()
        videoEffectsCoordinator.updateAvailability(retryPlayer)
        return true
    }

    private fun ExoPlayer.restoreRuntimeState(
        trackSelectionParameters: TrackSelectionParameters,
        shuffleModeEnabled: Boolean,
        repeatMode: Int,
        isSkipSilenceEnabled: Boolean,
        subtitleDelayMilliseconds: Long,
        subtitleSpeed: Float,
        playbackParameters: androidx.media3.common.PlaybackParameters,
        mediaItemIndex: Int,
        positionMs: Long,
    ) {
        this.trackSelectionParameters = trackSelectionParameters
        this.shuffleModeEnabled = shuffleModeEnabled
        this.repeatMode = repeatMode
        this.isSkipSilenceEnabledForPlayer = isSkipSilenceEnabled
        this.playerSpecificSubtitleDelayMilliseconds = subtitleDelayMilliseconds
        this.playerSpecificSubtitleSpeed = subtitleSpeed
        setPlaybackParameters(playbackParameters)
        seekTo(mediaItemIndex, positionMs)
    }

    private fun switchPlayerDecoderPriority(decoderPriority: DecoderPriority) {
        if (decoderPriority == activeDecoderPriority) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        val mediaItems = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
        if (mediaItems.isEmpty()) {
            Logger.info(TAG, "Switch decoder to ${decoderPriority.logName()} without active media items")
            val nextPlayer = createPlayer(
                decoderPriority = decoderPriority,
                assHandler = assHandler ?: return,
            )
            audioEffectsCoordinator.releaseLoudnessEnhancer()
            currentPlayer.removeListener(playbackStateListener)
            currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
            session.player = nextPlayer
            currentPlayer.release()
            return
        }

        val currentIndex = currentPlayer.currentMediaItemIndex.coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = currentPlayer.currentPosition.coerceAtLeast(0L)
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        val playbackParameters = currentPlayer.playbackParameters
        val trackSelectionParameters = currentPlayer.trackSelectionParameters
        val shuffleModeEnabled = currentPlayer.shuffleModeEnabled
        val repeatMode = currentPlayer.repeatMode
        val isSkipSilenceEnabled = currentPlayer.isSkipSilenceEnabledForPlayer
        val subtitleDelayMilliseconds = currentPlayer.playerSpecificSubtitleDelayMilliseconds
        val subtitleSpeed = currentPlayer.playerSpecificSubtitleSpeed
        val currentDecoderPriority = activeDecoderPriority
        val nextPlayer = createPlayer(
            decoderPriority = decoderPriority,
            assHandler = assHandler ?: return,
        )
        Logger.info(
            TAG,
            "Switch decoder from ${currentDecoderPriority.logName()} to ${decoderPriority.logName()} at index=$currentIndex position=$playbackPosition",
        )

        nextPlayer.setMediaItems(mediaItems, currentIndex, playbackPosition)
        nextPlayer.restoreRuntimeState(
            trackSelectionParameters = trackSelectionParameters,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            isSkipSilenceEnabled = isSkipSilenceEnabled,
            subtitleDelayMilliseconds = subtitleDelayMilliseconds,
            subtitleSpeed = subtitleSpeed,
            playbackParameters = playbackParameters,
            mediaItemIndex = currentIndex,
            positionMs = playbackPosition,
        )
        nextPlayer.playWhenReady = shouldPlayWhenReady

        audioEffectsCoordinator.releaseLoudnessEnhancer()
        currentPlayer.removeListener(playbackStateListener)
        currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
        session.player = nextPlayer
        nextPlayer.prepare()
        currentPlayer.clearMediaItems()
        currentPlayer.stop()
        currentPlayer.release()
        videoEffectsCoordinator.updateAvailability(nextPlayer)
    }

    private fun isHardwareVideoDecoderError(error: PlaybackException): Boolean {
        if (!activeDecoderPriority.shouldRetryWithSoftwareDecoder()) return false
        val exoError = error as? ExoPlaybackException ?: return false
        if (exoError.type != ExoPlaybackException.TYPE_RENDERER) return false
        if (exoError.rendererFormat?.sampleMimeType?.startsWith("video/") != true) return false
        val rendererException = exoError.rendererException
        if (rendererException !is MediaCodecRenderer.DecoderInitializationException && rendererException.cause == null) return false
        return true
    }

    // 解析失败后只对已识别的结构异常做定向容错重试
    private fun retryWithFixedSource(error: PlaybackException) {
        if (!hasParserExceptionCause(error)) return
        val player = mediaSession?.player as? ExoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        if (!mediaParserRetried.add(currentItem.mediaId)) return

        val mediaId = currentItem.mediaId
        serviceScope.launch {
            val uri = mediaId.toUri()
            val isMpegTsStream = withContext(Dispatchers.IO) { uri.isMpegTsStream(applicationContext) }
            val sourceFix = if (isMpegTsStream) {
                withContext(Dispatchers.IO) {
                    SourceFix(tsProgramMapPidFix = uri.detectMpegTsProgramMapPidFix(applicationContext))
                }
            } else {
                withContext(Dispatchers.IO) {
                    SourceFix(skipRegion = detectDuplicateMoov(uri))
                }
            }

            withContext(Dispatchers.Main) {
                val currentPlayer = mediaSession?.player as? ExoPlayer ?: return@withContext
                if (currentPlayer.currentMediaItem?.mediaId != mediaId) return@withContext

                val index = (0 until currentPlayer.mediaItemCount).firstOrNull {
                    currentPlayer.getMediaItemAt(it).mediaId == mediaId
                } ?: return@withContext
                val shouldPlayWhenReady = currentPlayer.playWhenReady

                val item = currentPlayer.getMediaItemAt(index)
                val baseDataSourceFactory = createDataSourceFactory(
                    uri = item.localConfiguration?.uri ?: uri,
                    requestHeaders = item.mediaMetadata.requestHeaders,
                )
                if (isMpegTsStream && sourceFix.tsProgramMapPidFix == null) return@withContext

                val dataSourceFactory = if (sourceFix.tsProgramMapPidFix != null) {
                    Logger.debug(
                        TAG,
                        "TS PMT PID ${sourceFix.tsProgramMapPidFix.declaredPmtPid.toMpegTsPidHex()} -> ${sourceFix.tsProgramMapPidFix.actualPmtPid.toMpegTsPidHex()}, retrying: ${mediaId.toPrivateMediaLogSummary()}",
                    )
                    DataSource.Factory {
                        TsProgramMapPidPatchDataSource(
                            upstream = baseDataSourceFactory.createDataSource(),
                            fix = sourceFix.tsProgramMapPidFix,
                        )
                    }
                } else if (sourceFix.skipRegion != null) {
                    Logger.debug(
                        TAG,
                        "Duplicate moov at ${sourceFix.skipRegion.start}+${sourceFix.skipRegion.length}, retrying: ${mediaId.toPrivateMediaLogSummary()}",
                    )
                    DataSource.Factory {
                        GapSkipDataSource(
                            upstream = baseDataSourceFactory.createDataSource(),
                            targetUri = uri,
                            gapStart = sourceFix.skipRegion.start,
                            gapLength = sourceFix.skipRegion.length,
                        )
                    }
                } else {
                    Logger.debug(TAG, "Retrying with lenient extractor: ${mediaId.toPrivateMediaLogSummary()}")
                    baseDataSourceFactory
                }

                val retryItem = if (sourceFix.tsProgramMapPidFix != null) {
                    item.buildUpon()
                        .setMimeType(MimeTypes.VIDEO_MP2T)
                        .build()
                } else {
                    item
                }
                val extractorsFactory = if (sourceFix.tsProgramMapPidFix != null) {
                    MpegTsExtractorsFactory(assSubtitleParserFactory)
                } else {
                    LenientExtractorsFactory()
                }
                val mediaSourceFactory = DefaultMediaSourceFactory(
                    dataSourceFactory,
                    extractorsFactory,
                ).setSubtitleParserFactory(assSubtitleParserFactory)
                sessionLoadErrorHandlingPolicy?.let(mediaSourceFactory::setLoadErrorHandlingPolicy)
                sessionDrmSessionManagerProvider?.let(mediaSourceFactory::setDrmSessionManagerProvider)
                val mediaSource = mediaSourceFactory.createMediaSource(retryItem)

                currentPlayer.removeMediaItem(index)
                currentPlayer.addMediaSource(index, mediaSource)
                currentPlayer.seekTo(index, 0)
                currentPlayer.prepare()
                currentPlayer.playWhenReady = shouldPlayWhenReady
            }
        }
    }

    private fun hasParserExceptionCause(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        repeat(3) {
            val current = cause ?: return false
            if (current is ParserException) return true
            cause = current.cause
        }
        return false
    }

    private data class SkipRegion(val start: Long, val length: Long)

    private data class SourceFix(
        val skipRegion: SkipRegion? = null,
        val tsProgramMapPidFix: MpegTsProgramMapPidFix? = null,
    )

    private data class PendingSubtitleSelection(
        val mediaId: String,
        val subtitleTrackIndex: Int,
        val canFallbackToBest: Boolean,
    )

    private fun createPlaybackExtractorsFactory(
        assSubtitleParserFactory: AssSubtitleParserFactory,
        assHandler: AssHandler,
        shouldUseFastStart: Boolean,
    ): ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
        return ExtractorsFactory {
            val extractors = baseFactory.createExtractors()
            for (i in extractors.indices) {
                if (extractors[i] is MatroskaExtractor) {
                    extractors[i] = NormalizingAssMatroskaExtractor(
                        assSubtitleParserFactory,
                        assHandler,
                    ).also { extractor ->
                        if (shouldUseFastStart) {
                            disableSeekForCues(extractor)
                        }
                    }
                }
            }
            extractors
        }
    }

    private fun createMediaSourceFactory(
        assSubtitleParserFactory: AssSubtitleParserFactory,
        assHandler: AssHandler,
        shouldUseFastStart: Boolean,
    ): DefaultMediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(applicationContext),
        createPlaybackExtractorsFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = shouldUseFastStart,
        ),
    ).setSubtitleParserFactory(assSubtitleParserFactory)

    private fun disableSeekForCues(extractor: MatroskaExtractor) {
        try {
            val field = MatroskaExtractor::class.java.getDeclaredField("seekForCuesEnabled")
            field.isAccessible = true
            field.set(extractor, false)
        } catch (e: Exception) {
            Logger.error(TAG, "disableSeekForCues failed", e)
        }
    }

    // 扫描 MP4 顶层 atom，检测连续出现的 moov box
    private fun detectDuplicateMoov(uri: Uri): SkipRegion? {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                var offset = 0L
                var hasSeenFirstMoov = false
                val header = ByteArray(8)

                while (true) {
                    if (!readFully(stream, header)) break

                    val size = ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (header[3].toLong() and 0xFF)
                    val type = String(header, 4, 4, Charsets.US_ASCII)

                    if (size < 8) break

                    if (type == "moov") {
                        if (hasSeenFirstMoov) {
                            return SkipRegion(start = offset, length = size)
                        }
                        hasSeenFirstMoov = true
                    }

                    if (type == "mdat") break

                    val bodySize = size - 8
                    var skipped = 0L
                    while (skipped < bodySize) {
                        val s = stream.skip(bodySize - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    if (skipped < bodySize) break
                    offset += size
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to scan MP4 structure", e)
        }
        return null
    }

    private class TsProgramMapPidPatchDataSource(
        private val upstream: DataSource,
        private val fix: MpegTsProgramMapPidFix,
    ) : DataSource by upstream {

        private var readPosition = 0L

        override fun open(dataSpec: DataSpec): Long {
            readPosition = dataSpec.uriPositionOffset + dataSpec.position
            return upstream.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead <= 0) return bytesRead

            val readStart = readPosition
            val readEnd = readStart + bytesRead
            buffer.patchMpegTsProgramMapPid(
                bufferOffset = offset,
                readStart = readStart,
                readEnd = readEnd,
                fix = fix,
            )
            readPosition += bytesRead
            return bytesRead
        }

        override fun close() {
            readPosition = 0L
            upstream.close()
        }
    }

    // DataSource 包装器，读取时透明跳过 [gapStart, gapStart + gapLength) 区间
    private class GapSkipDataSource(
        private val upstream: DataSource,
        private val targetUri: Uri,
        private val gapStart: Long,
        private val gapLength: Long,
    ) : DataSource by upstream {

        private var isTarget = false
        private var hasCrossedGap = false
        private var bytesUntilGap = Long.MAX_VALUE
        private var currentDataSpec: DataSpec? = null

        override fun open(dataSpec: DataSpec): Long {
            currentDataSpec = dataSpec
            isTarget = dataSpec.uri == targetUri
            if (!isTarget) return upstream.open(dataSpec)

            val virtualPos = dataSpec.position
            if (virtualPos >= gapStart) {
                hasCrossedGap = true
                bytesUntilGap = Long.MAX_VALUE
                val adjustedSpec = dataSpec.buildUpon()
                    .setPosition(virtualPos + gapLength)
                    .build()
                return upstream.open(adjustedSpec)
            }

            hasCrossedGap = false
            bytesUntilGap = gapStart - virtualPos
            val length = upstream.open(dataSpec)
            if (length == C.LENGTH_UNSET.toLong()) return length

            val physicalEnd = virtualPos + length
            return when {
                physicalEnd > gapStart + gapLength -> length - gapLength
                physicalEnd > gapStart -> gapStart - virtualPos
                else -> length
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isTarget) return upstream.read(buffer, offset, length)

            if (!hasCrossedGap && bytesUntilGap <= 0L) {
                upstream.close()
                upstream.open(
                    DataSpec.Builder()
                        .setUri(currentDataSpec!!.uri)
                        .setPosition(gapStart + gapLength)
                        .build(),
                )
                hasCrossedGap = true
            }

            val toRead = if (!hasCrossedGap) {
                minOf(length.toLong(), bytesUntilGap).toInt()
            } else {
                length
            }
            val bytesRead = upstream.read(buffer, offset, toRead)
            if (bytesRead > 0 && !hasCrossedGap) {
                bytesUntilGap -= bytesRead
            }
            return bytesRead
        }

        override fun close() {
            isTarget = false
            upstream.close()
        }
    }

    // 容错 ExtractorsFactory，Mp4 使用 LenientMp4Extractor，其余回退到默认实现
    private class LenientExtractorsFactory : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> {
            val defaults = DefaultExtractorsFactory().createExtractors()
            return Array(defaults.size + 1) { i ->
                if (i == 0) LenientMp4Extractor() else defaults[i - 1]
            }
        }
    }

    private class MpegTsExtractorsFactory(
        private val subtitleParserFactory: SubtitleParser.Factory,
    ) : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> = arrayOf(TsExtractor(subtitleParserFactory))
    }

    // 包装 Mp4Extractor，捕获 sample 级别的 ParserException
    private class LenientMp4Extractor : Extractor {

        private val delegate = Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED)

        override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

        override fun init(output: ExtractorOutput) = delegate.init(output)

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = try {
            delegate.read(input, seekPosition)
        } catch (e: ParserException) {
            Logger.error(TAG, "Lenient extractor treating error as end of input", e)
            Extractor.RESULT_END_OF_INPUT
        }

        override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

        override fun release() = delegate.release()
    }

    private fun applySeekParameters(player: ExoPlayer) {
        val mediaItem = player.currentMediaItem
        player.setSeekParameters(
            if (mediaItem.shouldUseFastSeek()) {
                FAST_SEEK_PARAMETERS
            } else {
                EXACT_SEEK_PARAMETERS
            },
        )
    }

    private fun MediaItem?.shouldUseFastSeek(): Boolean {
        val durationMs = this?.mediaMetadata?.durationMs ?: return false
        return durationMs >= playerPreferences.minDurationForFastSeek
    }

    private fun handleRepeatedPlayback(player: Player) {
        player.currentMediaItem?.mediaMetadata?.let { metadata ->
            player.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
            player.playerSpecificSubtitleDelayMilliseconds = metadata.subtitleDelayMilliseconds ?: 0L
            player.playerSpecificSubtitleSpeed = metadata.subtitleSpeed ?: 1f
        }
    }

    private fun Bundle.toPlayerPreferences(): PlayerPreferences = PlayerPreferences(
        shouldApplyVideoFilters = getBoolean(CustomCommands.SHOULD_APPLY_VIDEO_FILTERS_KEY, false),
        isVideoBrightnessFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_BRIGHTNESS_FILTER_ENABLED_KEY, false),
        videoBrightness = getFloat(CustomCommands.VIDEO_BRIGHTNESS_KEY, PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS),
        isVideoContrastFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_CONTRAST_FILTER_ENABLED_KEY, false),
        videoContrast = getFloat(CustomCommands.VIDEO_CONTRAST_KEY, PlayerPreferences.DEFAULT_VIDEO_CONTRAST),
        isVideoSaturationFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_SATURATION_FILTER_ENABLED_KEY, false),
        videoSaturation = getFloat(CustomCommands.VIDEO_SATURATION_KEY, PlayerPreferences.DEFAULT_VIDEO_SATURATION),
        isVideoHueFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_HUE_FILTER_ENABLED_KEY, false),
        videoHue = getFloat(CustomCommands.VIDEO_HUE_KEY, PlayerPreferences.DEFAULT_VIDEO_HUE),
        isVideoGammaFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_GAMMA_FILTER_ENABLED_KEY, false),
        videoGamma = getFloat(CustomCommands.VIDEO_GAMMA_KEY, PlayerPreferences.DEFAULT_VIDEO_GAMMA),
        isVideoSharpeningFilterEnabled = getBoolean(CustomCommands.IS_VIDEO_SHARPENING_FILTER_ENABLED_KEY, false),
        videoSharpening = getFloat(CustomCommands.VIDEO_SHARPENING_KEY, PlayerPreferences.DEFAULT_VIDEO_SHARPENING),
    )

    private fun String.toLogSummary(): String = Uri.parse(this).toLogSummary()

    private fun Uri.toLogSummary(): String {
        val extension = lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        return "scheme=${scheme.orEmpty()} host=${host.orEmpty()} extension=$extension"
    }

    private fun String.toPrivateMediaLogSummary(): String {
        val uri = Uri.parse(this)
        val extension = uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
        val hash = hashCode().toUInt().toString(radix = 16)
        return "scheme=${uri.scheme.orEmpty()} extension=$extension hash=$hash"
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future(Dispatchers.Default) {
            preciseSeekCoordinator.clearPreciseMediaIds()
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            preciseSeekCoordinator.prepareCachedMediaItems(updatedMediaItems)
            artworkLoader.loadInBackground(updatedMediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            preciseSeekCoordinator.prepareCachedMediaItems(updatedMediaItems)
            artworkLoader.loadInBackground(updatedMediaItems)
            return@future updatedMediaItems.toMutableList()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = serviceScope.future {
            val command = CustomCommands.fromSessionCommand(customCommand)
                ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

            when (command) {
                CustomCommands.ADD_SUBTITLE_TRACK -> {
                    val subtitleUriString = args.getString(CustomCommands.SUBTITLE_TRACK_URI_KEY)
                    if (subtitleUriString.isNullOrBlank()) {
                        Logger.info(TAG, "Add subtitle track rejected: empty uri")
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }
                    val subtitleUri = subtitleUriString.toUri()
                    val player = mediaSession?.player
                    if (player == null) {
                        Logger.info(TAG, "Add subtitle track rejected: player unavailable")
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }
                    val currentMediaItem = player.currentMediaItem
                    if (currentMediaItem == null) {
                        Logger.info(TAG, "Add subtitle track rejected: current media item unavailable")
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }

                    val newSubConfiguration = uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                    mediaRepository.updateMediumPosition(
                        uri = playbackStateUri,
                        position = player.currentPosition,
                    )
                    mediaRepository.addExternalSubtitleToMedium(
                        uri = playbackStateUri,
                        subtitleUri = subtitleUri,
                    )
                    player.addAdditionalSubtitleConfiguration(newSubConfiguration)
                    Logger.info(TAG, "Added subtitle track: subtitle=${subtitleUri.toLogSummary()} media=${playbackStateUri.toLogSummary()}")
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.PRECISE_SEEK_TO -> {
                    val targetPositionMs = args.getLong(CustomCommands.SEEK_POSITION_MS_KEY, C.TIME_UNSET)
                    if (targetPositionMs == C.TIME_UNSET) {
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }
                    return@future preciseSeekCoordinator.requestSeekForCurrentItem(targetPositionMs)
                }

                CustomCommands.SET_SKIP_SILENCE_ENABLED -> {
                    val isSkipSilenceEnabled = args.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY)
                    mediaSession?.player?.isSkipSilenceEnabledForPlayer = isSkipSilenceEnabled
                    mediaSession?.sessionExtras = Bundle().apply {
                        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, isSkipSilenceEnabled)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_SILENCE_ENABLED -> {
                    val isSkipSilenceEnabled = mediaSession?.player?.isSkipSilenceEnabledForPlayer ?: false
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, isSkipSilenceEnabled)
                        },
                    )
                }

                CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED -> {
                    val isScrubbingModeEnabled = args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY)
                    mediaSession?.player?.setIsScrubbingModeEnabled(isScrubbingModeEnabled)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_PERSISTENT_PLAYBACK_SPEED -> {
                    val playbackSpeed = args.getFloat(CustomCommands.PLAYBACK_SPEED_KEY)
                    val player = mediaSession?.player
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    player.setPlaybackSpeed(playbackSpeed)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_TRANSIENT_PLAYBACK_SPEED -> {
                    val playbackSpeed = args.getFloat(CustomCommands.PLAYBACK_SPEED_KEY)
                    val player = mediaSession?.player
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    player.setPlaybackSpeed(playbackSpeed)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, audioEffectsCoordinator.isLoudnessGainSupported)
                        },
                    )
                }

                CustomCommands.SET_LOUDNESS_GAIN -> {
                    val gain = args.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
                    audioEffectsCoordinator.setEnhancerTargetGain(
                        gain = gain,
                        preferences = playerPreferences,
                        audioSessionId = mediaSession?.player?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET,
                    )
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_LOUDNESS_GAIN -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.LOUDNESS_GAIN_KEY, audioEffectsCoordinator.requestedVolumeGain)
                        },
                    )
                }

                CustomCommands.PREVIEW_VIDEO_FILTERS -> {
                    videoEffectsCoordinator.preview(mediaSession?.player as? ExoPlayer, args.toPlayerPreferences())
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_VIDEO_FORMAT -> {
                    val format = videoEffectsCoordinator.currentFormat
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putString(CustomCommands.VIDEO_DECODER_PRIORITY_KEY, activeDecoderPriority.name)
                            putString(CustomCommands.VIDEO_DECODER_NAME_KEY, videoEffectsCoordinator.currentDecoderName)
                            putInt(CustomCommands.VIDEO_WIDTH_KEY, format?.width ?: 0)
                            putInt(CustomCommands.VIDEO_HEIGHT_KEY, format?.height ?: 0)
                            putInt(CustomCommands.VIDEO_COLOR_TRANSFER_KEY, format?.colorInfo?.colorTransfer ?: C.INDEX_UNSET)
                            putInt(CustomCommands.VIDEO_COLOR_STANDARD_KEY, format?.colorInfo?.colorSpace ?: C.INDEX_UNSET)
                            putInt(CustomCommands.VIDEO_COLOR_RANGE_KEY, format?.colorInfo?.colorRange ?: C.INDEX_UNSET)
                            putBoolean(CustomCommands.IS_VIDEO_HDR_KEY, videoEffectsCoordinator.isCurrentHdr)
                            putBoolean(CustomCommands.IS_VIDEO_EFFECTS_AVAILABLE_KEY, videoEffectsCoordinator.isAvailable())
                            putBoolean(CustomCommands.IS_VIDEO_EFFECTS_ACTIVE_KEY, videoEffectsCoordinator.isEffectActive)
                        },
                    )
                }

                CustomCommands.GET_SUBTITLE_DELAY -> {
                    val subtitleDelay = mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds ?: 0
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putLong(CustomCommands.SUBTITLE_DELAY_KEY, subtitleDelay)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_DELAY -> {
                    val subtitleDelay = args.getLong(CustomCommands.SUBTITLE_DELAY_KEY)
                    mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds = subtitleDelay
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = mediaSession?.player?.playerSpecificSubtitleSpeed ?: 0f
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putFloat(CustomCommands.SUBTITLE_SPEED_KEY, subtitleSpeed)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = args.getFloat(CustomCommands.SUBTITLE_SPEED_KEY)
                    mediaSession?.player?.playerSpecificSubtitleSpeed = subtitleSpeed
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.STOP_PLAYER_SESSION -> {
                    mediaSession?.run {
                        serviceScope.launch {
                            val currentMediaItem = player.currentMediaItem ?: return@launch
                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                            mediaRepository.updateMediumPosition(
                                uri = playbackStateUri,
                                position = player.currentPosition,
                            )
                        }
                        player.clearMediaItems()
                        player.stop()
                    }
                    stopSelf()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun createLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMsForLocalPlayback(
            LOCAL_MIN_BUFFER_MS,
            LOCAL_MAX_BUFFER_MS,
            LOCAL_BUFFER_FOR_PLAYBACK_MS,
            LOCAL_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .build()

    private fun createPlayer(
        decoderPriority: DecoderPriority,
        assHandler: AssHandler,
    ): ExoPlayer {
        activeDecoderPriority = decoderPriority
        videoEffectsCoordinator.setDecoderPriority(decoderPriority)
        val extensionRendererMode = decoderPriority.extensionRendererMode()
        val shouldEnableDecoderFallback = decoderPriority.shouldEnableDecoderFallback()
        val shouldUseAudioExtensionFallback = decoderPriority.shouldUseAudioExtensionFallback()
        Logger.info(
            TAG,
            "Create player decoder=${decoderPriority.logName()} policy=$decoderPriority extensionRendererMode=$extensionRendererMode decoderFallback=$shouldEnableDecoderFallback audioExtensionFallback=$shouldUseAudioExtensionFallback",
        )
        val renderersFactory = NormalizingRenderersFactory(
            context = applicationContext,
            volumeNormalizationAudioProcessor = audioEffectsCoordinator.volumeNormalizationAudioProcessor,
            shouldUseAudioExtensionFallback = shouldUseAudioExtensionFallback,
        )
            .setEnableDecoderFallback(shouldEnableDecoderFallback)
            .setExtensionRendererMode(extensionRendererMode)

        val preferences = playerPreferences
        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(preferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(preferences.preferredSubtitleLanguage),
            )
        }

        return ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(sessionMediaSourceFactory)
            .setRenderersFactory(renderersFactory.withAssSupport(assHandler))
            .setTrackSelector(trackSelector)
            .setLoadControl(createLoadControl())
            .setSeekParameters(FAST_SEEK_PARAMETERS)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                preferences.shouldRequireAudioFocus,
            )
            .setHandleAudioBecomingNoisy(preferences.shouldPauseOnHeadsetDisconnect)
            .build()
            .also {
                assHandler.init(it)
                applySeekParameters(it)
                it.addListener(playbackStateListener)
                it.addAnalyticsListener(startupAnalyticsListener)
                it.pauseAtEndOfMediaItems = !preferences.shouldAutoPlay
                it.repeatMode = when (preferences.loopMode) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
                videoEffectsCoordinator.resetPipeline()
                videoEffectsCoordinator.apply(it, preferences)
            }
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }
                .collect { preferences -> switchPlayerDecoderPriority(preferences.decoderPriority) }
        }
        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.toVideoFilterPreferences() == new.toVideoFilterPreferences() }
                .collect(videoEffectsCoordinator::apply)
        }
        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.isVolumeBoostEnabled == new.isVolumeBoostEnabled }
                .collect { preferences ->
                    if (preferences.isVolumeBoostEnabled) {
                        val audioSessionId = mediaSession?.player?.audioSessionId ?: return@collect
                        audioEffectsCoordinator.initializeLoudnessEnhancer(
                            audioSessionId = audioSessionId,
                            preferences = playerPreferences,
                        )
                    } else {
                        audioEffectsCoordinator.releaseLoudnessEnhancer()
                    }
                }
        }
        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.isVolumeNormalizationEnabled == new.isVolumeNormalizationEnabled }
                .collect { preferences ->
                    audioEffectsCoordinator.applyVolumeNormalization(preferences.isVolumeNormalizationEnabled)
                }
        }
        audioEffectsCoordinator.applyVolumeNormalization(playerPreferences.isVolumeNormalizationEnabled)
        val assHandler = AssHandler(renderType = resolveAssRenderType())
        this.assHandler = assHandler
        AssHandlerRegistry.register(assHandler)
        val assSubtitleParserFactory = AssSubtitleParserFactory(assHandler)
        this.assSubtitleParserFactory = assSubtitleParserFactory
        fastStartMediaSourceFactory = createMediaSourceFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = true,
        )
        preciseSeekMediaSourceFactory = createMediaSourceFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = false,
        )
        sessionMediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
                sessionDrmSessionManagerProvider = drmSessionManagerProvider
                return this
            }

            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
                sessionLoadErrorHandlingPolicy = loadErrorHandlingPolicy
                return this
            }

            override fun getSupportedTypes(): IntArray = fastStartMediaSourceFactory.supportedTypes

            override fun createMediaSource(mediaItem: MediaItem): MediaSource = this@PlayerService.createMediaSource(mediaItem)
        }

        val player = createPlayer(
            decoderPriority = playerPreferences.decoderPriority,
            assHandler = assHandler,
        )

        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create media session", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEffectsCoordinator.releaseLoudnessEnhancer()
        preciseSeekCoordinator.release()
        assHandler?.let(AssHandlerRegistry::unregister)
        assHandler = null
        mediaSession?.run {
            player.clearMediaItems()
            player.stop()
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
        subtitleCacheDir.deleteFiles()
        mediaParserRetried.clear()
        softwareDecoderRetried.clear()
        serviceScope.cancel()
    }

    private suspend fun updatedMediaItemsWithMetadata(
        mediaItems: List<MediaItem>,
    ): List<MediaItem> = supervisorScope {
        mediaItems.map { mediaItem ->
            async {
                val uri = mediaItem.mediaId.toUri()
                val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)
                val playbackStateCandidates = playbackStateCoordinator.playbackStateCandidates(mediaItem)
                val primaryVideoState = mediaRepository.getVideoState(uri = playbackStateUri)
                val fallbackVideoState = playbackStateCandidates
                    .firstOrNull { candidate -> candidate != playbackStateUri }
                    ?.let { fallbackUri ->
                        mediaRepository.getVideoState(uri = fallbackUri)
                    }
                playbackStateCoordinator.migrateFallbackStateToPlaybackStateUri(
                    playbackStateUri = playbackStateUri,
                    primaryVideoState = primaryVideoState,
                    fallbackVideoState = fallbackVideoState,
                )
                val video = mediaRepository.getVideoByUri(uri = playbackStateUri)
                val videoState = playbackStateCoordinator.mergeVideoState(
                    primaryVideoState = primaryVideoState,
                    fallbackVideoState = fallbackVideoState,
                )

                val externalSubs = videoState?.externalSubs ?: emptyList()
                val validExternalSubs = externalSubs.filter { subUri ->
                    if (externalSubtitleLoader.isDirectSubtitleUri(subUri)) return@filter true
                    try {
                        contentResolver.openInputStream(subUri)?.close()
                        true
                    } catch (_: Exception) {
                        Logger.debug(TAG, "Removing stale external subtitle: $subUri")
                        false
                    }
                }
                if (validExternalSubs.size != externalSubs.size) {
                    mediaRepository.updateExternalSubs(
                        uri = playbackStateUri,
                        externalSubs = validExternalSubs,
                    )
                }
                validExternalSubs.forEach(onlineSubtitleRepository::touchSubtitle)
                val existingSubConfigurations = mediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                val restoredSubConfigurations = validExternalSubs.map { subtitleUri ->
                    externalSubtitleLoader.buildConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                }
                val mergedSubConfigurations = externalSubtitleLoader.mergeConfigurations(
                    existing = existingSubConfigurations,
                    incoming = restoredSubConfigurations,
                )

                // 先写入占位封面，后台再异步加载真实封面
                val artworkUri = artworkLoader.defaultArtworkUri()

                val title = mediaItem.mediaMetadata.title ?: video?.nameWithExtension ?: getFilenameFromUri(uri)
                val positionMs = mediaItem.mediaMetadata.positionMs ?: videoState?.position
                val durationMs = mediaItem.mediaMetadata.durationMs
                    ?: video?.duration?.takeIf { it > 0L }
                    ?: extractDurationMs(uri)
                val videoScale = mediaItem.mediaMetadata.videoZoom ?: videoState?.videoScale
                val audioTrackIndex = mediaItem.mediaMetadata.audioTrackIndex ?: videoState?.audioTrackIndex
                val subtitleTrackIndex = mediaItem.mediaMetadata.subtitleTrackIndex ?: videoState?.subtitleTrackIndex
                val subtitleDelay = mediaItem.mediaMetadata.subtitleDelayMilliseconds ?: videoState?.subtitleDelayMilliseconds
                val subtitleSpeed = mediaItem.mediaMetadata.subtitleSpeed ?: videoState?.subtitleSpeed
                if (primaryVideoState != null && fallbackVideoState != null) {
                    positionMs?.takeIf { primaryVideoState.position == null }?.let { position ->
                        mediaRepository.updateMediumPosition(
                            uri = playbackStateUri,
                            position = position,
                        )
                    }
                    audioTrackIndex?.takeIf { primaryVideoState.audioTrackIndex == null }?.let { index ->
                        mediaRepository.updateMediumAudioTrack(
                            uri = playbackStateUri,
                            audioTrackIndex = index,
                        )
                    }
                    subtitleTrackIndex?.takeIf { primaryVideoState.subtitleTrackIndex == null }?.let { index ->
                        mediaRepository.updateMediumSubtitleTrack(
                            uri = playbackStateUri,
                            subtitleTrackIndex = index,
                        )
                    }
                    if (primaryVideoState.externalSubs.isEmpty() && validExternalSubs.isNotEmpty()) {
                        mediaRepository.updateExternalSubs(
                            uri = playbackStateUri,
                            externalSubs = validExternalSubs,
                        )
                    }
                    subtitleDelay?.takeIf { primaryVideoState.subtitleDelayMilliseconds == 0L }?.let { delay ->
                        mediaRepository.updateSubtitleDelay(
                            uri = playbackStateUri,
                            delay = delay,
                        )
                    }
                    subtitleSpeed?.takeIf { kotlin.math.abs(primaryVideoState.subtitleSpeed - 1f) <= 0.001f }?.let { speed ->
                        if (kotlin.math.abs(speed - 1f) > 0.001f) {
                            mediaRepository.updateSubtitleSpeed(
                                uri = playbackStateUri,
                                speed = speed,
                            )
                        }
                    }
                    videoScale?.takeIf { kotlin.math.abs(primaryVideoState.videoScale - 1f) <= 0.001f }?.let { scale ->
                        if (kotlin.math.abs(scale - 1f) > 0.001f) {
                            mediaRepository.updateMediumZoom(
                                uri = playbackStateUri,
                                zoom = scale,
                            )
                        }
                    }
                }
                // MediaStore 返回的宽高已考虑 rotation，用于预设屏幕方向
                val videoWidth = video?.width
                val videoHeight = video?.height
                val mediaPath = video?.path ?: videoState?.path ?: getPath(uri) ?: uri.path
                val isLocalUri = uri.scheme == ContentResolver.SCHEME_FILE || uri.scheme == ContentResolver.SCHEME_CONTENT
                val isApproximateSeekEnabled = isLocalUri && mediaPath?.endsWith(".mkv", ignoreCase = true) == true

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(mergedSubConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
                            setArtworkUri(artworkUri)
                            setDurationMs(durationMs)
                            setExtras(
                                positionMs = positionMs,
                                videoScale = videoScale,
                                audioTrackIndex = audioTrackIndex,
                                subtitleTrackIndex = subtitleTrackIndex,
                                subtitleDelayMilliseconds = subtitleDelay,
                                subtitleSpeed = subtitleSpeed,
                                videoWidth = videoWidth,
                                videoHeight = videoHeight,
                                isApproximateSeekEnabled = isApproximateSeekEnabled,
                                isVideoEffectsAvailable = shouldApplyVideoEffects(activeDecoderPriority),
                                requestHeaders = mediaItem.mediaMetadata.requestHeaders,
                                remoteServerId = mediaItem.mediaMetadata.remoteServerId,
                                remoteFilePath = mediaItem.mediaMetadata.remoteFilePath,
                                remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
                            )
                        }.build(),
                    )
                }.build()
            }
        }.awaitAll()
    }

    private fun extractDurationMs(uri: Uri): Long? {
        if (uri.isNonMediaStoreContentUri()) return null

        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(applicationContext, uri)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION,
            )?.toLongOrNull()
            duration?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            retriever?.release()
        }
    }

    private fun Uri.isNonMediaStoreContentUri(): Boolean = scheme == ContentResolver.SCHEME_CONTENT && authority != MediaStore.AUTHORITY

    private fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val requestHeaders = mediaItem.mediaMetadata.requestHeaders
        val uri = mediaItem.localConfiguration?.uri
        val isRemoteSource = uri?.scheme in REMOTE_SOURCE_URI_SCHEMES || requestHeaders.isNotEmpty()
        val dataSourceFactory = createDataSourceFactory(uri, requestHeaders)
        val cachedSeekMap = preciseSeekCoordinator.cachedSeekMap(mediaItem.mediaId)
        val shouldUseFastStart = mediaItem.mediaMetadata.isApproximateSeekEnabled && mediaItem.mediaId !in preciseSeekCoordinator
        if (cachedSeekMap != null && !shouldUseFastStart) {
            // 使用预解析的 SeekMap 注入到 extractor，跳过慢速 Cues 加载
            val factory = DefaultMediaSourceFactory(
                dataSourceFactory,
                createSeekMapInjectedExtractorsFactory(cachedSeekMap),
            ).setSubtitleParserFactory(assSubtitleParserFactory)
            sessionLoadErrorHandlingPolicy?.let(factory::setLoadErrorHandlingPolicy)
            sessionDrmSessionManagerProvider?.let(factory::setDrmSessionManagerProvider)
            return factory.createMediaSource(mediaItem)
        }

        val currentAssHandler = assHandler
        if (!isRemoteSource && currentAssHandler == null) {
            return if (shouldUseFastStart) {
                fastStartMediaSourceFactory.createMediaSource(mediaItem)
            } else {
                preciseSeekMediaSourceFactory.createMediaSource(mediaItem)
            }
        }

        if (currentAssHandler != null) {
            val mediaSourceFactory = DefaultMediaSourceFactory(
                dataSourceFactory,
                createPlaybackExtractorsFactory(
                    assSubtitleParserFactory = assSubtitleParserFactory,
                    assHandler = currentAssHandler,
                    shouldUseFastStart = shouldUseFastStart,
                ),
            ).setSubtitleParserFactory(assSubtitleParserFactory)
            sessionLoadErrorHandlingPolicy?.let(mediaSourceFactory::setLoadErrorHandlingPolicy)
            sessionDrmSessionManagerProvider?.let(mediaSourceFactory::setDrmSessionManagerProvider)
            return mediaSourceFactory.createMediaSource(mediaItem)
        }

        return DefaultMediaSourceFactory(dataSourceFactory)
            .apply {
                sessionLoadErrorHandlingPolicy?.let(::setLoadErrorHandlingPolicy)
                sessionDrmSessionManagerProvider?.let(::setDrmSessionManagerProvider)
            }
            .setSubtitleParserFactory(assSubtitleParserFactory)
            .createMediaSource(mediaItem)
    }

    private fun createDataSourceFactory(
        uri: Uri?,
        requestHeaders: Map<String, String>,
    ): DataSource.Factory {
        if (uri?.scheme == "smb") {
            val username = requestHeaders["_smb_username"].orEmpty()
            val password = requestHeaders["_smb_password"].orEmpty()
            return SmbDataSource.Factory(username, password)
        }
        if (uri?.scheme == "ftp") {
            val username = requestHeaders["_ftp_username"].orEmpty()
            val password = requestHeaders["_ftp_password"].orEmpty()
            return FtpDataSource.Factory(username, password)
        }

        val httpHeaders = requestHeaders.filterKeys { !it.startsWith("_") }
        if (httpHeaders.isEmpty()) {
            return DefaultDataSource.Factory(applicationContext)
        }

        val okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(httpHeaders)
        return DefaultDataSource.Factory(applicationContext, httpFactory)
    }

    // 创建注入了预解析 SeekMap 的 ExtractorsFactory
    private fun createSeekMapInjectedExtractorsFactory(
        seekMap: androidx.media3.extractor.SeekMap,
    ): ExtractorsFactory = ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
        val extractors = baseFactory.createExtractors()
        for (i in extractors.indices) {
            if (extractors[i] is MatroskaExtractor) {
                val assExtractor = NormalizingAssMatroskaExtractor(assSubtitleParserFactory, assHandler!!)
                disableSeekForCues(assExtractor)
                extractors[i] = SeekMapInjectingExtractor(assExtractor, seekMap)
            }
        }
        extractors
    }

    // 在主线程播放列表中按 mediaId 查找对应的 Player、索引和 MediaItem
    private suspend fun findMediaItemInSession(mediaId: String): Triple<Player, Int, MediaItem>? = withContext(
        Dispatchers.Main.immediate,
    ) {
        val player = mediaSession?.player ?: return@withContext null
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == mediaId
        } ?: return@withContext null
        Triple(player, index, player.getMediaItemAt(index))
    }

    // 无内置字幕时加载外部字幕（同名文件 + 已持久化的用户添加字幕）
    private fun loadExternalSubtitlesForCurrentItem(
        mediaId: String,
        requestHeaders: Map<String, String>,
        onNoNewSubtitles: () -> Unit = {},
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val currentMediaItem = findMediaItemInSession(mediaId)?.third
            val playbackStateUri = currentMediaItem?.let { playbackStateCoordinator.resolvePlaybackStateUri(it) }
                ?: mediaRepository.getCanonicalMediaUri(uri = mediaId)
            val playbackStateCandidates = currentMediaItem?.let(playbackStateCoordinator::playbackStateCandidates)
                ?: listOf(mediaId, playbackStateUri).distinct()
            val configurations = externalSubtitleLoader.buildConfigurations(
                mediaId = mediaId,
                requestHeaders = requestHeaders,
                playbackStateUri = playbackStateUri,
                playbackStateCandidates = playbackStateCandidates,
                subtitleEncoding = playerPreferences.subtitleTextEncoding,
            )
            if (configurations.isEmpty()) {
                withContext(Dispatchers.Main.immediate) { onNoNewSubtitles() }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val (player, index, currentMediaItem) = findMediaItemInSession(mediaId) ?: return@withContext
                if (player.currentMediaItem?.mediaId != mediaId) return@withContext

                val existingConfigs = currentMediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                val mergedConfigs = externalSubtitleLoader.mergeConfigurations(existingConfigs, configurations)
                if (mergedConfigs.size == existingConfigs.size) {
                    onNoNewSubtitles()
                    return@withContext
                }

                val updatedMediaItem = currentMediaItem.buildUpon()
                    .setSubtitleConfigurations(mergedConfigs)
                    .build()
                val currentPosition = player.currentPosition
                val shouldPlayWhenReady = player.playWhenReady
                isPendingExternalSubAutoSelect = true
                player.addMediaItem(index + 1, updatedMediaItem)
                player.seekTo(index + 1, currentPosition)
                player.playWhenReady = shouldPlayWhenReady
                player.removeMediaItem(index)
                Logger.info(TAG, "Applied external subtitles media=${mediaId.toPrivateMediaLogSummary()} count=${configurations.size}")
            }
        }
    }

    private fun Player.restorePendingOrBestSubtitleTrack(
        tracks: Tracks,
        shouldFallbackToBest: Boolean,
    ) {
        val textTracks = subtitleTrackSelector.supportedTextTracks(tracks)
        if (textTracks.isEmpty()) return

        val mediaId = currentMediaItem?.mediaId
        val rememberedSubtitleTrackIndex = pendingRememberedSubtitleSelection
            ?.takeIf { it.mediaId == mediaId }
            ?.subtitleTrackIndex
            ?: currentMediaItem?.mediaMetadata?.subtitleTrackIndex.takeIf { playerPreferences.shouldRememberSubtitleTrack }
        if (rememberedSubtitleTrackIndex in textTracks.indices || shouldFallbackToBest) {
            pendingRememberedSubtitleSelection = null
        }

        subtitleTrackSelector.switchToRememberedOrBestSubtitleTrack(
            player = this,
            textTracks = textTracks,
            rememberedSubtitleTrackIndex = rememberedSubtitleTrackIndex,
            shouldFallbackToBest = shouldFallbackToBest,
        )
    }

    private fun resolveAssRenderType(): AssRenderType = AssRenderType.OVERLAY_CANVAS
}

private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
    var pos = 0
    while (pos < buffer.size) {
        val read = stream.read(buffer, pos, buffer.size - pos)
        if (read < 0) return false
        pos += read
    }
    return true
}

@get:UnstableApi
@set:UnstableApi
private var Player.isSkipSilenceEnabledForPlayer: Boolean
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.skipSilenceEnabled
        else -> false
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.skipSilenceEnabled = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleDelayMilliseconds: Long
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleDelayMilliseconds
        else -> 0L
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleDelayMilliseconds = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleSpeed: Float
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleSpeed
        else -> 0f
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleSpeed = value
        }
    }
