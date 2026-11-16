package one.only.player.crash

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.MainActivity
import one.only.player.core.common.di.DispatchersModule
import one.only.player.core.common.extensions.applyPrivacyProtection
import one.only.player.core.datastore.readPersistedApplicationPreferences
import one.only.player.core.ui.R
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.scaledAppContext
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CrashActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val scale = newBase.readPersistedApplicationPreferences().appScale
        super.attachBaseContext(scaledAppContext(newBase, scale))
    }

    // 恢复进程跳过 Hilt，调度器直接取自不依赖业务图的基础模块。
    private val ioDispatcher = DispatchersModule.providesIODispatcher()
    private val reportStore by lazy { CrashReportStore(this) }
    private var report by mutableStateOf<String?>(null)
    private lateinit var reportLoading: Deferred<String>
    private val saveReportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) saveCrashLog(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPrivacyProtection(
            shouldPreventScreenshots = true,
            shouldHideInRecents = true,
        )
        val shouldUseDarkTheme = isSystemDarkTheme(resources.configuration)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { shouldUseDarkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { shouldUseDarkTheme },
            ),
        )
        val exceptionString = intent.getStringExtra(CRASH_EXCEPTION_EXTRA) ?: ""
        if (savedInstanceState == null) {
            Toast.makeText(
                this,
                R.string.crash_screen_feedback_toast,
                Toast.LENGTH_LONG,
            ).show()
        }
        val clipboardManager = getSystemService(ClipboardManager::class.java)
        reportLoading = lifecycleScope.async(ioDispatcher) {
            reportStore.readReport(
                isSnapshotSaved = intent.getBooleanExtra(CRASH_REPORT_SAVED_EXTRA, false),
                exception = exceptionString,
            )
        }
        lifecycleScope.launch {
            report = reportLoading.await()
        }

        setContent {
            OnlyPlayerTheme(
                shouldUseDarkTheme = shouldUseDarkTheme,
                shouldUseDynamicColor = false,
            ) {
                CrashScreen(
                    exceptionString = report?.take(REPORT_PREVIEW_LENGTH) ?: exceptionString,
                    isReportReady = report != null,
                    onShareCrashLogClick = ::shareCrashLog,
                    onCopyCrashLogClick = {
                        clipboardManager.setPrimaryClip(
                            // 剪贴板只复制异常摘要，完整报告通过文件导出。
                            createSensitiveClipData(exceptionString),
                        )
                    },
                    onSaveCrashLogClick = { saveReportLauncher.launch("only_player_crash.txt") },
                    onRestartClick = {
                        startActivity(
                            Intent(this@CrashActivity, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    private fun shareCrashLog() {
        val content = report ?: return
        lifecycleScope.launch {
            try {
                val file = withContext(ioDispatcher) { reportStore.exportFile(content) }
                val uri = FileProvider.getUriForFile(this@CrashActivity, "$packageName.crashfileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    clipData = ClipData.newRawUri(null, uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.crash_screen_share)))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                Toast.makeText(this@CrashActivity, R.string.logs_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCrashLog(uri: Uri) {
        lifecycleScope.launch {
            val content = reportLoading.await()
            val isSaved = withContext(ioDispatcher) {
                runCatching {
                    val output = contentResolver.openOutputStream(uri) ?: return@withContext false
                    output.use { it.write(content.toByteArray()) }
                }.isSuccess
            }
            Toast.makeText(
                this@CrashActivity,
                if (isSaved) R.string.logs_saved else R.string.logs_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private companion object {
        const val REPORT_PREVIEW_LENGTH = 8 * 1024
    }
}

@Composable
private fun CrashScreen(
    modifier: Modifier = Modifier,
    exceptionString: String,
    isReportReady: Boolean = true,
    onShareCrashLogClick: () -> Unit = {},
    onCopyCrashLogClick: () -> Unit = {},
    onSaveCrashLogClick: () -> Unit = {},
    onRestartClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.crash_screen_title),
                actions = {
                    IconButton(
                        onClick = onShareCrashLogClick,
                        enabled = isReportReady,
                        modifier = Modifier.testTag("button_crash_share"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Share,
                            contentDescription = stringResource(R.string.crash_screen_share),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onSaveCrashLogClick,
                        enabled = isReportReady,
                        modifier = Modifier.testTag("button_crash_save"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Save,
                            contentDescription = stringResource(R.string.save_logs),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onCopyCrashLogClick,
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(if (isReportReady) R.string.crash_screen_report_description else R.string.logs_loading),
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = exceptionString,
                    fontFamily = FontFamily.Monospace,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun CrashLogsScreenPreview() {
    OnlyPlayerTheme {
        CrashScreen(
            exceptionString = "Exception message",
        )
    }
}

private fun createSensitiveClipData(text: String): ClipData = ClipData.newPlainText(null, text).apply {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@apply
    description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
}

private fun isSystemDarkTheme(configuration: Configuration): Boolean {
    val nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightMode == Configuration.UI_MODE_NIGHT_YES
}
