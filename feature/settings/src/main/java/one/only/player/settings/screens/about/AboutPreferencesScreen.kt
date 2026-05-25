package one.only.player.settings.screens.about

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.extensions.appIcon
import one.only.player.core.ui.R
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.ListSectionTitle
import one.only.player.core.ui.components.NextTopAppBar
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    LaunchedEffect(uiState.shouldCheckForUpdatesOnStartup) {
        viewModel.maybeAutoCheck(currentVersionName)
    }

    Scaffold(
        topBar = {
            NextTopAppBar(
                title = stringResource(id = R.string.about_name),
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(horizontal = 16.dp),
        ) {
            AboutApp(
                onLibrariesClick = onLibrariesClick,
            )
            DiagnosticsSection(
                onLogsClick = onLogsClick,
            )
            UpdateSection(
                uiState = uiState,
                currentVersionName = currentVersionName,
                onEvent = viewModel::onEvent,
            )
            ListSectionTitle(text = stringResource(id = R.string.device_info))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PreferenceItem(
                    title = stringResource(R.string.architecture),
                    description = rememberDeviceArchitecture(),
                    icon = NextIcons.Decoder,
                    isEnabled = true,
                    isFirstItem = true,
                )
                PreferenceItem(
                    title = stringResource(R.string.android_version),
                    description = rememberAndroidVersion(),
                    icon = NextIcons.Update,
                    isEnabled = true,
                    isLastItem = true,
                )
            }
        }

        StartupUpdateDialog(
            uiState = uiState,
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiagnosticsSection(
    onLogsClick: () -> Unit,
) {
    ListSectionTitle(text = stringResource(id = R.string.diagnostics))
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_about_logs"),
            title = stringResource(R.string.app_logs),
            description = stringResource(R.string.app_logs_description),
            icon = NextIcons.BugReport,
            onClick = onLogsClick,
            isFirstItem = true,
            isLastItem = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSection(
    uiState: AboutPreferencesUiState,
    currentVersionName: String,
    onEvent: (AboutPreferencesUiEvent) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    ListSectionTitle(text = stringResource(id = R.string.update_check))
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_about_check_updates"),
            title = stringResource(R.string.check_for_updates),
            description = updateStatusText(uiState.updateState),
            icon = NextIcons.Update,
            onClick = {
                when (val state = uiState.updateState) {
                    is UpdateState.UpdateAvailable -> {
                        uriHandler.openUriOrShowToast(state.releaseUrl, context)
                    }
                    UpdateState.Checking -> {}
                    else -> onEvent(AboutPreferencesUiEvent.CheckForUpdates(currentVersionName))
                }
            },
            isFirstItem = true,
        )
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_about_check_updates_on_startup"),
            title = stringResource(R.string.check_updates_on_startup),
            description = stringResource(R.string.check_updates_on_startup_desc),
            isChecked = uiState.shouldCheckForUpdatesOnStartup,
            onClick = { onEvent(AboutPreferencesUiEvent.ToggleCheckOnStartup) },
            isLastItem = true,
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
private fun StartupUpdateDialog(
    uiState: AboutPreferencesUiState,
    onEvent: (AboutPreferencesUiEvent) -> Unit,
) {
    val state = uiState.updateState as? UpdateState.UpdateAvailable ?: return
    if (!uiState.shouldShowStartupUpdateDialog) return

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { onEvent(AboutPreferencesUiEvent.DismissStartupUpdateDialog) },
        title = { Text(text = stringResource(R.string.update_dialog_title)) },
        text = { Text(text = stringResource(R.string.update_dialog_message, state.latestVersion)) },
        confirmButton = {
            Button(
                onClick = {
                    onEvent(AboutPreferencesUiEvent.DismissStartupUpdateDialog)
                    uriHandler.openUriOrShowToast(state.releaseUrl, context)
                },
            ) {
                Text(text = stringResource(R.string.update_dialog_confirm))
            }
        },
        dismissButton = {
            Button(
                onClick = { onEvent(AboutPreferencesUiEvent.DismissStartupUpdateDialog) },
            ) {
                Text(text = stringResource(R.string.not_now))
            }
        },
    )
}

@Composable
fun AboutApp(
    modifier: Modifier = Modifier,
    onLibrariesClick: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember { context.appVersion() }
    val appIcon = remember { context.appIcon()?.asImageBitmap() }

    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer

    val transition = rememberInfiniteTransition()
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val cornerRadius = 24.dp

    Column(
        modifier = modifier
            .padding(
                vertical = 16.dp,
                horizontal = 8.dp,
            )
            .drawWithCache {
                val cx = size.width - size.width * fraction
                val cy = size.height * fraction

                val gradient = Brush.radialGradient(
                    colors = listOf(colorPrimary, colorTertiary),
                    center = Offset(cx, cy),
                    radius = 800f,
                )

                onDrawBehind {
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(
                            cornerRadius.toPx(),
                            cornerRadius.toPx(),
                        ),
                    )
                }
            }
            .padding(all = 24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            appIcon?.let {
                Image(
                    bitmap = it,
                    contentDescription = "App Logo",
                    modifier = Modifier.size(56.dp),
                )
            }
            Column {
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_settings_about_libraries"),
            onClick = onLibrariesClick,
            colors = ButtonDefaults.buttonColors(
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .12f),
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(text = stringResource(R.string.libraries))
        }
    }
}

private fun Context.appVersion(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

    return "${packageInfo.versionName} ($versionCode)"
}

private fun Context.versionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

@Composable
private fun rememberDeviceArchitecture(): String = remember {
    Build.SUPPORTED_ABIS.takeIf { it.isNotEmpty() }?.joinToString() ?: Build.UNKNOWN
}

@Composable
private fun rememberAndroidVersion(): String = remember {
    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

internal fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri = uri)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
    }
}
