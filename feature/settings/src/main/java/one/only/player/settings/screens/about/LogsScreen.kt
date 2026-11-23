package one.only.player.settings.screens.about

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.core.common.Logger
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppIconButton as IconButton
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogsScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logPreview by remember { mutableStateOf("") }
    var hasLogs by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val logsSavedMessage = stringResource(R.string.logs_saved)
    val logsSaveFailedMessage = stringResource(R.string.logs_save_failed)
    val logsClearedMessage = stringResource(R.string.logs_cleared)
    val logsClearFailedMessage = stringResource(R.string.logs_clear_failed)
    val saveLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val isSaved = context.saveLogsToUri(uri)
            Toast.makeText(
                context,
                if (isSaved) logsSavedMessage else logsSaveFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(LOG_PREVIEW_LOAD_DELAY_MILLIS)
        logPreview = withContext(Dispatchers.IO) { Logger.readLogPreview() }
        hasLogs = logPreview.isNotBlank()
        isLoading = false
    }

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.app_logs),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_logs_back"),
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { scope.launch { context.shareLogs() } },
                        isEnabled = hasLogs,
                        modifier = Modifier.testTag("button_logs_share"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Share,
                            contentDescription = stringResource(R.string.share_logs),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = { saveLogsLauncher.launch(LOG_EXPORT_FILE_NAME) },
                        isEnabled = hasLogs,
                        modifier = Modifier.testTag("button_logs_save"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Save,
                            contentDescription = stringResource(R.string.save_logs),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val isCleared = withContext(Dispatchers.IO) { Logger.clearLogs() }
                                if (isCleared) {
                                    logPreview = ""
                                    hasLogs = false
                                }
                                Toast.makeText(
                                    context,
                                    if (isCleared) logsClearedMessage else logsClearFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        isEnabled = hasLogs,
                        modifier = Modifier.testTag("button_logs_clear"),
                    ) {
                        Icon(
                            imageVector = AppIcons.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_logs),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                isLoading -> Text(text = stringResource(R.string.logs_loading))
                !hasLogs -> Text(text = stringResource(R.string.no_logs))
                else -> logPreview.toLogEntries().forEach { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = entry,
                            fontFamily = FontFamily.Monospace,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun String.toLogEntries(): List<String> {
    val entries = mutableListOf<String>()
    val entryHeader = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} [VDIWEF]/")
    for (line in lines()) {
        if (entryHeader.containsMatchIn(line)) {
            entries += line
            continue
        }
        if (entries.isEmpty()) entries += line else entries[entries.lastIndex] += "\\n$line"
    }
    return entries
}

private suspend fun Context.shareLogs() {
    val file = withContext(Dispatchers.IO) { Logger.exportFile() }
    if (file == null) {
        Toast.makeText(this, getString(R.string.logs_share_failed), Toast.LENGTH_SHORT).show()
        return
    }

    val uri = FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        clipData = ClipData.newRawUri(null, uri)
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(R.string.share_logs)))
    } catch (_: Exception) {
        Toast.makeText(this, getString(R.string.logs_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun Context.saveLogsToUri(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
    val logs = Logger.readLogs()
    runCatching {
        contentResolver.openOutputStream(uri)?.use { output ->
            output.write(logs.toByteArray())
        } ?: return@withContext false
    }.isSuccess
}

private const val LOG_EXPORT_FILE_NAME = "only_player_logs.txt"
private const val LOG_PREVIEW_LOAD_DELAY_MILLIS = 350L
