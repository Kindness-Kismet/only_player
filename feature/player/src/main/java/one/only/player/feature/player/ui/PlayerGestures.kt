package one.only.player.feature.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.time.Duration
import one.only.player.feature.player.extensions.detectCustomHorizontalDragGestures
import one.only.player.feature.player.extensions.detectCustomTransformGestures
import one.only.player.feature.player.extensions.detectCustomVerticalDragGestures
import one.only.player.feature.player.state.ControlsVisibilityState
import one.only.player.feature.player.state.PictureInPictureState
import one.only.player.feature.player.state.SeekGestureState
import one.only.player.feature.player.state.TapGestureState
import one.only.player.feature.player.state.VideoZoomAndContentScaleState
import one.only.player.feature.player.state.VolumeAndBrightnessGestureState

@Composable
fun PlayerGestures(
    modifier: Modifier = Modifier,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    pictureInPictureState: PictureInPictureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    isChapterSwipeEnabled: Boolean = false,
    onChapterSwipe: (ChapterSwipeDirection) -> Unit = {},
    isEnabled: Boolean = true,
) {
    BoxWithConstraints {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("player_gesture_surface")
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                        tapGestureState.handleLongPress()
                        if (!tapGestureState.isLongPressGestureInAction) return@awaitEachGesture

                        try {
                            longPress.consume()
                            var pointerId = longPress.id
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) break

                                val dragAmount = change.positionChangeIgnoreConsumed().x
                                if (dragAmount != 0f) {
                                    change.consume()
                                    tapGestureState.handleLongPressHorizontalDrag(dragAmount)
                                }
                            }
                        } finally {
                            tapGestureState.handleOnLongPressRelease()
                        }
                    }
                }
                .pointerInput(isEnabled, pictureInPictureState.isInPictureInPictureMode) {
                    if (!isEnabled) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            if (tapGestureState.seekMillis != 0L) return@detectTapGestures
                            controlsVisibilityState.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            if (controlsVisibilityState.isControlsLocked) return@detectTapGestures
                            tapGestureState.handleDoubleTap(offset = it, size = size)
                        },
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    videoZoomAndContentScaleState.canPanHorizontally,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput
                    if (videoZoomAndContentScaleState.canPanHorizontally) return@pointerInput

                    var shouldRestoreControlsAutoHideAfterSeek = false
                    fun restoreControlsAutoHideAfterSeek() {
                        if (!shouldRestoreControlsAutoHideAfterSeek) return
                        controlsVisibilityState.showControls()
                        shouldRestoreControlsAutoHideAfterSeek = false
                    }

                    try {
                        detectCustomHorizontalDragGestures(
                            onDragStart = {
                                if (tapGestureState.isLongPressGestureCaptured) return@detectCustomHorizontalDragGestures
                                val wasControlsVisible = controlsVisibilityState.isControlsVisible
                                seekGestureState.onDragStart()
                                shouldRestoreControlsAutoHideAfterSeek = wasControlsVisible && seekGestureState.isSeeking
                                if (shouldRestoreControlsAutoHideAfterSeek) {
                                    controlsVisibilityState.showControls(duration = Duration.INFINITE)
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (tapGestureState.isLongPressGestureCaptured) {
                                    change.consume()
                                    return@detectCustomHorizontalDragGestures
                                }
                                seekGestureState.onDrag(
                                    change = change,
                                    dragAmount = dragAmount,
                                    hysteresisPx = viewConfiguration.touchSlop * SEEK_PREVIEW_HYSTERESIS_FRACTION,
                                )
                            },
                            onDragCancel = {
                                try {
                                    if (!tapGestureState.isLongPressGestureCaptured) {
                                        seekGestureState.onDragEnd()
                                    }
                                } finally {
                                    restoreControlsAutoHideAfterSeek()
                                }
                            },
                            onDragEnd = {
                                try {
                                    if (!tapGestureState.isLongPressGestureCaptured) {
                                        seekGestureState.onDragEnd()
                                    }
                                } finally {
                                    restoreControlsAutoHideAfterSeek()
                                }
                            },
                        )
                    } finally {
                        restoreControlsAutoHideAfterSeek()
                    }
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    tapGestureState.isLongPressGestureInAction,
                    videoZoomAndContentScaleState.canPanHorizontally,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput
                    if (tapGestureState.isLongPressGestureInAction) return@pointerInput
                    if (!videoZoomAndContentScaleState.canPanHorizontally) return@pointerInput

                    detectCustomHorizontalDragGestures(
                        canStartGesture = { it.isInPanGestureArea(size) },
                        onHorizontalDrag = { _, dragAmount ->
                            videoZoomAndContentScaleState.onPanGesture(Offset(dragAmount, 0f))
                        },
                        onDragCancel = { videoZoomAndContentScaleState.onZoomPanGestureEnd() },
                        onDragEnd = { videoZoomAndContentScaleState.onZoomPanGestureEnd() },
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    tapGestureState.isLongPressGestureInAction,
                    videoZoomAndContentScaleState.canPanVertically,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput
                    if (tapGestureState.isLongPressGestureInAction) return@pointerInput
                    if (!videoZoomAndContentScaleState.canPanVertically) return@pointerInput

                    detectCustomVerticalDragGestures(
                        canStartGesture = { it.isInPanGestureArea(size) },
                        onVerticalDrag = { _, dragAmount ->
                            videoZoomAndContentScaleState.onPanGesture(Offset(0f, dragAmount))
                        },
                        onDragCancel = { videoZoomAndContentScaleState.onZoomPanGestureEnd() },
                        onDragEnd = { videoZoomAndContentScaleState.onZoomPanGestureEnd() },
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    tapGestureState.isLongPressGestureInAction,
                    videoZoomAndContentScaleState.canPanVertically,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput
                    if (tapGestureState.isLongPressGestureInAction) return@pointerInput

                    detectCustomVerticalDragGestures(
                        canStartGesture = {
                            !videoZoomAndContentScaleState.canPanVertically || !it.isInPanGestureArea(size)
                        },
                        onDragStart = { volumeAndBrightnessGestureState.onDragStart(it, size) },
                        onVerticalDrag = volumeAndBrightnessGestureState::onDrag,
                        onDragCancel = volumeAndBrightnessGestureState::onDragEnd,
                        onDragEnd = volumeAndBrightnessGestureState::onDragEnd,
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    isChapterSwipeEnabled,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    var accumulatedPan = Offset.Zero
                    var accumulatedZoom = 1f
                    var accumulatedRotation = 0f
                    detectCustomTransformGestures(
                        onGestureStart = {
                            accumulatedPan = Offset.Zero
                            accumulatedZoom = 1f
                            accumulatedRotation = 0f
                        },
                        onGesture = { _, panChange, zoomChange, rotationChange ->
                            if (tapGestureState.isLongPressGestureInAction) return@detectCustomTransformGestures
                            accumulatedPan += panChange
                            accumulatedZoom *= zoomChange
                            accumulatedRotation += rotationChange
                            videoZoomAndContentScaleState.onZoomPanGesture(
                                constraints = this@BoxWithConstraints.constraints,
                                panChange = panChange,
                                zoomChange = zoomChange,
                            )
                        },
                        onGestureEnd = {
                            val horizontalDistance = abs(accumulatedPan.x)
                            val verticalDistance = abs(accumulatedPan.y)
                            val minimumDistance = size.width * CHAPTER_SWIPE_DISTANCE_FRACTION
                            val isHorizontalSwipe = horizontalDistance >= minimumDistance &&
                                horizontalDistance >= verticalDistance * CHAPTER_SWIPE_DIRECTION_RATIO
                            val hasNoMeaningfulScale = abs(accumulatedZoom - 1f) <= CHAPTER_SWIPE_MAX_SCALE_DELTA
                            val hasNoMeaningfulRotation = abs(accumulatedRotation) <= CHAPTER_SWIPE_MAX_ROTATION_DEGREES
                            val canSwitchChapter = isChapterSwipeEnabled &&
                                videoZoomAndContentScaleState.zoom <= CHAPTER_SWIPE_MAX_ACTIVE_ZOOM &&
                                isHorizontalSwipe &&
                                hasNoMeaningfulScale &&
                                hasNoMeaningfulRotation
                            if (canSwitchChapter) {
                                onChapterSwipe(
                                    if (accumulatedPan.x < 0f) ChapterSwipeDirection.NEXT else ChapterSwipeDirection.PREVIOUS,
                                )
                            }
                            videoZoomAndContentScaleState.onZoomPanGestureEnd()
                        },
                    )
                },
        )
    }
}

private fun Offset.isInPanGestureArea(size: IntSize): Boolean {
    val positionFraction = x / size.width
    return positionFraction in PAN_GESTURE_START_FRACTION..PAN_GESTURE_END_FRACTION
}

private const val PAN_GESTURE_START_FRACTION = 0.25f
private const val PAN_GESTURE_END_FRACTION = 0.75f
private const val CHAPTER_SWIPE_DISTANCE_FRACTION = 0.12f
private const val CHAPTER_SWIPE_DIRECTION_RATIO = 1.5f
private const val CHAPTER_SWIPE_MAX_SCALE_DELTA = 0.06f
private const val CHAPTER_SWIPE_MAX_ROTATION_DEGREES = 8f
private const val CHAPTER_SWIPE_MAX_ACTIVE_ZOOM = 1.02f
private const val SEEK_PREVIEW_HYSTERESIS_FRACTION = 0.05f

enum class ChapterSwipeDirection {
    PREVIOUS,
    NEXT,
}
