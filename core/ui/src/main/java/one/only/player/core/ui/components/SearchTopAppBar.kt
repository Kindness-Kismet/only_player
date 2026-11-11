package one.only.player.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.navigation.MiuixNavigationGestureBlocker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SearchBarDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchTopAppBar(
    query: String,
    placeholder: String,
    searchFieldTestTag: String,
    clearButtonTestTag: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    closeButtonTestTag: String = "${searchFieldTestTag}_close",
    onSearch: () -> Unit = {},
    isActive: Boolean = true,
) {
    var canFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val topBarBackdrop = LocalTopBarBackdrop.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val canInteract = isActive && lifecycleState == Lifecycle.State.RESUMED
    val isKeyboardVisible = WindowInsets.isImeVisible
    val close = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onClose()
    }

    LaunchedEffect(canInteract) {
        if (canInteract) delay(300)
        canFocus = canInteract
    }
    MiuixNavigationGestureBlocker(isBlocked = canInteract)

    MiuixInteractions {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .surfaceBlur(topBarBackdrop)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(end = 16.dp),
            expanded = canInteract,
            onExpandedChange = { isExpanded ->
                if (!isExpanded && canInteract) {
                    if (isKeyboardVisible) keyboardController?.hide() else close()
                }
            },
            inputField = {
                InputField(
                    query = query,
                    onQueryChange = { if (canInteract) onQueryChange(it) },
                    onSearch = {
                        if (canInteract) {
                            onSearch()
                            keyboardController?.hide()
                        }
                    },
                    expanded = canFocus && canInteract,
                    onExpandedChange = {},
                    enabled = canInteract,
                    label = "",
                    modifier = Modifier
                        .testTag(searchFieldTestTag)
                        .semantics { contentDescription = placeholder },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Search,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = SearchBarDefaults.LeadingIconStartPadding, end = SearchBarDefaults.LeadingIconEndPadding)
                                .size(24.dp),
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(visible = query.isNotEmpty()) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = stringResource(R.string.clear_search),
                                modifier = Modifier
                                    .padding(start = SearchBarDefaults.TrailingIconStartPadding, end = SearchBarDefaults.TrailingIconEndPadding)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = canInteract, role = Role.Button) { onQueryChange("") }
                                    .testTag(clearButtonTestTag),
                            )
                        }
                    },
                )
            },
            outsideEndAction = {
                IconButton(
                    onClick = close,
                    enabled = canInteract,
                    modifier = Modifier.size(40.dp).testTag(closeButtonTestTag),
                ) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = stringResource(R.string.close_search),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        ) {}
    }
}
