package one.only.player.settings.screens.medialibrary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.createManageExternalStorageAccessIntent
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.ExtensionDecoderPreference
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.DoneButton
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextResetIconButton
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FileExtensionPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: FileExtensionPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FileExtensionPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileExtensionPreferencesContent(
    uiState: FileExtensionPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (FileExtensionPreferencesUiEvent) -> Unit,
) {
    val context = LocalContext.current
    var shouldShowAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingPreference: ExtensionDecoderPreference? by remember { mutableStateOf(null) }
    var isBatchMode by rememberSaveable { mutableStateOf(false) }
    var selectedExtensions by rememberSaveable { mutableStateOf(setOf<String>()) }
    var shouldShowBatchDecoderDialog by rememberSaveable { mutableStateOf(false) }
    var addInput by rememberSaveable { mutableStateOf("") }
    var addError by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAllFilesAccess by remember {
        mutableStateOf(hasManageExternalStorageAccess())
    }

    val alreadyExistsMessage = stringResource(R.string.file_extensions_already_exists)
    val invalidMessage = stringResource(R.string.file_extensions_invalid)

    val builtIn = remember(uiState.preferences) {
        uiState.preferences.filter { it.isBuiltIn }
    }
    val custom = remember(uiState.preferences) {
        uiState.preferences.filter { !it.isBuiltIn }
    }

    fun enterBatchMode(initialExtension: String? = null) {
        isBatchMode = true
        selectedExtensions = initialExtension?.let { setOf(it) } ?: emptySet()
    }

    fun exitBatchMode() {
        isBatchMode = false
        selectedExtensions = emptySet()
        shouldShowBatchDecoderDialog = false
    }

    fun toggleSelection(extension: String) {
        selectedExtensions = if (extension in selectedExtensions) {
            selectedExtensions - extension
        } else {
            selectedExtensions + extension
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (isBatchMode) {
                    stringResource(R.string.file_extensions_batch_selected, selectedExtensions.size)
                } else {
                    stringResource(id = R.string.file_extensions)
                },
                navigationIcon = {
                    MiuixIconButton(
                        onClick = {
                            if (isBatchMode) {
                                exitBatchMode()
                            } else {
                                onNavigateUp()
                            }
                        },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_file_extensions_back"),
                    ) {
                        MiuixIcon(
                            imageVector = if (isBatchMode) NextIcons.Close else NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    if (isBatchMode) {
                        if (selectedExtensions.isNotEmpty()) {
                            MiuixIconButton(
                                onClick = { shouldShowBatchDecoderDialog = true },
                                modifier = Modifier.testTag("btn_file_extensions_batch_decoder"),
                            ) {
                                MiuixIcon(
                                    imageVector = NextIcons.Decoder,
                                    contentDescription = stringResource(id = R.string.file_extensions_batch_set_decoder),
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        MiuixIconButton(
                            onClick = {
                                val all = uiState.preferences.map { it.extension }.toSet()
                                selectedExtensions = if (selectedExtensions.size == all.size) emptySet() else all
                            },
                            modifier = Modifier.testTag("btn_file_extensions_batch_toggle_all"),
                        ) {
                            MiuixIcon(
                                imageVector = if (selectedExtensions.size == uiState.preferences.size) {
                                    NextIcons.DeselectAll
                                } else {
                                    NextIcons.SelectAll
                                },
                                contentDescription = stringResource(id = R.string.select_all),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    } else {
                        NextResetIconButton(
                            modifier = Modifier.testTag("btn_file_extensions_restore_defaults"),
                            onClick = { onEvent(FileExtensionPreferencesUiEvent.RestoreDefaults) },
                            contentDescription = stringResource(id = R.string.file_extensions_restore_defaults),
                        )
                        MiuixIconButton(
                            onClick = { enterBatchMode() },
                            modifier = Modifier.testTag("btn_file_extensions_enter_batch"),
                        ) {
                            MiuixIcon(
                                imageVector = NextIcons.SelectAll,
                                contentDescription = stringResource(id = R.string.file_extensions_batch_set_decoder),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        MiuixIconButton(
                            onClick = {
                                addInput = ""
                                addError = null
                                hasAllFilesAccess = hasManageExternalStorageAccess()
                                shouldShowAddDialog = true
                            },
                            modifier = Modifier.testTag("btn_file_extensions_add"),
                        ) {
                            MiuixIcon(
                                imageVector = NextIcons.Add,
                                contentDescription = stringResource(id = R.string.file_extensions_add),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (!isBatchMode) {
                Text(
                    text = stringResource(R.string.file_extensions_long_press_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.file_extensions_built_in))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                builtIn.forEachIndexed { index, preference ->
                    ExtensionPreferenceRow(
                        preference = preference,
                        isBatchMode = isBatchMode,
                        isSelected = preference.extension in selectedExtensions,
                        isFirstItem = index == 0,
                        isLastItem = index == builtIn.lastIndex,
                        onClick = {
                            if (isBatchMode) {
                                toggleSelection(preference.extension)
                            } else {
                                editingPreference = preference
                            }
                        },
                        onLongClick = {
                            if (!isBatchMode) {
                                enterBatchMode(preference.extension)
                            }
                        },
                    )
                }
            }

            if (custom.isNotEmpty()) {
                ListSectionTitle(text = stringResource(id = R.string.file_extensions_custom))
                Column(
                    verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
                ) {
                    custom.forEachIndexed { index, preference ->
                        ExtensionPreferenceRow(
                            preference = preference,
                            isBatchMode = isBatchMode,
                            isSelected = preference.extension in selectedExtensions,
                            isFirstItem = index == 0,
                            isLastItem = index == custom.lastIndex,
                            onClick = {
                                if (isBatchMode) {
                                    toggleSelection(preference.extension)
                                } else {
                                    editingPreference = preference
                                }
                            },
                            onLongClick = {
                                if (!isBatchMode) {
                                    enterBatchMode(preference.extension)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (shouldShowAddDialog) {
        NextDialog(
            onDismissRequest = { shouldShowAddDialog = false },
            title = stringResource(R.string.file_extensions_add),
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = addInput,
                        onValueChange = {
                            addInput = it
                            addError = null
                        },
                        label = stringResource(R.string.file_extensions_add_hint),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_file_extension_add"),
                    )
                    Text(
                        text = stringResource(R.string.file_extensions_all_files_access_hint),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (!hasAllFilesAccess) {
                        TextButton(
                            text = stringResource(R.string.all_files_access_title),
                            onClick = {
                                context.startActivity(createManageExternalStorageAccessIntent(context))
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_file_extension_open_all_files_access"),
                        )
                    }
                    addError?.let { error ->
                        Text(
                            text = error,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            },
            confirmButton = {
                DoneButton(
                    onClick = {
                        val normalized = ExtensionDecoderPreference.normalizeExtension(addInput)
                        when {
                            normalized.isBlank() || !normalized.all { it.isLetterOrDigit() } -> {
                                addError = invalidMessage
                            }
                            uiState.preferences.any { it.extension == normalized } -> {
                                addError = alreadyExistsMessage
                            }
                            else -> {
                                onEvent(
                                    FileExtensionPreferencesUiEvent.AddExtension(
                                        ExtensionDecoderPreference(
                                            extension = normalized,
                                            decoderPriority = DecoderPriority.AUTOMATIC,
                                            isBuiltIn = false,
                                        ),
                                    ),
                                )
                                shouldShowAddDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_file_extension_add_done"),
                )
            },
            dismissButton = {
                CancelButton(
                    onClick = { shouldShowAddDialog = false },
                    modifier = Modifier.testTag("btn_file_extension_add_cancel"),
                )
            },
        )
    }

    editingPreference?.let { preference ->
        OptionsDialog(
            text = ".${preference.extension} · ${stringResource(R.string.file_extensions_decoder)}",
            onDismissClick = { editingPreference = null },
        ) {
            items(DecoderPriority.entries.toTypedArray()) { priority ->
                RadioTextButton(
                    modifier = Modifier.testTag(
                        "option_file_extension_decoder_${preference.extension}_${priority.name.lowercase()}",
                    ),
                    text = priority.name(),
                    isSelected = priority == preference.decoderPriority,
                    onClick = {
                        onEvent(
                            FileExtensionPreferencesUiEvent.UpdateDecoderPriority(
                                extension = preference.extension,
                                decoderPriority = priority,
                            ),
                        )
                        editingPreference = null
                    },
                )
            }
            // 内置扩展名也允许删除
            item {
                ClickablePreferenceItem(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("btn_file_extension_delete_${preference.extension}"),
                    title = stringResource(R.string.file_extensions_delete),
                    description = null,
                    icon = NextIcons.Delete,
                    onClick = {
                        onEvent(FileExtensionPreferencesUiEvent.RemoveExtension(preference.extension))
                        editingPreference = null
                    },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }
        }
    }

    if (shouldShowBatchDecoderDialog && selectedExtensions.isNotEmpty()) {
        OptionsDialog(
            text = stringResource(R.string.file_extensions_batch_set_decoder),
            onDismissClick = { shouldShowBatchDecoderDialog = false },
        ) {
            items(DecoderPriority.entries.toTypedArray()) { priority ->
                RadioTextButton(
                    modifier = Modifier.testTag(
                        "option_file_extension_batch_decoder_${priority.name.lowercase()}",
                    ),
                    text = priority.name(),
                    isSelected = false,
                    onClick = {
                        onEvent(
                            FileExtensionPreferencesUiEvent.BatchUpdateDecoderPriority(
                                extensions = selectedExtensions.toList(),
                                decoderPriority = priority,
                            ),
                        )
                        exitBatchMode()
                    },
                )
            }
        }
    }
}

@Composable
private fun ExtensionPreferenceRow(
    preference: ExtensionDecoderPreference,
    isBatchMode: Boolean,
    isSelected: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ClickablePreferenceItem(
        modifier = Modifier
            .testTag(
                if (preference.isBuiltIn) {
                    "item_file_extension_${preference.extension}"
                } else {
                    "item_file_extension_custom_${preference.extension}"
                },
            ),
        title = ".${preference.extension}",
        description = if (isBatchMode && isSelected) {
            stringResource(R.string.file_extensions_selected) + " · " + preference.decoderPriority.name()
        } else {
            preference.decoderPriority.name()
        },
        icon = if (isBatchMode) {
            if (isSelected) NextIcons.CheckBox else NextIcons.CheckBoxOutline
        } else {
            NextIcons.Decoder
        },
        onClick = onClick,
        // 始终传非空 longClick，保证 combinedClickable 长按批量可用
        onLongClick = onLongClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
    )
}
