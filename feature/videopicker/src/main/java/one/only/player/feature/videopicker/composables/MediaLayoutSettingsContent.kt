package one.only.player.feature.videopicker.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaItemLayout
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaLayoutTarget
import one.only.player.core.model.MediaQuickSettings
import one.only.player.core.ui.R
import one.only.player.feature.videopicker.extensions.name

@Composable
internal fun MediaLayoutSettingsContent(
    settings: MediaQuickSettings,
    shouldShowFolders: Boolean,
    onChange: (MediaQuickSettings) -> Unit,
) {
    val targets = if (shouldShowFolders) MediaLayoutTarget.entries else listOf(MediaLayoutTarget.VIDEOS)
    targets.forEach { target ->
        val id = when (target) {
            MediaLayoutTarget.FOLDERS -> "folder"
            MediaLayoutTarget.VIDEOS -> "video"
        }
        val layout = when (target) {
            MediaLayoutTarget.FOLDERS -> settings.folders
            MediaLayoutTarget.VIDEOS -> settings.videos
        }
        fun update(value: MediaItemLayout) {
            onChange(
                when (target) {
                    MediaLayoutTarget.FOLDERS -> settings.copy(folders = value.normalized())
                    MediaLayoutTarget.VIDEOS -> settings.copy(videos = value.normalized())
                },
            )
        }
        QuickSettingsSection(
            title = stringResource(
                when (target) {
                    MediaLayoutTarget.FOLDERS -> R.string.folder_layout
                    MediaLayoutTarget.VIDEOS -> R.string.video_layout
                },
            ),
        ) {
            QuickSettingsTabRow(
                options = MediaLayoutMode.entries,
                selectedOption = layout.mode,
                label = MediaLayoutMode::name,
                onOptionSelected = { update(layout.copy(mode = it)) },
                modifier = Modifier.testTag("tabs_${id}_layout_mode"),
            )
            if (layout.mode == MediaLayoutMode.GRID) {
                MediaLayoutScaleControls(
                    scale = layout.scale,
                    testTagPrefix = id,
                    onResetClick = { update(layout.copy(scale = ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE)) },
                    onDecreaseClick = { update(layout.copy(scale = layout.scale - ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP)) },
                    onIncreaseClick = { update(layout.copy(scale = layout.scale + ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP)) },
                )
            }
        }
    }
}
