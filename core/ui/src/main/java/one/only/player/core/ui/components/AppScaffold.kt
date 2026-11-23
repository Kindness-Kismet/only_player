package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import one.only.player.core.model.TopBarStyle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 顶栏模糊总开关，由 app 层根据外观设置 Provide
val LocalTopBarBlur = staticCompositionLocalOf { false }
val LocalTopBarStyle = staticCompositionLocalOf { TopBarStyle.STANDARD }

// 当前页面的顶栏 backdrop，由 AppScaffold 在模糊启用时 Provide
val LocalTopBarBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

// 与底栏一致的毛玻璃 Scaffold：内容捕获 backdrop，顶栏经 AppTopAppBar 采样
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    val shouldBlur = LocalTopBarBlur.current && isRuntimeShaderSupported()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    // 切换模糊时保持组合结构，保留页面滚动与输入状态。
    CompositionLocalProvider(LocalTopBarBackdrop provides backdrop.takeIf { shouldBlur }) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            containerColor = containerColor,
            contentWindowInsets = contentWindowInsets,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (shouldBlur) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                content(innerPadding)
            }
        }
    }
}
