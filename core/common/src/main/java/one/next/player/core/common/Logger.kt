package one.next.player.core.common

import android.content.Context
import android.util.Log

object Logger {
    private var fileLogStore: FileLogStore? = null

    fun initialize(context: Context) {
        fileLogStore = FileLogStore(context.applicationContext)
    }

    fun debug(tag: String, message: String) {
        runCatching { Log.d("Logger - $tag", message) }
        writeToFile("D", tag, message)
    }

    fun info(tag: String, message: String) {
        runCatching { Log.i("Logger - $tag", message) }
        writeToFile("I", tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        runCatching { Log.e("Logger - $tag", message, throwable) }
        writeToFile("E", tag, message, throwable)
    }

    fun readLogs(): String = runCatching { fileLogStore?.read().orEmpty() }.getOrDefault("")

    fun clearLogs() {
        runCatching { fileLogStore?.clear() }
    }

    fun exportFile() = runCatching { fileLogStore?.exportFile() }.getOrNull()

    private fun writeToFile(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        runCatching {
            fileLogStore?.append(
                level = level,
                tag = tag,
                message = message,
                throwable = throwable,
            )
        }
    }
}
