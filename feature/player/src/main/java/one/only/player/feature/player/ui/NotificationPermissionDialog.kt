package one.only.player.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun NotificationPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppDialog(
        modifier = Modifier.testTag("dialog_notification_permission"),
        title = stringResource(R.string.playback_notifications),
        onDismissRequest = onDismissRequest,
        content = {
            Text(text = stringResource(R.string.playback_notifications_settings))
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_notification_settings"),
                text = stringResource(R.string.open_settings),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = onOpenSettings,
            )
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("btn_notification_continue"),
                text = stringResource(R.string.continue_playback),
                onClick = onDismissRequest,
            )
        },
    )
}
