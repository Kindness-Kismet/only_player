package one.only.player.core.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import one.only.player.core.model.AppUpdateInfo
import one.only.player.core.ui.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val language = LocalConfiguration.current.locales[0].language
    val releaseNotes = remember(info.releaseNotes, language) {
        localizedReleaseNotes(info.releaseNotes, language)
    }

    AppDialog(
        modifier = Modifier.testTag("dialog_app_update"),
        title = stringResource(R.string.update_dialog_title, info.latestVersion),
        onDismissRequest = onDismissRequest,
        content = {
            if (releaseNotes.isNotBlank()) {
                Text(
                    text = releaseNotes,
                    modifier = Modifier
                        .heightIn(max = AppDialogDefaults.contentMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .testTag("update_release_notes"),
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_update_confirm"),
                text = stringResource(R.string.update_dialog_confirm),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    try {
                        uriHandler.openUri(info.releaseUrl)
                        onDismissRequest()
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, R.string.error_opening_link, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("btn_update_not_now"),
                text = stringResource(R.string.not_now),
                onClick = onDismissRequest,
            )
        },
    )
}

private fun localizedReleaseNotes(
    body: String,
    language: String,
): String {
    // 发布日志固定为英文、分隔线、中文；测试版的单语日志保留原文。
    val sections = body.split(Regex("""(?m)^---[ \t]*\r?$"""), limit = 2)
    val notes = if (language == "zh") sections.last() else sections.first()
    return notes.trim().lineSequence().joinToString("\n") { line ->
        val text = line.replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1").replace("`", "")
        when {
            text.startsWith("- ") -> "• ${text.removePrefix("- ")}"
            text.startsWith("## ") -> text.removePrefix("## ")
            else -> text
        }
    }
}
