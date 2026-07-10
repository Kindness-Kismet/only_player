package one.only.player.crash

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.BuildConfig
import one.only.player.MainActivity
import one.only.player.MainActivityUiState
import one.only.player.MainViewModel
import one.only.player.core.common.extensions.applyPrivacyProtection
import one.only.player.core.common.extensions.resolvePrivacyPreviewScrim
import one.only.player.core.ui.R
import one.only.player.core.ui.components.LogsSelectionContainer
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.navigation.NavigationBarColorEffect
import one.only.player.shouldUseDarkTheme
import one.only.player.shouldUseDynamicTheming
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@AndroidEntryPoint
class CrashActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrivacyProtection(
            shouldPreventScreenshots = viewModel.currentPreferences.shouldPreventScreenshots,
            shouldHideInRecents = viewModel.currentPreferences.shouldHideInRecents,
        )

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        val exceptionString = intent.getStringExtra("exception") ?: ""
        var logcat by mutableStateOf("")

        lifecycleScope.launch {
            logcat = collectLogcat()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        installSplashScreen().setKeepOnScreenCondition {
            when (uiState) {
                MainActivityUiState.Loading -> true
                is MainActivityUiState.Success -> false
            }
        }

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(uiState = uiState)

            val preferences = (uiState as? MainActivityUiState.Success)?.preferences
            val shouldPreventScreenshots = preferences?.shouldPreventScreenshots == true
            val shouldHideInRecents = preferences?.shouldHideInRecents == true

            LaunchedEffect(shouldPreventScreenshots, shouldHideInRecents) {
                if (preferences == null) return@LaunchedEffect
                this@CrashActivity.applyPrivacyProtection(
                    shouldPreventScreenshots = shouldPreventScreenshots,
                    shouldHideInRecents = shouldHideInRecents,
                )
            }

            LaunchedEffect(shouldHideInRecents, shouldUseDarkTheme) {
                val systemBarScrim = this@CrashActivity.resolvePrivacyPreviewScrim(shouldHideInRecents)
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = systemBarScrim,
                        darkScrim = systemBarScrim,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = systemBarScrim,
                        darkScrim = systemBarScrim,
                        detectDarkMode = { shouldUseDarkTheme },
                    ),
                )
            }

            OnlyPlayerTheme(
                shouldUseDarkTheme = shouldUseDarkTheme,
                shouldUseDynamicColor = shouldUseDynamicTheming(uiState = uiState),
            ) {
                NavigationBarColorEffect(
                    activity = this@CrashActivity,
                    color = MiuixTheme.colorScheme.surfaceContainer,
                )
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
                        finish()
                        startActivity(Intent(this@CrashActivity, MainActivity::class.java))
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
            "$packageName.fileprovider",
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
        bottomBar = {
            CrashBottomBar(
                onShareLogsClick = onShareLogsClick,
                onCopyLogsClick = onCopyLogsClick,
                onRestartClick = onRestartClick,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = NextIcons.BugReport,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.crash_screen_title),
                style = MiuixTheme.textStyles.title1,
            )
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CrashBottomBar(
    onShareLogsClick: () -> Unit,
    onCopyLogsClick: () -> Unit,
    onRestartClick: () -> Unit,
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
        CrashActionButton(
            text = stringResource(R.string.crash_screen_share),
            icon = NextIcons.Share,
            onClick = onShareLogsClick,
            modifier = Modifier.weight(1f),
        )
        CrashActionButton(
            text = stringResource(R.string.crash_screen_copy),
            icon = NextIcons.Copy,
            onClick = onCopyLogsClick,
            modifier = Modifier.weight(1f),
        )
        CrashActionButton(
            text = stringResource(R.string.crash_screen_restart),
            icon = NextIcons.Update,
            onClick = onRestartClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CrashActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
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

@PreviewLightDark
@Composable
private fun CrashLogsScreenPreview() {
    OnlyPlayerTheme {
        CrashScreen(
            exceptionString = "Exception message",
            logcat = "Logcat message",
        )
    }
}
