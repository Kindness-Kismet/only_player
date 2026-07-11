package one.only.player

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.AppThemeMode
import one.only.player.core.common.extensions.applyPrivacyProtection
import one.only.player.core.common.extensions.resolvePrivacyPreviewScrim
import one.only.player.core.common.storagePermission
import one.only.player.core.media.services.MediaService
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.ThemeColorSpec
import one.only.player.core.model.ThemeConfig
import one.only.player.core.model.ThemePaletteStyle
import one.only.player.core.ui.R as UiR
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.composables.rememberRuntimePermissionState
import one.only.player.core.ui.theme.DEFAULT_SEED_COLOR
import one.only.player.core.ui.theme.OnlyPlayerTheme
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.videopicker.navigation.MediaPickerRoute
import one.only.player.feature.videopicker.navigation.navigateToRecycleBinScreen
import one.only.player.feature.videopicker.navigation.navigateToSearch
import one.only.player.navigation.DEBUG_ACTION_OPEN_PAGE
import one.only.player.navigation.DEBUG_ACTION_OPEN_PLAYER
import one.only.player.navigation.DEBUG_EXTRA_PAGE
import one.only.player.navigation.DebugPageRoute
import one.only.player.navigation.MediaRootRoute
import one.only.player.navigation.NavigationBarColorEffect
import one.only.player.navigation.RootDestination
import one.only.player.navigation.RootScaffold
import one.only.player.navigation.cloudNavGraph
import one.only.player.navigation.favoritesNavGraph
import one.only.player.navigation.mediaNavGraph
import one.only.player.navigation.navigateToRoot
import one.only.player.navigation.rootTabIndex
import one.only.player.navigation.settingsNavGraph
import one.only.player.settings.navigation.navigateToAboutPreferences
import one.only.player.settings.navigation.navigateToAppearancePreferences
import one.only.player.settings.navigation.navigateToAudioPreferences
import one.only.player.settings.navigation.navigateToDecoderPreferences
import one.only.player.settings.navigation.navigateToFolderPreferencesScreen
import one.only.player.settings.navigation.navigateToGeneralPreferences
import one.only.player.settings.navigation.navigateToGesturePreferences
import one.only.player.settings.navigation.navigateToLibraries
import one.only.player.settings.navigation.navigateToLogs
import one.only.player.settings.navigation.navigateToMediaLibraryPreferencesScreen
import one.only.player.settings.navigation.navigateToPlayerPreferences
import one.only.player.settings.navigation.navigateToPrivacyPreferences
import one.only.player.settings.navigation.navigateToSubtitlePreferences
import one.only.player.settings.navigation.navigateToThumbnailPreferencesScreen
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val AUTO_REFRESH_INTERVAL_MILLIS = 30_000L
        private const val STARTUP_SPLASH_MIN_DURATION_MILLIS = 450L

        // 进程级时间戳，Activity 重建后不会重置，进程死亡后归零触发全量刷新
        @Volatile
        private var lastAutoRefreshAt = 0L
    }

    @Inject
    lateinit var synchronizer: MediaSynchronizer

    @Inject
    lateinit var mediaService: MediaService

    private val viewModel: MainViewModel by viewModels()
    private var pendingDebugPageRoute by mutableStateOf<DebugPageRoute?>(null)
    private var pendingDebugPlayerIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDebugIntent(intent)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val persistedThemeConfig = readPersistedThemeConfig(dataDir = applicationInfo.dataDir)
        val bootstrapShouldUseDynamicColors = readPersistedShouldUseDynamicColors(dataDir = applicationInfo.dataDir)
        val bootstrapTheme = resolveBootstrapTheme(
            themeConfig = persistedThemeConfig,
            isSystemDarkTheme = isSystemDarkTheme(resources.configuration),
        )
        setTheme(resolveBootstrapSplashThemeStyle(shouldUseDarkTheme = bootstrapTheme.shouldUseDarkTheme))
        val splashScreenStartedAt = SystemClock.elapsedRealtime()
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { it.remove() }
        super.onCreate(savedInstanceState)
        val bootstrapShouldHideInRecents = readPersistedHideInRecents(dataDir = applicationInfo.dataDir)
        applyPrivacyProtection(
            shouldPreventScreenshots = viewModel.currentPreferences.shouldPreventScreenshots,
            shouldHideInRecents = viewModel.currentPreferences.shouldHideInRecents,
        )
        mediaService.initialize(this@MainActivity)
        applySystemBars(
            shouldHideInRecents = bootstrapShouldHideInRecents,
            shouldUseDarkTheme = bootstrapTheme.shouldUseDarkTheme,
        )

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        var isStartupSplashReady by mutableStateOf(false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - splashScreenStartedAt < STARTUP_SPLASH_MIN_DURATION_MILLIS
        }
        consumeDebugIntent(intent)

        setContent {
            val shouldUseDarkTheme = shouldUseDarkTheme(
                uiState = uiState,
                bootstrapShouldUseDarkTheme = bootstrapTheme.shouldUseDarkTheme,
            )
            val shouldUseDynamicColor = shouldUseDynamicTheming(
                uiState = uiState,
                bootstrapShouldUseDynamicColors = bootstrapShouldUseDynamicColors,
            )

            val preferences = (uiState as? MainActivityUiState.Success)?.preferences
            val shouldPreventScreenshots = preferences?.shouldPreventScreenshots == true
            val shouldHideInRecents = preferences?.shouldHideInRecents == true
            val shouldShowStartupSplash = uiState == MainActivityUiState.Loading || !isStartupSplashReady

            LaunchedEffect(Unit) {
                delay(STARTUP_SPLASH_MIN_DURATION_MILLIS)
                isStartupSplashReady = true
            }

            LaunchedEffect(shouldPreventScreenshots, shouldHideInRecents) {
                if (preferences == null) return@LaunchedEffect
                this@MainActivity.applyPrivacyProtection(
                    shouldPreventScreenshots = shouldPreventScreenshots,
                    shouldHideInRecents = shouldHideInRecents,
                )
            }

            LaunchedEffect(shouldHideInRecents, shouldUseDarkTheme) {
                applySystemBars(
                    shouldHideInRecents = shouldHideInRecents,
                    shouldUseDarkTheme = shouldUseDarkTheme,
                )
            }

            OnlyPlayerTheme(
                shouldUseDarkTheme = shouldUseDarkTheme,
                shouldUseDynamicColor = shouldUseDynamicColor,
                seedColor = preferences?.themeSeedColor ?: DEFAULT_SEED_COLOR,
                paletteStyle = preferences?.themePaletteStyle ?: ThemePaletteStyle.TONAL_SPOT,
                colorSpec = preferences?.themeColorSpec ?: ThemeColorSpec.SPEC_2025,
            ) {
                if (!shouldShowStartupSplash) {
                    StartupUpdateDialog(viewModel = viewModel)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MiuixTheme.colorScheme.surface,
                ) {
                    if (shouldShowStartupSplash) {
                        StartupSplashScreen()
                    } else {
                        MainAppContent(
                            shouldUseFloatingNavigationBar = preferences?.shouldUseFloatingNavigationBar == true,
                            shouldBlurFloatingNavigationBar = preferences?.shouldBlurFloatingNavigationBar != false,
                            onPermissionGranted = {
                                synchronizer.startSync()
                                lastAutoRefreshAt = SystemClock.elapsedRealtime()
                            },
                            onResumeWithPermission = {
                                val now = SystemClock.elapsedRealtime()
                                if (lastAutoRefreshAt == 0L) {
                                    lastAutoRefreshAt = now
                                } else if (now - lastAutoRefreshAt >= AUTO_REFRESH_INTERVAL_MILLIS) {
                                    lifecycleScope.launch {
                                        synchronizer.refresh()
                                        lastAutoRefreshAt = SystemClock.elapsedRealtime()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun consumeDebugIntent(intent: Intent?) {
        consumeDebugPageRoute(intent)
        consumeDebugPlayerIntent(intent)
    }

    private fun consumeDebugPageRoute(intent: Intent?) {
        if (intent?.action != DEBUG_ACTION_OPEN_PAGE) return

        // Provider 可能先于 Compose 导航树启动，先暂存到首帧后执行。
        pendingDebugPageRoute = DebugPageRoute.from(intent.getStringExtra(DEBUG_EXTRA_PAGE))
    }

    private fun consumeDebugPlayerIntent(intent: Intent?) {
        if (intent?.action != DEBUG_ACTION_OPEN_PLAYER) return
        val uri = intent.data ?: return

        pendingDebugPlayerIntent = Intent(this, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            replaceExtras(intent.extras)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    private fun navigateToDebugPage(
        navController: NavHostController,
        pageRoute: DebugPageRoute,
    ) {
        navController.popBackStack(MediaPickerRoute(), inclusive = false)
        when (pageRoute) {
            DebugPageRoute.HOME -> navController.navigateToRoot(RootDestination.HOME)
            DebugPageRoute.SEARCH -> navController.navigateToSearch()
            DebugPageRoute.RECYCLE_BIN -> navController.navigateToRecycleBinScreen()
            DebugPageRoute.FAVORITES -> navController.navigateToRoot(RootDestination.FAVORITES)
            DebugPageRoute.CLOUD -> navController.navigateToRoot(RootDestination.CLOUD)
            DebugPageRoute.SETTINGS -> navController.navigateToRoot(RootDestination.SETTINGS)
            DebugPageRoute.SETTINGS_APPEARANCE -> navController.navigateToAppearancePreferences()
            DebugPageRoute.SETTINGS_MEDIA_LIBRARY -> navController.navigateToMediaLibraryPreferencesScreen()
            DebugPageRoute.SETTINGS_FOLDERS -> navController.navigateToFolderPreferencesScreen()
            DebugPageRoute.SETTINGS_THUMBNAILS -> navController.navigateToThumbnailPreferencesScreen()
            DebugPageRoute.SETTINGS_PLAYER -> navController.navigateToPlayerPreferences()
            DebugPageRoute.SETTINGS_GESTURES -> navController.navigateToGesturePreferences()
            DebugPageRoute.SETTINGS_DECODER -> navController.navigateToDecoderPreferences()
            DebugPageRoute.SETTINGS_AUDIO -> navController.navigateToAudioPreferences()
            DebugPageRoute.SETTINGS_SUBTITLE -> navController.navigateToSubtitlePreferences()
            DebugPageRoute.SETTINGS_PRIVACY -> navController.navigateToPrivacyPreferences()
            DebugPageRoute.SETTINGS_GENERAL -> navController.navigateToGeneralPreferences()
            DebugPageRoute.SETTINGS_ABOUT -> navController.navigateToAboutPreferences()
            DebugPageRoute.SETTINGS_LIBRARIES -> navController.navigateToLibraries()
            DebugPageRoute.SETTINGS_LOGS -> navController.navigateToLogs()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun MainAppContent(
        shouldUseFloatingNavigationBar: Boolean,
        shouldBlurFloatingNavigationBar: Boolean,
        onPermissionGranted: () -> Unit,
        onResumeWithPermission: () -> Unit,
    ) {
        val storagePermissionState = rememberRuntimePermissionState(permission = storagePermission)

        LifecycleEventEffect(event = Lifecycle.Event.ON_START) {
            storagePermissionState.launchPermissionRequest()
        }

        LaunchedEffect(storagePermissionState.isGranted) {
            if (!storagePermissionState.isGranted) return@LaunchedEffect
            onPermissionGranted()
        }

        LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
            if (!storagePermissionState.isGranted) return@LifecycleEventEffect
            onResumeWithPermission()
        }

        val mainNavController = rememberNavController()
        LaunchedEffect(mainNavController, pendingDebugPageRoute) {
            val pageRoute = pendingDebugPageRoute ?: return@LaunchedEffect
            navigateToDebugPage(mainNavController, pageRoute)
            pendingDebugPageRoute = null
        }
        LaunchedEffect(pendingDebugPlayerIntent) {
            val playerIntent = pendingDebugPlayerIntent ?: return@LaunchedEffect
            pendingDebugPlayerIntent = null
            startActivity(playerIntent)
        }
        NavigationBarColorEffect(
            activity = this@MainActivity,
            color = MiuixTheme.colorScheme.surface,
        )

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTagsAsResourceId = true
                },
            color = MiuixTheme.colorScheme.surface,
        ) {
            RootScaffold(
                navController = mainNavController,
                shouldUseFloatingNavigationBar = shouldUseFloatingNavigationBar,
                shouldBlurFloatingNavigationBar = shouldBlurFloatingNavigationBar,
            ) {
                NavHost(
                    navController = mainNavController,
                    startDestination = MediaRootRoute,
                    enterTransition = {
                        // root tab 互切按索引方向决定滑入方向，进入子页恒用 Start
                        val fromTab = initialState.rootTabIndex()
                        val toTab = targetState.rootTabIndex()
                        val towards = if (fromTab != null && toTab != null && toTab < fromTab) {
                            AnimatedContentTransitionScope.SlideDirection.End
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Start
                        }
                        slideIntoContainer(
                            towards = towards,
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearEasing,
                            ),
                        )
                    },
                    exitTransition = {
                        val fromTab = initialState.rootTabIndex()
                        val toTab = targetState.rootTabIndex()
                        val towards = if (fromTab != null && toTab != null && toTab < fromTab) {
                            AnimatedContentTransitionScope.SlideDirection.End
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Start
                        }
                        slideOutOfContainer(
                            towards = towards,
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearEasing,
                            ),
                            targetOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                        )
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearEasing,
                            ),
                            initialOffset = { fullOffset -> (fullOffset * 0.3f).toInt() },
                        )
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearEasing,
                            ),
                        )
                    },
                ) {
                    mediaNavGraph(
                        context = this@MainActivity,
                        navController = mainNavController,
                    )
                    cloudNavGraph(
                        context = this@MainActivity,
                        navController = mainNavController,
                    )
                    favoritesNavGraph(
                        context = this@MainActivity,
                        navController = mainNavController,
                    )
                    settingsNavGraph(navController = mainNavController)
                }
            }
        }
    }

    private fun applySystemBars(
        shouldHideInRecents: Boolean,
        shouldUseDarkTheme: Boolean,
    ) {
        val systemBarScrim = resolvePrivacyPreviewScrim(shouldHideInRecents)
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
}

internal data class BootstrapThemeResolution(
    val shouldUseDarkTheme: Boolean,
)

internal fun resolveBootstrapTheme(
    themeConfig: ThemeConfig,
    isSystemDarkTheme: Boolean,
): BootstrapThemeResolution = when (themeConfig) {
    ThemeConfig.SYSTEM -> BootstrapThemeResolution(shouldUseDarkTheme = isSystemDarkTheme)
    ThemeConfig.OFF -> BootstrapThemeResolution(shouldUseDarkTheme = false)
    ThemeConfig.ON -> BootstrapThemeResolution(shouldUseDarkTheme = true)
}

internal fun ThemeConfig.toAppThemeMode(): AppThemeMode = when (this) {
    ThemeConfig.SYSTEM -> AppThemeMode.FOLLOW_SYSTEM
    ThemeConfig.OFF -> AppThemeMode.LIGHT
    ThemeConfig.ON -> AppThemeMode.DARK
}

internal fun readPersistedThemeConfig(dataDir: String): ThemeConfig {
    val preferencesFile = File(dataDir, "files/datastore/app_preferences.json")
    if (!preferencesFile.exists()) return ThemeConfig.SYSTEM

    val rawConfig = runCatching { preferencesFile.readText() }
        .getOrNull()
        ?.let(THEME_CONFIG_PATTERN::find)
        ?.groupValues
        ?.getOrNull(1)
        ?: return ThemeConfig.SYSTEM

    return ThemeConfig.entries.firstOrNull { it.name == rawConfig } ?: ThemeConfig.SYSTEM
}

internal fun readPersistedHideInRecents(dataDir: String): Boolean {
    val preferencesFile = File(dataDir, "files/datastore/app_preferences.json")
    if (!preferencesFile.exists()) return false

    return runCatching { preferencesFile.readText() }
        .getOrNull()
        ?.let(HIDE_IN_RECENTS_PATTERN::find)
        ?.groupValues
        ?.getOrNull(1)
        ?.toBooleanStrictOrNull()
        ?: false
}

internal fun readPersistedShouldUseDynamicColors(dataDir: String): Boolean {
    val preferencesFile = File(dataDir, "files/datastore/app_preferences.json")
    if (!preferencesFile.exists()) return true

    return runCatching { preferencesFile.readText() }
        .getOrNull()
        ?.let(DYNAMIC_COLORS_PATTERN::find)
        ?.groupValues
        ?.getOrNull(1)
        ?.toBooleanStrictOrNull()
        ?: true
}

private fun resolveBootstrapSplashThemeStyle(shouldUseDarkTheme: Boolean): Int = if (shouldUseDarkTheme) {
    one.only.player.R.style.Theme_OnlyPlayer_Splash_Dark
} else {
    one.only.player.R.style.Theme_OnlyPlayer_Splash_Light
}

private fun isSystemDarkTheme(configuration: Configuration): Boolean = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private val THEME_CONFIG_PATTERN = "\"themeConfig\"\\s*:\\s*\"([A-Z_]+)\"".toRegex()
private val HIDE_IN_RECENTS_PATTERN = "\"shouldHideInRecents\"\\s*:\\s*(true|false)".toRegex()
private val DYNAMIC_COLORS_PATTERN = "\"shouldUseDynamicColors\"\\s*:\\s*(true|false)".toRegex()

@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState,
    bootstrapShouldUseDarkTheme: Boolean? = null,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> bootstrapShouldUseDarkTheme ?: isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.preferences.themeConfig) {
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
        ThemeConfig.OFF -> false
        ThemeConfig.ON -> true
    }
}

@Composable
fun shouldUseDynamicTheming(
    uiState: MainActivityUiState,
    bootstrapShouldUseDynamicColors: Boolean = false,
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> bootstrapShouldUseDynamicColors
    is MainActivityUiState.Success -> uiState.preferences.shouldUseDynamicColors
}

@Composable
private fun StartupSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                testTagsAsResourceId = true
            }
            .testTag("startup_splash"),
    )
}

@Composable
private fun StartupUpdateDialog(viewModel: MainViewModel) {
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val info = updateInfo ?: return

    val uriHandler = LocalUriHandler.current

    NextDialog(
        onDismissRequest = { viewModel.dismissUpdate() },
        title = stringResource(UiR.string.update_dialog_title),
        content = { Text(text = stringResource(UiR.string.update_dialog_message, info.latestVersion)) },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_update_confirm"),
                text = stringResource(UiR.string.update_dialog_confirm),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    viewModel.dismissUpdate()
                    try {
                        uriHandler.openUri(info.releaseUrl)
                    } catch (_: Exception) {
                        // 忽略
                    }
                },
            )
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.testTag("btn_update_not_now"),
                text = stringResource(UiR.string.not_now),
                onClick = { viewModel.dismissUpdate() },
            )
        },
    )
}
