package one.only.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.model.VideoContentScale
import one.only.player.core.ui.R
import one.only.player.feature.player.extensions.nameRes

@Composable
fun BoxScope.VideoContentScaleSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    videoContentScale: VideoContentScale,
    isCustomZoomActive: Boolean = false,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onShowVideoFilters: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.video_zoom),
    ) {
        VideoContentScaleSelectorContent(
            videoContentScale = videoContentScale,
            isCustomZoomActive = isCustomZoomActive,
            onVideoContentScaleChanged = onVideoContentScaleChanged,
            onShowVideoFilters = onShowVideoFilters,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun VideoContentScaleSelectorContent(
    videoContentScale: VideoContentScale,
    isCustomZoomActive: Boolean = false,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onShowVideoFilters: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
            .padding(horizontal = 24.dp),
    ) {
        if (onShowVideoFilters != null) {
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_video_filters"),
                onClick = onShowVideoFilters,
            ) {
                Text(text = stringResource(R.string.video_filters))
            }
            Spacer(modifier = Modifier.size(16.dp))
        }

        Column(modifier = Modifier.selectableGroup()) {
            VideoContentScale.entries.forEach { contentScale ->
                RadioButtonRow(
                    isSelected = !isCustomZoomActive && contentScale == videoContentScale,
                    text = stringResource(contentScale.nameRes()),
                    testTag = "btn_video_scale_${contentScale.name.lowercase()}",
                    onClick = {
                        onVideoContentScaleChanged(contentScale)
                        onDismiss()
                    },
                )
            }
        }
    }
}
