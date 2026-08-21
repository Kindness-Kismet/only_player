package one.only.player.crash

import android.content.Context
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

internal object StartupWatchdog {

    private val hasStarted = AtomicBoolean(false)
    private val hasCompleted = AtomicBoolean(false)
    private val watchdogThread = AtomicReference<Thread?>()

    fun start(context: Context) {
        if (!hasStarted.compareAndSet(false, true)) return

        val thread = Thread(
            {
                try {
                    Thread.sleep(STARTUP_TIMEOUT_MILLIS)
                } catch (_: InterruptedException) {
                    return@Thread
                }

                if (!hasCompleted.compareAndSet(false, true)) return@Thread

                Log.e(TAG, STARTUP_TIMEOUT_MESSAGE)
                try {
                    CrashScreenLauncher.launch(context, STARTUP_TIMEOUT_MESSAGE)
                } catch (launchException: RuntimeException) {
                    Log.e(TAG, "Unable to launch crash screen", launchException)
                } finally {
                    terminateProcess()
                }
            },
            THREAD_NAME,
        ).apply {
            isDaemon = true
        }
        watchdogThread.set(thread)
        thread.start()
    }

    fun markStartupComplete() {
        if (!hasCompleted.compareAndSet(false, true)) return
        watchdogThread.getAndSet(null)?.interrupt()
    }

    private fun terminateProcess(): Nothing {
        Process.killProcess(Process.myPid())
        exitProcess(EXIT_CODE_TIMEOUT)
    }

    private const val STARTUP_TIMEOUT_MILLIS = 10_000L
    private const val STARTUP_TIMEOUT_MESSAGE =
        "StartupTimeoutException: startup splash screen did not exit within 10 seconds."
    private const val THREAD_NAME = "startup-watchdog"
    private const val TAG = "StartupWatchdog"
    private const val EXIT_CODE_TIMEOUT = 10
}
