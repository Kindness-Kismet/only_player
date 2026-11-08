package one.only.player.feature.player.service.decoder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper
import androidx.media3.exoplayer.video.VideoFrameReleaseControl
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.video.VideoSink
import one.only.player.core.common.Logger
import one.only.player.feature.player.service.effects.isDolbyVisionVideoFormat

@OptIn(UnstableApi::class)
internal class VideoEffectsRenderer(
    builder: Builder,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
) : MediaCodecVideoRenderer(builder) {

    private val eventDispatcher = VideoRendererEventListener.EventDispatcher(eventHandler, eventListener)
    private var videoGraphWrapper: PlaybackVideoGraphWrapper? = null

    override fun supportsFormat(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
    ): Int {
        if (format.isDolbyVisionVideoFormat()) return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        return super.supportsFormat(mediaCodecSelector, format)
    }

    override fun changeVideoSinkInputStream(
        videoSink: VideoSink,
        inputType: Int,
        format: Format,
        firstFrameReleaseInstruction: Int,
    ) {
        super.changeVideoSinkInputStream(videoSink, inputType, format, firstFrameReleaseInstruction)
        // 效果管线可能不重发尺寸事件，此处已应用解码裁剪、旋转和像素比例。
        eventDispatcher.videoSizeChanged(VideoSize(format.width, format.height, format.pixelWidthHeightRatio))
    }

    override fun onDisabled() {
        try {
            super.onDisabled()
        } finally {
            // 原生解码器接管屏幕前，必须断开效果管线的图形输出。
            videoGraphWrapper?.clearOutputSurfaceInfo()
        }
    }

    override fun handleMessage(
        messageType: Int,
        message: Any?,
    ) {
        super.handleMessage(messageType, message)
        val isOutputMessage = messageType == MSG_SET_VIDEO_OUTPUT || messageType == MSG_SET_VIDEO_OUTPUT_RESOLUTION
        if (state == STATE_DISABLED && isOutputMessage) {
            videoGraphWrapper?.clearOutputSurfaceInfo()
            return
        }
        val shouldRedraw = state == STATE_ENABLED &&
            ((messageType == MSG_SET_VIDEO_OUTPUT && message != null) || messageType == MSG_SET_VIDEO_OUTPUT_RESOLUTION)
        // 暂停时输出画布重建或改尺寸后，需要重绘缓存帧。
        if (shouldRedraw) setVideoEffects(VideoFrameProcessor.REDRAW)
    }

    @SuppressLint("RestrictedApi")
    override fun createPlaybackVideoGraphWrapper(
        context: Context,
        videoFrameReleaseControl: VideoFrameReleaseControl,
    ): PlaybackVideoGraphWrapper {
        Logger.info("VideoEffectsRenderer", "Video output path=effects")
        val wrapper = PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
            .setEnablePlaylistMode(true)
            .setEnableReplayableCache(true)
            .experimentalSetLateThresholdToDropInputUs(DEFAULT_LATE_THRESHOLD_TO_DROP_DECODER_INPUT_US)
            .setClock(clock)
            .build()
        videoGraphWrapper = wrapper
        // 效果管线的输出尺寸必须继续上报，供方向和比例计算使用。
        wrapper.addListener(object : PlaybackVideoGraphWrapper.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                eventDispatcher.videoSizeChanged(videoSize)
            }
        })
        return wrapper
    }
}
