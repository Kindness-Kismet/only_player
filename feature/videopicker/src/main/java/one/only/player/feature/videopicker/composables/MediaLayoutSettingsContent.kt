package one.only.player.feature.videopicker.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaItemLayout
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaLayoutTarget
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.StoragePath
import one.only.player.core.model.resolveMediaLayouts
import one.only.player.core.model.withMediaLayout
import one.only.player.core.ui.R
import one.only.player.feature.videopicker.extensions.name
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MediaLayoutSettingsContent(
    preferences: ApplicationPreferences,
    target: QuickSettingsTarget,
    serverId: Long?,
    directoryPath: String?,
    onChange: (ApplicationPreferences) -> Unit,
) {
    val directory = directoryPath?.let(StoragePath::of)
    val resolved = when (target) {
        QuickSettingsTarget.LOCAL -> preferences.resolveMediaLayouts(directory)
        QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(serverId).resolveMediaLayouts(directoryPath)
    }
    val targets = if (target == QuickSettingsTarget.LOCAL && preferences.mediaViewMode == MediaViewMode.VIDEOS) {
        listOf(MediaLayoutTarget.VIDEOS)
    } else {
        MediaLayoutTarget.entries
    }
    fun update(layoutTarget: MediaLayoutTarget, layout: MediaItemLayout?) {
        onChange(
            when (target) {
                QuickSettingsTarget.LOCAL -> preferences.withMediaLayout(directory, layoutTarget, layout)
                QuickSettingsTarget.CLOUD -> preferences.withCloudQuickSettings(
                    serverId,
                    preferences.cloudQuickSettings(serverId).withMediaLayout(directoryPath, layoutTarget, layout),
                )
            },
        )
    }
    targets.forEach { layoutTarget ->
        val id = when (layoutTarget) {
            MediaLayoutTarget.FOLDERS -> "folder"
            MediaLayoutTarget.VIDEOS -> "video"
        }
        val value = resolved[layoutTarget]
        val layout = value.layout
        val isOverridden = when (layoutTarget) {
            MediaLayoutTarget.FOLDERS -> when (target) {
                QuickSettingsTarget.LOCAL -> preferences.directoryLayouts[directory]?.folders != null
                QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(serverId).directoryLayouts[directoryPath]?.folders != null
            }
            MediaLayoutTarget.VIDEOS -> when (target) {
                QuickSettingsTarget.LOCAL -> preferences.directoryLayouts[directory]?.videos != null
                QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(serverId).directoryLayouts[directoryPath]?.videos != null
            }
        }
        QuickSettingsSection(
            title = stringResource(
                when (layoutTarget) {
                    MediaLayoutTarget.FOLDERS -> R.string.folder_layout
                    MediaLayoutTarget.VIDEOS -> R.string.video_layout
                },
            ),
        ) {
            if (directoryPath != null) {
                Text(
                    text = if (isOverridden) {
                        stringResource(R.string.layout_directory_custom)
                    } else {
                        stringResource(R.string.layout_inherited_from, value.sourcePath ?: stringResource(R.string.layout_library_default))
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.testTag("text_${id}_layout_source"),
                )
            }
            QuickSettingsTabRow(
                options = MediaLayoutMode.entries,
                selectedOption = layout.mode,
                label = MediaLayoutMode::name,
                onOptionSelected = { update(layoutTarget, layout.copy(mode = it)) },
                modifier = Modifier.testTag("tabs_${id}_layout_mode"),
            )
            if (layout.mode == MediaLayoutMode.GRID) {
                MediaLayoutScaleControls(
                    scale = layout.scale,
                    testTagPrefix = id,
                    onResetClick = { update(layoutTarget, layout.copy(scale = ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE)) },
                    onDecreaseClick = { update(layoutTarget, layout.copy(scale = layout.scale - ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP)) },
                    onIncreaseClick = { update(layoutTarget, layout.copy(scale = layout.scale + ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP)) },
                )
            }
            if (isOverridden) {
                TextButton(
                    text = stringResource(R.string.layout_use_parent),
                    onClick = { update(layoutTarget, null) },
                    modifier = Modifier.testTag("btn_${id}_layout_inherit"),
                )
            }
        }
    }
}
