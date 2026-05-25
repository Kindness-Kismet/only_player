package one.only.player.feature.player.service.playback

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import one.only.player.core.common.Logger
import one.only.player.feature.player.service.effects.VideoEffectsCoordinator

@UnstableApi
internal class PlaybackStartupAnalyticsListener(
    private val tag: String,
    private val currentPlayerProvider: () -> ExoPlayer?,
    private val videoEffectsCoordinator: VideoEffectsCoordinator,
) : AnalyticsListener {

    private var startupTimestamp = 0L

    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) {
        if (state == Player.STATE_BUFFERING) {
            startupTimestamp = System.currentTimeMillis()
        }
        val label = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
        Logger.info(tag, "startup state=$label t=${elapsed()}ms")
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        retryCount: Int,
    ) {
        Logger.info(tag, "startup loadStart t=${elapsed()}ms type=${mediaLoadData.dataType}")
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
    ) {
        Logger.info(
            tag,
            "startup loadDone t=${elapsed()}ms type=${mediaLoadData.dataType} bytes=${loadEventInfo.bytesLoaded}",
        )
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long,
    ) {
        Logger.info(tag, "startup firstFrame t=${elapsed()}ms")
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        videoEffectsCoordinator.setDecoderName(decoderName)
        Logger.info(tag, "startup decoderInit=$decoderName dur=${initializationDurationMs}ms t=${elapsed()}ms")
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        videoEffectsCoordinator.onVideoInputFormatChanged(
            player = currentPlayerProvider(),
            format = format,
        )
        Logger.info(tag, "startup videoFormat transfer=${format.colorInfo?.colorTransfer} standard=${format.colorInfo?.colorSpace} range=${format.colorInfo?.colorRange}")
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        Logger.info(tag, "startup audioDecoder=$decoderName dur=${initializationDurationMs}ms t=${elapsed()}ms")
    }

    override fun onTracksChanged(
        eventTime: AnalyticsListener.EventTime,
        tracks: Tracks,
    ) {
        val player = currentPlayerProvider()
        Logger.info(
            tag,
            "startup tracksChanged t=${elapsed()}ms groups=${tracks.groups.size} seekable=${player?.isCurrentMediaItemSeekable} duration=${player?.duration}",
        )
    }

    private fun elapsed(): Long = System.currentTimeMillis() - startupTimestamp
}
