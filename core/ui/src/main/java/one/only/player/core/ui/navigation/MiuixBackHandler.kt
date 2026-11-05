package one.only.player.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
fun MiuixBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    MiuixNavigationGestureBlocker(isBlocked = enabled)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = onBack,
    )
}

@Composable
fun MiuixNavigationGestureBlocker(isBlocked: Boolean) {
    val entryGate = LocalMiuixEntryGestureGate.current ?: return
    if (!isBlocked) return

    DisposableEffect(entryGate) {
        entryGate.gate.block(entryGate.entryId)
        onDispose { entryGate.gate.unblock(entryGate.entryId) }
    }
}

internal val LocalMiuixEntryGestureGate = staticCompositionLocalOf<MiuixEntryGestureGate?> { null }

internal data class MiuixEntryGestureGate(
    val gate: MiuixNavigationGestureGate,
    val entryId: String,
)

@Stable
internal class MiuixNavigationGestureGate {
    private val blockers = mutableStateMapOf<String, Int>()

    fun isBlocked(entryId: String): Boolean = (blockers[entryId] ?: 0) > 0

    fun block(entryId: String) {
        blockers[entryId] = (blockers[entryId] ?: 0) + 1
    }

    fun unblock(entryId: String) {
        val remaining = blockers.getValue(entryId) - 1
        if (remaining == 0) {
            blockers.remove(entryId)
        } else {
            blockers[entryId] = remaining
        }
    }
}
