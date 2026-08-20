package one.only.player.feature.player.ui.controls

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import one.only.player.core.model.PlayerControl
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.feature.player.LocalControlsVisibilityState
import one.only.player.feature.player.extensions.formatted
import one.only.player.feature.player.extensions.noRippleClickable
import one.only.player.feature.player.state.MediaPresentationState
import one.only.player.feature.player.state.durationFormatted
import one.only.player.feature.player.ui.MenuRoute
import one.only.player.feature.player.ui.PlayerControlBinding
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ControlsBottomModernView(
    modifier: Modifier = Modifier,
    mediaPresentationState: MediaPresentationState,
    pendingSeekPosition: Long?,
    shouldAnimateSeekPreview: Boolean,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    bottomRightControls: List<PlayerControl>,
    bindings: Map<PlayerControl, PlayerControlBinding>,
    maxVisibleControls: Int,
    onOpenPanel: (MenuRoute) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val controlsVisibilityState = LocalControlsVisibilityState.current
    val displayedPosition = pendingSeekPosition ?: mediaPresentationState.position
    val displayedPendingPosition = (mediaPresentationState.duration - displayedPosition).coerceAtLeast(0L)
    Column(
        modifier = modifier
            .padding(systemBarsPadding)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModernSeekbar(
            modifier = Modifier.padding(
                playerProgressHorizontalPadding(
                    containerHorizontalPadding = 8.dp,
                    trackEdgeInset = 7.dp,
                ),
            ),
            position = displayedPosition.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            shouldAnimatePosition = shouldAnimateSeekPreview,
            onSeek = {
                controlsVisibilityState?.showControls()
                onSeek(it.toLong())
            },
            onSeekFinished = { onSeekEnd() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIconButton(
                modifier = Modifier.testTag("btn_play_pause_modern"),
                onClick = onPlayPauseClick,
            ) {
                MiuixIcon(
                    modifier = Modifier.size(28.dp),
                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = stringResource(R.string.player_controls_play_pause),
                    tint = Color.White,
                )
            }
            var shouldShowPendingPosition by rememberSaveable { mutableStateOf(false) }
            val positionText = when (shouldShowPendingPosition) {
                true -> "-${displayedPendingPosition.milliseconds.formatted()}"
                false -> displayedPosition.milliseconds.formatted()
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .noRippleClickable {
                        shouldShowPendingPosition = !shouldShowPendingPosition
                    },
                verticalArrangement = Arrangement.Center,
            ) {
                MiuixText(
                    text = positionText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = Color.White,
                    maxLines = 1,
                )
                MiuixText(
                    text = mediaPresentationState.durationFormatted,
                    style = MiuixTheme.textStyles.footnote1,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            MiuixIconButton(
                modifier = Modifier.testTag("btn_previous_modern"),
                onClick = onPreviousClick,
                enabled = hasPrevious,
            ) {
                MiuixIcon(
                    modifier = Modifier.size(24.dp),
                    imageVector = AppIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.player_controls_previous),
                    tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            MiuixIconButton(
                modifier = Modifier.testTag("btn_next_modern"),
                onClick = onNextClick,
                enabled = hasNext,
            ) {
                MiuixIcon(
                    modifier = Modifier.size(24.dp),
                    imageVector = AppIcons.SkipNext,
                    contentDescription = stringResource(R.string.player_controls_next),
                    tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            PlayerCornerControls(
                controls = bottomRightControls,
                bindings = bindings,
                maxVisibleControls = maxVisibleControls,
                onOpenPanel = onOpenPanel,
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    shouldAnimatePosition: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val targetPosition = position.coerceIn(0f, duration.coerceAtLeast(0f))
    var continueAnimatingAfterRelease by remember { mutableStateOf(false) }
    val currentShouldAnimatePosition = rememberUpdatedState(shouldAnimatePosition)
    SideEffect {
        if (shouldAnimatePosition) continueAnimatingAfterRelease = true
    }
    val usePreviewAnimation = shouldAnimatePosition || continueAnimatingAfterRelease
    val displayedPosition by animateFloatAsState(
        targetValue = targetPosition,
        animationSpec = if (usePreviewAnimation) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
                visibilityThreshold = 1f,
            )
        } else {
            snap()
        },
        label = "seekPreviewPosition",
        finishedListener = {
            if (!currentShouldAnimatePosition.value) continueAnimatingAfterRelease = false
        },
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(
            modifier = modifier.fillMaxWidth(),
            value = displayedPosition,
            valueRange = 0f..duration.coerceAtLeast(0f),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(2.dp, Color.White, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
    }
}
