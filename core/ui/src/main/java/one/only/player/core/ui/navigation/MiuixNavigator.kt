package one.only.player.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import kotlinx.coroutines.flow.StateFlow

@Navigator.Name("miuix")
internal class MiuixNavigator : Navigator<MiuixNavigator.Destination>() {
    val backStack: StateFlow<List<NavBackStackEntry>>
        get() = state.backStack

    val transitionsInProgress: StateFlow<Set<NavBackStackEntry>>
        get() = state.transitionsInProgress

    override fun createDestination(): Destination = Destination(this) {}

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?,
    ) {
        entries.forEach(state::pushWithTransition)
    }

    override fun popBackStack(
        popUpTo: NavBackStackEntry,
        savedState: Boolean,
    ) {
        state.popWithTransition(popUpTo, savedState)
    }

    override fun onLaunchSingleTop(backStackEntry: NavBackStackEntry) {
        state.onLaunchSingleTopWithTransition(backStackEntry)
    }

    fun prepareForBackGesture() {
        backStack.value.takeLast(2).forEach(state::prepareForTransition)
    }

    fun completeTransition(entry: NavBackStackEntry) {
        state.markTransitionComplete(entry)
    }

    fun completeBackStackTransitions() {
        val entries = backStack.value
        // 离栈页面由组合释放回调完成转场，保留退出动画所需的 ViewModel。
        transitionsInProgress.value.forEach { entry ->
            if (entries.any { it === entry }) state.markTransitionComplete(entry)
        }
    }

    fun completeTransitions() {
        state.transitionsInProgress.value.forEach(state::markTransitionComplete)
    }

    class Destination(
        navigator: MiuixNavigator,
        val content: @Composable (NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator)
}
