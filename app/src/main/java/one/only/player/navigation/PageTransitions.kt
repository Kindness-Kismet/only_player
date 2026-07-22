package one.only.player.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.navigation.NavBackStackEntry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private const val NAV_TRANSITION_DURATION_MS = 500

@Immutable
private class NavTransitionEasing(
    response: Float,
    damping: Float,
) : Easing {
    private val r: Float
    private val w: Float
    private val c2: Float

    init {
        val omega = 2.0 * PI / response
        val k = omega * omega
        val c = damping * 4.0 * PI / response
        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

private val NavAnimationEasing = NavTransitionEasing(response = 0.8f, damping = 0.95f)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnterTransition(): EnterTransition = slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Start,
    animationSpec = tween(
        durationMillis = NAV_TRANSITION_DURATION_MS,
        easing = NavAnimationEasing,
    ),
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExitTransition(): ExitTransition = slideOutOfContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Start,
    animationSpec = tween(
        durationMillis = NAV_TRANSITION_DURATION_MS,
        easing = NavAnimationEasing,
    ),
    targetOffset = { fullOffset -> fullOffset / 4 },
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopEnterTransition(): EnterTransition = slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.End,
    animationSpec = tween(
        durationMillis = NAV_TRANSITION_DURATION_MS,
        easing = NavAnimationEasing,
    ),
    initialOffset = { fullOffset -> fullOffset / 4 },
)

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopExitTransition(): ExitTransition = slideOutOfContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.End,
    animationSpec = tween(
        durationMillis = NAV_TRANSITION_DURATION_MS,
        easing = NavAnimationEasing,
    ),
)
