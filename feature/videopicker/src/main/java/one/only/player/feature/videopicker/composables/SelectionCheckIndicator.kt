package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SelectionCheckIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isSelected) return

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary)
            .testTag("selection_check_indicator"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Basic.Check,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}
