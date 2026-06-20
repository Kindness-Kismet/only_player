package one.only.player.feature.player.ui.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.VideoContentScale
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.feature.player.buttons.PlayerButton
import one.only.player.feature.player.state.SleepTimerState

@Composable
internal fun PlayerCustomizableControlButton(
    modifier: Modifier = Modifier,
    control: PlayerControl,
    player: Player,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    isCustomizingControls: Boolean,
    visiblePlayerControls: Set<PlayerControl>,
    isBeingDragged: Boolean = false,
    isOutlineOnly: Boolean = false,
    shouldHideLabel: Boolean = false,
    isMuted: Boolean,
    onPlaylistClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onMuteClick: () -> Unit,
    onPlaybackMarksClick: () -> Unit,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onDecoderClick: () -> Unit,
    onAmbienceModeClick: () -> Unit,
    isAmbienceModeEnabled: Boolean,
    onVideoFiltersClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    isTakingScreenshot: Boolean,
    onScreenshotClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onLoopClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    onSleepTimerClick: (() -> Unit)? = null,
    sleepTimerState: SleepTimerState? = null,
) {
    if (!isCustomizingControls && control !in visiblePlayerControls) return
    if (!isCustomizingControls && control == PlayerControl.PIP && !isPipSupported) return

    val isSelected = isCustomizingControls && control in visiblePlayerControls
    val isPlaceholder = isBeingDragged || isOutlineOnly
    val shouldShowLabel = isCustomizingControls || !shouldHideLabel
    val label = control.label(isMuted).takeIf { shouldShowLabel }
    val buttonModifier = modifier

    when (control) {
        PlayerControl.PLAYLIST -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlaylistClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist),
                    contentDescription = "btn_playlist",
                )
            }
        }

        PlayerControl.PLAYBACK_SPEED -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlaybackSpeedClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = "btn_speed",
                )
            }
        }

        PlayerControl.AUDIO -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onAudioClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_track),
                    contentDescription = "btn_audio",
                )
            }
        }

        PlayerControl.SUBTITLE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onSubtitleClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_subtitle_track),
                    contentDescription = "btn_subtitle",
                )
            }
        }

        PlayerControl.LOCK -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onLockControlsClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = "btn_lock",
                )
            }
        }

        PlayerControl.MUTE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onMuteClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(if (isMuted) R.drawable.ic_mute else R.drawable.ic_volume),
                    contentDescription = if (isMuted) "btn_unmute" else "btn_mute",
                )
            }
        }

        PlayerControl.MARK -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlaybackMarksClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.History,
                    contentDescription = "btn_playback_marks",
                )
            }
        }

        PlayerControl.SCALE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onVideoContentScaleClick,
                onLongClick = onVideoContentScaleLongClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.Frame,
                    contentDescription = "btn_scale",
                )
            }
        }

        PlayerControl.DECODER -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onDecoderClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.Decoder,
                    contentDescription = "btn_decoder",
                )
            }
        }

        PlayerControl.AMBIENCE_MODE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onAmbienceModeClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = if (isAmbienceModeEnabled) NextIcons.Frame else NextIcons.Background,
                    contentDescription = "btn_ambience_mode",
                )
            }
        }

        PlayerControl.VIDEO_FILTERS -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onVideoFiltersClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.Sensitivity,
                    contentDescription = "btn_video_filters",
                )
            }
        }

        PlayerControl.PIP -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPictureInPictureClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pip),
                    contentDescription = "btn_pip",
                )
            }
        }

        PlayerControl.SCREENSHOT -> {
            PlayerButton(
                modifier = buttonModifier.alpha(if (isTakingScreenshot) 0.5f else 1f),
                onClick = onScreenshotClick,
                isSelected = isSelected,
                isEnabled = !isTakingScreenshot,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                if (isTakingScreenshot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = NextIcons.Image,
                        contentDescription = "btn_screenshot",
                    )
                }
            }
        }

        PlayerControl.BACKGROUND_PLAY -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlayInBackgroundClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_headset),
                    contentDescription = "btn_background",
                )
            }
        }

        PlayerControl.LOOP -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = {
                    onLoopClick?.invoke()
                },
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.Loop,
                    contentDescription = "btn_loop",
                )
            }
        }

        PlayerControl.SHUFFLE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = {
                    onShuffleClick?.invoke()
                },
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    imageVector = NextIcons.Shuffle,
                    contentDescription = "btn_shuffle",
                )
            }
        }

        PlayerControl.SLEEP_TIMER -> {
            val isSleepTimerActive = sleepTimerState?.isActive == true
            PlayerButton(
                modifier = buttonModifier,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
                onClick = {
                    onSleepTimerClick?.invoke()
                },
            ) {
                if (isSleepTimerActive) {
                    val remainingMillis = sleepTimerState.remainingMillis
                    val remainingMin = ((remainingMillis + 59_999) / 60_000).toInt()
                    Text(
                        text = "$remainingMin",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_sleep_timer),
                        contentDescription = "btn_sleep_timer",
                    )
                }
            }
        }

        PlayerControl.ROTATE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onRotateClick,
                isSelected = isSelected,
                label = label,
                shouldDimWhenUnselected = isCustomizingControls,
                shouldShowCustomizeFrame = isCustomizingControls,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_screen_rotation),
                    contentDescription = "btn_rotate",
                )
            }
        }

        PlayerControl.BACK,
        PlayerControl.PREVIOUS,
        PlayerControl.PLAY_PAUSE,
        PlayerControl.NEXT,
        -> Unit
    }
}

@Composable
private fun PlayerControl.label(isMuted: Boolean): String = when (this) {
    PlayerControl.PLAYLIST -> stringResource(R.string.now_playing)

    PlayerControl.PLAYBACK_SPEED -> stringResource(R.string.speed)

    PlayerControl.AUDIO -> stringResource(R.string.audio)

    PlayerControl.SUBTITLE -> stringResource(R.string.subtitle)

    PlayerControl.LOCK -> stringResource(R.string.controls_lock)

    PlayerControl.MUTE -> stringResource(if (isMuted) R.string.controls_unmute else R.string.controls_mute)

    PlayerControl.MARK -> stringResource(R.string.controls_mark)

    PlayerControl.SCALE -> stringResource(R.string.video_zoom)

    PlayerControl.DECODER -> stringResource(R.string.decoder)

    PlayerControl.AMBIENCE_MODE -> stringResource(R.string.ambience_mode)

    PlayerControl.VIDEO_FILTERS -> stringResource(R.string.video_filters)

    PlayerControl.PIP -> stringResource(R.string.pip_settings)

    PlayerControl.SCREENSHOT -> stringResource(R.string.take_screenshot)

    PlayerControl.BACKGROUND_PLAY -> stringResource(R.string.background_play)

    PlayerControl.LOOP -> stringResource(R.string.loop_mode)

    PlayerControl.SHUFFLE -> stringResource(R.string.shuffle)

    PlayerControl.SLEEP_TIMER -> stringResource(R.string.sleep_timer)

    PlayerControl.ROTATE -> stringResource(R.string.screen_rotation)

    PlayerControl.BACK,
    PlayerControl.PREVIOUS,
    PlayerControl.PLAY_PAUSE,
    PlayerControl.NEXT,
    -> ""
}
