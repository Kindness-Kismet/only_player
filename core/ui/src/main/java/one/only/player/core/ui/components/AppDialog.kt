package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.navigation.MiuixBackHandler
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    additionalButton: @Composable (() -> Unit)? = null,
) {
    val dialogState = LocalAppDialogState.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isShown = dialogState?.isShown ?: true

    LaunchedEffect(isShown) {
        if (!isShown) keyboard?.hide()
    }

    MiuixInteractions {
        WindowDialog(
            show = isShown,
            modifier = modifier
                .semantics { testTagsAsResourceId = true },
            title = title,
            onDismissRequest = onDismissRequest,
            onDismissFinished = dialogState?.onDismissFinished,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    content()
                }
                DialogButtonRow(
                    confirmButton = confirmButton,
                    dismissButton = dismissButton,
                    additionalButton = additionalButton,
                )
            }
            MiuixBackHandler(enabled = isShown && WindowInsets.isImeVisible) {
                keyboard?.hide()
            }
        }
    }
}

@Composable
fun DoneCancelDialog(
    title: String,
    onDoneClick: () -> Unit,
    onDismissClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    AppDialog(
        title = title,
        confirmButton = { DoneButton(onClick = onDoneClick) },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        onDismissRequest = onDismissClick,
        content = content,
    )
}

@Composable
private fun DialogButtonRow(
    confirmButton: @Composable (() -> Unit)?,
    dismissButton: @Composable (() -> Unit)?,
    additionalButton: @Composable (() -> Unit)?,
) {
    val buttons = listOfNotNull(additionalButton, dismissButton, confirmButton)
    if (buttons.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            Box(
                modifier = Modifier.weight(1f),
                propagateMinConstraints = true,
            ) {
                button()
            }
        }
    }
}

object AppDialogDefaults {
    val contentMaxHeight: Dp = 420.dp
}
