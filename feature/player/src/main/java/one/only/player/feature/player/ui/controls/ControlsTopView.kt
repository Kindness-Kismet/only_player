package one.only.player.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import one.only.player.core.model.ControlButtonsPosition
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlZone
import one.only.player.core.model.VideoContentScale
import one.only.player.core.ui.R
import one.only.player.core.ui.extensions.copy
import one.only.player.feature.player.AnimatedPlayerControlPlacement
import one.only.player.feature.player.buttons.PlayerButton
import one.only.player.feature.player.playerControlDragSource
import one.only.player.feature.player.playerControlZoneTarget
import one.only.player.feature.player.state.SleepTimerState

@OptIn(UnstableApi::class)
@Composable
fun ControlsTopView(
    modifier: Modifier = Modifier,
    title: String,
    player: Player,
    topRightControls: List<PlayerControl>,
    controlButtonsPosition: ControlButtonsPosition,
    visiblePlayerControls: Set<PlayerControl>,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    isTakingScreenshot: Boolean,
    itemBounds: MutableMap<PlayerControl, Rect>,
    zoneBounds: MutableMap<PlayerControlZone, Rect>,
    isCustomizingControls: Boolean = false,
    shouldHideLabels: Boolean = false,
    draggingControl: PlayerControl? = null,
    onControlDropDragged: (PlayerControl, Offset) -> Unit = { _, _ -> },
    onControlDragStarted: (PlayerControl) -> Unit = {},
    onControlDragMoved: (PlayerControl, Offset) -> Unit = { _, _ -> },
    onControlDragCancelled: (PlayerControl) -> Unit = {},
    isBackVisible: Boolean = true,
    isBackSelected: Boolean = false,
    isBackInteractive: Boolean = true,
    onAudioClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onPlaybackSpeedClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    onLockControlsClick: () -> Unit = {},
    isMuted: Boolean = false,
    onMuteClick: () -> Unit = {},
    onPlaybackMarksClick: () -> Unit = {},
    onVideoContentScaleClick: () -> Unit = {},
    onVideoContentScaleLongClick: () -> Unit = {},
    onDecoderClick: () -> Unit = {},
    onAmbienceModeClick: () -> Unit = {},
    isAmbienceModeEnabled: Boolean = false,
    onVideoFiltersClick: () -> Unit = {},
    onPictureInPictureClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onRotateLongClick: () -> Unit = {},
    isOrientationRemembered: Boolean = false,
    onScreenshotClick: () -> Unit = {},
    onPlayInBackgroundClick: () -> Unit = {},
    onCustomizeControlsClick: () -> Unit = {},
    onLoopClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    onSleepTimerClick: (() -> Unit)? = null,
    sleepTimerState: SleepTimerState? = null,
    onBackClick: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // 旧版右上角固定最多 4 个，与进度条上方右侧控件同垂直对齐风格；不可横向滚动加更多
    val maxVisibleCount = 4
    val renderedTopControls = topRightControls
        .filter { control -> isCustomizingControls || control in visiblePlayerControls }
        .take(maxVisibleCount)

    @Composable
    fun BackButton() {
        if (!isBackVisible) return

        PlayerButton(
            onClick = onBackClick,
            isSelected = isBackSelected,
            label = stringResource(R.string.player_controls_exit).takeIf { isCustomizingControls },
            shouldShowSelectionBadge = false,
            shouldDimWhenUnselected = false,
            shouldShowCustomizeFrame = false,
            isInteractive = isBackInteractive,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = "btn_back",
            )
        }
    }

    @Composable
    fun RowScope.Title() {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true),
        )
    }

    @Composable
    fun RowScope.TopControls() {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .then(
                    when (isCustomizingControls) {
                        true -> Modifier.playerControlZoneTarget(
                            zone = PlayerControlZone.TOP_RIGHT,
                            zoneBounds = zoneBounds,
                        )
                        false -> Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            // 与进度条上方右侧控件 spacedBy(8.dp) 对齐
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            renderedTopControls.forEach { control ->
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
                            isTakingScreenshot = isTakingScreenshot,
                            onScreenshotClick = onScreenshotClick,
                            onPlayInBackgroundClick = onPlayInBackgroundClick,
                            onCustomizeControlsClick = onCustomizeControlsClick,
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

    // 与进度条上方右侧控件垂直方向对齐：水平边距 8.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            // 旧版右上角最右间距 24dp
            .padding(start = 8.dp, end = 24.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (controlButtonsPosition) {
            ControlButtonsPosition.LEFT -> {
                BackButton()
                Title()
                TopControls()
            }
            ControlButtonsPosition.RIGHT -> {
                TopControls()
                Title()
                BackButton()
            }
        }
    }
}
