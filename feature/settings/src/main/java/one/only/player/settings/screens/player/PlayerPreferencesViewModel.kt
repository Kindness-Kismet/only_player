package one.only.player.settings.screens.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.extensions.round
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.ControlButtonsPosition
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Resume
import one.only.player.core.model.ScreenOrientation

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        PlayerPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: PlayerPreferencesUiEvent) {
        when (event) {
            is PlayerPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            PlayerPreferencesUiEvent.TogglePlaybackResume -> togglePlaybackResume()
            PlayerPreferencesUiEvent.ToggleAutoplay -> toggleAutoplay()
            PlayerPreferencesUiEvent.ToggleAutoPip -> toggleAutoPip()
            PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay -> toggleAutoBackgroundPlay()
            PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel -> toggleRememberBrightnessLevel()
            PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation -> toggleRememberPlayerScreenOrientation()
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition -> updatePreferredControlButtonsPosition(event.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(event.value)
            is PlayerPreferencesUiEvent.UpdatePlayerIconStyle -> updatePlayerIconStyle(event.value)
            PlayerPreferencesUiEvent.TogglePlayerControlLabels -> togglePlayerControlLabels()
            is PlayerPreferencesUiEvent.UpdateHiddenPlayerControls -> updateHiddenPlayerControls(event.value)
        }
    }

    private fun showDialog(value: PlayerPreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun togglePlaybackResume() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    resume = when (it.resume) {
                        Resume.YES -> Resume.NO
                        Resume.NO -> Resume.YES
                    },
                )
            }
        }
    }

    private fun toggleAutoplay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoPlay = !it.shouldAutoPlay)
            }
        }
    }

    private fun toggleAutoPip() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoEnterPip = !it.shouldAutoEnterPip)
            }
        }
    }

    private fun toggleAutoBackgroundPlay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoPlayInBackground = !it.shouldAutoPlayInBackground)
            }
        }
    }

    private fun toggleRememberBrightnessLevel() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldRememberPlayerBrightness = !it.shouldRememberPlayerBrightness)
            }
        }
    }

    private fun updatePreferredPlayerOrientation(value: ScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    playerScreenOrientation = value,
                    lastPlayerScreenOrientation = null,
                )
            }
        }
    }

    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    lastPlayerScreenOrientation = null,
                )
            }
        }
    }

    private fun updatePreferredControlButtonsPosition(value: ControlButtonsPosition) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controlButtonsPosition = value)
            }
        }
    }

    private fun updateDefaultPlaybackSpeed(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(defaultPlaybackSpeed = value.round(2))
            }
        }
    }

    private fun updateControlAutoHideTimeout(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHideTimeout = value)
            }
        }
    }

    private fun updatePlayerIconStyle(value: PlayerIconStyle) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(playerIconStyle = value)
            }
        }
    }

    private fun togglePlayerControlLabels() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldHidePlayerControlLabels = !it.shouldHidePlayerControlLabels)
            }
        }
    }

    private fun updateHiddenPlayerControls(value: Set<PlayerControl>) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(hiddenPlayerControls = value)
            }
        }
    }
}

@Stable
data class PlayerPreferencesUiState(
    val showDialog: PlayerPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface PlayerPreferenceDialog {
    data object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    data object ControlButtonsDialog : PlayerPreferenceDialog
    data object PlayerIconStyleDialog : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data object TogglePlaybackResume : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data object ToggleRememberPlayerScreenOrientation : PlayerPreferencesUiEvent
    data object TogglePlayerControlLabels : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdatePreferredControlButtonsPosition(val value: ControlButtonsPosition) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data class UpdatePlayerIconStyle(val value: PlayerIconStyle) : PlayerPreferencesUiEvent
    data class UpdateHiddenPlayerControls(val value: Set<PlayerControl>) : PlayerPreferencesUiEvent
}
