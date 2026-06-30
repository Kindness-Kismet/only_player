package one.only.player.feature.videopicker.screens.cloud

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol

@HiltViewModel
class CloudHomeViewModel @Inject constructor(
    private val repository: RemoteServerRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<CloudHomeUiState> = combine(
        repository.getAll(),
        preferencesRepository.applicationPreferences,
    ) { servers, preferences ->
        CloudHomeUiState(
            servers = servers,
            pinnedServerIds = preferences.pinnedCloudServerIds,
            preferences = preferences,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudHomeUiState(),
    )

    fun onEvent(event: CloudHomeEvent) {
        when (event) {
            is CloudHomeEvent.SaveServer -> saveServer(event.server, event.showOnHomeScreen)
            is CloudHomeEvent.DeleteServer -> deleteServer(event.id)
            is CloudHomeEvent.TogglePinnedServer -> togglePinnedServer(event.serverId, event.showOnHomeScreen)
            is CloudHomeEvent.UpdateQuickSettings -> updateQuickSettings(event.preferences)
        }
    }

    private fun updateQuickSettings(preferences: ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences }
        }
    }

    private fun saveServer(server: RemoteServer, showOnHomeScreen: Boolean) {
        viewModelScope.launch {
            val serverId = if (server.id == 0L) {
                repository.insert(server)
            } else {
                repository.update(server)
                server.id
            }
            preferencesRepository.updateApplicationPreferences { prefs ->
                if (showOnHomeScreen) {
                    prefs.copy(pinnedCloudServerIds = prefs.pinnedCloudServerIds + serverId)
                } else {
                    prefs.copy(pinnedCloudServerIds = prefs.pinnedCloudServerIds - serverId)
                }
            }
        }
    }

    private fun togglePinnedServer(serverId: Long, showOnHomeScreen: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { prefs ->
                if (showOnHomeScreen) {
                    prefs.copy(pinnedCloudServerIds = prefs.pinnedCloudServerIds + serverId)
                } else {
                    prefs.copy(pinnedCloudServerIds = prefs.pinnedCloudServerIds - serverId)
                }
            }
        }
    }

    private fun deleteServer(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            preferencesRepository.updateApplicationPreferences {
                it.withoutCloudQuickSettings(id).withoutPinnedCloudServer(id)
            }
        }
    }
}

@Stable
data class CloudHomeUiState(
    val servers: List<RemoteServer> = emptyList(),
    val pinnedServerIds: Set<Long> = emptySet(),
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface CloudHomeEvent {
    data class SaveServer(val server: RemoteServer, val showOnHomeScreen: Boolean) : CloudHomeEvent
    data class DeleteServer(val id: Long) : CloudHomeEvent
    data class TogglePinnedServer(val serverId: Long, val showOnHomeScreen: Boolean) : CloudHomeEvent
    data class UpdateQuickSettings(val preferences: ApplicationPreferences) : CloudHomeEvent
}

// 新建服务器的默认模板
fun newServerTemplate(protocol: ServerProtocol) = RemoteServer(
    name = "",
    protocol = protocol,
    host = "",
    port = when (protocol) {
        ServerProtocol.WEBDAV -> null
        ServerProtocol.SMB -> 445
        ServerProtocol.FTP -> 21
    },
    path = "/",
)
