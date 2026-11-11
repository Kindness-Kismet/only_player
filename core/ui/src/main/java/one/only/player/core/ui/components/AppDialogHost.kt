package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

// 业务状态关闭后保留弹窗内容，交给 WindowDialog 完成退出动画。
@Composable
fun <T : Any> AppDialogHost(
    dialog: T?,
    content: @Composable (T) -> Unit,
) {
    var retainedDialog by remember { mutableStateOf(dialog) }
    val latestDialog by rememberUpdatedState(dialog)
    val currentDialog = dialog ?: retainedDialog ?: return

    SideEffect {
        if (dialog != null) retainedDialog = dialog
    }
    CompositionLocalProvider(
        LocalAppDialogState provides AppDialogState(
            isShown = dialog != null,
            onDismissFinished = {
                if (latestDialog == null) retainedDialog = null
            },
        ),
    ) {
        content(currentDialog)
    }
}

internal val LocalAppDialogState = staticCompositionLocalOf<AppDialogState?> { null }

internal data class AppDialogState(
    val isShown: Boolean,
    val onDismissFinished: () -> Unit,
)
