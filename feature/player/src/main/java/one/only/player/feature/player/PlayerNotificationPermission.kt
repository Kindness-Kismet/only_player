package one.only.player.feature.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

internal class PlayerNotificationPermission(
    private val activity: AppCompatActivity,
) {
    var isSettingsDialogVisible by mutableStateOf(false)
        private set

    private val preferences by lazy {
        activity.getSharedPreferences("player_notification_permission", Context.MODE_PRIVATE)
    }
    private var onContinueInBackground: (() -> Unit)? = null

    private val launcher = activity.registerForActivityResult(RequestPermission()) { isGranted ->
        if (!isGranted && !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            isSettingsDialogVisible = true
        }
    }

    fun requestOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (preferences.getBoolean(HAS_REQUESTED_PERMISSION, false)) return

        // 保存申请记录，避免每次进入播放器都打断播放。
        preferences.edit { putBoolean(HAS_REQUESTED_PERMISSION, true) }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun playInBackground(onContinue: () -> Unit) {
        if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            onContinue()
            return
        }

        onContinueInBackground = onContinue
        isSettingsDialogVisible = true
    }

    fun dismissDialog() {
        isSettingsDialogVisible = false
        val onContinue = onContinueInBackground
        onContinueInBackground = null
        onContinue?.invoke()
    }

    fun openSettings() {
        isSettingsDialogVisible = false
        onContinueInBackground = null
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
        )
    }

    private companion object {
        const val HAS_REQUESTED_PERMISSION = "has_requested_permission"
    }
}
