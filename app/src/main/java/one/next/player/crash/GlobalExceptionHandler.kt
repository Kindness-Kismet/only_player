package one.next.player.crash

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess
import one.next.player.core.common.Logger

class GlobalExceptionHandler(
    private val context: Context,
    private val activity: Class<*>,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        Logger.error(TAG, "Uncaught exception on ${t.name}", e)
        val intent = Intent(context, activity)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra("exception", e.stackTraceToString())
        context.startActivity(intent)
        exitProcess(0)
    }

    private companion object {
        const val TAG = "GlobalExceptionHandler"
    }
}
