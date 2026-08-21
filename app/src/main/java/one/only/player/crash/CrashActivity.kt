package one.only.player.crash

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.BuildConfig
import one.only.player.MainActivity
import one.only.player.core.ui.R
import one.only.player.core.ui.components.LogsSelectionContainer
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val exceptionString = intent.getStringExtra(CrashScreenLauncher.EXTRA_EXCEPTION).orEmpty()
        var logcat by mutableStateOf("")

        lifecycleScope.launch {
            logcat = collectLogcat()
        }

        setContent {
            OnlyPlayerTheme(
                shouldUseDarkTheme = isSystemInDarkTheme(),
                shouldUseDynamicColor = false,
            ) {
                val clipboard = LocalClipboard.current
                CrashScreen(
                    exceptionString = exceptionString,
                    logcat = logcat,
                    onShareLogsClick = {
                        lifecycleScope.launch {
                            shareLogs(
                                deviceInfo = collectDeviceInfo(),
                                exceptionString = exceptionString,
                                logcat = logcat,
                            )
                        }
                    },
                    onCopyLogsClick = {
                        clipboard.nativeClipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                null,
                                concatLogs(collectDeviceInfo(), exceptionString, logcat),
                            ),
                        )
                    },
                    onRestartClick = {
                        val restartIntent = Intent(this@CrashActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restartIntent)
                        finish()
                    },
                )
            }
        }
    }

    private suspend fun shareLogs(
        deviceInfo: String,
        exceptionString: String,
        logcat: String,
    ) = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "only_player_logs.txt").also {
            if (it.exists()) it.delete()
            it.createNewFile()
        }
        val logs = concatLogs(
            deviceInfo = deviceInfo,
            crashLogs = exceptionString,
            logcat = logcat,
        )
        file.writeText(text = logs)
        val uri = FileProvider.getUriForFile(
            this@CrashActivity,
            "$packageName.crash.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newRawUri(null, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(
            Intent.createChooser(intent, getString(R.string.crash_screen_share)),
        )
    }

    private fun concatLogs(
        deviceInfo: String,
        crashLogs: String? = null,
        logcat: String,
    ): String = StringBuilder().apply {
        appendLine(deviceInfo)
        appendLine()
        if (!crashLogs.isNullOrBlank()) {
            appendLine("-".repeat(50))
            appendLine("Exception:")
            appendLine(crashLogs)
            appendLine()
        }
        appendLine("-".repeat(50))
        appendLine("Logcat:")
        appendLine(logcat)
    }.toString()

    private suspend fun collectLogcat(): String = withContext(Dispatchers.IO) {
        val process = Runtime.getRuntime()
        val reader = BufferedReader(InputStreamReader(process.exec("logcat -d").inputStream))
        val logcat = StringBuilder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            reader.lines().forEach(logcat::appendLine)
        } else {
            reader.readLines().forEach(logcat::appendLine)
        }
        logcat.toString()
    }

    private fun collectDeviceInfo(): String = """
        App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Android version: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})
        Device brand: ${Build.BRAND}
        Device manufacturer: ${Build.MANUFACTURER}
        Device model: ${Build.MODEL} (${Build.DEVICE})
    """.trimIndent()
}

@Composable
private fun CrashScreen(
    modifier: Modifier = Modifier,
    exceptionString: String,
    logcat: String,
    onShareLogsClick: () -> Unit = {},
    onCopyLogsClick: () -> Unit = {},
    onRestartClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.crash_screen_title),
                actions = {
                    IconButton(
                        onClick = onShareLogsClick,
                        modifier = Modifier.testTag("button_crash_share"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Share,
                            contentDescription = stringResource(R.string.crash_screen_share),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onCopyLogsClick,
                        modifier = Modifier.testTag("button_crash_copy"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Copy,
                            contentDescription = stringResource(R.string.crash_screen_copy),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onRestartClick,
                        modifier = Modifier.testTag("button_crash_restart"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Update,
                            contentDescription = stringResource(R.string.crash_screen_restart),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(paddingValues.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_screen_subtitle, stringResource(R.string.app_name)),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.crash_screen_logs_title),
                style = MiuixTheme.textStyles.title3,
            )
            LogsSelectionContainer(logs = exceptionString)
            Text(
                text = stringResource(R.string.crash_screen_logcat),
                style = MiuixTheme.textStyles.title3,
            )
            LogsSelectionContainer(logs = logcat)
        }
    }
}

@Composable
@PreviewLightDark
private fun CrashLogsScreenPreview() {
    OnlyPlayerTheme {
        CrashScreen(
            exceptionString = "Exception message",
            logcat = "Logcat message",
        )
    }
}
