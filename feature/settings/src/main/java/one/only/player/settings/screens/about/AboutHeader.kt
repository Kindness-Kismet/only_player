// SPDX-License-Identifier: GPL-3.0-only
package one.only.player.settings.screens.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

object AboutLayoutDefaults {
    val ContentTopPadding = 144.dp
    val ContentHorizontalPadding = 16.dp
    val HeaderActionsSpacing = 16.dp
    val QuickActionSize = 40.dp
    val QuickActionIconSize = 24.dp
    val QuickActionSpacing = 8.dp
    val DonationSpacing = 12.dp
    val DonationDescriptionSpacing = 8.dp
    val InformationSectionSpacing = 70.dp
    val CardSpacing = 12.dp
    val BackButtonInset = 16.dp
    val BackButtonSize = 40.dp
}

object AboutHeaderDefaults {
    val IconContainerSize = 100.dp
    val ForegroundCanvasSize = 200.dp
    val IconNameSpacing = 12.dp
    val NameVersionSpacing = 5.dp
    val NameFontSize = 35.sp
    val VersionFontSize = 14.sp
    const val ForegroundBlurRadius = 150f
}

@Composable
fun AboutHeader(
    foreground: Painter,
    appName: String,
    versionName: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
    foregroundCanvasSize: Dp = AboutHeaderDefaults.ForegroundCanvasSize,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(AboutHeaderDefaults.IconContainerSize).clipToBounds().testTag("about_icon_slot"),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = foreground,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.onBackground),
                modifier = Modifier.requiredSize(foregroundCanvasSize).aboutForeground(
                    backdrop = backdrop,
                    isDarkTheme = isDarkTheme,
                ),
            )
        }
        Spacer(Modifier.height(AboutHeaderDefaults.IconNameSpacing))
        Text(
            text = appName,
            modifier = Modifier.aboutForeground(
                backdrop = backdrop,
                isDarkTheme = isDarkTheme,
                shape = RoundedCornerShape(16.dp),
            ),
            color = colors.onBackground,
            fontSize = AboutHeaderDefaults.NameFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AboutHeaderDefaults.NameVersionSpacing))
        Text(
            text = "v$versionName",
            color = colors.onSurfaceVariantSummary,
            fontSize = AboutHeaderDefaults.VersionFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.aboutForeground(
    backdrop: LayerBackdrop?,
    isDarkTheme: Boolean,
    shape: Shape = RectangleShape,
): Modifier {
    if (backdrop == null) return this
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = AboutHeaderDefaults.ForegroundBlurRadius,
        colors = BlurColors(blendColors = if (isDarkTheme) LogoDark else LogoLight),
        contentBlendMode = BlendMode.DstIn,
    )
}

private val LogoLight = listOf(
    BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
    BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
)

private val LogoDark = listOf(
    BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
    BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
)
