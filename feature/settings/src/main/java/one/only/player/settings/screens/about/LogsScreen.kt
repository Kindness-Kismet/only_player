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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
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
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val saveLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val isSaved = context.saveLogsToUri(uri, logs)
            Toast.makeText(
                context,
                context.getString(if (isSaved) R.string.logs_saved else R.string.logs_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        logs = withContext(Dispatchers.IO) { Logger.readLogs() }
        isLoading = false
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.app_logs),
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                        )
                    }
                },
            )
        },
        bottomBar = {
            LogsBottomBar(
                hasLogs = logs.isNotBlank(),
                onShareLogsClick = {
                    scope.launch { context.shareLogs(logs) }
                },
                onSaveLogsClick = { saveLogsLauncher.launch(LOG_EXPORT_FILE_NAME) },
                onClearLogsClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Logger.clearLogs() }
                        logs = ""
                        Toast.makeText(context, context.getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = NextIcons.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_logs),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.app_logs_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.crash_screen_logcat),
                style = MaterialTheme.typography.headlineSmall,
            )
            LogsTextContainer(
                logs = when {
                    isLoading -> stringResource(R.string.logs_loading)
                    logs.isBlank() -> stringResource(R.string.no_logs)
                    else -> logs
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LogsTextContainer(
    logs: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Text(
            text = logs,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
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
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
            icon = { Icon(imageVector = NextIcons.Share, contentDescription = null) },
            enabled = hasLogs,
            onClick = onShareLogsClick,
            modifier = Modifier.weight(1f),
        )
        LogActionButton(
            text = stringResource(R.string.save_logs),
            icon = { Icon(imageVector = NextIcons.Save, contentDescription = null) },
            enabled = hasLogs,
            onClick = onSaveLogsClick,
            modifier = Modifier.weight(1f),
        )
        LogActionButton(
            text = stringResource(R.string.clear_logs),
            icon = { Icon(imageVector = NextIcons.DeleteSweep, contentDescription = null) },
            enabled = hasLogs,
            onClick = onClearLogsClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogActionButton(
    text: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        icon()
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private suspend fun Context.shareLogs(logs: String) {
    val file = withContext(Dispatchers.IO) { Logger.exportFile(logs) }
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

private suspend fun Context.saveLogsToUri(
    uri: android.net.Uri,
    logs: String,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.openOutputStream(uri)?.use { output ->
            output.write(logs.toByteArray())
        } ?: return@withContext false
    }.isSuccess
}

private const val LOG_EXPORT_FILE_NAME = "only_player_logs.txt"
private const val LOG_PREVIEW_MAX_LINES = 300
