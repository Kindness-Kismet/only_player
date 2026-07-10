package one.only.player.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceSwitch(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        SwitchPreference(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            checked = isChecked,
            onCheckedChange = { onClick() },
            enabled = isEnabled,
        )
    }
}

@Preview
@Composable
fun PreferenceSwitchPreview() {
    PreferenceSwitch(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
    )
}
