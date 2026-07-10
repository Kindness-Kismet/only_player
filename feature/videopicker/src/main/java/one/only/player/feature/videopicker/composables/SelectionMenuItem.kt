package one.only.player.feature.videopicker.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup

@Immutable
data class SelectionMenuAction(
    val text: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
fun SelectionActionsPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<SelectionMenuAction>>,
) {
    val entries = groups.map { group ->
        DropdownEntry(
            items = group.map { action ->
                DropdownItem(
                    text = action.text,
                    onClick = action.onClick,
                    icon = { modifier ->
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.text,
                            modifier = modifier.testTag(action.testTag),
                        )
                    },
                )
            },
        )
    }
    WindowDropdownPopup(
        entries = entries,
        show = expanded,
        onDismiss = onDismissRequest,
        onDismissFinished = {},
        maxHeight = null,
        dropdownColors = DropdownDefaults.dropdownColors(),
    )
}
