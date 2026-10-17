package one.only.player.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.media.extensions.storageRootLabelOf
import one.only.player.core.media.extensions.storageRootLabels
import one.only.player.core.model.StoragePath
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CardItemGap
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.SelectablePreference
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: FolderPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    FolderPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun FolderPreferencesContent(
    uiState: FolderPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (FolderPreferencesUiEvent) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val storageRootLabels = remember(context) { context.storageRootLabels() }

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.manage_folders),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_folders_back"),
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
        when (uiState.foldersDataState) {
            is DataState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            is DataState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = innerPadding.withBottomFallback() +
                        PaddingValues(top = PageContentTopPadding) +
                        PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(CardItemGap),
                ) {
                    itemsIndexed(uiState.foldersDataState.value, key = { _, folder -> folder.path }) { index, folder ->
                        val title = remember(folder.path, storageRootLabels) {
                            storageRootLabels.storageRootLabelOf(folder.path) ?: folder.name
                        }
                        SelectablePreference(
                            modifier = Modifier.testTag("item_settings_folder_$index"),
                            title = title,
                            description = folder.path,
                            isSelected = StoragePath.of(folder.path) in uiState.preferences.excludeFolders,
                            onClick = { onEvent(FolderPreferencesUiEvent.UpdateExcludeList(folder.path)) },
                        )
                    }
                }
            }

            is DataState.Error -> Unit
        }
    }
}

@PreviewLightDark
@Composable
private fun FolderPreferencesScreenPreview() {
    OnlyPlayerTheme {
        FolderPreferencesContent(
            uiState = FolderPreferencesUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
