package one.only.player.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    showArrow: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        if (showArrow) {
            ArrowPreference(
                title = title,
                summary = description,
                startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
                endActions = trailingContent,
                onClick = onClick,
                enabled = isEnabled,
            )
        } else {
            BasicComponent(
                title = title,
                summary = description,
                startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
                endActions = trailingContent,
                onClick = if (isEnabled) onClick else null,
                enabled = isEnabled,
            )
        }
    }
}

// 段圆角：仅在段首/段尾大圆角，中间保持小圆角
@Composable
internal fun preferenceSegmentShape(
    isFirstItem: Boolean,
    isLastItem: Boolean,
): RoundedCornerShape {
    val large = 24.dp
    val small = 0.dp
    return RoundedCornerShape(
        topStart = if (isFirstItem) large else small,
        topEnd = if (isFirstItem) large else small,
        bottomStart = if (isLastItem) large else small,
        bottomEnd = if (isLastItem) large else small,
    )
}

@Composable
internal fun PreferenceIcon(
    icon: ImageVector,
    isEnabled: Boolean,
) {
    MiuixIcon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(end = 12.dp),
        tint = MiuixTheme.colorScheme.onBackground.applyAlpha(isEnabled),
    )
}

@Composable
fun SelectablePreference(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        CheckboxPreference(
            title = title,
            summary = description,
            checked = isSelected,
            onCheckedChange = { onClick() },
            checkboxLocation = CheckboxLocation.End,
        )
    }
}

@Composable
fun SingleSelectablePreference(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
) {
    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        RadioButtonPreference(
            title = title,
            summary = description,
            selected = isSelected,
            onClick = onClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreferenceItemPreview() {
    PreferenceItem(
        title = "Title",
        description = "Description of the preference item goes here.",
        icon = NextIcons.DoubleTap,
        isEnabled = true,
    )
}

@Preview(showBackground = true)
@Composable
fun SelectablePreferencePreview() {
    SelectablePreference(
        title = "Title",
        description = "Description of the preference item goes here.",
    )
}

internal fun Color.applyAlpha(isEnabled: Boolean): Color = if (isEnabled) this else this.copy(alpha = 0.6f)
