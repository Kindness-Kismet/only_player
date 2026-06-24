package one.only.player.settings.screens.medialibrary

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.RemoteServer
import one.only.player.core.ui.base.DataState

@HiltViewModel
class HomeCloudServerPreferencesViewModel @Inject constructor(
    remoteServerRepository: RemoteServerRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        HomeCloudServerPreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState: StateFlow<HomeCloudServerPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            remoteServerRepository.getAll().collect { servers ->
                uiStateInternal.update { currentState ->
                    currentState.copy(serversDataState = DataState.Success(servers))
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = preferences)
                }
            }
        }
    }

    fun onEvent(event: HomeCloudServerPreferencesUiEvent) {
        when (event) {
            is HomeCloudServerPreferencesUiEvent.UpdateHomeCloudServerSelection -> updateHomeCloudServerSelection(event.serverId)
        }
    }

    private fun updateHomeCloudServerSelection(serverId: Long) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences ->
                preferences.withHomeCloudServer(
                    serverId = serverId,
                    isSelected = serverId !in preferences.homeCloudServerIds,
                )
            }
        }
    }
}

@Stable
data class HomeCloudServerPreferencesUiState(
    val serversDataState: DataState<List<RemoteServer>> = DataState.Loading,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface HomeCloudServerPreferencesUiEvent {
    data class UpdateHomeCloudServerSelection(val serverId: Long) : HomeCloudServerPreferencesUiEvent
}
