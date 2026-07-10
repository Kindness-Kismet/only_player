package one.only.player.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

val SegmentedItemGap = 0.dp
val SettingsContentTopPadding = 12.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextSegmentedListItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    selectedContainerColor: Color = MiuixTheme.colorScheme.primaryContainer,
    colors: Any? = null,
    shapes: Any? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredMaterialCompatibility = colors to shapes
    val itemInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val itemShape = preferenceSegmentShape(isFirstItem, isLastItem)
    val clickableModifier = if (isEnabled) {
        Modifier
            .clip(itemShape)
            .combinedClickable(
                interactionSource = itemInteractionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
    } else {
        Modifier
    }

    Surface(
        shape = itemShape,
        color = if (isSelected) {
            selectedContainerColor
        } else {
            containerColor
        },
        modifier = modifier.then(clickableModifier),
    ) {
        BasicComponent(
            startAction = leadingContent,
            endActions = trailingContent,
            insideMargin = contentPadding,
            enabled = isEnabled,
        ) {
            overlineContent?.invoke()
            content()
            supportingContent?.let {
                Spacer(modifier = Modifier.height(4.dp))
                it()
            }
        }
    }
}

@Composable
fun ListSectionTitle(
    modifier: Modifier = Modifier,
    text: String,
    contentPadding: PaddingValues = PaddingValues(
        start = 12.dp,
        top = 4.dp,
        bottom = 4.dp,
    ),
    color: Color = MiuixTheme.colorScheme.primary,
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        textColor = color,
        insideMargin = contentPadding,
    )
}
