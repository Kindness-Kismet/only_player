package one.only.player.settings.screens.general

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GeneralPreferencesContent(
    uiState: GeneralPreferencesUiState,
    onEvent: (GeneralPreferencesUiEvent) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
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
                createBackupLauncher.launch(context.getString(R.string.settings_backup_file_name))
            }
            GeneralPreferencesPendingAction.RestoreSettings -> {
                restoreBackupLauncher.launch(arrayOf("application/json"))
            }
            null -> Unit
        }
    }

    LaunchedEffect(uiState.resultMessage) {
        val message = when (uiState.resultMessage) {
            GeneralPreferencesResultMessage.BackupSucceeded -> context.getString(R.string.backup_settings_success)
            GeneralPreferencesResultMessage.BackupFailed -> context.getString(R.string.backup_settings_failed)
            GeneralPreferencesResultMessage.RestoreSucceeded -> context.getString(R.string.restore_settings_success)
            GeneralPreferencesResultMessage.RestoreFailed -> context.getString(R.string.restore_settings_failed)
            null -> null
        } ?: return@LaunchedEffect

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onEvent(GeneralPreferencesUiEvent.ClearResultMessage)
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.general_name),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
        ) {
            ListSectionTitle(text = stringResource(id = R.string.user_data))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
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
                        title = {
                            Text(
                                text = stringResource(R.string.delete_video_cache),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                modifier = Modifier.testTag("btn_confirm_settings_general_clear_video_cache"),
                                onClick = {
                                    onEvent(GeneralPreferencesUiEvent.ClearVideoCache)
                                    onEvent(GeneralPreferencesUiEvent.ShowDialog(null))
                                },
                            ) {
                                Text(text = stringResource(R.string.delete))
                            }
                        },
                        dismissButton = { CancelButton(onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) }) },
                        content = {
                            Text(
                                text = stringResource(R.string.delete_video_cache_confirmation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                }
                GeneralPreferencesDialog.ResetSettingsDialog -> {
                    NextDialog(
                        onDismissRequest = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) },
                        title = {
                            Text(
                                text = stringResource(R.string.reset_settings),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        confirmButton = {
                            TextButton(
                                modifier = Modifier.testTag("btn_confirm_settings_general_reset_settings"),
                                onClick = {
                                    onEvent(GeneralPreferencesUiEvent.ResetSettings)
                                    onEvent(GeneralPreferencesUiEvent.ShowDialog(null))
                                },
                            ) {
                                Text(text = stringResource(R.string.reset))
                            }
                        },
                        dismissButton = { CancelButton(onClick = { onEvent(GeneralPreferencesUiEvent.ShowDialog(null)) }) },
                        content = {
                            Text(
                                text = stringResource(R.string.reset_settings_confirmation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}
