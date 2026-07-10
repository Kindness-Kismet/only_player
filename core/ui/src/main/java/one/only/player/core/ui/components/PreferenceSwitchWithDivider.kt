package one.only.player.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceSwitchWithDivider(
    title: String = "",
    modifier: Modifier = Modifier,
    switchModifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    onChecked: () -> Unit = {},
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        BasicComponent(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            onClick = onClick,
            enabled = isEnabled,
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .height(40.dp)
                            .width(1.dp)
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                    )
                    Switch(
                        modifier = switchModifier,
                        checked = isChecked,
                        onCheckedChange = { onChecked() },
                        enabled = isEnabled,
                    )
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreferenceSwitchWithDividerPreview() {
    PreferenceSwitchWithDivider(
        title = "Title",
        description = "Description of the preference items goes here.",
        icon = NextIcons.DoubleTap,
        onClick = {},
        onChecked = {},
    )
}

@Composable
fun PreferenceCheckbox(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    isChecked: Boolean = true,
    onClick: (() -> Unit) = {},
    onLongClick: (() -> Unit) = {},
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem = true, isLastItem = true),
        color = MiuixTheme.colorScheme.surfaceContainer,
    ) {
        CheckboxPreference(
            title = title,
            summary = description,
            checked = isChecked,
            onCheckedChange = { onClick() },
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            checkboxLocation = CheckboxLocation.End,
            enabled = isEnabled,
        )
    }
}
