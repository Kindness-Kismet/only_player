package one.only.player.feature.player

import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import one.only.player.core.common.Logger
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.VideoContentScale
import one.only.player.feature.player.state.ControlsVisibilityState
import one.only.player.feature.player.state.PictureInPictureState
import one.only.player.feature.player.state.SeekGestureState
import one.only.player.feature.player.state.TapGestureState
import one.only.player.feature.player.state.VideoZoomAndContentScaleState
import one.only.player.feature.player.state.VolumeAndBrightnessGestureState
import one.only.player.feature.player.state.rememberSurfacePresentationState
import one.only.player.feature.player.state.rememberTracksState
import one.only.player.feature.player.ui.PlayerGestures
import one.only.player.feature.player.ui.ShutterView
import one.only.player.feature.player.ui.SubtitleConfiguration
import one.only.player.feature.player.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
    decoderPriority: DecoderPriority,
    isGesturesEnabled: Boolean = true,
    shouldUseTextureView: Boolean = false,
    isVideoMirrored: Boolean = false,
    /**
     * 视频滤镜：Android ColorMatrixColorFilter。
     * 非 null 时用自建 TextureView + setColorFilter（SurfaceView/Compose colorFilter 都不可靠）。
     */
    videoFilterColorFilter: ColorMatrixColorFilter? = null,
) {
    // decoder 切换重建 SurfaceView，重新绑定视频输出（否则 SurfaceView + graphicsLayer 会黑屏）
    // contentScale 纯走 graphicsLayer，绝不因缩放切换重建 Surface（logs11 黑屏根因）
    var surfaceRefreshKey by remember { mutableIntStateOf(0) }
    var previousDecoderPriority by remember { mutableStateOf(decoderPriority) }
    LaunchedEffect(decoderPriority) {
        if (previousDecoderPriority == decoderPriority) return@LaunchedEffect
        previousDecoderPriority = decoderPriority
        // 解码切换后尽快重建 surface；缩短二次 refresh，减少“几秒后才正常”
        surfaceRefreshKey++
        delay(40)
        surfaceRefreshKey++
    }

    // 不用 Media3 rememberPresentationState：MediaController+RemotableTimeline 会在 getIndexOfPeriod 崩
    val presentationState = rememberSurfacePresentationState(player)
    val density = LocalDensity.current
    val textTracksState = rememberTracksState(player = player, trackType = C.TRACK_TYPE_TEXT)
    val isAssSubtitleSelected = textTracksState.tracks.any { track ->
        track.isSelected &&
            (0 until track.mediaTrackGroup.length).any { index ->
                val format = track.mediaTrackGroup.getFormat(index)
                format.sampleMimeType == MimeTypes.TEXT_SSA || format.codecs == MimeTypes.TEXT_SSA
            }
    }
    var lastLoggedSurfaceLayout by remember { mutableStateOf("") }
    val shouldUseFilterTextureView = videoFilterColorFilter != null
    val surfaceType = when {
        shouldUseFilterTextureView -> SURFACE_TYPE_TEXTURE_VIEW // 仅作 key/日志；实际走 AndroidView
        shouldUseTextureView -> SURFACE_TYPE_TEXTURE_VIEW
        else -> SURFACE_TYPE_SURFACE_VIEW
    }

    // Media3 1.10.1 的 videoSizeDp 名带 Dp 但实际存视频 px
    val metadataVideoSizePx = run {
        val w = videoZoomAndContentScaleState.metadataVideoWidth.toFloat()
        val h = videoZoomAndContentScaleState.metadataVideoHeight.toFloat()
        if (w <= 0f || h <= 0f) return@run null
        val rotation = videoZoomAndContentScaleState.metadataVideoRotation
        if (rotation == 90 || rotation == 270) Size(h, w) else Size(w, h)
    }
    val sourceVideoSizePx = presentationState.videoSizeDp ?: metadataVideoSizePx

    key(surfaceRefreshKey, surfaceType) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val videoSizePx = sourceVideoSizePx
            val contentVideoSizePx = videoSizePx
            val videoWidth = videoSizePx?.width ?: containerWidth
            val videoHeight = (videoSizePx?.height ?: containerHeight).coerceAtLeast(1f)
            val contentVideoWidth = contentVideoSizePx?.width ?: videoWidth
            val contentVideoHeight = (contentVideoSizePx?.height ?: videoHeight).coerceAtLeast(1f)
            val fillX = containerWidth / videoWidth
            val fillY = containerHeight / videoHeight

            // SurfaceView 锁视频原始 px，避开 holder resize 异步竞态；graphicsLayer 缩放使 HUNDRED_PERCENT 1:1 无插值
            val (baseScaleX, baseScaleY) = when (videoZoomAndContentScaleState.videoContentScale) {
                VideoContentScale.STRETCH -> fillX to fillY
                VideoContentScale.BEST_FIT -> min(fillX, fillY).let { it to it }
                VideoContentScale.CROP -> max(fillX, fillY).let { it to it }
                VideoContentScale.HUNDRED_PERCENT -> 1f to 1f
            }
            val surfaceWidthDp = with(density) { videoWidth.toDp() }
            val surfaceHeightDp = with(density) { videoHeight.toDp() }
            val contentSurfaceWidthDp = with(density) { contentVideoWidth.toDp() }
            val contentSurfaceHeightDp = with(density) { contentVideoHeight.toDp() }
            val mirrorScaleX = if (isVideoMirrored) -1f else 1f
            LaunchedEffect(
                containerWidth,
                containerHeight,
                videoWidth,
                videoHeight,
                baseScaleX,
                baseScaleY,
            ) {
                videoZoomAndContentScaleState.updateVideoContentLayout(
                    containerSize = Size(containerWidth, containerHeight),
                    baseContentSize = Size(
                        width = videoWidth * baseScaleX,
                        height = videoHeight * baseScaleY,
                    ),
                )
            }

            val surfaceModifier = Modifier
                .requiredSize(surfaceWidthDp, surfaceHeightDp)
                .graphicsLayer {
                    scaleX = baseScaleX * videoZoomAndContentScaleState.zoom * mirrorScaleX
                    scaleY = baseScaleY * videoZoomAndContentScaleState.zoom
                    translationX = videoZoomAndContentScaleState.offset.x
                    translationY = videoZoomAndContentScaleState.offset.y
                }
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    val rect = Rect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    )
                    val key = "${rect.width()}x${rect.height()}@${rect.left},${rect.top}:${videoZoomAndContentScaleState.videoContentScale}:${videoSizePx?.width}x${videoSizePx?.height}:${contentVideoSizePx?.width}x${contentVideoSizePx?.height}:$surfaceType:$surfaceRefreshKey:$isVideoMirrored:${videoFilterColorFilter != null}"
                    if (key != lastLoggedSurfaceLayout) {
                        lastLoggedSurfaceLayout = key
                        Logger.info(
                            TAG,
                            "Player surface layout size=${rect.width()}x${rect.height()} left=${rect.left} top=${rect.top} contentScale=${videoZoomAndContentScaleState.videoContentScale} videoPx=${videoSizePx?.width}x${videoSizePx?.height} contentPx=${contentVideoSizePx?.width}x${contentVideoSizePx?.height} coverSurface=${presentationState.coverSurface} refresh=$surfaceRefreshKey filter=${videoFilterColorFilter != null}",
                        )
                    }
                    pictureInPictureState.updateVideoViewRect(rect)
                }

            if (shouldUseFilterTextureView) {
                // 自建 TextureView：View.setColorFilter，比 Compose graphicsLayer/SurfaceView 可靠
                val filter = videoFilterColorFilter
                val textureViewRef = remember(surfaceRefreshKey) { arrayOfNulls<TextureView>(1) }
                AndroidView(
                    factory = { context ->
                        TextureView(context).also { textureView ->
                            textureViewRef[0] = textureView
                            player.setVideoTextureView(textureView)
                            run {
                                val paint = android.graphics.Paint()
                                paint.colorFilter = filter
                                textureView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                            }
                            Logger.info(TAG, "Bound filtered TextureView")
                        }
                    },
                    update = { textureView ->
                        textureViewRef[0] = textureView
                        run {
                                val paint = android.graphics.Paint()
                                paint.colorFilter = filter
                                textureView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                            }
                        player.setVideoTextureView(textureView)
                    },
                    modifier = surfaceModifier,
                )
                DisposableEffect(player, surfaceRefreshKey) {
                    onDispose {
                        textureViewRef[0]?.let { view ->
                            runCatching { player.clearVideoTextureView(view) }
                        }
                        textureViewRef[0] = null
                    }
                }
            } else {
                PlayerSurface(
                    player = player,
                    surfaceType = surfaceType,
                    modifier = surfaceModifier,
                )
            }

            if (!presentationState.coverSurface) {
                val subtitleModifier = if (isAssSubtitleSelected) {
                    val subtitleScale = min(fillX, fillY)
                    Modifier
                        .requiredSize(contentSurfaceWidthDp, contentSurfaceHeightDp)
                        .graphicsLayer {
                            scaleX = subtitleScale
                            scaleY = subtitleScale
                        }
                } else {
                    val subtitleWidthDp = with(density) { min(containerWidth, contentVideoWidth * baseScaleX).toDp() }
                    val subtitleHeightDp = with(density) { min(containerHeight, contentVideoHeight * baseScaleY).toDp() }
                    Modifier.requiredSize(subtitleWidthDp, subtitleHeightDp)
                }
                SubtitleView(
                    modifier = subtitleModifier,
                    player = player,
                    isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
                    configuration = subtitleConfiguration,
                )
            }
        }
    }

    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        pictureInPictureState = pictureInPictureState,
        seekGestureState = seekGestureState,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
        isEnabled = isGesturesEnabled,
    )

    if (presentationState.coverSurface) {
        ShutterView()
    }
}

private const val TAG = "PlayerContentFrame"
