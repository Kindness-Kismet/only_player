package one.only.player.core.ui.extensions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
fun PaddingValues.withBottomFallback(
    fallback: Dp = 24.dp,
): PaddingValues {
    val bottomPadding = calculateBottomPadding()
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val rootBottomBarPadding = LocalRootBottomBarPadding.current.calculateBottomPadding()
    return copy(
        bottom = maxOf(bottomPadding, navigationBarBottomPadding, rootBottomBarPadding) + fallback,
    )
}
