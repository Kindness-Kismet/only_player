package one.only.player.core.ui.navigation

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberMiuixNavController(): NavHostController {
    val navigator = remember { MiuixNavigator() }
    return rememberNavController(navigator)
}

@Composable
fun MiuixNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    shouldEnablePredictiveBack: Boolean = false,
    route: KClass<*>? = null,
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    builder: NavGraphBuilder.() -> Unit,
) {
    val graph = remember(navController, startDestination, route, typeMap, builder) {
        navController.createGraph(startDestination, route, typeMap, builder)
    }
    MiuixNavHost(navController, graph, modifier, shouldEnablePredictiveBack)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MiuixNavHost(
    navController: NavHostController,
    graph: NavGraph,
    modifier: Modifier = Modifier,
    shouldEnablePredictiveBack: Boolean = false,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    navController.setViewModelStore(viewModelStoreOwner.viewModelStore)
    navController.graph = graph

    val navigator = navController.navigatorProvider.getNavigator<MiuixNavigator>("miuix")
    val entries by navigator.backStack.collectAsState()
    val saveableStateHolder = rememberSaveableStateHolder()
    val gestureGate = remember { MiuixNavigationGestureGate() }

    DisposableEffect(navController, lifecycleOwner) {
        navController.setLifecycleOwner(lifecycleOwner)
        onDispose {}
    }
    DisposableEffect(navigator) {
        onDispose { navigator.completeTransitions() }
    }

    if (entries.isEmpty()) return

    // 路由和进程恢复由 NavController 管理，Miuix 只持有用于展示的返回栈。
    val displayBackStack = remember(entries) {
        entries.map<NavBackStackEntry, NavKey>(::MiuixBackStackKey).toMutableStateList()
    }
    val swipeDismiss = if (
        shouldEnablePredictiveBack && !WindowInsets.isImeVisible && !gestureGate.isBlocked(entries.last().id)
    ) {
        NavSwipeDirection.LeftToRight
    } else {
        NavSwipeDirection.None
    }

    NavDisplay(
        backStack = displayBackStack,
        modifier = modifier.clipToBounds(),
        onBack = { navController.popBackStack() },
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            dimAmount = 0.5f,
            backdropColor = MiuixTheme.colorScheme.background,
        ),
    ) {
        entry<MiuixBackStackKey>(contentKey = { it.entry.id }, swipeDismiss = swipeDismiss) { key ->
            MiuixEntryContent(
                entry = key.entry,
                navigator = navigator,
                saveableStateHolder = saveableStateHolder,
                gestureGate = gestureGate,
                shouldEnablePredictiveBack = shouldEnablePredictiveBack,
                canNavigateBack = entries.size > 1,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MiuixEntryContent(
    entry: NavBackStackEntry,
    navigator: MiuixNavigator,
    saveableStateHolder: SaveableStateHolder,
    gestureGate: MiuixNavigationGestureGate,
    shouldEnablePredictiveBack: Boolean,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
) {
    val transitionScope = LocalNavTransitionScope.current
    LaunchedEffect(navigator, entry, transitionScope) {
        snapshotFlow { transitionScope.gesture != null }.collect { hasGesture ->
            if (hasGesture && navigator.backStack.value.lastOrNull() == entry) {
                navigator.prepareForBackGesture()
            }
        }
    }
    LaunchedEffect(navigator, entry, transitionScope) {
        snapshotFlow { !transitionScope.isRunning && transitionScope.relativeDepth == 0f }
            .combine(navigator.transitionsInProgress) { isSettled, transitions ->
                isSettled && transitions.isNotEmpty()
            }
            .collect { canComplete ->
                if (
                    canComplete &&
                    !transitionScope.isRunning &&
                    transitionScope.relativeDepth == 0f &&
                    navigator.backStack.value.lastOrNull() == entry
                ) {
                    navigator.completeTransitions()
                }
            }
    }
    DisposableEffect(navigator, entry) {
        onDispose { navigator.completeTransition(entry) }
    }

    entry.LocalOwnersProvider(saveableStateHolder) {
        // 先注册宿主返回，页面内部的返回处理保持更高优先级。
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = !shouldEnablePredictiveBack && canNavigateBack,
            onBackCompleted = onNavigateBack,
        )
        CompositionLocalProvider(
            LocalMiuixEntryGestureGate provides remember(gestureGate, entry.id) {
                MiuixEntryGestureGate(gestureGate, entry.id)
            },
        ) {
            (entry.destination as MiuixNavigator.Destination).content(entry)
        }
    }
}

private data class MiuixBackStackKey(val entry: NavBackStackEntry) : NavKey
