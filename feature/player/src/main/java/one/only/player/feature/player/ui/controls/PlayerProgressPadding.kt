package one.only.player.feature.player.ui.controls

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun playerProgressHorizontalPadding(
    containerHorizontalPadding: Dp,
    trackEdgeInset: Dp,
): PaddingValues {
    val displayCutoutPadding = WindowInsets.displayCutout.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val extraPadding = (30.dp - containerHorizontalPadding - trackEdgeInset).coerceAtLeast(0.dp)
    val hasHorizontalDisplayCutout = displayCutoutPadding.calculateStartPadding(layoutDirection) > 0.dp ||
        displayCutoutPadding.calculateEndPadding(layoutDirection) > 0.dp
    val shouldSkipStartPadding = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        hasHorizontalDisplayCutout
    val startPadding = if (shouldSkipStartPadding) 0.dp else extraPadding
    return PaddingValues(start = startPadding, end = extraPadding)
}
