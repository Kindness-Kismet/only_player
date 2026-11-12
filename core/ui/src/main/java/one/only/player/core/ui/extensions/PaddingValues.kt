package one.only.player.core.ui.extensions

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.areNavigationBarsVisible
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Root Tab 底栏留白（悬浮或贴底）由 RootScaffold 注入；非 root 页保持 0
val LocalRootBottomBarPadding = staticCompositionLocalOf { PaddingValues(0.dp) }

private val PageBottomPadding = 12.dp
private val PageBottomPaddingWithoutBars = 20.dp

@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding(),
    start = this.calculateStartPadding(LocalLayoutDirection.current) +
        other.calculateStartPadding(LocalLayoutDirection.current),
    end = this.calculateEndPadding(LocalLayoutDirection.current) +
        other.calculateEndPadding(LocalLayoutDirection.current),
)

@Composable
fun PaddingValues.copy(
    top: Dp = this.calculateTopPadding(),
    bottom: Dp = this.calculateBottomPadding(),
    start: Dp = this.calculateStartPadding(LocalLayoutDirection.current),
    end: Dp = this.calculateEndPadding(LocalLayoutDirection.current),
): PaddingValues = PaddingValues(start, top, end, bottom)

@Composable
fun PaddingValues.subtractBottomPadding(padding: Dp): PaddingValues = copy(
    bottom = maxOf(0.dp, calculateBottomPadding() - padding),
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PaddingValues.withBottomFallback(): PaddingValues {
    val bottomPadding = calculateBottomPadding()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val navigationBarBottomPadding = navigationBarPadding.calculateBottomPadding()
    val rootBottomBarPadding = LocalRootBottomBarPadding.current.calculateBottomPadding()
    // 隐藏导航条的系统模块可能保留可见标记，留白以实际占位为准。
    val hasNavigationBarSpace = WindowInsets.areNavigationBarsVisible &&
        maxOf(
            navigationBarBottomPadding,
            navigationBarPadding.calculateTopPadding(),
            navigationBarPadding.calculateStartPadding(LocalLayoutDirection.current),
            navigationBarPadding.calculateEndPadding(LocalLayoutDirection.current),
        ) > 0.dp
    val pageBottomPadding = if (hasNavigationBarSpace || rootBottomBarPadding > 0.dp) {
        PageBottomPadding
    } else {
        PageBottomPaddingWithoutBars
    }
    return copy(
        bottom = maxOf(bottomPadding, navigationBarBottomPadding, rootBottomBarPadding) + pageBottomPadding,
    )
}
