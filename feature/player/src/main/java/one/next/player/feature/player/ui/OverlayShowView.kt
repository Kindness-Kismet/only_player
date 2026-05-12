package one.next.player.feature.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.VideoContentScale
import one.next.player.core.ui.R
import one.next.player.core.ui.components.VideoFiltersPanel
import one.next.player.feature.player.extensions.noRippleClickable
import one.next.player.feature.player.state.SubtitleOptionsEvent

@Composable
fun BoxScope.OverlayShowView(
    player: Player,
    overlayView: OverlayView?,
    videoContentScale: VideoContentScale,
    videoFilterPreferences: PlayerPreferences,
    onDismiss: () -> Unit = {},
    onSelectSubtitleClick: () -> Unit = {},
    onAddOnlineSubtitleClick: (String) -> Unit = {},
    onSubtitleOptionEvent: (SubtitleOptionsEvent) -> Unit = {},
    onVideoContentScaleChanged: (VideoContentScale) -> Unit = {},
    onPreviewVideoFilters: (PlayerPreferences) -> Unit = {},
    onConfirmVideoFilters: (PlayerPreferences) -> Unit = {},
    onCloseVideoFilters: () -> Unit = {},
    onShowVideoFilters: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(
                if (overlayView != null) {
                    Modifier.noRippleClickable(onClick = onDismiss)
                } else {
                    Modifier
                },
            ),
    )

    AudioTrackSelectorView(
        shouldShow = overlayView == OverlayView.AUDIO_SELECTOR,
        player = player,
        onDismiss = onDismiss,
    )

    SubtitleSelectorView(
        shouldShow = overlayView == OverlayView.SUBTITLE_SELECTOR,
        player = player,
        onSelectSubtitleClick = onSelectSubtitleClick,
        onAddOnlineSubtitleClick = onAddOnlineSubtitleClick,
        onEvent = onSubtitleOptionEvent,
        onDismiss = onDismiss,
    )

    PlaybackSpeedSelectorView(
        shouldShow = overlayView == OverlayView.PLAYBACK_SPEED,
        player = player,
    )

    VideoContentScaleSelectorView(
        shouldShow = overlayView == OverlayView.VIDEO_CONTENT_SCALE,
        videoContentScale = videoContentScale,
        onVideoContentScaleChanged = onVideoContentScaleChanged,
        onShowVideoFilters = onShowVideoFilters,
        onDismiss = onDismiss,
    )

    VideoFilterOverlayView(
        shouldShow = overlayView == OverlayView.VIDEO_FILTERS,
        preferences = videoFilterPreferences,
        onDismissRequest = onCloseVideoFilters,
        onPreviewPreferences = onPreviewVideoFilters,
        onConfirmPreferences = onConfirmVideoFilters,
    )

    PlaylistView(
        shouldShow = overlayView == OverlayView.PLAYLIST,
        player = player,
    )
}

@Composable
private fun BoxScope.VideoFilterOverlayView(
    shouldShow: Boolean,
    preferences: PlayerPreferences,
    onDismissRequest: () -> Unit,
    onPreviewPreferences: (PlayerPreferences) -> Unit,
    onConfirmPreferences: (PlayerPreferences) -> Unit,
) {
    OverlayView(
        shouldShow = shouldShow,
        title = stringResource(R.string.video_filters),
        testTag = "panel_video_filters",
    ) {
        VideoFiltersPanel(
            preferences = preferences,
            onDismissRequest = onDismissRequest,
            onPreviewPreferences = onPreviewPreferences,
            onConfirmPreferences = onConfirmPreferences,
        )
    }
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

enum class OverlayView {
    AUDIO_SELECTOR,
    SUBTITLE_SELECTOR,
    PLAYBACK_SPEED,
    VIDEO_CONTENT_SCALE,
    VIDEO_FILTERS,
    PLAYLIST,
}
