package one.only.player.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry

// Nested screens: enter from right, exit to left (matches Android predictive back).
private const val NESTED_TRANSITION_MS = 280

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nestedEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(durationMillis = NESTED_TRANSITION_MS, easing = FastOutSlowInEasing),
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nestedExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Left,
        animationSpec = tween(durationMillis = NESTED_TRANSITION_MS, easing = FastOutSlowInEasing),
        targetOffset = { fullOffset -> (fullOffset * 0.25f).toInt() },
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nestedPopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(durationMillis = NESTED_TRANSITION_MS, easing = FastOutSlowInEasing),
        initialOffset = { fullOffset -> (fullOffset * 0.25f).toInt() },
    )

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.nestedPopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(durationMillis = NESTED_TRANSITION_MS, easing = FastOutSlowInEasing),
    )
