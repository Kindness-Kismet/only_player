package one.only.player.navigation

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import one.only.player.core.ui.R as UiR
import one.only.player.core.ui.extensions.LocalRootBottomBarPadding
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

@Serializable
data object RootPagerRoute

@Composable
fun RootScaffold(
    rootNavigationState: RootNavigationState,
    modifier: Modifier = Modifier,
    shouldUseFloatingNavigationBar: Boolean = false,
    shouldBlurFloatingNavigationBar: Boolean = true,
    content: @Composable (RootDestination) -> Unit,
) {
    val currentPage = rootNavigationState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        rootNavigationState.syncPage()
    }
    BackHandler(enabled = rootNavigationState.selectedDestination != RootDestination.HOME) {
        rootNavigationState.animateTo(RootDestination.HOME)
    }

    // 内容区底部预留：系统导航栏 + 底栏高度，避免导航栏遮挡内容
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeight = if (shouldUseFloatingNavigationBar) FLOATING_NAV_BAR_RESERVED_HEIGHT else NAV_BAR_CONTENT_HEIGHT
    val bottomBarPadding = PaddingValues(bottom = navigationBarsBottom + navigationBarHeight)
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

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            modifier = Modifier
                .fillMaxSize()
                .then(if (floatingBlurBackdrop != null) Modifier.layerBackdrop(floatingBlurBackdrop) else Modifier),
            state = rootNavigationState.pagerState,
            beyondViewportPageCount = RootDestination.entries.lastIndex,
            key = { page -> RootDestination.entries[page] },
        ) { page ->
            CompositionLocalProvider(LocalRootBottomBarPadding provides bottomBarPadding) {
                content(RootDestination.entries[page])
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            RootBottomBar(
                currentRoot = rootNavigationState.selectedDestination,
                shouldUseFloatingNavigationBar = shouldUseFloatingNavigationBar,
                floatingBlurBackdrop = floatingBlurBackdrop,
                onTabSelected = rootNavigationState::animateTo,
            )
        }
    }
}

@Composable
private fun RootBottomBar(
    currentRoot: RootDestination,
    shouldUseFloatingNavigationBar: Boolean,
    floatingBlurBackdrop: Backdrop?,
    onTabSelected: (RootDestination) -> Unit,
) {
    if (shouldUseFloatingNavigationBar) {
        FloatingRootBottomBar(
            currentRoot = currentRoot,
            blurBackdrop = floatingBlurBackdrop,
            onTabSelected = onTabSelected,
        )
        return
    }

    NavigationBar(
        color = MiuixTheme.colorScheme.surface,
    ) {
        RootDestination.entries.forEach { target ->
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
    currentRoot: RootDestination,
    blurBackdrop: Backdrop?,
    onTabSelected: (RootDestination) -> Unit,
) {
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // isBlurEnabled 为 false 时 backdrop 不被采样，兜底一个空 backdrop 即可
    val fallbackBackdrop = rememberLayerBackdrop { drawContent() }
    val backdrop = blurBackdrop ?: fallbackBackdrop
    val selectedIndex = currentRoot.ordinal

    FloatingBottomBar(
        modifier = Modifier.padding(bottom = navigationBarsBottom + 12.dp),
        selectedIndex = { selectedIndex },
        onSelected = { index -> onTabSelected(RootDestination.entries[index]) },
        backdrop = backdrop,
        tabsCount = RootDestination.entries.size,
        isBlurEnabled = blurBackdrop != null,
    ) {
        RootDestination.entries.forEach { target ->
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

private val NAV_BAR_CONTENT_HEIGHT = 72.dp
private val FLOATING_NAV_BAR_RESERVED_HEIGHT = 88.dp
