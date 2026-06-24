package one.only.player.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol
import one.only.player.core.ui.R
import one.only.player.core.ui.base.DataState
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.SelectablePreference
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme

@Composable
fun HomeCloudServerPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: HomeCloudServerPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)

    HomeCloudServerPreferencesContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeCloudServerPreferencesContent(
    uiState: HomeCloudServerPreferencesUiState,
    onNavigateUp: () -> Unit,
    onEvent: (HomeCloudServerPreferencesUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.home_cloud_servers),
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
        when (val serversDataState = uiState.serversDataState) {
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding.withBottomFallback() + PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    if (serversDataState.value.isEmpty()) {
                        item {
                            PreferenceItem(
                                modifier = Modifier.testTag("item_settings_home_cloud_servers_empty"),
                                title = stringResource(id = R.string.no_servers_configured),
                                description = stringResource(id = R.string.home_cloud_servers_empty_desc),
                                icon = NextIcons.Cloud,
                                isEnabled = false,
                                isFirstItem = true,
                                isLastItem = true,
                            )
                        }
                    }

                    itemsIndexed(serversDataState.value) { index, server ->
                        SelectablePreference(
                            modifier = Modifier.testTag("item_settings_home_cloud_server_${server.id}"),
                            title = server.displayName(),
                            description = server.description(),
                            isSelected = server.id in uiState.preferences.homeCloudServerIds,
                            shouldStrikeThroughSelected = false,
                            onClick = {
                                onEvent(
                                    HomeCloudServerPreferencesUiEvent.UpdateHomeCloudServerSelection(server.id),
                                )
                            },
                            isFirstItem = index == 0,
                            isLastItem = index == serversDataState.value.lastIndex,
                        )
                    }
                }
            }

            is DataState.Error -> Unit
        }
    }
}

private fun RemoteServer.displayName(): String = name.ifBlank { host }

private fun RemoteServer.description(): String {
    val portSuffix = port?.let { ":$it" }.orEmpty()
    val pathSuffix = path.takeIf { it.isNotBlank() && it != "/" }?.let { " · $it" }.orEmpty()
    return "${protocol.name} · $host$portSuffix$pathSuffix"
}

@PreviewLightDark
@Composable
private fun HomeCloudServerPreferencesScreenPreview() {
    OnlyPlayerTheme {
        HomeCloudServerPreferencesContent(
            uiState = HomeCloudServerPreferencesUiState(
                serversDataState = DataState.Success(
                    listOf(
                        RemoteServer(
                            id = 1L,
                            name = "NAS",
                            protocol = ServerProtocol.WEBDAV,
                            host = "192.168.1.10",
                        ),
                    ),
                ),
                preferences = ApplicationPreferences(homeCloudServerIds = listOf(1L)),
            ),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
