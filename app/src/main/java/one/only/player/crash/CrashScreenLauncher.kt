package one.only.player.crash

import android.content.Context
import android.content.Intent

internal object CrashScreenLauncher {

    const val EXTRA_EXCEPTION = "exception"

    fun launch(context: Context, exception: String) {
        val intent = Intent(context, CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_EXCEPTION, exception)
        }
        context.startActivity(intent)
    }
}
