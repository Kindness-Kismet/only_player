package one.only.player.settings.screens.about

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import one.only.player.core.model.UpdateChannel
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialogHost
import one.only.player.core.ui.components.AppUpdateDialog
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceItem
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.RadioTextButton
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.settings.composables.OptionsDialog
import one.only.player.settings.extensions.name
import one.only.player.settings.screens.about.effect.FlowLightBackground
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.squircle.squircleClip
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
    val isDarkTheme = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val backdrop = if (isRuntimeShaderSupported()) rememberLayerBackdrop() else null
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val contentPadding = WindowInsets.safeDrawing.asPaddingValues().withBottomFallback()

    FlowLightBackground(
        modifier = Modifier.fillMaxSize(),
        isAnimated = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
        isDarkTheme = isDarkTheme,
        backgroundModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(top = AboutLayoutDefaults.ContentTopPadding)
                .padding(horizontal = AboutLayoutDefaults.ContentHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutHero(
                onLibrariesClick = onLibrariesClick,
                versionName = currentVersionName,
                isDarkTheme = isDarkTheme,
                backdrop = backdrop,
            )
            Spacer(Modifier.height(AboutLayoutDefaults.InformationSectionSpacing))
            AboutPanel(
                backdrop = backdrop,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.testTag("about_device_panel"),
            ) {
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
                    icon = AppIcons.BugReport,
                    onClick = onLogsClick,
                )
            }
            Spacer(Modifier.height(AboutLayoutDefaults.CardSpacing))
            UpdateSection(
                uiState = uiState,
                currentVersionName = currentVersionName,
                onEvent = viewModel::onEvent,
                backdrop = backdrop,
                isDarkTheme = isDarkTheme,
            )
        }
        MiuixIconButton(
            onClick = onNavigateUp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(AboutLayoutDefaults.BackButtonInset)
                .size(AboutLayoutDefaults.BackButtonSize)
                .testTag("button_about_back"),
        ) {
            MiuixIcon(
                imageVector = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.navigate_up),
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun AboutPanel(
    backdrop: LayerBackdrop?,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val blendColors = if (isDarkTheme) AboutPanelDark else AboutPanelLight
    val panelModifier = if (backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(16.dp),
            blurRadius = 60f,
            colors = BlurColors(blendColors = blendColors),
        )
    } else {
        Modifier
    }
    PreferenceGroup(
        modifier = modifier.then(panelModifier),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            Color.Transparent,
        ),
        content = content,
    )
}

private val AboutPanelLight = listOf(
    BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
)

private val AboutPanelDark = listOf(
    BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
    BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
)

@Composable
private fun UpdateSection(
    uiState: AboutPreferencesUiState,
    currentVersionName: String,
    onEvent: (AboutPreferencesUiEvent) -> Unit,
    backdrop: LayerBackdrop?,
    isDarkTheme: Boolean,
) {
    val updateChannels = remember { UpdateChannel.entries }

    AboutPanel(
        backdrop = backdrop,
        isDarkTheme = isDarkTheme,
        modifier = Modifier.testTag("about_update_panel"),
    ) {
        ClickablePreferenceItem(
            modifier = Modifier.testTag("item_settings_about_check_updates"),
            title = stringResource(R.string.check_for_updates),
            description = if (uiState.updateState == UpdateState.Idle) null else updateStatusText(uiState.updateState),
            isEnabled = uiState.updateState != UpdateState.Checking,
            icon = AppIcons.Update,
            onClick = {
                when (val state = uiState.updateState) {
                    is UpdateState.UpdateAvailable -> {
                        onEvent(AboutPreferencesUiEvent.ShowDialog(AboutPreferenceDialog.Update(state.info)))
                    }
                    UpdateState.Checking -> {}
                    UpdateState.Idle,
                    UpdateState.UpToDate,
                    UpdateState.Error,
                    -> onEvent(AboutPreferencesUiEvent.CheckForUpdates(currentVersionName))
                }
            },
        )
        BasicComponent(
            modifier = Modifier.squircleClip(16.dp).testTag("item_settings_about_update_channel"),
            title = stringResource(R.string.update_channel),
            summary = uiState.updateChannel.name(),
            startAction = {
                MiuixIcon(
                    imageVector = AppIcons.Transfer,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            },
            endActions = {
                DropdownArrowEndAction(actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions)
            },
            holdDownState = uiState.showDialog == AboutPreferenceDialog.UpdateChannel,
            onClick = {
                onEvent(AboutPreferencesUiEvent.ShowDialog(AboutPreferenceDialog.UpdateChannel))
            },
        )
        PreferenceSwitch(
            modifier = Modifier.testTag("switch_settings_about_check_updates_on_startup"),
            title = stringResource(R.string.check_updates_on_startup),
            isChecked = uiState.shouldCheckForUpdatesOnStartup,
            onClick = { onEvent(AboutPreferencesUiEvent.ToggleCheckOnStartup) },
        )
    }

    AppDialogHost(dialog = uiState.showDialog) { dialog ->
        when (dialog) {
            is AboutPreferenceDialog.Update -> AppUpdateDialog(
                info = dialog.info,
                onDismissRequest = { onEvent(AboutPreferencesUiEvent.ShowDialog(null)) },
            )
            AboutPreferenceDialog.UpdateChannel -> {
                OptionsDialog(
                    text = stringResource(R.string.update_channel),
                    onDismissClick = { onEvent(AboutPreferencesUiEvent.ShowDialog(null)) },
                ) {
                    items(updateChannels) { channel ->
                        RadioTextButton(
                            modifier = Modifier.testTag(
                                "option_settings_about_update_channel_${channel.name.lowercase()}",
                            ),
                            text = channel.name(),
                            isSelected = channel == uiState.updateChannel,
                            onClick = {
                                onEvent(AboutPreferencesUiEvent.SetUpdateChannel(channel))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun updateStatusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.update_status_idle)
    UpdateState.Checking -> stringResource(R.string.update_status_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_status_up_to_date)
    is UpdateState.UpdateAvailable -> stringResource(R.string.update_status_available, state.info.latestVersion)
    UpdateState.Error -> stringResource(R.string.update_status_error)
}

@Composable
private fun AboutHero(
    onLibrariesClick: () -> Unit,
    versionName: String,
    isDarkTheme: Boolean,
    backdrop: LayerBackdrop?,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_information"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutHeader(
            foreground = painterResource(R.drawable.ic_launcher_foreground),
            appName = stringResource(R.string.app_name),
            versionName = versionName,
            isDarkTheme = isDarkTheme,
            backdrop = backdrop,
            modifier = Modifier.testTag("about_header"),
        )
        Row(
            modifier = Modifier
                .padding(top = AboutLayoutDefaults.HeaderActionsSpacing)
                .testTag("about_quick_actions"),
            horizontalArrangement = Arrangement.spacedBy(AboutLayoutDefaults.QuickActionSpacing),
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
        AboutSupport(
            onClick = { uriHandler.openUriOrShowToast(AFDIAN_SUPPORT_URL, context) },
        )
    }
}

@Composable
private fun AboutSupport(
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val isDarkTheme = colors.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .padding(top = AboutLayoutDefaults.DonationSpacing)
            .testTag("about_support_section"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .testTag("btn_settings_about_support")
                .clip(shape)
                .background(Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.57f))
                .border(1.dp, Color.White.copy(alpha = if (isDarkTheme) 0.21f else 0.79f), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiuixIcon(
                imageVector = AppIcons.Support,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.primary,
            )
            MiuixText(
                modifier = Modifier.weight(1f, fill = false),
                text = stringResource(R.string.support_on_afdian),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            MiuixIcon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.primary.copy(alpha = 0.75f),
            )
        }
        MiuixText(
            modifier = Modifier
                .padding(top = AboutLayoutDefaults.DonationDescriptionSpacing)
                .testTag("about_support_description"),
            text = stringResource(R.string.support_on_afdian_description),
            color = colors.onSurfaceVariantSummary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
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
        modifier = Modifier.size(AboutLayoutDefaults.QuickActionSize).testTag(testTag),
        onClick = onClick,
    ) {
        MiuixIcon(
            painter = icon,
            modifier = Modifier.size(AboutLayoutDefaults.QuickActionIconSize),
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
        modifier = Modifier.size(AboutLayoutDefaults.QuickActionSize).testTag(testTag),
        onClick = onClick,
    ) {
        MiuixIcon(
            imageVector = icon,
            modifier = Modifier.size(AboutLayoutDefaults.QuickActionIconSize),
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
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
private const val AFDIAN_SUPPORT_URL = "https://afdian.com/a/KiritoXD"

internal fun UriHandler.openUriOrShowToast(uri: String, context: Context) {
    try {
        openUri(uri = uri)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
    }
}
