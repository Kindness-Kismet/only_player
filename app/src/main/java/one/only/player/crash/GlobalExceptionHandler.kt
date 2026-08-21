package one.only.player.crash

import android.content.Context
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    private val context: Context,
) : Thread.UncaughtExceptionHandler {

    private val isHandlingException = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!isHandlingException.compareAndSet(false, true)) terminateProcess()

        Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
        try {
            CrashScreenLauncher.launch(context, throwable.stackTraceToString())
        } catch (launchException: RuntimeException) {
            Log.e(TAG, "Unable to launch crash screen", launchException)
        } finally {
            terminateProcess()
        }
    }

    private fun terminateProcess(): Nothing {
        Process.killProcess(Process.myPid())
        exitProcess(EXIT_CODE_CRASH)
    }

    private companion object {
        const val TAG = "GlobalExceptionHandler"
        const val EXIT_CODE_CRASH = 1
    }
}
