package one.only.player.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogsSelectionContainer(
    logs: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
    ) {
        Text(
            text = logs,
            fontFamily = FontFamily.Monospace,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(4.dp),
        )
    }
}
