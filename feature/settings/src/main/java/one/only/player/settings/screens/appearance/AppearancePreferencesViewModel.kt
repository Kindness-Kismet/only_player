package one.only.player.settings.screens.appearance

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.common.AppLanguageManager
import one.only.player.core.common.AppThemeMode
import one.only.player.core.common.AppThemeModeManager
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.NavigationBarLabelMode
import one.only.player.core.model.NavigationBarStyle
import one.only.player.core.model.ThemeConfig

@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        AppearancePreferencesUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: AppearancePreferencesEvent) {
        when (event) {
            is AppearancePreferencesEvent.ShowDialog -> showDialog(event.value)
            is AppearancePreferencesEvent.UpdateThemeConfig -> updateThemeConfig(event.themeConfig)
            is AppearancePreferencesEvent.UpdateAppLanguage -> updateAppLanguage(event.languageTag)
            AppearancePreferencesEvent.ToggleUseDynamicColors -> toggleUseDynamicColors()
            AppearancePreferencesEvent.ToggleNavigateHomeOnTitleLongPress -> toggleNavigateHomeOnTitleLongPress()
            is AppearancePreferencesEvent.UpdateNavigationBarStyle -> updateNavigationBarStyle(event.style)
            is AppearancePreferencesEvent.UpdateNavigationBarLabelMode -> updateNavigationBarLabelMode(event.mode)
            AppearancePreferencesEvent.ToggleBlurNavigationBar -> toggleBlurNavigationBar()
            AppearancePreferencesEvent.ToggleBlurTopBar -> toggleBlurTopBar()
            AppearancePreferencesEvent.ToggleShowCloudTab -> toggleShowCloudTab()
            is AppearancePreferencesEvent.ToggleEnablePredictiveBack -> {
                toggleEnablePredictiveBack(event.isEnabled, event.onApplied)
            }
        }
    }

    private fun showDialog(value: AppearancePreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(themeConfig = themeConfig)
            }
            AppThemeModeManager.applyToCurrent(
                context = context,
                mode = themeConfig.toAppThemeMode(),
            )
        }
    }

    private fun updateAppLanguage(languageTag: String) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(appLanguage = languageTag)
            }
            AppLanguageManager.applyToCurrent(languageTag)
        }
    }

    private fun toggleUseDynamicColors() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldUseDynamicColors = !it.shouldUseDynamicColors)
            }
        }
    }

    private fun toggleNavigateHomeOnTitleLongPress() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    shouldNavigateHomeOnTitleLongPress = !it.shouldNavigateHomeOnTitleLongPress,
                )
            }
        }
    }

    private fun updateNavigationBarStyle(style: NavigationBarStyle) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(navigationBarStyle = style)
            }
        }
    }

    private fun updateNavigationBarLabelMode(mode: NavigationBarLabelMode) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(navigationBarLabelMode = mode)
            }
        }
    }

    private fun toggleBlurNavigationBar() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    shouldBlurNavigationBar = !it.shouldBlurNavigationBar,
                )
            }
        }
    }

    private fun toggleBlurTopBar() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldBlurTopBar = !it.shouldBlurTopBar)
            }
        }
    }

    private fun toggleShowCloudTab() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldShowCloudTab = !it.shouldShowCloudTab)
            }
        }
    }

    private fun toggleEnablePredictiveBack(
        isEnabled: Boolean,
        onApplied: () -> Unit,
    ) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldEnablePredictiveBack = isEnabled)
            }
            onApplied()
        }
    }
}

@Stable
data class AppearancePreferencesUiState(
    val showDialog: AppearancePreferenceDialog? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

private fun ThemeConfig.toAppThemeMode(): AppThemeMode = when (this) {
    ThemeConfig.SYSTEM -> AppThemeMode.FOLLOW_SYSTEM
    ThemeConfig.OFF -> AppThemeMode.LIGHT
    ThemeConfig.ON -> AppThemeMode.DARK
}

sealed interface AppearancePreferencesEvent {
    data class ShowDialog(val value: AppearancePreferenceDialog?) : AppearancePreferencesEvent
    data class UpdateThemeConfig(val themeConfig: ThemeConfig) : AppearancePreferencesEvent
    data class UpdateAppLanguage(val languageTag: String) : AppearancePreferencesEvent
    data object ToggleUseDynamicColors : AppearancePreferencesEvent
    data object ToggleNavigateHomeOnTitleLongPress : AppearancePreferencesEvent
    data class UpdateNavigationBarStyle(val style: NavigationBarStyle) : AppearancePreferencesEvent
    data class UpdateNavigationBarLabelMode(val mode: NavigationBarLabelMode) : AppearancePreferencesEvent
    data object ToggleBlurNavigationBar : AppearancePreferencesEvent
    data object ToggleBlurTopBar : AppearancePreferencesEvent
    data object ToggleShowCloudTab : AppearancePreferencesEvent
    data class ToggleEnablePredictiveBack(
        val isEnabled: Boolean,
        val onApplied: () -> Unit,
    ) : AppearancePreferencesEvent
}

sealed interface AppearancePreferenceDialog {
    data object Theme : AppearancePreferenceDialog
    data object AppLanguage : AppearancePreferenceDialog
    data object NavigationBarStyle : AppearancePreferenceDialog
    data object NavigationBarLabelMode : AppearancePreferenceDialog
}
