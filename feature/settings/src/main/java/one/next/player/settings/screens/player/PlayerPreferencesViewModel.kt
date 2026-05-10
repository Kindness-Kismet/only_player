package one.next.player.settings.screens.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.Logger
import one.next.player.core.common.extensions.round
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.ControlButtonsPosition
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.Resume
import one.next.player.core.model.ScreenOrientation

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private companion object {
        const val TAG = "PlayerPreferencesViewModel"
    }

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
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition -> updatePreferredControlButtonsPosition(event.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoBrightness -> updateVideoBrightness(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoContrast -> updateVideoContrast(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoSaturation -> updateVideoSaturation(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoHue -> updateVideoHue(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoGamma -> updateVideoGamma(event.value)
            is PlayerPreferencesUiEvent.UpdateVideoSharpening -> updateVideoSharpening(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(event.value)
            PlayerPreferencesUiEvent.ToggleUseClassicPlayerIcons -> toggleUseClassicPlayerIcons()
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
                it.copy(playerScreenOrientation = value)
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

    private fun updateVideoBrightness(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS).round(2)
        updateVideoFilter("brightness=$normalizedValue") { it.copy(videoBrightness = normalizedValue) }
    }

    private fun updateVideoContrast(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST).round(2)
        updateVideoFilter("contrast=$normalizedValue") { it.copy(videoContrast = normalizedValue) }
    }

    private fun updateVideoSaturation(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION).round(0)
        updateVideoFilter("saturation=$normalizedValue") { it.copy(videoSaturation = normalizedValue) }
    }

    private fun updateVideoHue(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE).round(0)
        updateVideoFilter("hue=$normalizedValue") { it.copy(videoHue = normalizedValue) }
    }

    private fun updateVideoGamma(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA).round(2)
        updateVideoFilter("gamma=$normalizedValue") { it.copy(videoGamma = normalizedValue) }
    }

    private fun updateVideoSharpening(value: Float) {
        val normalizedValue = value.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING).round(2)
        updateVideoFilter("sharpening=$normalizedValue") { it.copy(videoSharpening = normalizedValue) }
    }

    private fun updateVideoFilter(
        debugValue: String,
        transform: (PlayerPreferences) -> PlayerPreferences,
    ) {
        Logger.debug(TAG, "Update video filter from settings: $debugValue")
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences(transform)
        }
    }

    private fun updateControlAutoHideTimeout(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHideTimeout = value)
            }
        }
    }

    private fun toggleUseClassicPlayerIcons() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldUseClassicPlayerIcons = !it.shouldUseClassicPlayerIcons)
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
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data object TogglePlaybackResume : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdatePreferredControlButtonsPosition(val value: ControlButtonsPosition) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoBrightness(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoContrast(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoSaturation(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoHue(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoGamma(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateVideoSharpening(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data object ToggleUseClassicPlayerIcons : PlayerPreferencesUiEvent
    data class UpdateHiddenPlayerControls(val value: Set<PlayerControl>) : PlayerPreferencesUiEvent
}
