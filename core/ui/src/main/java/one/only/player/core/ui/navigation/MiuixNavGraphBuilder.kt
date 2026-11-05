package one.only.player.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import kotlin.reflect.KClass
import kotlin.reflect.KType

fun NavGraphBuilder.miuixComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    val navigator = provider.getNavigator<MiuixNavigator>("miuix")
    destination(
        MiuixDestinationBuilder(navigator, route, content).apply {
            arguments.forEach { argument(it.name, it.argument) }
            deepLinks.forEach(::deepLink)
        },
    )
}

inline fun <reified T : Any> NavGraphBuilder.miuixComposable(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    deepLinks: List<NavDeepLink> = emptyList(),
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    miuixComposable(T::class, typeMap, deepLinks, content)
}

@PublishedApi
internal fun NavGraphBuilder.miuixComposable(
    route: KClass<*>,
    typeMap: Map<KType, NavType<*>>,
    deepLinks: List<NavDeepLink>,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    val navigator = provider.getNavigator<MiuixNavigator>("miuix")
    destination(
        MiuixDestinationBuilder(navigator, route, typeMap, content).apply {
            deepLinks.forEach(::deepLink)
        },
    )
}

private class MiuixDestinationBuilder : NavDestinationBuilder<MiuixNavigator.Destination> {
    private val miuixNavigator: MiuixNavigator
    private val content: @Composable (NavBackStackEntry) -> Unit

    constructor(
        navigator: MiuixNavigator,
        route: String,
        content: @Composable (NavBackStackEntry) -> Unit,
    ) : super(navigator, route) {
        miuixNavigator = navigator
        this.content = content
    }

    constructor(
        navigator: MiuixNavigator,
        route: KClass<*>,
        typeMap: Map<KType, NavType<*>>,
        content: @Composable (NavBackStackEntry) -> Unit,
    ) : super(navigator, route, typeMap) {
        miuixNavigator = navigator
        this.content = content
    }

    override fun instantiateDestination(): MiuixNavigator.Destination = MiuixNavigator.Destination(miuixNavigator, content)
}
