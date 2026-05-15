package one.next.player.settings.screens.about

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import one.next.player.core.common.Logger
import one.next.player.core.ui.R
import one.next.player.core.ui.components.LogsSelectionContainer
import one.next.player.core.ui.components.NextTopAppBar
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.withBottomFallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    var logs by remember { mutableStateOf(Logger.readLogs()) }

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
                onShareLogsClick = { context.shareLogs() },
                onCopyLogsClick = {
                    clipboard.nativeClipboard.setPrimaryClip(
                        ClipData.newPlainText(null, logs),
                    )
                    Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
                },
                onClearLogsClick = {
                    Logger.clearLogs()
                    logs = Logger.readLogs()
                    Toast.makeText(context, context.getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
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
            LogsSelectionContainer(logs = logs.ifBlank { stringResource(R.string.no_logs) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LogsBottomBar(
    hasLogs: Boolean,
    onShareLogsClick: () -> Unit,
    onCopyLogsClick: () -> Unit,
    onClearLogsClick: () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = Dp.Hairline.value,
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = onShareLogsClick,
                enabled = hasLogs,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.share_logs))
            }
            FilledIconButton(
                onClick = onCopyLogsClick,
                enabled = hasLogs,
            ) {
                Icon(imageVector = NextIcons.Copy, contentDescription = stringResource(R.string.copy_logs))
            }
        }
        OutlinedButton(
            onClick = onClearLogsClick,
            enabled = hasLogs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.clear_logs))
        }
    }
}

private fun Context.shareLogs() {
    val file = Logger.exportFile()
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
