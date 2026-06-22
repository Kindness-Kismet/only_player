package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R

@Composable
fun BoxScope.ToggleOptionSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    titleRes: Int,
    panelTestTag: String,
    isEnabled: Boolean,
    offTestTag: String,
    onTestTag: String,
    onEnabledChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(titleRes),
        testTag = panelTestTag,
    ) {
        ToggleOptionSelectorContent(
            panelTestTag = panelTestTag,
            isEnabled = isEnabled,
            offTestTag = offTestTag,
            onTestTag = onTestTag,
            onEnabledChanged = onEnabledChanged,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun ToggleOptionSelectorContent(
    panelTestTag: String,
    isEnabled: Boolean,
    offTestTag: String,
    onTestTag: String,
    onEnabledChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag(panelTestTag)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
            .selectableGroup(),
    ) {
        toggleOptions(
            offTestTag = offTestTag,
            onTestTag = onTestTag,
        ).forEach { option ->
            RadioButtonRow(
                isSelected = isEnabled == option.isEnabled,
                text = stringResource(option.labelRes),
                testTag = option.testTag,
                onClick = {
                    onEnabledChanged(option.isEnabled)
                    onDismiss()
                },
            )
        }
    }
}

private data class ToggleOption(
    val isEnabled: Boolean,
    val labelRes: Int,
    val testTag: String,
)

private fun toggleOptions(
    offTestTag: String,
    onTestTag: String,
): List<ToggleOption> = listOf(
    ToggleOption(
        isEnabled = false,
        labelRes = R.string.off,
        testTag = offTestTag,
    ),
    ToggleOption(
        isEnabled = true,
        labelRes = R.string.on,
        testTag = onTestTag,
    ),
)
