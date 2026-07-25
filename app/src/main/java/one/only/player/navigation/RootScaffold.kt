package one.only.player.navigation

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import one.only.player.core.ui.R as UiR
import one.only.player.core.ui.extensions.LocalRootBottomBarPadding
import one.only.player.core.ui.extensions.LocalRootMenuScrimSetter
import one.only.player.core.ui.extensions.LocalRootMenuScrimVisible
import one.only.player.feature.videopicker.navigation.CloudHomeRoute
import one.only.player.feature.videopicker.navigation.FavoritesRoute
import one.only.player.feature.videopicker.navigation.MediaPickerRoute
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.folderIdArg
import one.only.player.feature.videopicker.navigation.screenModeArg
import one.only.player.settings.navigation.settingsNavigationRoute
import one.only.player.ui.component.FloatingBottomBar
import one.only.player.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 根 Tab 定义，每项对应一个顶级导航目的地
enum class RootDestination(
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String,
) {
    HOME(UiR.string.tab_home, Icons.Rounded.Home, "root_tab_home"),
    CLOUD(UiR.string.tab_cloud, Icons.Rounded.Cloud, "root_tab_cloud"),
    FAVORITES(UiR.string.tab_favorites, Icons.Rounded.Star, "root_tab_favorites"),
    SETTINGS(UiR.string.tab_settings, Icons.Rounded.Settings, "root_tab_settings"),
}

@Composable
fun RootScaffold(
    currentRoot: RootDestination?,
    onTabSelected: (RootDestination) -> Unit,
    modifier: Modifier = Modifier,
    visibleTabs: List<RootDestination> = RootDestination.entries,
    selectedIndexProvider: () -> Int = {
        visibleTabs.indexOf(currentRoot).coerceAtLeast(0)
    },
    shouldUseFloatingNavigationBar: Boolean = false,
    shouldBlurFloatingNavigationBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shouldShowBar = currentRoot != null
    var isRootMenuScrimVisible by remember { mutableStateOf(false) }
    val setRootMenuScrim = remember {
        { visible: Boolean -> isRootMenuScrimVisible = visible }
    }

    // 内容区底部预留：系统导航栏 + 底栏高度。
    // 即使当前页不显示 tab（如进入文件夹），也按有 tab 预留，保证近期播放 FAB 等与主页同高。
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeight = if (shouldUseFloatingNavigationBar) FLOATING_NAV_BAR_RESERVED_HEIGHT else NAV_BAR_CONTENT_HEIGHT
    val reservedBottom = navigationBarsBottom + navigationBarHeight
    val bottomBarPadding = PaddingValues(bottom = reservedBottom)
    val shouldEnableFloatingBlur = shouldUseFloatingNavigationBar &&
        shouldBlurFloatingNavigationBar &&
        isRenderEffectSupported()
    val floatingBlurBackdrop = if (shouldEnableFloatingBlur) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }

    CompositionLocalProvider(
        LocalRootBottomBarPadding provides bottomBarPadding,
        LocalRootMenuScrimVisible provides isRootMenuScrimVisible,
        LocalRootMenuScrimSetter provides setRootMenuScrim,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (floatingBlurBackdrop != null) Modifier.layerBackdrop(floatingBlurBackdrop) else Modifier),
            ) {
                content()
            }
            // Use plain if to avoid even a one-frame bar flash when nested/hidden.
            if (shouldShowBar) {
                // 全宽父盒：悬浮底栏在子盒内居中。
                // 悬浮胶囊的压暗在 FloatingBottomBar 内做（只盖胶囊），避免全宽遮罩错位/整条变暗。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        RootBottomBar(
                            currentRoot = currentRoot,
                            visibleTabs = visibleTabs,
                            selectedIndexProvider = selectedIndexProvider,
                            shouldUseFloatingNavigationBar = shouldUseFloatingNavigationBar,
                            floatingBlurBackdrop = floatingBlurBackdrop,
                            onTabSelected = onTabSelected,
                        )
                    }
                    // 非悬浮底栏：Miuix 窗口遮罩盖不到底栏，只压暗底栏本身
                    if (!shouldUseFloatingNavigationBar) {
                        AnimatedVisibility(
                            visible = isRootMenuScrimVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(NAV_BAR_CONTENT_HEIGHT)
                                    .background(Color.Black.copy(alpha = 0.36f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootBottomBar(
    currentRoot: RootDestination?,
    visibleTabs: List<RootDestination>,
    selectedIndexProvider: () -> Int,
    shouldUseFloatingNavigationBar: Boolean,
    floatingBlurBackdrop: Backdrop?,
    onTabSelected: (RootDestination) -> Unit,
) {
    if (shouldUseFloatingNavigationBar) {
        FloatingRootBottomBar(
            visibleTabs = visibleTabs,
            selectedIndexProvider = selectedIndexProvider,
            blurBackdrop = floatingBlurBackdrop,
            onTabSelected = onTabSelected,
        )
        return
    }

    NavigationBar(
        color = MiuixTheme.colorScheme.surface,
    ) {
        visibleTabs.forEach { target ->
            RootNavigationBarItem(
                destination = target,
                isSelected = currentRoot == target,
                onClick = { onTabSelected(target) },
            )
        }
    }
}

@Composable
private fun FloatingRootBottomBar(
    visibleTabs: List<RootDestination>,
    selectedIndexProvider: () -> Int,
    blurBackdrop: Backdrop?,
    onTabSelected: (RootDestination) -> Unit,
) {
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // isBlurEnabled 为 false 时 backdrop 不被采样，兜底一个空 backdrop 即可
    val fallbackBackdrop = rememberLayerBackdrop { drawContent() }
    val backdrop = blurBackdrop ?: fallbackBackdrop

    FloatingBottomBar(
        modifier = Modifier.padding(bottom = navigationBarsBottom + 12.dp),
        selectedIndex = selectedIndexProvider,
        onSelected = { index ->
            visibleTabs.getOrNull(index)?.let(onTabSelected)
        },
        backdrop = backdrop,
        tabsCount = visibleTabs.size,
        isBlurEnabled = blurBackdrop != null,
    ) {
        visibleTabs.forEach { target ->
            val label = stringResource(target.labelRes)
            FloatingBottomBarItem(
                onClick = { onTabSelected(target) },
                modifier = Modifier
                    .defaultMinSize(minWidth = 76.dp)
                    .testTag(target.tag),
            ) {
                // 图标恒用 onSurface，选中态由上层 tint 采样药丸表现
                Icon(
                    imageVector = target.icon,
                    contentDescription = label,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = label,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RowScope.RootNavigationBarItem(
    destination: RootDestination,
    isSelected: Boolean,
    itemHeight: androidx.compose.ui.unit.Dp = NAV_BAR_CONTENT_HEIGHT,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) {
        MiuixTheme.colorScheme.onSurfaceContainer
    } else {
        MiuixTheme.colorScheme.onSurfaceContainer.copy(alpha = 0.45f)
    }
    val label = stringResource(destination.labelRes)

    Column(
        modifier = Modifier
            .height(itemHeight)
            .weight(1f)
            .testTag(destination.tag)
            .clickable(
                enabled = !isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// 底栏切 Tab 用 saveState / restoreState 保留各 Tab 独立栈
fun NavHostController.navigateToRoot(destination: RootDestination) {
    val options: NavOptionsBuilder.() -> Unit = {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val builtOptions = navOptions(options)
    when (destination) {
        RootDestination.HOME -> navigate(MediaPickerRoute(), builtOptions)
        RootDestination.CLOUD -> navigate(CloudHomeRoute, builtOptions)
        RootDestination.FAVORITES -> navigate(FavoritesRoute, builtOptions)
        RootDestination.SETTINGS -> navigate(settingsNavigationRoute, builtOptions)
    }
}

// 解析 back stack entry 对应的 root tab 索引，非 root 目的地返回 null
internal fun NavBackStackEntry.rootTabIndex(): Int? {
    val tab = destination.resolveRootTab(arguments) ?: return null
    return RootDestination.entries.indexOf(tab)
}

// 仅当前 destination 为 4 个 root startDestination 时展示底栏
internal fun NavDestination?.resolveRootTab(arguments: Bundle?): RootDestination? {
    val dest = this ?: return null
    return when {
        dest.hasRoute<CloudHomeRoute>() -> RootDestination.CLOUD
        dest.hasRoute<FavoritesRoute>() -> RootDestination.FAVORITES
        dest.route == settingsNavigationRoute -> RootDestination.SETTINGS
        dest.hasRoute<MediaPickerRoute>() -> {
            val folderId = arguments?.getString(folderIdArg)
            val screenMode = arguments?.getString(screenModeArg)
            val isLibraryRoot = folderId == null &&
                (screenMode == null || screenMode == MediaPickerScreenMode.LIBRARY.name)
            if (isLibraryRoot) RootDestination.HOME else null
        }
        else -> null
    }
}

private val NAV_BAR_CONTENT_HEIGHT = 72.dp
private val FLOATING_NAV_BAR_HEIGHT = 64.dp
private val FLOATING_NAV_BAR_RESERVED_HEIGHT = 88.dp
