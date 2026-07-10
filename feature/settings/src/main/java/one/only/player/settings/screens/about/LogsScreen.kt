package one.only.player.settings.screens.about

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.core.common.Logger
import one.only.player.core.ui.R
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.app_logs),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_logs_back"),
                    ) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        bottomBar = {
            LogsBottomBar(
                hasLogs = hasLogs,
                onShareLogsClick = {
                    scope.launch { context.shareLogs() }
                },
                onSaveLogsClick = { saveLogsLauncher.launch(LOG_EXPORT_FILE_NAME) },
                onClearLogsClick = {
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
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = NextIcons.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_logs),
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = stringResource(R.string.app_logs_description),
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = stringResource(R.string.crash_screen_logcat),
                style = MiuixTheme.textStyles.title3,
            )
            LogsTextContainer(
                text = when {
                    isLoading -> stringResource(R.string.logs_loading)
                    !hasLogs -> stringResource(R.string.no_logs)
                    else -> logPreview
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LogsTextContainer(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = LOG_PREVIEW_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun LogsBottomBar(
    hasLogs: Boolean,
    onShareLogsClick: () -> Unit,
    onSaveLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
) {
    val borderColor = MiuixTheme.colorScheme.dividerLine
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = Dp.Hairline.value,
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogActionButton(
            text = stringResource(R.string.share_logs),
            icon = NextIcons.Share,
            isEnabled = hasLogs,
            onClick = onShareLogsClick,
            modifier = Modifier.weight(1f),
        )
        LogActionButton(
            text = stringResource(R.string.save_logs),
            icon = NextIcons.Save,
            isEnabled = hasLogs,
            onClick = onSaveLogsClick,
            modifier = Modifier.weight(1f),
        )
        LogActionButton(
            text = stringResource(R.string.clear_logs),
            icon = NextIcons.DeleteSweep,
            isEnabled = hasLogs,
            onClick = onClearLogsClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogActionButton(
    text: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.button,
            )
        }
    }
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
private const val LOG_PREVIEW_MAX_LINES = 50
