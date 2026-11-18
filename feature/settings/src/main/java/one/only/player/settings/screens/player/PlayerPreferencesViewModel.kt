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
import one.only.player.core.model.ControllerAutoHidePreset
import one.only.player.core.model.PictureInPictureMode
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlSlot
import one.only.player.core.model.PlayerControlsArrangement
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Resume
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.model.VideoLandscapeOrientation
import one.only.player.core.model.withControlMoved
import one.only.player.core.model.withControlShifted

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
            PlayerPreferencesUiEvent.TogglePauseAtEndOfQueue -> togglePauseAtEndOfQueue()
            PlayerPreferencesUiEvent.ToggleAutoPip -> toggleAutoPip()
            is PlayerPreferencesUiEvent.UpdatePictureInPictureMode -> updatePictureInPictureMode(event.value)
            PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay -> toggleAutoBackgroundPlay()
            PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel -> toggleRememberBrightnessLevel()
            PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation -> toggleRememberPlayerScreenOrientation()
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoLandscapeOrientation -> updateVideoLandscapeOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHidePreset -> updateControlAutoHidePreset(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(event.value)
            PlayerPreferencesUiEvent.ToggleDimVideoWhenControlsVisible -> toggleDimVideoWhenControlsVisible()
            is PlayerPreferencesUiEvent.MoveControl -> moveControl(event.control, event.slot)
            is PlayerPreferencesUiEvent.ShiftControl -> shiftControl(event.control, event.offset)
            PlayerPreferencesUiEvent.ResetControlsArrangement -> resetControlsArrangement()
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

    private fun togglePauseAtEndOfQueue() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldPauseAtEndOfQueue = !it.shouldPauseAtEndOfQueue)
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

    private fun updatePictureInPictureMode(value: PictureInPictureMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(pictureInPictureMode = value)
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

    private fun updateVideoLandscapeOrientation(value: VideoLandscapeOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(videoLandscapeOrientation = value) }
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
                it.copy(
                    controllerAutoHidePreset = ControllerAutoHidePreset.CUSTOM,
                    controllerAutoHideTimeout = value.coerceAtLeast(1),
                )
            }
        }
    }

    private fun updateControlAutoHidePreset(value: ControllerAutoHidePreset) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHidePreset = value)
            }
        }
    }

    private fun moveControl(
        control: PlayerControl,
        slot: PlayerControlSlot,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withControlMoved(control, slot)
            }
        }
    }

    private fun shiftControl(
        control: PlayerControl,
        offset: Int,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.withControlShifted(control, offset)
            }
        }
    }

    private fun resetControlsArrangement() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controlsArrangement = PlayerControlsArrangement())
            }
        }
    }

    private fun toggleDimVideoWhenControlsVisible() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldDimVideoWhenControlsVisible = !it.shouldDimVideoWhenControlsVisible)
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
    data object ControllerAutoHideDialog : PlayerPreferenceDialog
    data object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    data object VideoLandscapeOrientationDialog : PlayerPreferenceDialog
    data object PictureInPictureModeDialog : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data object TogglePlaybackResume : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object TogglePauseAtEndOfQueue : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data class UpdatePictureInPictureMode(val value: PictureInPictureMode) : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data object ToggleRememberPlayerScreenOrientation : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdateVideoLandscapeOrientation(val value: VideoLandscapeOrientation) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHidePreset(val value: ControllerAutoHidePreset) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data object ToggleDimVideoWhenControlsVisible : PlayerPreferencesUiEvent
    data class MoveControl(val control: PlayerControl, val slot: PlayerControlSlot) : PlayerPreferencesUiEvent

    // offset 为 -1 上移、1 下移
    data class ShiftControl(val control: PlayerControl, val offset: Int) : PlayerPreferencesUiEvent
    data object ResetControlsArrangement : PlayerPreferencesUiEvent
}
