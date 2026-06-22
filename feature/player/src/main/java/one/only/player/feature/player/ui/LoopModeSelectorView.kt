package one.only.player.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import one.only.player.core.ui.R

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.LoopModeSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    player: Player,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.loop_mode),
        testTag = "panel_loop_mode",
    ) {
        LoopModeSelectorContent(
            player = player,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LoopModeSelectorContent(
    player: Player,
    onDismiss: () -> Unit,
) {
    val state = rememberRepeatButtonState(player)
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
            .selectableGroup(),
    ) {
        loopModeOptions().forEach { option ->
            RadioButtonRow(
                isSelected = state.repeatModeState == option.repeatMode,
                text = stringResource(option.labelRes),
                testTag = option.testTag,
                onClick = {
                    player.repeatMode = option.repeatMode
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun BoxScope.ShuffleModeSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    player: Player,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.shuffle),
        testTag = "panel_shuffle_mode",
    ) {
        ShuffleModeSelectorContent(
            player = player,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ShuffleModeSelectorContent(
    player: Player,
    onDismiss: () -> Unit,
) {
    val state = rememberShuffleButtonState(player)
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
            .selectableGroup(),
    ) {
        shuffleModeOptions().forEach { option ->
            RadioButtonRow(
                isSelected = state.shuffleOn == option.isEnabled,
                text = stringResource(option.labelRes),
                testTag = option.testTag,
                onClick = {
                    player.shuffleModeEnabled = option.isEnabled
                    onDismiss()
                },
            )
        }
    }
}

private data class LoopModeOption(
    val repeatMode: @Player.RepeatMode Int,
    val labelRes: Int,
    val testTag: String,
)

private data class ShuffleModeOption(
    val isEnabled: Boolean,
    val labelRes: Int,
    val testTag: String,
)

private fun loopModeOptions(): List<LoopModeOption> = listOf(
    LoopModeOption(
        repeatMode = Player.REPEAT_MODE_OFF,
        labelRes = R.string.loop_mode_off,
        testTag = "btn_loop_mode_off",
    ),
    LoopModeOption(
        repeatMode = Player.REPEAT_MODE_ONE,
        labelRes = R.string.loop_mode_one,
        testTag = "btn_loop_mode_one",
    ),
    LoopModeOption(
        repeatMode = Player.REPEAT_MODE_ALL,
        labelRes = R.string.loop_mode_all,
        testTag = "btn_loop_mode_all",
    ),
)

private fun shuffleModeOptions(): List<ShuffleModeOption> = listOf(
    ShuffleModeOption(
        isEnabled = false,
        labelRes = R.string.off,
        testTag = "btn_shuffle_mode_off",
    ),
    ShuffleModeOption(
        isEnabled = true,
        labelRes = R.string.on,
        testTag = "btn_shuffle_mode_on",
    ),
)
