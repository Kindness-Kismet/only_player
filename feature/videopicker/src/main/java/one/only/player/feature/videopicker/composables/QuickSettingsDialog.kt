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
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.Sort
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppDialogDefaults
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
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
    isLibraryRoot: Boolean = false,
) {
    val originalPreferences = remember(target, cloudServerId, directoryPath) { applicationPreferences }
    var isDirectoryScope by remember(target, cloudServerId, directoryPath) { mutableStateOf(directoryPath != null) }
    var preferences by remember(target, cloudServerId, directoryPath) {
        mutableStateOf(applicationPreferences.withSupportedSort(target, cloudServerId))
    }
    val canConfigureDirectory = directoryPath != null &&
        !(target == QuickSettingsTarget.LOCAL && isLibraryRoot && preferences.mediaViewMode != MediaViewMode.FOLDER_TREE)
    val sortBy = preferences.sortBy(target, cloudServerId)
    val sortOrder = preferences.sortOrder(target, cloudServerId)
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
                if (target == QuickSettingsTarget.LOCAL) {
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
                    QuickSettingsSection(title = stringResource(R.string.layout_scope)) {
                        QuickSettingsTabRow(
                            options = listOf(false, true),
                            selectedOption = isDirectoryScope,
                            label = { stringResource(if (it) R.string.layout_directory_scope else R.string.layout_library_default) },
                            onOptionSelected = { isDirectoryScope = it },
                            modifier = Modifier.testTag("tabs_layout_scope"),
                        )
                        if (isDirectoryScope) {
                            Text(
                                text = requireNotNull(directoryPath),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.testTag("text_layout_directory"),
                            )
                            Text(
                                text = stringResource(R.string.layout_recursive_description),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
                MediaLayoutSettingsContent(
                    preferences = preferences,
                    target = target,
                    serverId = cloudServerId,
                    directoryPath = directoryPath.takeIf { canConfigureDirectory && isDirectoryScope },
                    onChange = { preferences = it },
                )
                QuickSettingsSection(title = stringResource(R.string.sort)) {
                    QuickSettingsTabRow(
                        options = target.supportedSortOptions,
                        selectedOption = sortBy,
                        label = { it.label() },
                        onOptionSelected = { preferences = preferences.withSortBy(target, cloudServerId, it) },
                        modifier = Modifier.testTag("tabs_${target.dialogTestTag}_sort_by"),
                    )
                    QuickSettingsTabRow(
                        options = Sort.Order.entries,
                        selectedOption = sortOrder,
                        label = { it.name(sortBy = sortBy) },
                        onOptionSelected = { preferences = preferences.withSortOrder(target, cloudServerId, it) },
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
                            preferences = preferences,
                            target = target,
                            cloudServerId = cloudServerId,
                            onPreferencesChange = { preferences = it },
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
    preferences: ApplicationPreferences,
    target: QuickSettingsTarget,
    cloudServerId: Long?,
    onPreferencesChange: (ApplicationPreferences) -> Unit,
) {
    when (target) {
        QuickSettingsTarget.LOCAL -> {
            FieldChip(
                key = "duration",
                label = stringResource(id = R.string.video_duration),
                isSelected = preferences.shouldShowDurationField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowDurationField = !preferences.shouldShowDurationField)) },
            )
            FieldChip(
                key = "extension",
                label = stringResource(id = R.string.extension),
                isSelected = preferences.shouldShowExtensionField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowExtensionField = !preferences.shouldShowExtensionField)) },
            )
            FieldChip(
                key = "path",
                label = stringResource(id = R.string.folder_path),
                isSelected = preferences.shouldShowPathField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowPathField = !preferences.shouldShowPathField)) },
            )
            FieldChip(
                key = "played_progress",
                label = stringResource(id = R.string.played_progress),
                isSelected = preferences.shouldShowPlayedProgress,
                onClick = { onPreferencesChange(preferences.copy(shouldShowPlayedProgress = !preferences.shouldShowPlayedProgress)) },
            )
            FieldChip(
                key = "resolution",
                label = stringResource(id = R.string.resolution),
                isSelected = preferences.shouldShowResolutionField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowResolutionField = !preferences.shouldShowResolutionField)) },
            )
            FieldChip(
                key = "size",
                label = stringResource(id = R.string.size),
                isSelected = preferences.shouldShowSizeField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowSizeField = !preferences.shouldShowSizeField)) },
            )
            FieldChip(
                key = "thumbnail",
                label = stringResource(id = R.string.thumbnail),
                isSelected = preferences.shouldShowThumbnailField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowThumbnailField = !preferences.shouldShowThumbnailField)) },
            )
        }
        QuickSettingsTarget.CLOUD -> {
            val cloudSettings = preferences.cloudQuickSettings(cloudServerId)
            FieldChip(
                key = "cloud_extension",
                label = stringResource(id = R.string.extension),
                isSelected = cloudSettings.shouldShowExtensionField,
                onClick = {
                    onPreferencesChange(
                        preferences.withCloudQuickSettings(
                            serverId = cloudServerId,
                            settings = cloudSettings.copy(shouldShowExtensionField = !cloudSettings.shouldShowExtensionField),
                        ),
                    )
                },
            )
            FieldChip(
                key = "cloud_path",
                label = stringResource(id = R.string.folder_path),
                isSelected = cloudSettings.shouldShowPathField,
                onClick = {
                    onPreferencesChange(
                        preferences.withCloudQuickSettings(
                            serverId = cloudServerId,
                            settings = cloudSettings.copy(shouldShowPathField = !cloudSettings.shouldShowPathField),
                        ),
                    )
                },
            )
            FieldChip(
                key = "cloud_played_progress",
                label = stringResource(id = R.string.played_progress),
                isSelected = cloudSettings.shouldShowPlayedProgress,
                onClick = {
                    onPreferencesChange(
                        preferences.withCloudQuickSettings(
                            serverId = cloudServerId,
                            settings = cloudSettings.copy(shouldShowPlayedProgress = !cloudSettings.shouldShowPlayedProgress),
                        ),
                    )
                },
            )
            FieldChip(
                key = "cloud_size",
                label = stringResource(id = R.string.size),
                isSelected = cloudSettings.shouldShowSizeField,
                onClick = {
                    onPreferencesChange(
                        preferences.withCloudQuickSettings(
                            serverId = cloudServerId,
                            settings = cloudSettings.copy(shouldShowSizeField = !cloudSettings.shouldShowSizeField),
                        ),
                    )
                },
            )
            FieldChip(
                key = "cloud_thumbnail",
                label = stringResource(id = R.string.thumbnail),
                isSelected = cloudSettings.shouldShowThumbnailField,
                onClick = {
                    onPreferencesChange(
                        preferences.withCloudQuickSettings(
                            serverId = cloudServerId,
                            settings = cloudSettings.copy(shouldShowThumbnailField = !cloudSettings.shouldShowThumbnailField),
                        ),
                    )
                },
            )
        }
    }
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

private fun ApplicationPreferences.withSupportedSort(
    target: QuickSettingsTarget,
    serverId: Long?,
): ApplicationPreferences {
    if (sortBy(target, serverId) in target.supportedSortOptions) return this
    return withSortBy(target, serverId, Sort.By.TITLE)
}

private fun ApplicationPreferences.sortBy(
    target: QuickSettingsTarget,
    serverId: Long?,
): Sort.By = when (target) {
    QuickSettingsTarget.LOCAL -> sortBy
    QuickSettingsTarget.CLOUD -> cloudQuickSettings(serverId).sortBy.takeIf { it in target.supportedSortOptions } ?: Sort.By.TITLE
}

private fun ApplicationPreferences.withSortBy(
    target: QuickSettingsTarget,
    serverId: Long?,
    sortBy: Sort.By,
): ApplicationPreferences = when (target) {
    QuickSettingsTarget.LOCAL -> copy(sortBy = sortBy)
    QuickSettingsTarget.CLOUD -> withCloudQuickSettings(
        serverId = serverId,
        settings = cloudQuickSettings(serverId).copy(sortBy = sortBy.takeIf { it in target.supportedSortOptions } ?: Sort.By.TITLE),
    )
}

private fun ApplicationPreferences.sortOrder(
    target: QuickSettingsTarget,
    serverId: Long?,
): Sort.Order = when (target) {
    QuickSettingsTarget.LOCAL -> sortOrder
    QuickSettingsTarget.CLOUD -> cloudQuickSettings(serverId).sortOrder
}

private fun ApplicationPreferences.withSortOrder(
    target: QuickSettingsTarget,
    serverId: Long?,
    sortOrder: Sort.Order,
): ApplicationPreferences = when (target) {
    QuickSettingsTarget.LOCAL -> copy(sortOrder = sortOrder)
    QuickSettingsTarget.CLOUD -> withCloudQuickSettings(
        serverId = serverId,
        settings = cloudQuickSettings(serverId).copy(sortOrder = sortOrder),
    )
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
