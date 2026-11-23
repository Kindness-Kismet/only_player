package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.only.player.core.model.TopBarStyle
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalGlassTopBar = staticCompositionLocalOf { false }

// 毛玻璃表面：模糊 backdrop 后叠加 surface 蒙层，参数与底栏保持一致
@Composable
fun Modifier.surfaceBlur(
    backdrop: Backdrop?,
    shape: Shape = RectangleShape,
): Modifier {
    if (backdrop == null) return this
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = 25f,
        colors = BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)),
            ),
        ),
    )
}

// TopAppBar 的毛玻璃包装，模糊关闭时行为与 miuix TopAppBar 一致
@Composable
fun AppTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
) {
    val backdrop = LocalTopBarBackdrop.current
    if (LocalTopBarStyle.current == TopBarStyle.HYPER_OS_4) {
        HyperOsTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
        )
        return
    }
    TopAppBar(
        title = title,
        modifier = modifier.surfaceBlur(backdrop),
        color = if (backdrop != null) Color.Transparent else color,
        titlePadding = titlePadding,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

// SmallTopAppBar 的毛玻璃包装
@Composable
fun AppSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backdrop = LocalTopBarBackdrop.current
    if (LocalTopBarStyle.current == TopBarStyle.HYPER_OS_4) {
        HyperOsTopAppBar(
            title = title,
            modifier = modifier,
            isExpandable = false,
            navigationIcon = navigationIcon,
            actions = actions,
        )
        return
    }
    SmallTopAppBar(
        title = title,
        modifier = modifier.surfaceBlur(backdrop),
        color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
private fun HyperOsTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    isExpandable: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
) {
    val backdrop = LocalTopBarBackdrop.current
    CompositionLocalProvider(LocalGlassTopBar provides true) {
        GlassTopAppBar(
            title = title,
            // 保持未滚动时的按钮材质与图标可见，标题折叠仍跟随滚动。
            isContentScrolled = true,
            modifier = modifier,
            expandable = isExpandable,
            backdrop = backdrop,
            progressiveBlur = backdrop != null,
            navigationIcon = navigationIcon,
            actions = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            },
            scrollBehavior = scrollBehavior,
            buttonShadow = null,
        )
    }
}
