package one.only.player.core.common

import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 系统未提供运行时开关预测性返回的公开 API，与 KSU 相同走 ApplicationInfo 隐藏方法。
 * API 34+ 才生效；失败时静默忽略。
 */
object PredictiveBackSupport {
    private const val PREFS_RELATIVE_PATH = "files/datastore/app_preferences.json"
    private val PREDICTIVE_BACK_PATTERN =
        "\"shouldEnablePredictiveBack\"\\s*:\\s*(true|false)".toRegex()

    fun applyFromPersistedPreferences(dataDir: String, applicationInfo: ApplicationInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        setEnabled(applicationInfo, readPersisted(dataDir))
    }

    fun setEnabled(applicationInfo: ApplicationInfo, isEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(applicationInfo, isEnabled)
        }
    }

    private fun readPersisted(dataDir: String): Boolean {
        val preferencesFile = File(dataDir, PREFS_RELATIVE_PATH)
        if (!preferencesFile.exists()) return false
        return runCatching { preferencesFile.readText() }
            .getOrNull()
            ?.let(PREDICTIVE_BACK_PATTERN::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBooleanStrictOrNull()
            ?: false
    }
}
