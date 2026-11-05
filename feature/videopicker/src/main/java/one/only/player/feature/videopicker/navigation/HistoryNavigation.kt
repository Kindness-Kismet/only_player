package one.only.player.feature.videopicker.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import kotlinx.serialization.Serializable
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.ui.navigation.miuixComposable
import one.only.player.feature.videopicker.screens.history.HistoryRoute as HistoryScreenRoute

@Serializable
data object HistoryRoute

fun NavController.navigateToHistory(navOptions: NavOptions? = null) {
    this.navigate(HistoryRoute, navOptions)
}

fun NavGraphBuilder.historyScreen(
    onNavigateUp: () -> Unit,
    onPlayVideo: (video: Video, playerPreferences: PlayerPreferences) -> Unit,
) {
    miuixComposable<HistoryRoute> {
        HistoryScreenRoute(
            onNavigateUp = onNavigateUp,
            onPlayVideo = onPlayVideo,
        )
    }
}
