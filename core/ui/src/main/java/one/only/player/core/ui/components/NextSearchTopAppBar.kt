package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField

@Composable
fun NextSearchTopAppBar(
    query: String,
    placeholder: String,
    searchFieldTestTag: String,
    clearButtonTestTag: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    closeButtonTestTag: String = "${searchFieldTestTag}_close",
    onSearch: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column {
        SmallTopAppBar(
            title = "",
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag(closeButtonTestTag),
                ) {
                    Icon(
                        imageVector = NextIcons.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            },
            actions = {
                if (query.isNotEmpty()) {
                    IconButton(
                        modifier = Modifier.testTag(clearButtonTestTag),
                        onClick = { onQueryChange("") },
                    ) {
                        Icon(
                            imageVector = NextIcons.Close,
                            contentDescription = stringResource(R.string.clear_history),
                        )
                    }
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                label = placeholder,
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch()
                        keyboardController?.hide()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(searchFieldTestTag),
            )
        }
    }
}
