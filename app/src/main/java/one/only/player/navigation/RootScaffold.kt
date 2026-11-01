package one.only.player.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import kotlinx.serialization.Serializable
import one.only.player.core.model.NavigationBarStyle
import one.only.player.core.ui.R as UiR
import one.only.player.core.ui.components.surfaceBlur
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.LocalRootBottomBarPadding
import one.only.player.ui.component.FloatingBottomBar
import one.only.player.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.DividerDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 根 Tab 定义，每项对应一个顶级导航目的地
enum class RootDestination(
    val labelRes: Int,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
    val tag: String,
) {
    HOME(UiR.string.tab_home, AppIcons.HomeLine, AppIcons.HomeFilled, "root_tab_home"),
    CLOUD(UiR.string.tab_cloud, AppIcons.CloudLine, AppIcons.CloudFilled, "root_tab_cloud"),
    FAVORITES(UiR.string.tab_favorites, AppIcons.FavoritesLine, AppIcons.FavoritesFilled, "root_tab_favorites"),
    SETTINGS(UiR.string.tab_settings, AppIcons.SettingsLine, AppIcons.SettingsFilled, "root_tab_settings"),
}

@Serializable
data object RootPagerRoute

// 底栏与分页只渲染可见目的地，隐藏项连页面一起移除
@Composable
fun rememberVisibleRootDestinations(shouldShowCloudTab: Boolean): List<RootDestination> = remember(shouldShowCloudTab) {
    RootDestination.entries.filter { it != RootDestination.CLOUD || shouldShowCloudTab }
}

@Composable
fun RootScaffold(
    rootNavigationState: RootNavigationState,
    modifier: Modifier = Modifier,
    navigationBarStyle: NavigationBarStyle = NavigationBarStyle.STANDARD,
    shouldBlurNavigationBar: Boolean = true,
    shouldShowBottomBar: Boolean = true,
    content: @Composable (RootDestination) -> Unit,
) {
    val destinations = rootNavigationState.destinations
    val currentPage = rootNavigationState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        rootNavigationState.syncPage()
    }
    LaunchedEffect(destinations) {
        rootNavigationState.clampToDestinations()
    }
    if (!shouldShowBottomBar) {
        Box(modifier = modifier.fillMaxSize()) {
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = rootNavigationState.pagerState,
                // 首帧只组合首页，优先显示已缓存的媒体内容。
                beyondViewportPageCount = 0,
                key = { page -> destinations[page] },
            ) { page ->
                content(destinations[page])
            }
        }
        return
    }

    val bottomBarPadding = rememberRootBottomBarPadding(navigationBarStyle)
    val floatingBlurBackdrop = rememberRootBlurBackdrop(
        navigationBarStyle = navigationBarStyle,
        shouldBlurNavigationBar = shouldBlurNavigationBar,
    )

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .then(if (floatingBlurBackdrop != null) Modifier.layerBackdrop(floatingBlurBackdrop) else Modifier),
            state = rootNavigationState.pagerState,
            // 首帧只组合首页，优先显示已缓存的媒体内容。
            beyondViewportPageCount = 0,
            key = { page -> destinations[page] },
        ) { page ->
            CompositionLocalProvider(LocalRootBottomBarPadding provides bottomBarPadding) {
                content(destinations[page])
            }
        }
        RootBottomBar(
            currentRoot = rootNavigationState.selectedDestination,
            destinations = destinations,
            navigationBarStyle = navigationBarStyle,
            floatingBlurBackdrop = floatingBlurBackdrop,
            onTabSelected = rootNavigationState::animateTo,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun rememberRootBottomBarPadding(
    navigationBarStyle: NavigationBarStyle,
): PaddingValues {
    // 内容区底部预留：系统导航栏 + 底栏高度，避免导航栏遮挡内容。
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val captionBarBottom = if (navigationBarStyle == NavigationBarStyle.FLOATING) {
        0.dp
    } else {
        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    }
    val navigationBarHeight = when (navigationBarStyle) {
        NavigationBarStyle.STANDARD -> NAV_BAR_CONTENT_HEIGHT + DividerDefaults.Thickness + captionBarBottom
        NavigationBarStyle.FLOATING -> FLOATING_NAV_BAR_RESERVED_HEIGHT
        NavigationBarStyle.MIUIX -> {
            // 最小高度和底部间距与 Miuix 0.9.4 原生悬浮栏一致。
            val bottomSpacing = if (navigationBarsBottom > 0.dp) 26.dp else 36.dp
            val contentHeight = FloatingNavigationBarDefaults.IconSize + FloatingNavigationBarDefaults.IconPadding * 2
            maxOf(52.dp, contentHeight + captionBarBottom) + bottomSpacing
        }
    }
    return remember(navigationBarsBottom, navigationBarHeight) {
        PaddingValues(bottom = navigationBarsBottom + navigationBarHeight)
    }
}

@Composable
fun rememberRootBlurBackdrop(
    navigationBarStyle: NavigationBarStyle,
    shouldBlurNavigationBar: Boolean,
): LayerBackdrop? {
    if (!navigationBarStyle.canBlurBackground || !shouldBlurNavigationBar || !isRuntimeShaderSupported()) return null

    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun RootBottomBar(
    currentRoot: RootDestination,
    destinations: List<RootDestination>,
    navigationBarStyle: NavigationBarStyle,
    floatingBlurBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onTabSelected: (RootDestination) -> Unit,
) {
    if (navigationBarStyle == NavigationBarStyle.FLOATING) {
        FloatingRootBottomBar(
            currentRoot = currentRoot,
            destinations = destinations,
            blurBackdrop = floatingBlurBackdrop,
            modifier = modifier,
            onTabSelected = onTabSelected,
        )
        return
    }

    if (navigationBarStyle == NavigationBarStyle.MIUIX) {
        MiuixFloatingRootBottomBar(
            currentRoot = currentRoot,
            destinations = destinations,
            modifier = modifier,
            onTabSelected = onTabSelected,
        )
        return
    }

    val isBlurEnabled = floatingBlurBackdrop != null
    val barColor = if (isBlurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface
    NavigationBar(
        modifier = modifier.surfaceBlur(floatingBlurBackdrop),
        color = barColor,
    ) {
        destinations.forEach { target ->
            RootNavigationBarItem(
                destination = target,
                isSelected = currentRoot == target,
                onClick = { onTabSelected(target) },
            )
        }
    }
}

@Composable
private fun MiuixFloatingRootBottomBar(
    currentRoot: RootDestination,
    destinations: List<RootDestination>,
    modifier: Modifier = Modifier,
    onTabSelected: (RootDestination) -> Unit,
) {
    // 原生组件将 modifier 用于内部导航条，外层单独承接底部对齐。
    Box(modifier = modifier.testTag("root_navigation_bar_miuix")) {
        FloatingNavigationBar {
            destinations.forEach { target ->
                FloatingNavigationBarItem(
                    modifier = Modifier.testTag(target.tag),
                    selected = currentRoot == target,
                    onClick = { onTabSelected(target) },
                    icon = if (currentRoot == target) target.selectedIcon else target.unselectedIcon,
                    label = stringResource(target.labelRes),
                )
            }
        }
    }
}

@Composable
private fun FloatingRootBottomBar(
    currentRoot: RootDestination,
    destinations: List<RootDestination>,
    blurBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onTabSelected: (RootDestination) -> Unit,
) {
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // isBlurEnabled 为 false 时 backdrop 不被采样，兜底一个空 backdrop 即可
    val fallbackBackdrop = rememberLayerBackdrop { drawContent() }
    val backdrop = blurBackdrop ?: fallbackBackdrop
    val selectedIndex = destinations.indexOf(currentRoot).coerceAtLeast(0)

    FloatingBottomBar(
        modifier = modifier.padding(bottom = navigationBarsBottom + 12.dp),
        selectedIndex = { selectedIndex },
        onSelected = { index -> destinations.getOrNull(index)?.let(onTabSelected) },
        backdrop = backdrop,
        tabsCount = destinations.size,
        isBlurEnabled = blurBackdrop != null,
    ) {
        destinations.forEach { target ->
            val label = stringResource(target.labelRes)
            FloatingBottomBarItem(
                onClick = { onTabSelected(target) },
                modifier = Modifier
                    .defaultMinSize(minWidth = 76.dp)
                    .testTag(target.tag),
            ) {
                // 图标恒用 onSurface，选中态由上层 tint 采样药丸表现
                Icon(
                    imageVector = if (currentRoot == target) target.selectedIcon else target.unselectedIcon,
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
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
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

private val NAV_BAR_CONTENT_HEIGHT = 72.dp
private val FLOATING_NAV_BAR_RESERVED_HEIGHT = 76.dp
