package one.only.player.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OptionsDialog(
    title: String,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: LazyListScope.() -> Unit,
) {
    NextDialog(
        modifier = modifier,
        onDismissRequest = onDismissClick,
        title = title,
        content = {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.selectableGroup(),
                content = options,
            )
        },
        dismissButton = { CancelButton(onClick = onDismissClick) },
        confirmButton = { },
    )
}
