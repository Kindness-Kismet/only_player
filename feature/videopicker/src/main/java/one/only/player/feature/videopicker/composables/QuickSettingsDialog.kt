package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.Sort
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.videopicker.extensions.name

enum class QuickSettingsTarget {
    LOCAL,
    CLOUD,
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsDialog(
    applicationPreferences: ApplicationPreferences,
    onDismiss: () -> Unit,
    updatePreferences: (ApplicationPreferences) -> Unit,
    target: QuickSettingsTarget = QuickSettingsTarget.LOCAL,
    cloudServerId: Long? = null,
) {
    var preferences by remember(applicationPreferences, target, cloudServerId) {
        mutableStateOf(applicationPreferences.withSupportedSort(target, cloudServerId))
    }
    val layoutMode = preferences.layoutMode(target, cloudServerId)
    val sortBy = preferences.sortBy(target, cloudServerId)
    val sortOrder = preferences.sortOrder(target, cloudServerId)

    NextDialog(
        modifier = Modifier
            .padding(PaddingValues(bottom = 0.dp).withBottomFallback())
            .testTag(target.dialogTestTag),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when (target) {
                        QuickSettingsTarget.LOCAL -> R.string.quick_settings
                        QuickSettingsTarget.CLOUD -> R.string.cloud_quick_settings
                    },
                ),
            )
        },
        content = {
            HorizontalDivider()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (target == QuickSettingsTarget.LOCAL) {
                    DialogSectionTitle(text = stringResource(R.string.media_view_mode))
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MediaViewMode.entries.forEachIndexed { index, viewMode ->
                            SegmentedButton(
                                selected = preferences.mediaViewMode == viewMode,
                                onClick = { preferences = preferences.copy(mediaViewMode = viewMode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = MediaViewMode.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                    activeBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(text = viewMode.name())
                            }
                        }
                    }
                }
                DialogSectionTitle(text = stringResource(R.string.media_layout))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MediaLayoutMode.entries.forEachIndexed { index, layoutMode ->
                        SegmentedButton(
                            selected = preferences.layoutMode(target, cloudServerId) == layoutMode,
                            onClick = { preferences = preferences.withLayoutMode(target, cloudServerId, layoutMode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MediaLayoutMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(text = layoutMode.name())
                        }
                    }
                }
                if (layoutMode == MediaLayoutMode.GRID) {
                    MediaLayoutScaleControls(
                        scale = preferences.normalizedLayoutScale(target, cloudServerId),
                        onResetClick = {
                            preferences = preferences.withLayoutScale(
                                target = target,
                                serverId = cloudServerId,
                                scale = ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE,
                            )
                        },
                        onDecreaseClick = {
                            preferences = preferences.withLayoutScale(
                                target = target,
                                serverId = cloudServerId,
                                scale = preferences.layoutScale(target, cloudServerId) - ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP,
                            )
                        },
                        onIncreaseClick = {
                            preferences = preferences.withLayoutScale(
                                target = target,
                                serverId = cloudServerId,
                                scale = preferences.layoutScale(target, cloudServerId) + ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP,
                            )
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.sort))
                SortOptions(
                    selectedSortBy = sortBy,
                    options = target.supportedSortOptions,
                    onOptionSelected = { preferences = preferences.withSortBy(target, cloudServerId, it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Sort.Order.entries.forEachIndexed { index, sortOrder ->
                        SegmentedButton(
                            selected = preferences.sortOrder(target, cloudServerId) == sortOrder,
                            onClick = { preferences = preferences.withSortOrder(target, cloudServerId, sortOrder) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = Sort.Order.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                            icon = {
                                Icon(
                                    imageVector = if (sortOrder == Sort.Order.ASCENDING) NextIcons.ArrowUpward else NextIcons.ArrowDownward,
                                    contentDescription = stringResource(R.string.ascending),
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        ) {
                            Text(text = sortOrder.name(sortBy = sortBy))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.fields))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickSettingsFields(
                        preferences = preferences,
                        target = target,
                        cloudServerId = cloudServerId,
                        onPreferencesChange = { preferences = it },
                    )
                }
            }
        },
        confirmButton = {
            DoneButton(
                onClick = {
                    updatePreferences(preferences)
                    onDismiss()
                },
            )
        },
        dismissButton = {
            CancelButton(onClick = onDismiss)
        },
    )
}

@Composable
private fun MediaLayoutScaleControls(
    scale: Float,
    onResetClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
) {
    DialogSectionTitle(text = stringResource(R.string.media_layout_scale))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .testTag("text_media_layout_scale"),
        )
        TextIconToggleButton(
            text = stringResource(R.string.media_layout_scale_reset),
            icon = NextIcons.Replay,
            modifier = Modifier.testTag("btn_media_layout_scale_reset"),
            onClick = { onResetClick() },
        )
        TextIconToggleButton(
            text = stringResource(R.string.media_layout_scale_decrease),
            icon = NextIcons.Remove,
            modifier = Modifier.testTag("btn_media_layout_scale_decrease"),
            onClick = { onDecreaseClick() },
        )
        TextIconToggleButton(
            text = stringResource(R.string.media_layout_scale_increase),
            icon = NextIcons.Add,
            modifier = Modifier.testTag("btn_media_layout_scale_increase"),
            onClick = { onIncreaseClick() },
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
                label = stringResource(id = R.string.duration),
                isSelected = preferences.shouldShowDurationField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowDurationField = !preferences.shouldShowDurationField)) },
            )
            FieldChip(
                label = stringResource(id = R.string.extension),
                isSelected = preferences.shouldShowExtensionField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowExtensionField = !preferences.shouldShowExtensionField)) },
            )
            FieldChip(
                label = stringResource(id = R.string.path),
                isSelected = preferences.shouldShowPathField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowPathField = !preferences.shouldShowPathField)) },
            )
            FieldChip(
                label = stringResource(id = R.string.played_progress),
                isSelected = preferences.shouldShowPlayedProgress,
                onClick = { onPreferencesChange(preferences.copy(shouldShowPlayedProgress = !preferences.shouldShowPlayedProgress)) },
            )
            FieldChip(
                label = stringResource(id = R.string.resolution),
                isSelected = preferences.shouldShowResolutionField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowResolutionField = !preferences.shouldShowResolutionField)) },
            )
            FieldChip(
                label = stringResource(id = R.string.size),
                isSelected = preferences.shouldShowSizeField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowSizeField = !preferences.shouldShowSizeField)) },
            )
            FieldChip(
                label = stringResource(id = R.string.thumbnail),
                isSelected = preferences.shouldShowThumbnailField,
                onClick = { onPreferencesChange(preferences.copy(shouldShowThumbnailField = !preferences.shouldShowThumbnailField)) },
            )
        }
        QuickSettingsTarget.CLOUD -> {
            val cloudSettings = preferences.cloudQuickSettings(cloudServerId)
            FieldChip(
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
                label = stringResource(id = R.string.path),
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

@Composable
fun FieldChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = NextIcons.CheckBox,
    unselectedIcon: ImageVector = NextIcons.CheckBoxOutline,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = "",
                modifier = Modifier.size(FilterChipDefaults.IconSize),
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            selectedBorderWidth = 1.dp,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )
}

@Composable
private fun SortOptions(
    selectedSortBy: Sort.By,
    options: List<Sort.By>,
    onOptionSelected: (Sort.By) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { option ->
            TextIconToggleButton(
                text = option.label(),
                icon = option.icon(),
                isSelected = selectedSortBy == option,
                onClick = { onOptionSelected(option) },
            )
        }
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

private fun ApplicationPreferences.layoutMode(
    target: QuickSettingsTarget,
    serverId: Long?,
): MediaLayoutMode = when (target) {
    QuickSettingsTarget.LOCAL -> mediaLayoutMode
    QuickSettingsTarget.CLOUD -> cloudQuickSettings(serverId).mediaLayoutMode
}

private fun ApplicationPreferences.withLayoutMode(
    target: QuickSettingsTarget,
    serverId: Long?,
    layoutMode: MediaLayoutMode,
): ApplicationPreferences = when (target) {
    QuickSettingsTarget.LOCAL -> copy(mediaLayoutMode = layoutMode)
    QuickSettingsTarget.CLOUD -> withCloudQuickSettings(
        serverId = serverId,
        settings = cloudQuickSettings(serverId).copy(mediaLayoutMode = layoutMode),
    )
}

private fun ApplicationPreferences.layoutScale(
    target: QuickSettingsTarget,
    serverId: Long?,
): Float = when (target) {
    QuickSettingsTarget.LOCAL -> mediaLayoutScale
    QuickSettingsTarget.CLOUD -> cloudQuickSettings(serverId).mediaLayoutScale
}

private fun ApplicationPreferences.normalizedLayoutScale(
    target: QuickSettingsTarget,
    serverId: Long?,
): Float = when (target) {
    QuickSettingsTarget.LOCAL -> normalizedMediaLayoutScale()
    QuickSettingsTarget.CLOUD -> cloudQuickSettings(serverId).normalizedMediaLayoutScale()
}

private fun ApplicationPreferences.withLayoutScale(
    target: QuickSettingsTarget,
    serverId: Long?,
    scale: Float,
): ApplicationPreferences = when (target) {
    QuickSettingsTarget.LOCAL -> withMediaLayoutScale(scale)
    QuickSettingsTarget.CLOUD -> withCloudQuickSettings(
        serverId = serverId,
        settings = cloudQuickSettings(serverId).withMediaLayoutScale(scale),
    )
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

private fun Sort.By.icon(): ImageVector = when (this) {
    Sort.By.TITLE -> NextIcons.Title
    Sort.By.LENGTH -> NextIcons.Length
    Sort.By.DATE -> NextIcons.Calendar
    Sort.By.SIZE -> NextIcons.Size
    Sort.By.PATH -> NextIcons.Location
}

@Composable
private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Preview
@Composable
fun QuickSettingsPreview() {
    Surface {
        QuickSettingsDialog(applicationPreferences = ApplicationPreferences(), onDismiss = { }, updatePreferences = {})
    }
}
