package one.only.player.settings.screens.about

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.extensions.appIcon
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.LocalTopBarBackdrop
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.components.surfaceBlur
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.screens.about.effect.FlowLightBackground
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutPreferencesScreen(
    onLibrariesClick: () -> Unit,
    onLogsClick: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: AboutPreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentVersionName = remember { context.versionName() }

    FlowLightBackground(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            containerColor = Color.Transparent,
            topBar = {
                // 小标题固定顶栏，透明底透出流光背景
                MiuixSmallTopAppBar(
                    title = stringResource(id = R.string.about_name),
                    modifier = Modifier.surfaceBlur(LocalTopBarBackdrop.current),
                    color = Color.Transparent,
                    navigationIcon = {
                        MiuixIconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .testTag("button_about_back"),
                        ) {
                            MiuixIcon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = stringResource(id = R.string.navigate_up),
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
                    // 小标题顶栏较矮，hero 区需要额外的顶部留白
                    .padding(top = 50.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AboutHero(onLibrariesClick = onLibrariesClick)
                PreferenceGroup {
                    PreferenceItem(
                        title = stringResource(R.string.architecture),
                        description = rememberPrimaryArchitecture(),
                        icon = AppIcons.Decoder,
                        isEnabled = true,
                    )
                    PreferenceItem(
                        title = stringResource(R.string.android_version),
                        description = rememberAndroidVersion(),
                        icon = AppIcons.Update,
                        isEnabled = true,
                    )
                    ClickablePreferenceItem(
                        modifier = Modifier.testTag("item_settings_about_logs"),
                        title = stringResource(R.string.app_logs),
                        description = stringResource(R.string.app_logs_description),
                        icon = AppIcons.BugReport,
                        onClick = onLogsClick,
                    )
                }
                UpdateSection(
                    uiState = uiState,
                    currentVersionName = currentVersionName,
                    onEvent = viewModel::onEvent,
                )
            }
        }
    }
}

@Composable
private fun UpdateSection(
    uiState: AboutPreferencesUiState,
    currentVersionName: String,
    onEvent: (AboutPreferencesUiEvent) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    PreferenceGroup {
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_about_check_updates"),
            title = stringResource(R.string.check_for_updates),
            description = updateStatusText(uiState.updateState),
            icon = AppIcons.Update,
            onClick = {
                when (val state = uiState.updateState) {
                    is UpdateState.UpdateAvailable -> {
                        uriHandler.openUriOrShowToast(state.releaseUrl, context)
                    }
                    UpdateState.Checking -> {}
                    else -> onEvent(AboutPreferencesUiEvent.CheckForUpdates(currentVersionName))
                }
            },
        )
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_about_check_updates_on_startup"),
            title = stringResource(R.string.check_updates_on_startup),
            description = stringResource(R.string.check_updates_on_startup_desc),
            isChecked = uiState.shouldCheckForUpdatesOnStartup,
            onClick = { onEvent(AboutPreferencesUiEvent.ToggleCheckOnStartup) },
        )
    }
}

@Composable
private fun updateStatusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.update_status_idle)
    UpdateState.Checking -> stringResource(R.string.update_status_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_status_up_to_date)
    is UpdateState.UpdateAvailable -> stringResource(R.string.update_status_available, state.latestVersion)
    UpdateState.Error -> stringResource(R.string.update_status_error)
}

@Composable
private fun AboutHero(
    onLibrariesClick: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = remember { context.appVersion() }
    val appIcon = remember { context.appIcon()?.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            appIcon?.let { icon ->
                Image(
                    bitmap = icon,
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier.size(74.dp),
                )
            }
        }
        MiuixText(
            modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
            text = stringResource(id = R.string.app_name),
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
        )
        MiuixText(
            text = appVersion,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutIconButton(
                icon = AppIcons.LibraryBooks,
                contentDescription = stringResource(R.string.libraries),
                testTag = "btn_settings_about_libraries",
                onClick = onLibrariesClick,
            )
            AboutIconButton(
                icon = painterResource(R.drawable.ic_brand_github),
                contentDescription = stringResource(R.string.project_repository),
                testTag = "btn_settings_about_repository",
                onClick = { uriHandler.openUriOrShowToast(PROJECT_REPOSITORY_URL, context) },
            )
            AboutIconButton(
                icon = painterResource(R.drawable.ic_brand_telegram),
                contentDescription = stringResource(R.string.telegram_group),
                testTag = "btn_settings_about_telegram",
                onClick = { uriHandler.openUriOrShowToast(TELEGRAM_GROUP_URL, context) },
            )
        }
    }
}

@Composable
private fun AboutIconButton(
    icon: Painter,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    MiuixIconButton(
        modifier = Modifier.testTag(testTag),
        onClick = onClick,
    ) {
        MiuixIcon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AboutIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    MiuixIconButton(
        modifier = Modifier.testTag(testTag),
        onClick = onClick,
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

private fun Context.appVersion(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return "${packageInfo.versionName} ($versionCode)"
}

private fun Context.versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

@Composable
private fun rememberPrimaryArchitecture(): String = remember {
    Build.SUPPORTED_ABIS.firstOrNull() ?: Build.UNKNOWN
}

@Composable
private fun rememberAndroidVersion(): String = remember {
    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

private const val PROJECT_REPOSITORY_URL = "https://github.com/Kindness-Kismet/only_player"
private const val TELEGRAM_GROUP_URL = "https://t.me/MaterialDesign3"

internal fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri = uri)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
    }
}
