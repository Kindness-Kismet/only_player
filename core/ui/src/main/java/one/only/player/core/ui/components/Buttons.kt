package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.ui.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun DoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    TextButton(
        text = stringResource(R.string.done),
        enabled = isEnabled,
        onClick = onClick,
        modifier = Modifier
            .testTag("btn_done")
            .then(modifier),
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
}

@Composable
fun CancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    TextButton(
        text = stringResource(R.string.cancel),
        enabled = isEnabled,
        onClick = onClick,
        modifier = Modifier
            .testTag("btn_cancel")
            .then(modifier),
    )
}
