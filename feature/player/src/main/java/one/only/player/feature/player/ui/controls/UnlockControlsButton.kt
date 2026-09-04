package one.only.player.feature.player.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.feature.player.ui.panel.rememberPlayerPanelTokens
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton

private val UnlockControlsButtonSize = 32.dp
private val UnlockControlsIconSize = 20.dp

// 锁定时唯一的逃生出口：面板配色小圆钮，点击即解锁
@Composable
internal fun UnlockControlsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = rememberPlayerPanelTokens()
    MiuixIconButton(
        modifier = modifier
            .size(UnlockControlsButtonSize)
            .testTag("btn_unlock_controls")
            .clip(CircleShape)
            .background(tokens.containerColor),
        onClick = onClick,
    ) {
        MiuixIcon(
            modifier = Modifier.size(UnlockControlsIconSize),
            imageVector = AppIcons.Unlock,
            contentDescription = stringResource(R.string.controls_unlock),
            tint = tokens.contentColor,
        )
    }
}
