package one.only.player.navigation

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navOptions
import one.only.player.core.ui.R as UiR
import one.only.player.core.ui.extensions.LocalRootBottomBarPadding
import one.only.player.feature.videopicker.navigation.CloudHomeRoute
import one.only.player.feature.videopicker.navigation.FavoritesRoute
import one.only.player.feature.videopicker.navigation.MediaPickerRoute
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.folderIdArg
import one.only.player.feature.videopicker.navigation.screenModeArg
import one.only.player.settings.navigation.settingsNavigationRoute
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
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
    navController: NavHostController,
    modifier: Modifier = Modifier,
    shouldUseFloatingNavigationBar: Boolean = false,
    shouldBlurFloatingNavigationBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentRoot = destination.resolveRootTab(backStackEntry?.arguments)
    val shouldShowBar = currentRoot != null

    // 内容区底部预留：系统导航栏 + 底栏高度，避免导航栏遮挡内容
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeight = if (shouldUseFloatingNavigationBar) FLOATING_NAV_BAR_RESERVED_HEIGHT else NAV_BAR_CONTENT_HEIGHT
    val reservedBottom = if (shouldShowBar) navigationBarsBottom + navigationBarHeight else 0.dp
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

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (floatingBlurBackdrop != null) Modifier.layerBackdrop(floatingBlurBackdrop) else Modifier),
        ) {
            CompositionLocalProvider(LocalRootBottomBarPadding provides bottomBarPadding) {
                content()
            }
        }
        AnimatedVisibility(
            visible = shouldShowBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            RootBottomBar(
                currentRoot = currentRoot,
                shouldUseFloatingNavigationBar = shouldUseFloatingNavigationBar,
                floatingBlurBackdrop = floatingBlurBackdrop,
                onTabSelected = { navController.navigateToRoot(it) },
            )
        }
    }
}

@Composable
private fun RootBottomBar(
    currentRoot: RootDestination?,
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
    currentRoot: RootDestination?,
    blurBackdrop: Backdrop?,
    onTabSelected: (RootDestination) -> Unit,
) {
    val navigationBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val shape = CircleShape
    val containerColor = if (blurBackdrop != null) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f)
    } else {
        MiuixTheme.colorScheme.surface
    }
    val selectedIndex = RootDestination.entries.indexOf(currentRoot).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = navigationBarsBottom + 12.dp)
            .fillMaxWidth()
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = 0.2f,
                ),
            )
            .then(
                if (blurBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = blurBackdrop,
                        shape = { shape },
                        effects = {
                            with(density) {
                                blur(20.dp.toPx(), 20.dp.toPx())
                            }
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                } else {
                    Modifier.background(
                        color = containerColor,
                        shape = shape,
                    )
                },
            )
            .height(FLOATING_NAV_BAR_HEIGHT)
            .padding(4.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / RootDestination.entries.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                label = "rootFloatingBarIndicator",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RootDestination.entries.forEach { target ->
                    RootNavigationBarItem(
                        destination = target,
                        isSelected = currentRoot == target,
                        itemHeight = FLOATING_NAV_BAR_HEIGHT,
                        onClick = { onTabSelected(target) },
                    )
                }
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

// 仅当前 destination 为 4 个 root startDestination 时展示底栏
private fun NavDestination?.resolveRootTab(arguments: Bundle?): RootDestination? {
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
