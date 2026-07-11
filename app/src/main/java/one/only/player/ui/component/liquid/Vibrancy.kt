// 改编自 Kyant0/AndroidLiquidGlass：https://github.com/Kyant0/AndroidLiquidGlass（Apache 2.0）。
// 与 compose-miuix-ui 示例保持一致。

package one.only.player.ui.component.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
