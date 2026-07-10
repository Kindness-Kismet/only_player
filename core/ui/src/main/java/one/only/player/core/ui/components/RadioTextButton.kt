package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

@Composable
fun RadioTextButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RadioButtonPreference(
        modifier = modifier,
        title = text,
        selected = isSelected,
        onClick = onClick,
    )
}
