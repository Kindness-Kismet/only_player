package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.model.DecoderPriority
import one.only.player.core.ui.R

@Composable
fun BoxScope.DecoderPrioritySelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    currentDecoderPriority: DecoderPriority,
    onDecoderPriorityClick: (DecoderPriority) -> Unit,
    isRememberForThisFileEnabled: Boolean = false,
    onRememberForThisFileChanged: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.decoder_priority),
        testTag = "panel_decoder_priority",
    ) {
        DecoderPrioritySelectorContent(
            currentDecoderPriority = currentDecoderPriority,
            onDecoderPriorityClick = onDecoderPriorityClick,
            isRememberForThisFileEnabled = isRememberForThisFileEnabled,
            onRememberForThisFileChanged = onRememberForThisFileChanged,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun DecoderPrioritySelectorContent(
    currentDecoderPriority: DecoderPriority,
    onDecoderPriorityClick: (DecoderPriority) -> Unit,
    isRememberForThisFileEnabled: Boolean = false,
    onRememberForThisFileChanged: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // 始终展示“记住该文件”开关；若外层未接线则用本地状态兜底，避免只显示文字/无反馈
    val isRememberCallbackProvided = onRememberForThisFileChanged != null
    var localRememberEnabled by remember(isRememberForThisFileEnabled) {
        mutableStateOf(isRememberForThisFileEnabled)
    }
    val rememberChecked = if (isRememberCallbackProvided) {
        isRememberForThisFileEnabled
    } else {
        localRememberEnabled
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp)
            .selectableGroup(),
    ) {
        DecoderPriority.entries.forEach { decoderPriority ->
            RadioButtonRow(
                isSelected = decoderPriority == currentDecoderPriority,
                text = decoderPriority.shortName(),
                testTag = "btn_decoder_${decoderPriority.logSuffix()}",
                onClick = {
                    onDecoderPriorityClick(decoderPriority)
                    // 始终保留面板，方便继续操作开关
                },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("row_decoder_remember_this_file")
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.remember_decoder_for_this_file),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = rememberChecked,
                onCheckedChange = { enabled ->
                    if (isRememberCallbackProvided) {
                        onRememberForThisFileChanged?.invoke(enabled)
                    } else {
                        localRememberEnabled = enabled
                    }
                },
                modifier = Modifier.testTag("switch_decoder_remember_this_file"),
            )
        }
    }
}

@Composable
private fun DecoderPriority.shortName(): String = when (this) {
    DecoderPriority.AUTOMATIC -> stringResource(R.string.auto_hw_decoder)
    DecoderPriority.AUTOMATIC_PREFER_DEVICE -> stringResource(R.string.auto_hw_plus_decoder)
    DecoderPriority.DEVICE_ONLY -> stringResource(R.string.hw_decoder)
    DecoderPriority.PREFER_DEVICE -> stringResource(R.string.hw_plus_decoder)
    DecoderPriority.PREFER_APP -> stringResource(R.string.sw_decoder)
}

private fun DecoderPriority.logSuffix(): String = when (this) {
    DecoderPriority.AUTOMATIC -> "auto_hw"
    DecoderPriority.AUTOMATIC_PREFER_DEVICE -> "auto_hw_plus"
    DecoderPriority.DEVICE_ONLY -> "hw"
    DecoderPriority.PREFER_DEVICE -> "hw_plus"
    DecoderPriority.PREFER_APP -> "sw"
}
