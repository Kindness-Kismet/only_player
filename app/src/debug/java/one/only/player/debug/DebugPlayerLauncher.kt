package one.only.player.debug

import android.content.Context
import android.content.Intent
import one.only.player.AppForegroundTracker
import one.only.player.MainActivity
import one.only.player.feature.player.PlayerActivity
import one.only.player.navigation.DEBUG_ACTION_OPEN_PLAYER

internal fun Context.startDebugPlayerActivity(playerIntent: Intent) {
    if (AppForegroundTracker.hasResumedActivity) {
        startActivity(
            playerIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        return
    }

    startActivity(
        Intent(this, MainActivity::class.java).apply {
            action = DEBUG_ACTION_OPEN_PLAYER
            data = playerIntent.data
            replaceExtras(playerIntent.extras)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
    )
}

internal fun debugPlayerIntent(
    context: Context,
    configure: Intent.() -> Unit,
): Intent = Intent(context, PlayerActivity::class.java).apply {
    action = Intent.ACTION_VIEW
    configure()
}
