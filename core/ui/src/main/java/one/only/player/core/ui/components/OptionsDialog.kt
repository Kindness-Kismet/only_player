package one.only.player.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun OptionsDialog(
    title: String,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: LazyListScope.() -> Unit,
) {
    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismissClick,
        title = title,
        content = {
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = AppDialogDefaults.contentMaxHeight)
                        .selectableGroup(),
                    content = options,
                )
            }
        },
        confirmButton = { DoneButton(onClick = onDismissClick) },
    )
}
