package one.only.player.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@Stable
class RootNavigationState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    val selectedDestination: RootDestination
        get() = RootDestination.entries[selectedPage]

    private var navigationJob: Job? = null

    fun animateTo(destination: RootDestination) {
        val targetPage = destination.ordinal
        if (targetPage == selectedPage) return

        navigationJob?.cancel()
        selectedPage = targetPage
        isNavigating = true

        val pageDistance = abs(targetPage - pagerState.currentPage).coerceAtLeast(2)
        val durationMillis = 100 * pageDistance + 100
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distanceInPages = targetPage - pagerState.currentPage - pagerState.currentPageOffsetFraction

        navigationJob = coroutineScope.launch {
            val currentJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = distanceInPages * pageSize,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = EaseInOut,
                    ),
                )
            } finally {
                if (navigationJob == currentJob) {
                    isNavigating = false
                    selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun jumpTo(destination: RootDestination) {
        navigationJob?.cancel()
        navigationJob = null
        isNavigating = false
        selectedPage = destination.ordinal
        pagerState.requestScrollToPage(destination.ordinal)
    }

    fun syncPage() {
        if (isNavigating || selectedPage == pagerState.currentPage) return
        selectedPage = pagerState.currentPage
    }
}

@Composable
fun rememberRootNavigationState(
    initialPage: Int = RootDestination.HOME.ordinal,
): RootNavigationState {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { RootDestination.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        RootNavigationState(
            pagerState = pagerState,
            coroutineScope = coroutineScope,
        )
    }
}
