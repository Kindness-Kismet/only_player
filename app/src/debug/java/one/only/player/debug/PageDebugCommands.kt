package one.only.player.debug

import android.content.Context
import android.content.Intent
import one.only.player.MainActivity
import one.only.player.navigation.DEBUG_ACTION_OPEN_PAGE
import one.only.player.navigation.DEBUG_EXTRA_PAGE
import one.only.player.navigation.DebugPageRoute

internal fun Context.openDebugPage(pageId: String?) = DebugPageRoute.from(pageId)?.let { pageRoute ->
    val intent = Intent(this, MainActivity::class.java).apply {
        action = DEBUG_ACTION_OPEN_PAGE
        putExtra(DEBUG_EXTRA_PAGE, pageRoute.id)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    startActivity(intent)
    debugResult(
        isOk = true,
        message = "Opening page: ${pageRoute.id}",
        command = METHOD_PAGE_OPEN,
        target = pageRoute.id,
    )
} ?: debugResult(
    isOk = false,
    message = "Unknown page: $pageId",
)
