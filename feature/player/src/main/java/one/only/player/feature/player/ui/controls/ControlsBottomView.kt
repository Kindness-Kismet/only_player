package one.only.player.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration.Companion.milliseconds
import one.only.player.core.model.ControlButtonsPosition
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlZone
import one.only.player.core.model.VideoContentScale
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.feature.player.AnimatedPlayerControlPlacement
import one.only.player.feature.player.LocalControlsVisibilityState
import one.only.player.feature.player.LocalPlayerIconStyle
import one.only.player.core.model.PlayerIconStyle
import one.only.player.feature.player.buttons.PlayerButton
import one.only.player.feature.player.extensions.formatted
import one.only.player.feature.player.extensions.noRippleClickable
import one.only.player.feature.player.playerControlDragSource
import one.only.player.feature.player.playerControlZoneTarget
import one.only.player.feature.player.state.MediaPresentationState
import one.only.player.feature.player.state.SleepTimerState
import one.only.player.feature.player.state.durationFormatted

@OptIn(UnstableApi::class)
@Composable
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    player: Player,
    mediaPresentationState: MediaPresentationState,
    bottomLeftControls: List<PlayerControl>,
    aboveSeekbarRightControls: List<PlayerControl> = emptyList(),
    controlButtonsPosition: ControlButtonsPosition,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    pendingSeekPosition: Long?,
    itemBounds: MutableMap<PlayerControl, Rect>,
    zoneBounds: MutableMap<PlayerControlZone, Rect>,
    onPlaylistClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onAmbienceModeClick: () -> Unit,
    isAmbienceModeEnabled: Boolean,
    onVideoFiltersClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    isMuted: Boolean,
    onMuteClick: () -> Unit,
    onPlaybackMarksClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    onRotateLongClick: () -> Unit = {},
    isOrientationRemembered: Boolean = false,
    onPlayInBackgroundClick: () -> Unit,
    isTakingScreenshot: Boolean,
    onScreenshotClick: () -> Unit,
    onCustomizeControlsClick: () -> Unit,
    onLoopClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    onSleepTimerClick: (() -> Unit)? = null,
    sleepTimerState: SleepTimerState? = null,
    isCustomizingControls: Boolean,
    shouldHideLabels: Boolean,
    shouldKeepHiddenControlSlots: Boolean = false,
    draggingControl: PlayerControl? = null,
    onControlDropDragged: (PlayerControl, Offset) -> Unit = { _, _ -> },
    onControlDragStarted: (PlayerControl) -> Unit = {},
    onControlDragMoved: (PlayerControl, Offset) -> Unit = { _, _ -> },
    onControlDragCancelled: (PlayerControl) -> Unit = {},
    visiblePlayerControls: Set<PlayerControl>,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val controlsVisibilityState = LocalControlsVisibilityState.current
    val displayedPosition = pendingSeekPosition ?: mediaPresentationState.position
    val displayedPendingPosition = (mediaPresentationState.duration - displayedPosition).coerceAtLeast(0L)

    fun isVisible(control: PlayerControl): Boolean = isCustomizingControls || control in visiblePlayerControls
    fun isSelected(control: PlayerControl): Boolean = isCustomizingControls && control in visiblePlayerControls

    // 相对原先同列布局：时间再下移 26dp，进度条 5dp，进度条右上控件 6dp
    val timeLowerOffset = 26.dp
    val seekbarLowerOffset = 5.dp
    val aboveSeekbarControlLowerOffset = 6.dp

    Column(
        modifier = modifier
            .padding(systemBarsPadding.copy(top = 0.dp))
            .padding(start = 12.dp, end = 8.dp)
            .padding(top = 16.dp, bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            var shouldShowPendingPosition by rememberSaveable { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(y = timeLowerOffset)
                    .noRippleClickable {
                        shouldShowPendingPosition = !shouldShowPendingPosition
                    },
            ) {
                Text(
                    text = when (shouldShowPendingPosition) {
                        true -> "-${displayedPendingPosition.milliseconds.formatted()}"
                        false -> displayedPosition.milliseconds.formatted()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = mediaPresentationState.durationFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }

            // 进度条上方最右侧只保留 1 个控件，不能添多
            val limitedAboveSeekbarControls = aboveSeekbarRightControls
                .filter { control -> isCustomizingControls || control in visiblePlayerControls }
                .take(1)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = aboveSeekbarControlLowerOffset)
                    .padding(end = 8.dp)
                    .heightIn(min = 40.dp)
                    .playerControlZoneTarget(
                        zone = PlayerControlZone.ABOVE_SEEKBAR_RIGHT,
                        zoneBounds = zoneBounds,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                // 无控件时在编辑模式显示可投放空位
                if (isCustomizingControls && limitedAboveSeekbarControls.isEmpty()) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
                limitedAboveSeekbarControls.forEach { control ->
                    key(control) {
                        AnimatedPlayerControlPlacement(
                            control = control,
                            itemBounds = itemBounds,
                            isTracking = isCustomizingControls,
                        ) {
                            PlayerCustomizableControlButton(
                                modifier = Modifier.playerControlDragSource(
                                    control = control,
                                    enabled = isCustomizingControls,
                                    onDropDragged = onControlDropDragged,
                                    onDragStarted = onControlDragStarted,
                                    onDragMoved = onControlDragMoved,
                                    onDragCancelled = onControlDragCancelled,
                                ),
                                control = control,
                                isBeingDragged = draggingControl == control,
                                player = player,
                                videoContentScale = videoContentScale,
                                isPipSupported = isPipSupported,
                                isCustomizingControls = isCustomizingControls,
                                shouldHideLabel = shouldHideLabels,
                                visiblePlayerControls = visiblePlayerControls,
                                isMuted = isMuted,
                                onPlaylistClick = onPlaylistClick,
                                onPlaybackSpeedClick = onPlaybackSpeedClick,
                                onAudioClick = onAudioClick,
                                onSubtitleClick = onSubtitleClick,
                                onLockControlsClick = onLockControlsClick,
                                onMuteClick = onMuteClick,
                                onPlaybackMarksClick = onPlaybackMarksClick,
                                onVideoContentScaleClick = onVideoContentScaleClick,
                                onVideoContentScaleLongClick = onVideoContentScaleLongClick,
                                onDecoderClick = onDecoderClick,
                                onAmbienceModeClick = onAmbienceModeClick,
                                isAmbienceModeEnabled = isAmbienceModeEnabled,
                                onVideoFiltersClick = onVideoFiltersClick,
                                onPictureInPictureClick = onPictureInPictureClick,
                                onRotateClick = onRotateClick,
                                onRotateLongClick = onRotateLongClick,
                                isOrientationRemembered = isOrientationRemembered,
                                onCustomizeControlsClick = onCustomizeControlsClick,
                                isTakingScreenshot = isTakingScreenshot,
                                onScreenshotClick = onScreenshotClick,
                                onPlayInBackgroundClick = onPlayInBackgroundClick,
                                onLoopClick = onLoopClick,
                                onShuffleClick = onShuffleClick,
                                onSleepTimerClick = onSleepTimerClick,
                                sleepTimerState = sleepTimerState,
                            )
                        }
                    }
                }
            }
        }
        // 时间与进度条保持 12dp 间距，进度条再额外下移 5dp
        Spacer(modifier = Modifier.height(12.dp))
        PlayerSeekbar(
            modifier = Modifier
                .offset(y = seekbarLowerOffset)
                .padding(
                    playerProgressHorizontalPadding(
                        containerHorizontalPadding = 8.dp,
                        trackEdgeInset = 2.dp,
                    ),
                ),
            position = displayedPosition.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            onSeek = {
                controlsVisibilityState?.showControls()
                onSeek(it.toLong())
            },
            onSeekFinished = { onSeekEnd() },
        )
        // 进度条下移后给底栏留空隙，底栏间距 12dp
        Spacer(modifier = Modifier.height(12.dp + seekbarLowerOffset))
        // 只统计真实绘制的按钮（不含隐藏占位），避免误开横滑
        val renderedBottomControls = bottomLeftControls.filter { control ->
            isCustomizingControls || control in visiblePlayerControls
        }
        val shouldAllowHorizontalScroll = renderedBottomControls.size > 5
        val bottomControlsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
                .then(
                    when (isCustomizingControls) {
                        true ->
                            Modifier
                                .playerControlZoneTarget(
                                    zone = PlayerControlZone.BOTTOM_LEFT,
                                    zoneBounds = zoneBounds,
                                )
                                .heightIn(min = 72.dp)
                        false -> Modifier
                    },
                )
                .horizontalScroll(
                    state = bottomControlsScrollState,
                    enabled = shouldAllowHorizontalScroll,
                ),
            verticalAlignment = when (isCustomizingControls) {
                true -> Alignment.Top
                false -> Alignment.CenterVertically
            },
            horizontalArrangement = when (controlButtonsPosition) {
                ControlButtonsPosition.LEFT -> Arrangement.spacedBy(8.dp, Alignment.Start)
                ControlButtonsPosition.RIGHT -> Arrangement.spacedBy(8.dp, Alignment.End)
            },
        ) {
            bottomLeftControls.forEach { control ->
                val isHidden = control !in visiblePlayerControls
                if (!isCustomizingControls && isHidden && !shouldKeepHiddenControlSlots) return@forEach
                key(control) {
                    if (!isCustomizingControls && isHidden && shouldKeepHiddenControlSlots) {
                        // 只留白占位，不画框、不可点
                        Spacer(modifier = Modifier.size(40.dp))
                    } else {
                        AnimatedPlayerControlPlacement(
                            control = control,
                            itemBounds = itemBounds,
                            isTracking = isCustomizingControls,
                        ) {
                            PlayerCustomizableControlButton(
                                modifier = Modifier.playerControlDragSource(
                                    control = control,
                                    enabled = isCustomizingControls,
                                    onDropDragged = onControlDropDragged,
                                    onDragStarted = onControlDragStarted,
                                    onDragMoved = onControlDragMoved,
                                    onDragCancelled = onControlDragCancelled,
                                ),
                                control = control,
                                isBeingDragged = draggingControl == control,
                                isOutlineOnly = false,
                                player = player,
                                videoContentScale = videoContentScale,
                                isPipSupported = isPipSupported,
                                isCustomizingControls = isCustomizingControls,
                                shouldHideLabel = shouldHideLabels,
                                visiblePlayerControls = visiblePlayerControls,
                                isMuted = isMuted,
                                onPlaylistClick = onPlaylistClick,
                                onPlaybackSpeedClick = onPlaybackSpeedClick,
                                onAudioClick = onAudioClick,
                                onSubtitleClick = onSubtitleClick,
                                onLockControlsClick = onLockControlsClick,
                                onMuteClick = onMuteClick,
                                onPlaybackMarksClick = onPlaybackMarksClick,
                                onVideoContentScaleClick = onVideoContentScaleClick,
                                onVideoContentScaleLongClick = onVideoContentScaleLongClick,
                                onDecoderClick = onDecoderClick,
                                onAmbienceModeClick = onAmbienceModeClick,
                                isAmbienceModeEnabled = isAmbienceModeEnabled,
                                onVideoFiltersClick = onVideoFiltersClick,
                                onPictureInPictureClick = onPictureInPictureClick,
                                onRotateClick = onRotateClick,
                                onRotateLongClick = onRotateLongClick,
                                isOrientationRemembered = isOrientationRemembered,
                                onCustomizeControlsClick = onCustomizeControlsClick,
                                isTakingScreenshot = isTakingScreenshot,
                                onScreenshotClick = onScreenshotClick,
                                onPlayInBackgroundClick = onPlayInBackgroundClick,
                                onLoopClick = onLoopClick,
                                onShuffleClick = onShuffleClick,
                                onSleepTimerClick = onSleepTimerClick,
                                sleepTimerState = sleepTimerState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialYouSlider(
            modifier = modifier.fillMaxWidth(),
            value = position,
            valueRange = 0f..duration,
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    // 全透图标风格：进度条与控件一致用白色，不使用主题着色
    val accentColor = when (LocalPlayerIconStyle.current) {
        PlayerIconStyle.TRANSPARENT -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }
    val primaryColor = accentColor
    val interactionSource = remember { MutableInteractionSource() }
    val trackHeight = 8.dp
    val thumbWidth = 4.dp
    val trackThumbGapWidth = 12.dp

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.height(24.dp).semantics { contentDescription = "slider_seek" },
        track = { sliderState ->
            val disabledAlpha = 0.4f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight),
            ) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                val playedPixels = size.width * playedFraction

                val endCornerRadius = size.height / 2f
                val insideCornerRadius = 2.dp.toPx()
                val gapHalf = trackThumbGapWidth.toPx() / 2f
                val leftEnd = (playedPixels - gapHalf).coerceIn(0f, size.width)
                val rightStart = (playedPixels + gapHalf).coerceIn(0f, size.width)

                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }

                if (rightStart < size.width) {
                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(size.width - rightStart, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = endCornerRadius,
                    )
                }

                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor,
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .height(20.dp)
                    .background(primaryColor, CircleShape),
            )
        },
    )
}

private fun DrawScope.drawRoundedRect(
    offset: Offset,
    size: Size,
    color: Color,
    startCornerRadius: Float,
    endCornerRadius: Float,
) {
    val startCorner = CornerRadius(startCornerRadius, startCornerRadius)
    val endCorner = CornerRadius(endCornerRadius, endCornerRadius)
    val track = RoundRect(
        rect = Rect(Offset(offset.x, 0f), size = Size(size.width, size.height)),
        topLeft = startCorner,
        topRight = endCorner,
        bottomRight = endCorner,
        bottomLeft = startCorner,
    )
    drawPath(
        path = Path().apply {
            addRoundRect(track)
        },
        color = color,
    )
}
