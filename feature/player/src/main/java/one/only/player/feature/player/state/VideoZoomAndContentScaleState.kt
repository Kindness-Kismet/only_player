package one.only.player.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.core.model.VideoContentScale
import one.only.player.feature.player.extensions.copy
import one.only.player.feature.player.extensions.next
import one.only.player.feature.player.extensions.videoHeight
import one.only.player.feature.player.extensions.videoRotation
import one.only.player.feature.player.extensions.videoWidth
import one.only.player.feature.player.extensions.videoZoom

@UnstableApi
@Composable
fun rememberVideoZoomAndContentScaleState(
    player: Player,
    initialContentScale: VideoContentScale,
    isZoomGestureEnabled: Boolean,
    isPanGestureEnabled: Boolean,
    onEvent: (VideoZoomEvent) -> Unit = {},
): VideoZoomAndContentScaleState {
    val coroutineScope = rememberCoroutineScope()
    val videoZoomAndContentScaleState = remember {
        VideoZoomAndContentScaleState(
            player = player,
            initialContentScale = initialContentScale,
            isZoomGestureEnabled = isZoomGestureEnabled,
            isPanGestureEnabled = isPanGestureEnabled,
            onEvent = onEvent,
            coroutineScope = coroutineScope,
        )
    }
    LaunchedEffect(player) { videoZoomAndContentScaleState.observe() }
    LaunchedEffect(initialContentScale) {
        videoZoomAndContentScaleState.updateContentScaleFromPreferences(initialContentScale)
    }
    LaunchedEffect(isZoomGestureEnabled, isPanGestureEnabled) {
        videoZoomAndContentScaleState.updateGestureSettings(
            isZoomGestureEnabled = isZoomGestureEnabled,
            isPanGestureEnabled = isPanGestureEnabled,
        )
    }
    return videoZoomAndContentScaleState
}

@Stable
class VideoZoomAndContentScaleState(
    private val player: Player,
    initialContentScale: VideoContentScale,
    private var isZoomGestureEnabled: Boolean = true,
    private var isPanGestureEnabled: Boolean = true,
    private val onEvent: (VideoZoomEvent) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    companion object Companion {
        private const val TAG = "VideoZoomAndContentScaleState"
        private const val MIN_ZOOM = 0.25f
        private const val MAX_ZOOM = 4f
        private const val CONTENT_SCALE_INDICATOR_DURATION_MS = 1000L
    }

    var videoContentScale: VideoContentScale by mutableStateOf(initialContentScale)
        private set

    var zoom: Float by mutableFloatStateOf(1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    var isZooming: Boolean by mutableStateOf(false)
        private set

    var shouldShowContentScaleIndicator: Boolean by mutableStateOf(false)
        private set

    val canPanZoomedVideo: Boolean
        get() = isZoomGestureEnabled && isPanGestureEnabled && hasPanBounds()

    private var containerSize: Size by mutableStateOf(Size.Zero)
    private var baseContentSize: Size by mutableStateOf(Size.Zero)

    // 从 metadata extras 追踪视频尺寸，用于 resizeWithContentScale 的后备值
    var metadataVideoWidth: Int by mutableIntStateOf(0)
        private set
    var metadataVideoHeight: Int by mutableIntStateOf(0)
        private set
    var metadataVideoRotation: Int by mutableIntStateOf(0)
        private set

    private var showContentScaleJob: Job? = null

    fun onVideoContentScaleChanged(newContentScale: VideoContentScale) {
        val previousContentScale = videoContentScale
        videoContentScale = newContentScale
        zoom = 1f
        offset = Offset.Zero
        Logger.info(
            TAG,
            "Video content scale changed from=$previousContentScale to=$newContentScale metadataVideo=${metadataVideoWidth}x$metadataVideoHeight rotation=$metadataVideoRotation",
        )
        onEvent(VideoZoomEvent.ContentScaleChanged(videoContentScale))
        updateVideoScaleMetadataAndSendEvent()
        shouldShowContentScaleIndicator()
    }

    fun updateContentScaleFromPreferences(newContentScale: VideoContentScale) {
        if (videoContentScale == newContentScale) return
        videoContentScale = newContentScale
        zoom = 1f
        offset = Offset.Zero
        updateVideoScaleMetadataAndSendEvent()
    }

    fun updateGestureSettings(
        isZoomGestureEnabled: Boolean,
        isPanGestureEnabled: Boolean,
    ) {
        this.isZoomGestureEnabled = isZoomGestureEnabled
        this.isPanGestureEnabled = isPanGestureEnabled
        if (!canPanZoomedVideo) {
            offset = Offset.Zero
        }
    }

    fun updateVideoContentLayout(
        containerSize: Size,
        baseContentSize: Size,
    ) {
        this.containerSize = containerSize
        this.baseContentSize = baseContentSize
        offset = offset.coerceInPanBounds()
    }

    private fun shouldShowContentScaleIndicator() {
        showContentScaleJob?.cancel()
        shouldShowContentScaleIndicator = true
        showContentScaleJob = coroutineScope.launch {
            delay(CONTENT_SCALE_INDICATOR_DURATION_MS)
            shouldShowContentScaleIndicator = false
            showContentScaleJob = null
        }
    }

    fun switchToNextVideoContentScale() {
        onVideoContentScaleChanged(videoContentScale.next())
    }

    fun onZoomPanGesture(constraints: Constraints, panChange: Offset, zoomChange: Float) {
        if (player.duration == C.TIME_UNSET) return
        if (!isZoomGestureEnabled) return

        if (containerSize == Size.Zero) {
            updateVideoContentLayout(
                containerSize = Size(
                    width = constraints.maxWidth.toFloat().coerceAtLeast(1f),
                    height = constraints.maxHeight.toFloat().coerceAtLeast(1f),
                ),
                baseContentSize = Size(
                    width = constraints.maxWidth.toFloat().coerceAtLeast(1f),
                    height = constraints.maxHeight.toFloat().coerceAtLeast(1f),
                ),
            )
        }

        isZooming = true
        zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        offset = if (isPanGestureEnabled) {
            (offset + panChange).coerceInPanBounds()
        } else {
            Offset.Zero
        }
    }

    fun onPanGesture(panChange: Offset) {
        if (player.duration == C.TIME_UNSET) return
        if (!canPanZoomedVideo) return

        isZooming = true
        offset = (offset + panChange).coerceInPanBounds()
    }

    fun onZoomPanGestureEnd() {
        isZooming = false
        offset = offset.coerceInPanBounds()
        updateVideoScaleMetadataAndSendEvent()
    }

    suspend fun observe() {
        updateFromMetadata()
        zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
        player.listen { events ->
            if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                updateFromMetadata()
                zoom = player.currentMediaItem?.mediaMetadata?.videoZoom ?: 1f
                offset = offset.coerceInPanBounds()
            }
        }
    }

    private fun updateFromMetadata() {
        val metadata = player.currentMediaItem?.mediaMetadata ?: return
        val previousVideoWidth = metadataVideoWidth
        val previousVideoHeight = metadataVideoHeight
        val previousVideoRotation = metadataVideoRotation
        metadataVideoWidth = metadata.videoWidth ?: 0
        metadataVideoHeight = metadata.videoHeight ?: 0
        metadataVideoRotation = metadata.videoRotation ?: 0
        if (previousVideoWidth == metadataVideoWidth && previousVideoHeight == metadataVideoHeight && previousVideoRotation == metadataVideoRotation) return

        Logger.info(TAG, "Video metadata size=${metadataVideoWidth}x$metadataVideoHeight rotation=$metadataVideoRotation scale=$videoContentScale zoom=$zoom")
    }

    private fun updateVideoScaleMetadataAndSendEvent(zoom: Float = this.zoom) {
        val currentMediaItem = player.currentMediaItem ?: return
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(videoZoom = zoom),
        )
        onEvent(VideoZoomEvent.ZoomChanged(currentMediaItem, zoom))
    }

    private fun hasPanBounds(): Boolean = maxPanX() > 0f || maxPanY() > 0f

    private fun Offset.coerceInPanBounds(): Offset {
        val maxX = maxPanX()
        val maxY = maxPanY()
        return Offset(
            x = x.coerceIn(-maxX, maxX),
            y = y.coerceIn(-maxY, maxY),
        )
    }

    private fun maxPanX(): Float = ((baseContentSize.width * zoom - containerSize.width) / 2f)
        .coerceAtLeast(0f)

    private fun maxPanY(): Float = ((baseContentSize.height * zoom - containerSize.height) / 2f)
        .coerceAtLeast(0f)
}

sealed interface VideoZoomEvent {
    data class ContentScaleChanged(val contentScale: VideoContentScale) : VideoZoomEvent
    data class ZoomChanged(val mediaItem: MediaItem, val zoom: Float) : VideoZoomEvent
}
