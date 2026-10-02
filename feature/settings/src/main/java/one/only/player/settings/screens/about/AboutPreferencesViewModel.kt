package one.only.player.settings.screens.about

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.AppUpdateChecker
import one.only.player.core.data.repository.AppUpdateResult
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.AppUpdateInfo
import one.only.player.core.model.UpdateChannel

@HiltViewModel
class AboutPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        AboutPreferencesUiState(
            shouldCheckForUpdatesOnStartup = preferencesRepository.applicationPreferences.value.shouldCheckForUpdatesOnStartup,
            updateChannel = preferencesRepository.applicationPreferences.value.updateChannel,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update {
                    it.copy(
                        shouldCheckForUpdatesOnStartup = prefs.shouldCheckForUpdatesOnStartup,
                        updateChannel = prefs.updateChannel,
                    )
                }
            }
        }
    }

    fun onEvent(event: AboutPreferencesUiEvent) {
        when (event) {
            is AboutPreferencesUiEvent.CheckForUpdates -> checkForUpdates(event.currentVersion)
            AboutPreferencesUiEvent.ToggleCheckOnStartup -> toggleCheckOnStartup()
            is AboutPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            is AboutPreferencesUiEvent.SetUpdateChannel -> setUpdateChannel(event.channel)
        }
    }

    private fun checkForUpdates(currentVersion: String) {
        if (uiStateInternal.value.updateState == UpdateState.Checking) return
        uiStateInternal.update { it.copy(updateState = UpdateState.Checking) }

        viewModelScope.launch {
            val channel = uiStateInternal.value.updateChannel
            val result = when (val checkResult = appUpdateChecker.checkForUpdate(currentVersion, channel)) {
                is AppUpdateResult.Available -> UpdateState.UpdateAvailable(checkResult.info)
                AppUpdateResult.UpToDate -> UpdateState.UpToDate
                AppUpdateResult.Failed -> UpdateState.Error
            }
            uiStateInternal.update {
                if (it.updateChannel != channel) return@update it
                it.copy(
                    updateState = result,
                    showDialog = if (result is UpdateState.UpdateAvailable) {
                        AboutPreferenceDialog.Update(result.info)
                    } else {
                        it.showDialog
                    },
                )
            }
        }
    }

    private fun toggleCheckOnStartup() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldCheckForUpdatesOnStartup = !it.shouldCheckForUpdatesOnStartup)
            }
        }
    }

    private fun showDialog(value: AboutPreferenceDialog?) {
        uiStateInternal.update { it.copy(showDialog = value) }
    }

    private fun setUpdateChannel(channel: UpdateChannel) {
        uiStateInternal.update {
            it.copy(
                updateChannel = channel,
                updateState = UpdateState.Idle,
                showDialog = null,
            )
        }
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(updateChannel = channel)
            }
        }
    }
}

@Stable
data class AboutPreferencesUiState(
    val updateState: UpdateState = UpdateState.Idle,
    val shouldCheckForUpdatesOnStartup: Boolean = false,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val showDialog: AboutPreferenceDialog? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateState
    data object Error : UpdateState
}

sealed interface AboutPreferencesUiEvent {
    data class CheckForUpdates(val currentVersion: String) : AboutPreferencesUiEvent
    data object ToggleCheckOnStartup : AboutPreferencesUiEvent
    data class ShowDialog(val value: AboutPreferenceDialog?) : AboutPreferencesUiEvent
    data class SetUpdateChannel(val channel: UpdateChannel) : AboutPreferencesUiEvent
}

sealed interface AboutPreferenceDialog {
    data object UpdateChannel : AboutPreferenceDialog
    data class Update(val info: AppUpdateInfo) : AboutPreferenceDialog
}
