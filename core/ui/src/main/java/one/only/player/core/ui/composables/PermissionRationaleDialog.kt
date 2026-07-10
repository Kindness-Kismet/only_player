package one.only.player.core.ui.composables

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.ui.R
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun PermissionRationaleDialog(
    text: String,
    modifier: Modifier = Modifier,
    onConfirmButtonClick: () -> Unit,
) {
    NextDialog(
        onDismissRequest = {},
        modifier = modifier,
        title = stringResource(R.string.permission_request),
        content = {
            Text(text = text)
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_permission_grant"),
                text = stringResource(R.string.grant_permission),
                onClick = onConfirmButtonClick,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        },
    )
}

@DayNightPreview
@Composable
fun PermissionRationaleDialogPreview() {
    OnlyPlayerTheme {
        Surface {
            PermissionRationaleDialog(
                text = stringResource(
                    id = R.string.permission_info,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ),
                onConfirmButtonClick = {},
            )
        }
    }
}
