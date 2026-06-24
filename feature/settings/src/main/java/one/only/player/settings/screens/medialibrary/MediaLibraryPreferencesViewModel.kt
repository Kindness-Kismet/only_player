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
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.HomeCloudServersPlacement

@HiltViewModel
class MediaLibraryPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(MediaLibraryPreferencesUiState())
    val uiState: StateFlow<MediaLibraryPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        preferences = preferences,
                    )
                }
            }
        }
    }

    fun onEvent(event: MediaLibraryPreferencesUiEvent) {
        when (event) {
            is MediaLibraryPreferencesUiEvent.SetIgnoreNoMediaFiles -> setIgnoreNoMediaFiles(event.shouldIgnoreNoMediaFiles)
            is MediaLibraryPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            is MediaLibraryPreferencesUiEvent.UpdateHomeCloudServersPlacement -> updateHomeCloudServersPlacement(event.value)
            MediaLibraryPreferencesUiEvent.ResetRestrictedFeatures -> resetRestrictedFeatures()
            MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
            MediaLibraryPreferencesUiEvent.ToggleRestoreLastPlayedMediaInFolders -> toggleRestoreLastPlayedMediaInFolders()
            MediaLibraryPreferencesUiEvent.ToggleRecycleBinEnabled -> toggleRecycleBinEnabled()
        }
    }

    private fun setIgnoreNoMediaFiles(shouldIgnoreNoMediaFiles: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                if (it.shouldIgnoreNoMediaFiles == shouldIgnoreNoMediaFiles) {
                    it
                } else {
                    it.copy(shouldIgnoreNoMediaFiles = shouldIgnoreNoMediaFiles)
                }
            }
        }
    }

    private fun toggleMarkLastPlayedMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldMarkLastPlayedMedia = !it.shouldMarkLastPlayedMedia)
            }
        }
    }

    private fun toggleRestoreLastPlayedMediaInFolders() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldRestoreLastPlayedMediaInFolders = !it.shouldRestoreLastPlayedMediaInFolders)
            }
        }
    }

    private fun showDialog(dialog: MediaLibraryPreferenceDialog?) {
        uiStateInternal.update { it.copy(showDialog = dialog) }
    }

    private fun updateHomeCloudServersPlacement(value: HomeCloudServersPlacement) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(homeCloudServersPlacement = value)
            }
        }
    }

    private fun resetRestrictedFeatures() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                val shouldResetIgnoreNoMediaFiles = it.shouldIgnoreNoMediaFiles
                val shouldResetRecycleBin = it.isRecycleBinEnabled

                if (!shouldResetIgnoreNoMediaFiles && !shouldResetRecycleBin) {
                    it
                } else {
                    it.copy(
                        shouldIgnoreNoMediaFiles = false,
                        isRecycleBinEnabled = false,
                    )
                }
            }
        }
    }

    private fun toggleRecycleBinEnabled() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(isRecycleBinEnabled = !it.isRecycleBinEnabled)
            }
        }
    }
}

@Stable
data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val showDialog: MediaLibraryPreferenceDialog? = null,
)

sealed interface MediaLibraryPreferencesUiEvent {
    data class SetIgnoreNoMediaFiles(val shouldIgnoreNoMediaFiles: Boolean) : MediaLibraryPreferencesUiEvent
    data class ShowDialog(val value: MediaLibraryPreferenceDialog?) : MediaLibraryPreferencesUiEvent
    data class UpdateHomeCloudServersPlacement(val value: HomeCloudServersPlacement) : MediaLibraryPreferencesUiEvent
    data object ResetRestrictedFeatures : MediaLibraryPreferencesUiEvent
    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
    data object ToggleRestoreLastPlayedMediaInFolders : MediaLibraryPreferencesUiEvent
    data object ToggleRecycleBinEnabled : MediaLibraryPreferencesUiEvent
}

enum class MediaLibraryPreferenceDialog {
    HomeCloudServersPlacement,
}
