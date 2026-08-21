package one.only.player

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import one.only.player.core.common.AppThemeModeManager
import one.only.player.core.common.Logger
import one.only.player.core.common.PredictiveBackSupport
import one.only.player.crash.GlobalExceptionHandler

@HiltAndroidApp
class OnlyPlayerApplication :
    Application(),
    SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (Application.getProcessName() == base.packageName) {
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(base))
        }
    }

    override fun onCreate() {
        val processName = Application.getProcessName()
        if (processName == "$packageName$CRASH_PROCESS_SUFFIX") {
            // Keep the crash process independent from Hilt and main-process startup.
            return
        }

        val isMainProcess = processName == packageName
        super.onCreate()
        if (!isMainProcess) return

        AppForegroundTracker.register(this)
        val startupPreferences = StartupPreferencesCache.initialize(context = this)
        AppThemeModeManager.applyPlatformToCurrent(
            context = applicationContext,
            mode = startupPreferences.themeConfig.toAppThemeMode(),
        )
        PredictiveBackSupport.setEnabled(
            applicationInfo = applicationInfo,
            isEnabled = startupPreferences.shouldEnablePredictiveBack,
        )
        Logger.initialize(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    private companion object {
        const val CRASH_PROCESS_SUFFIX = ":crash"
    }
}
