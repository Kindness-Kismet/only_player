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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsDialog(
    applicationPreferences: ApplicationPreferences,
    onDismiss: () -> Unit,
    updatePreferences: (ApplicationPreferences) -> Unit,
) {
    var preferences by remember { mutableStateOf(applicationPreferences) }

    NextDialog(
        modifier = Modifier.padding(PaddingValues(bottom = 0.dp).withBottomFallback()),
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.quick_settings))
        },
        content = {
            HorizontalDivider()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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
                DialogSectionTitle(text = stringResource(R.string.media_layout))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MediaLayoutMode.entries.forEachIndexed { index, layoutMode ->
                        SegmentedButton(
                            selected = preferences.mediaLayoutMode == layoutMode,
                            onClick = { preferences = preferences.copy(mediaLayoutMode = layoutMode) },
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
                if (preferences.mediaLayoutMode == MediaLayoutMode.GRID) {
                    MediaLayoutScaleControls(
                        scale = preferences.normalizedMediaLayoutScale(),
                        onResetClick = { preferences = preferences.withMediaLayoutScale(ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE) },
                        onDecreaseClick = {
                            preferences = preferences.withMediaLayoutScale(
                                preferences.mediaLayoutScale - ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP,
                            )
                        },
                        onIncreaseClick = {
                            preferences = preferences.withMediaLayoutScale(
                                preferences.mediaLayoutScale + ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP,
                            )
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                DialogSectionTitle(text = stringResource(R.string.sort))
                SortOptions(
                    selectedSortBy = preferences.sortBy,
                    onOptionSelected = { preferences = preferences.copy(sortBy = it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Sort.Order.entries.forEachIndexed { index, sortOrder ->
                        SegmentedButton(
                            selected = preferences.sortOrder == sortOrder,
                            onClick = { preferences = preferences.copy(sortOrder = sortOrder) },
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
                            Text(text = sortOrder.name(sortBy = preferences.sortBy))
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
                    FieldChip(
                        label = stringResource(id = R.string.duration),
                        isSelected = preferences.shouldShowDurationField,
                        onClick = { preferences = preferences.copy(shouldShowDurationField = !preferences.shouldShowDurationField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.extension),
                        isSelected = preferences.shouldShowExtensionField,
                        onClick = { preferences = preferences.copy(shouldShowExtensionField = !preferences.shouldShowExtensionField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.path),
                        isSelected = preferences.shouldShowPathField,
                        onClick = { preferences = preferences.copy(shouldShowPathField = !preferences.shouldShowPathField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.played_progress),
                        isSelected = preferences.shouldShowPlayedProgress,
                        onClick = { preferences = preferences.copy(shouldShowPlayedProgress = !preferences.shouldShowPlayedProgress) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.resolution),
                        isSelected = preferences.shouldShowResolutionField,
                        onClick = { preferences = preferences.copy(shouldShowResolutionField = !preferences.shouldShowResolutionField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.size),
                        isSelected = preferences.shouldShowSizeField,
                        onClick = { preferences = preferences.copy(shouldShowSizeField = !preferences.shouldShowSizeField) },
                    )
                    FieldChip(
                        label = stringResource(id = R.string.thumbnail),
                        isSelected = preferences.shouldShowThumbnailField,
                        onClick = { preferences = preferences.copy(shouldShowThumbnailField = !preferences.shouldShowThumbnailField) },
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
    onOptionSelected: (Sort.By) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        TextIconToggleButton(
            text = stringResource(id = R.string.title),
            icon = NextIcons.Title,
            isSelected = selectedSortBy == Sort.By.TITLE,
            onClick = { onOptionSelected(Sort.By.TITLE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.duration),
            icon = NextIcons.Length,
            isSelected = selectedSortBy == Sort.By.LENGTH,
            onClick = { onOptionSelected(Sort.By.LENGTH) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.date),
            icon = NextIcons.Calendar,
            isSelected = selectedSortBy == Sort.By.DATE,
            onClick = { onOptionSelected(Sort.By.DATE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.size),
            icon = NextIcons.Size,
            isSelected = selectedSortBy == Sort.By.SIZE,
            onClick = { onOptionSelected(Sort.By.SIZE) },
        )
        TextIconToggleButton(
            text = stringResource(id = R.string.location),
            icon = NextIcons.Location,
            isSelected = selectedSortBy == Sort.By.PATH,
            onClick = { onOptionSelected(Sort.By.PATH) },
        )
    }
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
