package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(2.dp),
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Normal),
        color = contentColor,
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .padding(vertical = 1.dp, horizontal = 3.dp),
    )
}
