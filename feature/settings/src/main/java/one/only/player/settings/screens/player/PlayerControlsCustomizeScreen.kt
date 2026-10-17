package one.only.player.settings.screens.player

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlSlot
import one.only.player.core.model.controlsIn
import one.only.player.core.model.slotOf
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.icon
import one.only.player.core.ui.extensions.id
import one.only.player.core.ui.extensions.label
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.composables.OptionsDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DISABLED_ICON_ALPHA = 0.3f

private sealed interface CustomizeDialog {
    data class SlotPicker(val control: PlayerControl) : CustomizeDialog

    data object PreviewPicker : CustomizeDialog

    data object ResetConfirm : CustomizeDialog
}

@Composable
fun PlayerControlsCustomizeScreen(
    onNavigateUp: () -> Unit,
    viewModel: PlayerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewLayout by rememberSaveable { mutableStateOf(ControlsPreviewLayout.PORTRAIT) }
    var activeDialog by remember { mutableStateOf<CustomizeDialog?>(null) }
    val arrangement = uiState.preferences.controlsArrangement

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.customize_player_controls),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_customize_controls_back"),
                    ) {
                        MiuixIcon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp)
                .testTag("panel_customize_controls"),
            verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
        ) {
            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_customize_preview_layout"),
                    title = stringResource(R.string.customize_controls_preview_layout),
                    description = previewLayout.label(),
                    icon = AppIcons.Player,
                    onClick = { activeDialog = CustomizeDialog.PreviewPicker },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_customize_reset"),
                    title = stringResource(R.string.customize_controls_reset),
                    description = stringResource(R.string.customize_controls_reset_description),
                    icon = AppIcons.History,
                    onClick = { activeDialog = CustomizeDialog.ResetConfirm },
                )
            }
            PlayerControlsPreviewCard(
                layout = previewLayout,
                topRightControls = arrangement.controlsIn(PlayerControlSlot.TOP_RIGHT),
                bottomRightControls = arrangement.controlsIn(PlayerControlSlot.BOTTOM_RIGHT),
                menuControls = arrangement.controlsIn(PlayerControlSlot.MENU),
            )
            PlayerControlSlot.entries.forEach { slot ->
                ArrangementSection(
                    slot = slot,
                    controls = arrangement.controlsIn(slot),
                    onSlotClick = { activeDialog = CustomizeDialog.SlotPicker(it) },
                    onShift = { control, offset ->
                        viewModel.onEvent(PlayerPreferencesUiEvent.ShiftControl(control, offset))
                    },
                )
            }
        }
    }

    when (val dialog = activeDialog) {
        null -> Unit

        is CustomizeDialog.SlotPicker -> {
            val control = dialog.control
            val selectedSlot = arrangement.slotOf(control)
            OptionsDialog(
                text = control.label(),
                onDismissClick = { activeDialog = null },
            ) {
                items(PlayerControlSlot.entries, key = { it.name }) { slot ->
                    RadioTextButton(
                        modifier = Modifier.testTag(
                            "option_settings_customize_slot_${control.id}_${slot.name.lowercase()}",
                        ),
                        text = slot.label(),
                        isSelected = slot == selectedSlot,
                        onClick = {
                            viewModel.onEvent(PlayerPreferencesUiEvent.MoveControl(control, slot))
                            activeDialog = null
                        },
                    )
                }
            }
        }

        CustomizeDialog.PreviewPicker -> OptionsDialog(
            text = stringResource(R.string.customize_controls_preview_layout),
            onDismissClick = { activeDialog = null },
        ) {
            items(ControlsPreviewLayout.entries, key = { it.name }) { layout ->
                RadioTextButton(
                    modifier = Modifier.testTag("option_settings_customize_preview_${layout.name.lowercase()}"),
                    text = layout.label(),
                    isSelected = layout == previewLayout,
                    onClick = {
                        previewLayout = layout
                        activeDialog = null
                    },
                )
            }
        }

        CustomizeDialog.ResetConfirm -> AppDialog(
            onDismissRequest = { activeDialog = null },
            title = stringResource(R.string.customize_controls_reset),
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.reset),
                    modifier = Modifier.testTag("btn_confirm_settings_customize_reset"),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        viewModel.onEvent(PlayerPreferencesUiEvent.ResetControlsArrangement)
                        activeDialog = null
                    },
                )
            },
            dismissButton = { CancelButton(onClick = { activeDialog = null }) },
            content = {
                MiuixText(
                    text = stringResource(R.string.customize_controls_reset_confirmation),
                    style = MiuixTheme.textStyles.body1,
                )
            },
        )
    }
}

@Composable
private fun ArrangementSection(
    slot: PlayerControlSlot,
    controls: List<PlayerControl>,
    onSlotClick: (PlayerControl) -> Unit,
    onShift: (PlayerControl, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MiuixText(
            text = slot.sectionTitle(controls.size),
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .padding(start = 4.dp)
                .testTag("text_settings_customize_section_${slot.name.lowercase()}"),
        )
        PreferenceGroup(
            modifier = Modifier.testTag("group_settings_customize_${slot.name.lowercase()}"),
        ) {
            if (controls.isEmpty()) {
                PreferenceItem(
                    modifier = Modifier.testTag("item_settings_customize_${slot.name.lowercase()}_empty"),
                    title = stringResource(R.string.customize_controls_empty),
                    isEnabled = false,
                )
            }
            controls.forEachIndexed { index, control ->
                PreferenceItem(
                    modifier = Modifier.testTag("item_settings_customize_${slot.name.lowercase()}_${control.id}"),
                    title = control.label(),
                    icon = control.icon(),
                    isEnabled = true,
                    onClick = { onSlotClick(control) },
                    trailingContent = {
                        // 未显示区没有顺序，不给排序按钮
                        if (slot != PlayerControlSlot.HIDDEN) {
                            ShiftButton(
                                control = control,
                                icon = AppIcons.ArrowUpward,
                                labelRes = R.string.customize_controls_move_up,
                                testTag = "btn_customize_up_${control.id}",
                                isEnabled = index > 0,
                                onClick = { onShift(control, -1) },
                            )
                            ShiftButton(
                                control = control,
                                icon = AppIcons.ArrowDownward,
                                labelRes = R.string.customize_controls_move_down,
                                testTag = "btn_customize_down_${control.id}",
                                isEnabled = index < controls.lastIndex,
                                onClick = { onShift(control, 1) },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ShiftButton(
    control: PlayerControl,
    icon: ImageVector,
    @StringRes labelRes: Int,
    testTag: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val onBackground = MiuixTheme.colorScheme.onBackground
    val tint = if (isEnabled) onBackground else onBackground.copy(alpha = DISABLED_ICON_ALPHA)
    MiuixIconButton(
        modifier = Modifier.testTag(testTag),
        onClick = onClick,
        enabled = isEnabled,
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = "${stringResource(labelRes)} ${control.label()}",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PlayerControlSlot.sectionTitle(count: Int): String = when (this) {
    PlayerControlSlot.TOP_RIGHT -> "${stringResource(R.string.customize_controls_top_right)}  $count"
    PlayerControlSlot.BOTTOM_RIGHT -> "${stringResource(R.string.customize_controls_bottom_right)}  $count"
    PlayerControlSlot.MENU -> "${stringResource(R.string.customize_controls_menu)}  $count"
    PlayerControlSlot.HIDDEN -> "${stringResource(R.string.customize_controls_hidden)}  $count"
}

@Composable
private fun PlayerControlSlot.label(): String = when (this) {
    PlayerControlSlot.TOP_RIGHT -> stringResource(R.string.customize_controls_top_right)
    PlayerControlSlot.BOTTOM_RIGHT -> stringResource(R.string.customize_controls_bottom_right)
    PlayerControlSlot.MENU -> stringResource(R.string.customize_controls_menu)
    PlayerControlSlot.HIDDEN -> stringResource(R.string.customize_controls_hidden)
}
