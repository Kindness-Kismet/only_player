package one.only.player.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
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
    // 始终用 combinedClickable 处理点击/长按，避免 ArrowPreference 吞掉 long-click
    val clickModifier = if (isEnabled) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier.then(clickModifier),
    ) {
        // 不走 BasicComponent 的 onClick，避免长按失效
        BasicComponent(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            endActions = {
                trailingContent()
                if (showArrow) {
                    MiuixIcon(
                        imageVector = NextIcons.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
            onClick = null,
            enabled = isEnabled,
        )
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

private fun Color.applyAlpha(isEnabled: Boolean): Color =
    if (isEnabled) this else copy(alpha = 0.38f)

@Preview
@Composable
private fun PreferenceItemPreview() {
    PreferenceItem(
        title = "Title",
        description = "Description",
        icon = NextIcons.DoubleTap,
        isEnabled = true,
        showArrow = true,
    )
}
