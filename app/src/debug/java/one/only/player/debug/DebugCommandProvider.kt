package one.only.player.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class DebugCommandProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        if (!isAuthorizedCaller()) {
            return debugResult(
                isOk = false,
                message = "Unauthorized caller",
                command = method,
                target = arg,
            )
        }

        val context = context ?: return debugResult(
            isOk = false,
            message = "Context is not ready",
            command = method,
            target = arg,
        )

        return when (method) {
            METHOD_PAGE_OPEN -> context.openDebugPage(arg)
            METHOD_SETTINGS_SET -> context.runSettingsCommand(method, arg, extras) { setSetting(arg, extras) }
            METHOD_SETTINGS_TOGGLE -> context.runSettingsCommand(method, arg, extras) { toggleSetting(arg) }
            METHOD_SETTINGS_ACTION -> context.runSettingsCommand(method, arg, extras) { runSettingAction(arg) }
            in CLOUD_SERVER_METHODS -> context.runCloudServerCommand(method.removePrefix("cloud.server."), arg, extras)
            in MEDIA_METHODS -> context.runMediaCommand(method.removePrefix("media."), arg, extras)
            in PLAYER_ACTION_METHODS -> context.runPlayerAction(method.removePrefix("player."), arg, extras.withTarget(arg))
            in PLAYER_GET_METHODS -> context.runPlayerGet(method.removePrefix("player."))
            else -> debugResult(
                isOk = false,
                message = "Unknown method: $method",
                command = method,
                target = arg,
            )
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun isAuthorizedCaller(): Boolean {
        val callingUid = Binder.getCallingUid()
        return callingUid == Process.SHELL_UID ||
            callingUid == Process.ROOT_UID ||
            callingUid == Process.myUid()
    }
}
