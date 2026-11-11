package one.only.player.core.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

// MaterialTheme 内的 Miuix 控件使用原生按压状态层。
@Composable
internal fun MiuixInteractions(content: @Composable () -> Unit) {
    val color = MiuixTheme.colorScheme.onBackground
    val indication = remember(color) { MiuixIndication(color) }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}
