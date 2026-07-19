package one.only.player.feature.videopicker.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import one.only.player.core.ui.R

@Composable
fun rememberPullToRefreshTexts(): List<String> {
    val pullHint = stringResource(R.string.pull_to_refresh_hint_pull)
    val releaseHint = stringResource(R.string.pull_to_refresh_hint_release)
    val refreshingHint = stringResource(R.string.pull_to_refresh_hint_refreshing)
    val completeHint = stringResource(R.string.pull_to_refresh_hint_complete)

    return remember(
        pullHint,
        releaseHint,
        refreshingHint,
        completeHint,
    ) {
        listOf(
            pullHint,
            releaseHint,
            refreshingHint,
            completeHint,
        )
    }
}
