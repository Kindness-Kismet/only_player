package one.only.player.feature.player.service.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import one.only.player.core.common.Logger
import one.only.player.core.ui.R as coreUiR
import one.only.player.feature.player.PlayerActivity

/**
 * Non-MediaStyle foreground notification for mediaPlayback FGS requirement.
 * CATEGORY_SERVICE / no session token so system media center does not treat it as a media card.
 */
class PlaybackForegroundNotifier(
    private val service: Service,
) {
    fun publish(startInForegroundRequired: Boolean) {
        // 仅在系统强制 FGS 时发前台通知；非强制路径不再 notify 常驻条，
        // 避免 MIUI 等把 CATEGORY_SERVICE 条当成系统媒体（也不 cancel，以免打断已有 FGS）。
        if (!startInForegroundRequired) return
        val notification = buildNotification()
        runCatching {
            ServiceCompat.startForeground(
                service,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        }.onFailure { error ->
            Logger.error(TAG, "Failed to publish playback foreground notification", error)
        }
    }

    fun cancel() {
        runCatching {
            service.getSystemService(NotificationManager::class.java)
                ?.cancel(NOTIFICATION_ID)
        }
    }

    private fun ensureChannel(): String {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = service.getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    service.getString(coreUiR.string.notification_channel_player_name),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = service.getString(coreUiR.string.notification_channel_player_description)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                nm?.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        val notification = NotificationCompat.Builder(service, channelId)
            .setContentTitle(service.getString(coreUiR.string.app_name))
            .setContentText(service.getString(coreUiR.string.playing_in_background))
            .setSmallIcon(coreUiR.drawable.ic_play)
            .setOngoing(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(
                PendingIntent.getActivity(
                    service,
                    0,
                    Intent(service, PlayerActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        // Oplus isMediaNotification 看 extras token；FGS 条绝不带 media session
        notification.extras?.apply {
            remove("android.mediaSession")
            remove("android.mediaSessionCompat")
            remove(Notification.EXTRA_MEDIA_SESSION)
        }
        return notification
    }

    companion object {
        private const val TAG = "PlaybackForegroundNotifier"
        const val NOTIFICATION_ID = 0x4F504C59
        private const val CHANNEL_ID = "player_playback_foreground"
    }
}
