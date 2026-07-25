package one.only.player.feature.player.buttons

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import one.only.player.core.ui.R as coreUiR
import one.only.player.feature.player.LocalControlsVisibilityState

@OptIn(UnstableApi::class)
@Composable
internal fun PreviousButton(
    player: Player,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    label: String? = null,
    isInteractive: Boolean = true,
    /**
     * 覆盖 Media3 默认「上一」可用态。
     * 关闭「重播当前」时首个文件应灰显（不可点），与新版控件一致。
     * null 时退回 Media3 [rememberPreviousButtonState]。
     */
    isEnabledOverride: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    val state = rememberPreviousButtonState(player)
    val controlsVisibilityState = LocalControlsVisibilityState.current
    val isEnabled = isEnabledOverride ?: state.isEnabled

    PlayerButton(
        modifier = modifier,
        buttonSize = 48.dp,
        isEnabled = isEnabled,
        isSelected = isSelected,
        label = label,
        isInteractive = isInteractive && isEnabled,
        onClick = {
            if (!isEnabled) return@PlayerButton
            if (onClick != null) {
                onClick()
            } else {
                state.onClick()
            }
            // 自定义 onClick（偏好「上一」）与默认路径都需要保持控件可见
            if (isInteractive) {
                controlsVisibilityState?.showControls()
            }
        },
    ) {
        Icon(
            painter = painterResource(coreUiR.drawable.ic_skip_prev),
            contentDescription = stringResource(coreUiR.string.player_controls_previous),
            modifier = Modifier.size(28.dp),
        )
    }
}
