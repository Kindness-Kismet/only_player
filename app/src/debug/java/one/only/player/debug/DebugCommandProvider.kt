package one.only.player.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

class DebugCommandProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    @Suppress("DEPRECATION")
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val result = executeCommand(method, arg, extras)
        if (extras?.getBoolean("response_json") != true) return result

        val payload = JSONObject()
        result.keySet().forEach { key -> payload.put(key, JSONObject.wrap(result[key])) }
        return Bundle().apply { putString("response_json", payload.toString()) }
    }

    private fun executeCommand(
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
            METHOD_SETTINGS_SET -> context.runSettingsCommand(method, arg, extras) { setSetting(context, arg, extras) }
            METHOD_SETTINGS_TOGGLE -> context.runSettingsCommand(method, arg, extras) { toggleSetting(arg) }
            METHOD_SETTINGS_ACTION -> context.runSettingsCommand(method, arg, extras) { runSettingAction(context, arg, extras) }
            in CLOUD_SERVER_METHODS -> context.runCloudServerCommand(method.removePrefix("cloud.server."), arg, extras)
            in CLOUD_MEDIA_METHODS -> context.runCloudMediaCommand(method.removePrefix("cloud.media."), arg, extras)
            in CLOUD_QUICK_SETTINGS_METHODS -> context.runCloudQuickSettingsCommand(method.removePrefix("cloud.quick_settings."), arg, extras)
            in QUICK_SETTINGS_METHODS -> context.runQuickSettingsCommand(method.removePrefix("quick_settings."), arg, extras)
            in FAVORITE_METHODS -> context.runFavoriteCommand(method.removePrefix("favorite."), arg, extras)
            in PLAYLIST_METHODS -> context.runPlaylistCommand(method.removePrefix("playlist."), arg, extras)
            in HISTORY_METHODS -> context.runHistoryCommand(method.removePrefix("history."), arg, extras)
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
