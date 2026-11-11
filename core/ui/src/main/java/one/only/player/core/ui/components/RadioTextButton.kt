package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
fun RadioTextButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        RadioButtonPreference(
            modifier = modifier.squircleClip(16.dp),
            title = text,
            selected = isSelected,
            radioButtonLocation = RadioButtonLocation.End,
            onClick = onClick,
        )
    }
}
