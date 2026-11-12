package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.designsystem.AppIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalPreferenceGrouped = compositionLocalOf { false }

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalPreferenceGrouped provides true) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors,
            content = content,
        )
    }
}

@Composable
internal fun PreferenceContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalPreferenceGrouped.current) {
        Box(modifier = modifier) {
            content()
        }
    } else {
        Surface(
            shape = preferenceCardShape(),
            color = MiuixTheme.colorScheme.surfaceContainer,
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    isEnabled: Boolean,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    showArrow: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    PreferenceContainer(modifier = modifier) {
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

// 独立卡片圆角：每个条目使用统一的完整圆角
@Composable
internal fun preferenceCardShape(): RoundedCornerShape = RoundedCornerShape(18.dp)

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
) {
    PreferenceContainer(modifier = modifier) {
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
) {
    PreferenceContainer(modifier = modifier) {
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
        icon = AppIcons.DoubleTap,
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
