// 参考 Storage Redirect X 的 HyperOS 4 底栏适配（Kindness-Kismet，GPL-3.0-or-later）。

package one.only.player.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.only.player.navigation.RootDestination
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val navigationIconPlaceholder = ImageVector.Builder(
    name = "NavigationIconPlaceholder",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).build()

@Composable
fun HyperOsBottomBar(
    currentRoot: RootDestination,
    destinations: List<RootDestination>,
    backdrop: Backdrop?,
    minimumHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    onTabSelected: (RootDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val unselectedColor = MiuixTheme.colorScheme.onBackground
    val selectedColor = if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else unselectedColor
    val labels = destinations.map { stringResource(it.labelRes) }
    val items = remember(destinations, currentRoot, labels, selectedColor, unselectedColor) {
        destinations.mapIndexed { index, target ->
            val isSelected = currentRoot == target
            GlassNavigationItem(
                icon = navigationIconPlaceholder,
                label = labels[index],
                contentDescription = labels[index],
                badge = {
                    HyperOsNavigationIcon(
                        imageVector = if (isSelected) target.selectedIcon else target.unselectedIcon,
                        tint = if (isSelected) selectedColor else unselectedColor,
                    )
                },
            )
        }
    }
    GlassNavigationBar(
        items = items,
        selectedIndex = destinations.indexOf(currentRoot).coerceAtLeast(0),
        onSelect = { index -> onTabSelected(destinations[index]) },
        backdrop = backdrop,
        modifier = modifier.onSizeChanged { onHeightChange(with(density) { it.height.toDp() }) },
        defaultWindowInsetsPadding = false,
        height = minimumHeight,
    )
}

// beta03 的 ColorProducer 缓存会漏画，使用普通 Icon 并抵消徽标槽位偏移。
@Composable
private fun HyperOsNavigationIcon(
    imageVector: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(GlassNavigationBarDefaults.IconSize)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        x = 12.dp.roundToPx() - placeable.width,
                        y = placeable.height - 14.dp.roundToPx(),
                    )
                }
            },
    )
}
