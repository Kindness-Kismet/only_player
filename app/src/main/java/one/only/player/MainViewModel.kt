package one.only.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.AppUpdateChecker
import one.only.player.core.data.repository.AppUpdateResult
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.AppUpdateInfo
import one.only.player.core.model.ApplicationPreferences

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {

    private val initialPreferences = preferencesRepository.applicationPreferences.value

    val currentPreferences: ApplicationPreferences
        get() = preferencesRepository.applicationPreferences.value

    val uiState = preferencesRepository.applicationPreferences.map { preferences ->
        MainActivityUiState.Success(preferences)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainActivityUiState.Success(initialPreferences),
    )

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.applicationPreferences.value
            if (!prefs.shouldCheckForUpdatesOnStartup) return@launch
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: return@launch
            // 等首屏画完再查，否则弹窗要新建一个 Window，和启动一起抢主线程会明显掉帧
            delay(STARTUP_CHECK_DELAY_MS)
            // 启动时只在确实有新版本时弹窗，检查失败不打扰
            _updateInfo.value = when (val result = appUpdateChecker.checkForUpdate(versionName, prefs.updateChannel)) {
                is AppUpdateResult.Available -> result.info
                AppUpdateResult.UpToDate, AppUpdateResult.Failed -> null
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.update { null }
    }

    private companion object {
        const val STARTUP_CHECK_DELAY_MS = 2_000L
    }
}

sealed interface MainActivityUiState {
    object Loading : MainActivityUiState
    data class Success(val preferences: ApplicationPreferences) : MainActivityUiState
}
