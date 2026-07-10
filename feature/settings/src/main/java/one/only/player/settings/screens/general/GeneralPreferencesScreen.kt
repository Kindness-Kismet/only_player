package one.only.player.settings.screens.general

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GeneralPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: GeneralPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GeneralPreferencesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
private fun GeneralPreferencesContent(
    uiState: GeneralPreferencesUiState,
    onEvent: (GeneralPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val backupFileName = stringResource(R.string.settings_backup_file_name)
    val backupSucceededMessage = stringResource(R.string.backup_settings_success)
    val backupFailedMessage = stringResource(R.string.backup_settings_failed)
    val restoreSucceededMessage = stringResource(R.string.restore_settings_success)
    val restoreFailedMessage = stringResource(R.string.restore_settings_failed)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("application/json"),
    ) { uri ->
        onEvent(GeneralPreferencesUiEvent.OnBackupFileSelected(context, uri))
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = OpenDocument(),
    ) { uri ->
        onEvent(GeneralPreferencesUiEvent.OnRestoreFileSelected(context, uri))
    }

    LaunchedEffect(uiState.pendingAction) {
        when (uiState.pendingAction) {
            GeneralPreferencesPendingAction.BackupSettings -> {
                createBackupLauncher.launch(backupFileName)
            }
            GeneralPreferencesPendingAction.RestoreSettings -> {
                restoreBackupLauncher.launch(arrayOf("application/json"))
            }
            null -> Unit
        }
    }

    LaunchedEffect(uiState.resultMessage) {
        val message = when (uiState.resultMessage) {
            GeneralPreferencesResultMessage.BackupSucceeded -> backupSucceededMessage
            GeneralPreferencesResultMessage.BackupFailed -> backupFailedMessage
            GeneralPreferencesResultMessage.RestoreSucceeded -> restoreSucceededMessage
            GeneralPreferencesResultMessage.RestoreFailed -> restoreFailedMessage
            null -> null
        } ?: return@LaunchedEffect

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(GeneralPreferencesUiEvent.ClearResultMessage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.general_name),
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_general_back"),
                    ) {
                        MiuixIcon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_general_backup_settings"),
                    title = stringResource(R.string.backup_settings),
                    description = stringResource(R.string.backup_settings_description),
                    icon = NextIcons.FileOpen,
                    onClick = { onEvent(GeneralPreferencesUiEvent.BackupSettings) },
                    isFirstItem = true,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_general_restore_settings"),
                    title = stringResource(R.string.restore_settings),
                    description = stringResource(R.string.restore_settings_description),
                    icon = NextIcons.History,
                    onClick = { onEvent(GeneralPreferencesUiEvent.RestoreSettings) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_general_clear_video_cache"),
                    title = stringResource(R.string.delete_video_cache),
                    description = stringResource(R.string.delete_video_cache_description),
                    icon = NextIcons.DeleteSweep,
                    onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(GeneralPreferencesDialog.ClearVideoCacheDialog)) },
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_general_reset_settings"),
                    title = stringResource(R.string.reset_settings),
                    description = stringResource(R.string.reset_settings_description),
                    icon = NextIcons.Delete,
                    onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(GeneralPreferencesDialog.ResetSettingsDialog)) },
                    isLastItem = true,
                )
            }
        }

        uiState.showDialog?.let { dialog ->
            when (dialog) {
                GeneralPreferencesDialog.ClearVideoCacheDialog -> {
                    NextDialog(
                        onDismissRequest = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) },
                        title = stringResource(R.string.delete_video_cache),
                        confirmButton = {
                            TextButton(
                                text = stringResource(R.string.delete),
                                modifier = Modifier.testTag("btn_confirm_settings_general_clear_video_cache"),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    onEvent(GeneralPreferencesUiEvent.ClearVideoCache)
                                    onEvent(GeneralPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        },
                        dismissButton = { CancelButton(onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) }) },
                        content = {
                            Text(
                                text = stringResource(R.string.delete_video_cache_confirmation),
                                style = MiuixTheme.textStyles.body1,
                            )
                        },
                    )
                }
                GeneralPreferencesDialog.ResetSettingsDialog -> {
                    NextDialog(
                        onDismissRequest = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) },
                        title = stringResource(R.string.reset_settings),
                        confirmButton = {
                            TextButton(
                                text = stringResource(R.string.reset),
                                modifier = Modifier.testTag("btn_confirm_settings_general_reset_settings"),
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    onEvent(GeneralPreferencesUiEvent.ResetSettings)
                                    onEvent(GeneralPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        },
                        dismissButton = { CancelButton(onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) }) },
                        content = {
                            Text(
                                text = stringResource(R.string.reset_settings_confirmation),
                                style = MiuixTheme.textStyles.body1,
                            )
                        },
                    )
                }
            }
        }
    }
}
