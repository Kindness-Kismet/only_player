package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaDisplayFields
import one.only.player.core.model.MediaQuickSettings
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.Sort
import one.only.player.core.model.StoragePath
import one.only.player.core.model.hasInheritedQuickSettings
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withQuickSettings
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppDialogDefaults
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.feature.videopicker.extensions.name
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class QuickSettingsTarget {
    LOCAL,
    CLOUD,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSettingsDialog(
    applicationPreferences: ApplicationPreferences,
    onDismiss: () -> Unit,
    updatePreferences: ((ApplicationPreferences) -> ApplicationPreferences) -> Unit,
    target: QuickSettingsTarget = QuickSettingsTarget.LOCAL,
    cloudServerId: Long? = null,
    directoryPath: String? = null,
    isRoot: Boolean = false,
) {
    val originalPreferences = remember(target, cloudServerId, directoryPath) { applicationPreferences }
    var preferences by remember(target, cloudServerId, directoryPath) {
        mutableStateOf(applicationPreferences)
    }
    val settingsDirectoryPath = directoryPath.takeUnless {
        target == QuickSettingsTarget.LOCAL && isRoot && preferences.mediaViewMode != MediaViewMode.FOLDER_TREE
    }
    val canConfigureDirectory = settingsDirectoryPath != null && !isRoot
    val overrides = when (target) {
        QuickSettingsTarget.LOCAL -> preferences.directoryQuickSettings[settingsDirectoryPath?.let(StoragePath::of)]
        QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(cloudServerId).directoryQuickSettings[settingsDirectoryPath]
    }
    val hasIndependentSettings = overrides?.isEmpty == false
    val editorDirectoryPath = settingsDirectoryPath.takeIf { isRoot || hasIndependentSettings }
    val hasInheritedSettings = if (canConfigureDirectory) {
        when (target) {
            QuickSettingsTarget.LOCAL -> preferences.hasInheritedQuickSettings(StoragePath.of(requireNotNull(settingsDirectoryPath)))
            QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(cloudServerId).hasInheritedQuickSettings(requireNotNull(settingsDirectoryPath))
        }
    } else {
        false
    }
    val settings = when (target) {
        QuickSettingsTarget.LOCAL -> preferences.resolveQuickSettings(editorDirectoryPath?.let(StoragePath::of))
        QuickSettingsTarget.CLOUD -> preferences.cloudQuickSettings(cloudServerId).resolveQuickSettings(editorDirectoryPath)
    }
    fun updateSettings(updated: MediaQuickSettings) {
        preferences = when (target) {
            QuickSettingsTarget.LOCAL -> preferences.withQuickSettings(editorDirectoryPath?.let(StoragePath::of), updated)
            QuickSettingsTarget.CLOUD -> preferences.withCloudQuickSettings(
                cloudServerId,
                preferences.cloudQuickSettings(cloudServerId).withQuickSettings(editorDirectoryPath, updated),
            )
        }
    }
    val sortBy = settings.sort.by
    val sortOrder = settings.sort.order
    AppDialog(
        modifier = Modifier.testTag(target.dialogTestTag),
        onDismissRequest = onDismiss,
        title = stringResource(
            when (target) {
                QuickSettingsTarget.LOCAL -> R.string.quick_settings
                QuickSettingsTarget.CLOUD -> R.string.cloud_quick_settings
            },
        ),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDialogDefaults.contentMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                if (target == QuickSettingsTarget.LOCAL && isRoot) {
                    QuickSettingsSection(title = stringResource(R.string.media_view_mode)) {
                        QuickSettingsTabRow(
                            options = MediaViewMode.entries,
                            selectedOption = preferences.mediaViewMode,
                            label = MediaViewMode::name,
                            onOptionSelected = { preferences = preferences.copy(mediaViewMode = it) },
                            modifier = Modifier.testTag("tabs_${target.dialogTestTag}_view_mode"),
                        )
                    }
                }
                if (canConfigureDirectory) {
                    PreferenceSwitch(
                        title = stringResource(R.string.layout_independent_config),
                        isChecked = hasIndependentSettings,
                        onClick = {
                            preferences = preferences.withIndependentQuickSettings(
                                target = target,
                                serverId = cloudServerId,
                                directoryPath = requireNotNull(settingsDirectoryPath),
                                isEnabled = !hasIndependentSettings,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("switch_independent_config"),
                    )
                    if (!hasIndependentSettings) {
                        Text(
                            text = stringResource(R.string.quick_settings_edit_global),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.testTag("text_quick_settings_scope"),
                        )
                        if (hasInheritedSettings) {
                            Text(
                                text = stringResource(R.string.quick_settings_parent_override),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.testTag("text_quick_settings_parent_override"),
                            )
                        }
                    }
                }
                MediaLayoutSettingsContent(
                    settings = settings,
                    shouldShowFolders = target == QuickSettingsTarget.CLOUD || preferences.mediaViewMode != MediaViewMode.VIDEOS,
                    onChange = ::updateSettings,
                )
                QuickSettingsSection(title = stringResource(R.string.sort)) {
                    QuickSettingsTabRow(
                        options = target.supportedSortOptions,
                        selectedOption = sortBy,
                        label = { it.label() },
                        onOptionSelected = { updateSettings(settings.copy(sort = settings.sort.copy(by = it))) },
                        modifier = Modifier.testTag("tabs_${target.dialogTestTag}_sort_by"),
                    )
                    QuickSettingsTabRow(
                        options = Sort.Order.entries,
                        selectedOption = sortOrder,
                        label = { it.name(sortBy = sortBy) },
                        onOptionSelected = { updateSettings(settings.copy(sort = settings.sort.copy(order = it))) },
                        modifier = Modifier.testTag("tabs_${target.dialogTestTag}_sort_order"),
                    )
                }
                QuickSettingsSection(title = stringResource(R.string.fields)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickSettingsFields(
                            fields = settings.fields,
                            target = target,
                            onChange = { updateSettings(settings.copy(fields = it)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DoneButton(
                onClick = {
                    updatePreferences { current ->
                        current.applyQuickSettingsChanges(originalPreferences, preferences, target, cloudServerId)
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_${target.dialogTestTag}_done"),
            )
        },
        dismissButton = {
            CancelButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_${target.dialogTestTag}_cancel"),
            )
        },
    )
}

// 标题在控件上方，控件直接铺在对话框背景上，与 miuix 原生对话框风格一致。
@Composable
internal fun QuickSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

// 单选项使用 miuix 分段控件，选项少时自动铺满整行，多时可横向滚动。
@Composable
internal fun <T> QuickSettingsTabRow(
    options: List<T>,
    selectedOption: T,
    label: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRowWithContour(
        tabs = options.map { option -> label(option) },
        selectedTabIndex = options.indexOf(selectedOption).coerceAtLeast(0),
        onTabSelected = { index -> onOptionSelected(options[index]) },
        modifier = modifier,
    )
}

@Composable
internal fun MediaLayoutScaleControls(
    scale: Float,
    testTagPrefix: String,
    onResetClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.media_layout_scale),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.testTag("text_${testTagPrefix}_layout_scale"),
        )
        ScaleIconButton(
            icon = AppIcons.Remove,
            contentDescription = stringResource(R.string.media_layout_scale_decrease),
            testTag = "btn_${testTagPrefix}_layout_scale_decrease",
            onClick = onDecreaseClick,
        )
        ScaleIconButton(
            icon = AppIcons.Add,
            contentDescription = stringResource(R.string.media_layout_scale_increase),
            testTag = "btn_${testTagPrefix}_layout_scale_increase",
            onClick = onIncreaseClick,
        )
        ScaleIconButton(
            icon = AppIcons.Replay,
            contentDescription = stringResource(R.string.media_layout_scale_reset),
            testTag = "btn_${testTagPrefix}_layout_scale_reset",
            onClick = onResetClick,
        )
    }
}

@Composable
private fun ScaleIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MiuixTheme.colorScheme.secondaryContainer,
        modifier = Modifier.testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(7.dp)
                .size(18.dp),
        )
    }
}

@Composable
private fun QuickSettingsFields(
    fields: MediaDisplayFields,
    target: QuickSettingsTarget,
    onChange: (MediaDisplayFields) -> Unit,
) {
    val prefix = if (target == QuickSettingsTarget.CLOUD) "cloud_" else ""
    if (target == QuickSettingsTarget.LOCAL) {
        FieldChip(
            key = "${prefix}duration",
            label = stringResource(R.string.video_duration),
            isSelected = fields.shouldShowDurationField,
            onClick = { onChange(fields.copy(shouldShowDurationField = !fields.shouldShowDurationField)) },
        )
    }
    FieldChip(
        key = "${prefix}extension",
        label = stringResource(R.string.extension),
        isSelected = fields.shouldShowExtensionField,
        onClick = { onChange(fields.copy(shouldShowExtensionField = !fields.shouldShowExtensionField)) },
    )
    FieldChip(
        key = "${prefix}path",
        label = stringResource(R.string.folder_path),
        isSelected = fields.shouldShowPathField,
        onClick = { onChange(fields.copy(shouldShowPathField = !fields.shouldShowPathField)) },
    )
    FieldChip(
        key = "${prefix}played_progress",
        label = stringResource(R.string.played_progress),
        isSelected = fields.shouldShowPlayedProgress,
        onClick = { onChange(fields.copy(shouldShowPlayedProgress = !fields.shouldShowPlayedProgress)) },
    )
    if (target == QuickSettingsTarget.LOCAL) {
        FieldChip(
            key = "${prefix}resolution",
            label = stringResource(R.string.resolution),
            isSelected = fields.shouldShowResolutionField,
            onClick = { onChange(fields.copy(shouldShowResolutionField = !fields.shouldShowResolutionField)) },
        )
    }
    FieldChip(
        key = "${prefix}size",
        label = stringResource(R.string.size),
        isSelected = fields.shouldShowSizeField,
        onClick = { onChange(fields.copy(shouldShowSizeField = !fields.shouldShowSizeField)) },
    )
    FieldChip(
        key = "${prefix}thumbnail",
        label = stringResource(R.string.thumbnail),
        isSelected = fields.shouldShowThumbnailField,
        onClick = { onChange(fields.copy(shouldShowThumbnailField = !fields.shouldShowThumbnailField)) },
    )
}

// 多选字段用胶囊 Chip，选中态填充主题色，与 miuix 无边框风格一致。
@Composable
fun FieldChip(
    key: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
        modifier = modifier.testTag("chip_quick_settings_field_$key"),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

private val QuickSettingsTarget.dialogTestTag: String
    get() = when (this) {
        QuickSettingsTarget.LOCAL -> "dialog_quick_settings"
        QuickSettingsTarget.CLOUD -> "dialog_cloud_quick_settings"
    }

private val QuickSettingsTarget.supportedSortOptions: List<Sort.By>
    get() = when (this) {
        QuickSettingsTarget.LOCAL -> Sort.By.entries
        QuickSettingsTarget.CLOUD -> listOf(Sort.By.TITLE, Sort.By.SIZE, Sort.By.PATH)
    }

@Composable
private fun Sort.By.label(): String = when (this) {
    Sort.By.TITLE -> stringResource(id = R.string.title)
    Sort.By.LENGTH -> stringResource(id = R.string.duration)
    Sort.By.DATE -> stringResource(id = R.string.date)
    Sort.By.SIZE -> stringResource(id = R.string.size)
    Sort.By.PATH -> stringResource(id = R.string.location)
}

@Preview
@Composable
fun QuickSettingsPreview() {
    Surface {
        QuickSettingsDialog(applicationPreferences = ApplicationPreferences(), onDismiss = { }, updatePreferences = {})
    }
}

private val SectionSpacing = 14.dp
