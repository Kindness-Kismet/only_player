package one.only.player.navigation

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import one.only.player.feature.videopicker.navigation.CloudHomeRoute
import one.only.player.feature.videopicker.navigation.FavoritesRoute
import one.only.player.feature.videopicker.navigation.MediaPickerRoute
import one.only.player.feature.videopicker.navigation.MediaPickerScreenMode
import one.only.player.feature.videopicker.navigation.navigateToMediaPickerScreen
import one.only.player.settings.navigation.settingsNavigationRoute

/**
 * Root tabs as a real [HorizontalPager] (SukiSU-like):
 * - finger drag shows adjacent tab content live
 * - tab click animates across intermediate pages
 * - Cloud / Favorites can be hidden via appearance preferences
 *
 * Each tab owns its own [NavHostController] for nested destinations.
 */
@Composable
fun RootTabPager(
    context: Context,
    modifier: Modifier = Modifier,
    shouldUseFloatingNavigationBar: Boolean = true,
    shouldBlurFloatingNavigationBar: Boolean = true,
    shouldHideCloudTab: Boolean = false,
    shouldHideFavoritesTab: Boolean = false,
    onBindRootController: (RootTabController) -> Unit = {},
) {
    val visibleTabs = remember(shouldHideCloudTab, shouldHideFavoritesTab) {
        RootDestination.entries.filter { destination ->
            when (destination) {
                RootDestination.CLOUD -> !shouldHideCloudTab
                RootDestination.FAVORITES -> !shouldHideFavoritesTab
                RootDestination.HOME, RootDestination.SETTINGS -> true
            }
        }
    }

    val homeNavController = rememberNavController()
    val cloudNavController = rememberNavController()
    val favoritesNavController = rememberNavController()
    val settingsNavController = rememberNavController()
    val allTabNavControllers = remember(
        homeNavController,
        cloudNavController,
        favoritesNavController,
        settingsNavController,
    ) {
        mapOf(
            RootDestination.HOME to homeNavController,
            RootDestination.CLOUD to cloudNavController,
            RootDestination.FAVORITES to favoritesNavController,
            RootDestination.SETTINGS to settingsNavController,
        )
    }

    // 用 destination 稳定 key 维护选中 tab；隐藏/显示时按 destination 映射，避免页码错位闪烁
    var selectedDestination by remember { mutableStateOf(RootDestination.HOME) }
    // 标记是否正在程序化滚动，避免 LaunchedEffect 用 scrollToPage 打断 animateScrollToPage
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { visibleTabs.size.coerceAtLeast(1) },
    )
    val scope = rememberCoroutineScope()

    // 仅在 visibleTabs 结构变化时同步（隐藏/显示 tab），不跟 selectedDestination，避免点 tab 时 snap
    LaunchedEffect(visibleTabs) {
        if (selectedDestination !in visibleTabs && visibleTabs.isNotEmpty()) {
            val fallback = visibleTabs.first()
            selectedDestination = fallback
            val target = 0
            if (pagerState.currentPage != target) {
                pagerState.scrollToPage(target)
            }
            return@LaunchedEffect
        }
        val target = visibleTabs.indexOf(selectedDestination).takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != target) {
            // 结构变化用无动画对齐，避免中间态闪一下
            pagerState.scrollToPage(target)
        }
    }

    // 用户手指滑动结束后同步 selectedDestination
    LaunchedEffect(pagerState, visibleTabs) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, isScrolling) ->
                if (isScrolling) return@collect
                isProgrammaticScroll = false
                val dest = visibleTabs.getOrNull(page) ?: return@collect
                if (dest != selectedDestination) {
                    selectedDestination = dest
                }
            }
    }

    val rootController = remember(pagerState, allTabNavControllers) {
        RootTabController(
            pagerState = pagerState,
            visibleTabs = { visibleTabs },
            allTabNavControllers = allTabNavControllers,
            animateToPage = { page ->
                scope.launch {
                    val max = (visibleTabs.size - 1).coerceAtLeast(0)
                    val clamped = page.coerceIn(0, max)
                    visibleTabs.getOrNull(clamped)?.let { selectedDestination = it }
                    if (pagerState.currentPage == clamped) return@launch
                    isProgrammaticScroll = true
                    pagerState.animateScrollToPage(clamped)
                }
            },
        )
    }

    LaunchedEffect(rootController) {
        onBindRootController(rootController)
    }

    val currentDestination = visibleTabs.getOrNull(pagerState.currentPage)
        ?: selectedDestination.takeIf { it in visibleTabs }
        ?: RootDestination.HOME
    val activeTabNavController = allTabNavControllers.getValue(currentDestination)
    val activeBackStackEntry by activeTabNavController.currentBackStackEntryAsState()
    val isOnRootTab = activeBackStackEntry?.destination.resolveRootTab(activeBackStackEntry?.arguments) != null
    val userScrollEnabled = isOnRootTab && visibleTabs.size > 1
    val displayRoot = if (isOnRootTab) currentDestination else null

    RootScaffold(
        currentRoot = displayRoot,
        visibleTabs = visibleTabs,
        selectedIndexProvider = {
            val max = (visibleTabs.size - 1).coerceAtLeast(0)
            // 程序化多跳 / 任意滚动中：始终用 targetPage，避免中间页因 offset 阈值被点亮
            // 手指拖动跟手：用 currentPage + offset 阈值
            val index = if (isProgrammaticScroll) {
                pagerState.targetPage
            } else if (kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f) {
                val page = pagerState.currentPage
                val offset = pagerState.currentPageOffsetFraction
                if (offset >= 0.5f) page + 1 else if (offset <= -0.5f) page - 1 else page
            } else {
                val byDest = visibleTabs.indexOf(selectedDestination)
                if (byDest >= 0) byDest else pagerState.currentPage
            }
            index.coerceIn(0, max)
        },
        shouldUseFloatingNavigationBar = shouldUseFloatingNavigationBar,
        shouldBlurFloatingNavigationBar = shouldBlurFloatingNavigationBar,
        onTabSelected = { destination ->
            rootController.selectRoot(destination)
        },
        modifier = modifier,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = userScrollEnabled,
            key = { page -> visibleTabs.getOrNull(page)?.name ?: "page_$page" },
        ) { page ->
            val destination = visibleTabs.getOrNull(page) ?: return@HorizontalPager
            when (destination) {
                RootDestination.HOME -> HomeTabNavHost(
                    context = context,
                    navController = homeNavController,
                    rootController = rootController,
                )
                RootDestination.CLOUD -> CloudTabNavHost(
                    context = context,
                    navController = cloudNavController,
                )
                RootDestination.FAVORITES -> FavoritesTabNavHost(
                    context = context,
                    navController = favoritesNavController,
                    homeNavController = homeNavController,
                    rootController = rootController,
                )
                RootDestination.SETTINGS -> SettingsTabNavHost(
                    navController = settingsNavController,
                )
            }
        }
    }
}

class RootTabController internal constructor(
    private val pagerState: PagerState,
    private val visibleTabs: () -> List<RootDestination>,
    private val allTabNavControllers: Map<RootDestination, NavHostController>,
    private val animateToPage: (Int) -> Unit,
) {
    val currentPage: Int
        get() = pagerState.currentPage

    fun selectRoot(destination: RootDestination) {
        val tabs = visibleTabs()
        val page = tabs.indexOf(destination)
        if (page < 0) return

        val nav = allTabNavControllers.getValue(destination)
        when (destination) {
            RootDestination.HOME -> {
                nav.popBackStack(MediaPickerRoute(), inclusive = false)
            }
            RootDestination.CLOUD -> {
                runCatching { nav.popBackStack(CloudHomeRoute, inclusive = false) }
            }
            RootDestination.FAVORITES -> {
                runCatching { nav.popBackStack(FavoritesRoute, inclusive = false) }
            }
            RootDestination.SETTINGS -> {
                runCatching { nav.popBackStack(settingsNavigationRoute, inclusive = false) }
            }
        }
        animateToPage(page)
    }

    fun activeNavController(): NavHostController {
        val tabs = visibleTabs()
        val destination = tabs.getOrNull(pagerState.currentPage) ?: RootDestination.HOME
        return allTabNavControllers.getValue(destination)
    }
}

@Composable
private fun HomeTabNavHost(
    context: Context,
    navController: NavHostController,
    rootController: RootTabController,
) {
    NavHost(
        navController = navController,
        startDestination = MediaRootRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { nestedEnterTransition() },
        exitTransition = { nestedExitTransition() },
        popEnterTransition = { nestedPopEnterTransition() },
        popExitTransition = { nestedPopExitTransition() },
    ) {
        mediaNavGraph(
            context = context,
            navController = navController,
            onCloudClick = { rootController.selectRoot(RootDestination.CLOUD) },
            onFavoritesClick = { rootController.selectRoot(RootDestination.FAVORITES) },
            onSettingsClick = { rootController.selectRoot(RootDestination.SETTINGS) },
        )
    }
}

@Composable
private fun CloudTabNavHost(
    context: Context,
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = CloudRootRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { nestedEnterTransition() },
        exitTransition = { nestedExitTransition() },
        popEnterTransition = { nestedPopEnterTransition() },
        popExitTransition = { nestedPopExitTransition() },
    ) {
        cloudNavGraph(
            context = context,
            navController = navController,
        )
    }
}

@Composable
private fun FavoritesTabNavHost(
    context: Context,
    navController: NavHostController,
    homeNavController: NavHostController,
    rootController: RootTabController,
) {
    NavHost(
        navController = navController,
        startDestination = FavoritesRootRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { nestedEnterTransition() },
        exitTransition = { nestedExitTransition() },
        popEnterTransition = { nestedPopEnterTransition() },
        popExitTransition = { nestedPopExitTransition() },
    ) {
        favoritesNavGraph(
            context = context,
            navController = navController,
            onOpenLocalFolder = { folderPath ->
                homeNavController.navigateToMediaPickerScreen(
                    folderId = folderPath,
                    screenMode = MediaPickerScreenMode.LIBRARY,
                )
                rootController.selectRoot(RootDestination.HOME)
            },
        )
    }
}

@Composable
private fun SettingsTabNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = SETTINGS_ROUTE,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { nestedEnterTransition() },
        exitTransition = { nestedExitTransition() },
        popEnterTransition = { nestedPopEnterTransition() },
        popExitTransition = { nestedPopExitTransition() },
    ) {
        settingsNavGraph(navController = navController)
    }
}
