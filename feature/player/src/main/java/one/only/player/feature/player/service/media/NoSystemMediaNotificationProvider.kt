package one.only.player.feature.player.service.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import one.only.player.core.ui.R as coreUiR
import one.only.player.feature.player.PlayerActivity

/**
 * Media3 notification provider that never attaches a MediaStyle / session token.
 * Registered so Media3 internal paths that consult the provider still get a plain
 * CATEGORY_SERVICE notification instead of a system media card.
 *
 * PlayerService still never calls super.onUpdateNotification; this is a belt-and-
 * suspenders fallback if Media3 notifies through another path.
 */
@UnstableApi
class NoSystemMediaNotificationProvider(
    private val context: Context,
) : MediaNotification.Provider {

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        ensureChannel()
        // Explicitly avoid MediaStyleNotificationHelper / android.mediaSession extras.
        // Oplus SystemUI isMediaNotification 读 extras 里的 token 才会进 QS 媒体卡。
        val extras = Bundle().apply {
            remove("android.mediaSession")
            remove("android.mediaSessionCompat")
            remove(Notification.EXTRA_MEDIA_SESSION)
        }
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(coreUiR.string.app_name))
            .setContentText(context.getString(coreUiR.string.playing_in_background))
            .setSmallIcon(coreUiR.drawable.ic_play)
            .setOngoing(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setExtras(extras)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, PlayerActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        // 再保险：构建后若 OEM 注入 token，去掉
        notification.extras?.apply {
            remove("android.mediaSession")
            remove("android.mediaSessionCompat")
            remove(Notification.EXTRA_MEDIA_SESSION)
        }
        return MediaNotification(PlaybackForegroundNotifier.NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        return MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            context.getString(coreUiR.string.notification_channel_player_name),
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(coreUiR.string.notification_channel_player_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(coreUiR.string.notification_channel_player_description)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "player_playback_foreground"
    }
}
