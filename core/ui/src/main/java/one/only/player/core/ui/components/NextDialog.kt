package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NextDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current

    WindowDialog(
        show = true,
        modifier = modifier
            .widthIn(max = configuration.screenWidthDp.dp - NextDialogDefaults.dialogMargin * 2),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            Column {
                title()
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
            NextDialogButtonRow(
                confirmButton = confirmButton,
                dismissButton = dismissButton,
            )
        }
    }
}

@Composable
fun NextDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current

    WindowDialog(
        show = true,
        modifier = modifier
            .widthIn(max = configuration.screenWidthDp.dp - NextDialogDefaults.dialogMargin * 2),
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            // 字符串标题：略小于 title2，加粗居中，并与内容留距
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiuixText(
                    text = title,
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
            NextDialogButtonRow(
                confirmButton = confirmButton,
                dismissButton = dismissButton,
            )
        }
    }
}

@Composable
fun NextDialogWithDoneAndCancelButtons(
    title: String,
    onDoneClick: () -> Unit,
    onDismissClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    NextDialog(
        title = title,
        confirmButton = { DoneButton(onClick = onDoneClick) },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        onDismissRequest = onDismissClick,
        content = content,
    )
}

@Composable
private fun NextDialogButtonRow(
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dismissButton?.invoke()
        confirmButton()
    }
}

object NextDialogDefaults {
    val dialogMargin: Dp = 16.dp
}
