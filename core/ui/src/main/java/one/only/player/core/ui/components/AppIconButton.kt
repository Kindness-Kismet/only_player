package one.only.player.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isHeldDown: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!LocalGlassTopBar.current) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = isEnabled,
            holdDownState = isHeldDown,
            content = content,
        )
        return
    }

    val heldOverlay = GlassTopAppBarDefaults.buttonPressedOverlay()
    val disabledModifier = if (isEnabled) {
        Modifier
    } else {
        Modifier
            .semantics { disabled() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
    }
    GlassIconButton(
        onClick = { if (isEnabled) onClick() },
        backdrop = LocalTopBarBackdrop.current,
        modifier = modifier
            .then(disabledModifier)
            .graphicsLayer { alpha = if (isEnabled) 1f else 0.38f },
        shadow = null,
    ) {
        Box(
            modifier = Modifier
                .size(GlassTopAppBarDefaults.ButtonSize)
                .drawBehind { if (isHeldDown) drawCircle(heldOverlay) },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
