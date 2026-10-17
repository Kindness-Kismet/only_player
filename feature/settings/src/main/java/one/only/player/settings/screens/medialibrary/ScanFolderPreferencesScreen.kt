package one.only.player.settings.screens.medialibrary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.StoragePath
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScanFolderPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: ScanFolderPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScanFolderPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun ScanFolderPreferencesContent(
    uiState: ScanFolderPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (ScanFolderPreferencesUiEvent) -> Unit,
) {
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentTree(),
    ) { treeUri ->
        treeUri?.let { onEvent(ScanFolderPreferencesUiEvent.AddFolder(it)) }
    }
    val scanFolders = uiState.preferences.scanFolders

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.scan_folders),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_scan_folders_back"),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding.withBottomFallback() +
                PaddingValues(top = PageContentTopPadding) +
                PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(CardItemGap),
        ) {
            itemsIndexed(scanFolders, key = { _, folderPath -> folderPath.value }) { index, folderPath ->
                PreferenceItem(
                    modifier = Modifier.testTag("item_settings_scan_folder_$index"),
                    title = folderPath.name,
                    description = folderPath.value,
                    icon = AppIcons.Folder,
                    isEnabled = true,
                    trailingContent = {
                        MiuixIconButton(
                            onClick = { onEvent(ScanFolderPreferencesUiEvent.RemoveFolder(folderPath)) },
                            modifier = Modifier.testTag("button_remove_scan_folder_$index"),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.Delete,
                                contentDescription = stringResource(id = R.string.delete),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }

            item {
                ClickablePreferenceItem(
                    modifier = Modifier
                        .padding(top = if (scanFolders.isEmpty()) 0.dp else SettingsGroupGap)
                        .testTag("item_settings_add_scan_folder"),
                    title = stringResource(id = R.string.add_folder),
                    description = stringResource(id = R.string.scan_folders_empty_desc).takeIf { scanFolders.isEmpty() },
                    icon = AppIcons.Add,
                    onClick = { directoryPickerLauncher.launch(null) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ScanFolderPreferencesScreenPreview() {
    OnlyPlayerTheme {
        ScanFolderPreferencesContent(
            uiState = ScanFolderPreferencesUiState(
                preferences = ApplicationPreferences(
                    scanFolders = listOf(
                        StoragePath.of("/storage/emulated/0/Movies"),
                        StoragePath.of("/storage/emulated/0/DCIM/Camera"),
                    ),
                ),
            ),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
