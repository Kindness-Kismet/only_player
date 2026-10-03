package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.Video
import one.only.player.core.ui.components.CardListItem
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    layoutMode: MediaLayoutMode = preferences.videoLayoutMode,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    when (layoutMode) {
        MediaLayoutMode.LIST -> VideoListItem(
            video = video,
            isRecentlyPlayedVideo = isRecentlyPlayedVideo,
            preferences = preferences,
            modifier = modifier,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        MediaLayoutMode.GRID -> VideoGridItem(
            video = video,
            isRecentlyPlayedVideo = isRecentlyPlayedVideo,
            preferences = preferences,
            modifier = modifier,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun VideoListItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    CardListItem(
        modifier = modifier.testTag("item_video_${video.displayName}"),
        isSelected = false,
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(MediaItemContentPadding),
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            VideoThumbnail(
                video = video,
                preferences = preferences,
                modifier = Modifier.width(libraryListThumbWidth()),
            )
        },
        trailingContent = {
            SelectionCheckIndicator(isSelected = isSelected)
        },
        content = {
            Text(
                text = if (preferences.shouldShowExtensionField) video.nameWithExtension else video.displayName,
                maxLines = 2,
                style = MiuixTheme.textStyles.title4,
                overflow = TextOverflow.Ellipsis,
                color = videoTitleColor(isRecentlyPlayedVideo, preferences),
            )
        },
        supportingContent = {
            MediaMetaText(parts = video.metaParts(preferences))
        },
    )
}

@Composable
private fun VideoGridItem(
    video: Video,
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    CardListItem(
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_video_${video.displayName}"),
        isSelected = false,
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(MediaItemContentPadding),
        onClick = onClick,
        onLongClick = onLongClick,
        trailingContent = {
            SelectionCheckIndicator(isSelected = isSelected)
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VideoThumbnail(
                    video = video,
                    preferences = preferences,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (preferences.shouldShowExtensionField) video.nameWithExtension else video.displayName,
                        maxLines = 2,
                        style = MiuixTheme.textStyles.title4,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = videoTitleColor(isRecentlyPlayedVideo, preferences),
                    )
                    MediaMetaText(
                        parts = video.metaParts(preferences),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}

@Composable
private fun videoTitleColor(
    isRecentlyPlayedVideo: Boolean,
    preferences: ApplicationPreferences,
): Color = if (isRecentlyPlayedVideo && preferences.shouldMarkLastPlayedMedia) {
    MiuixTheme.colorScheme.primary
} else {
    MiuixTheme.colorScheme.onSurface
}

@Composable
internal fun VideoThumbnail(
    modifier: Modifier = Modifier,
    video: Video,
    preferences: ApplicationPreferences,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .aspectRatio(16f / 10f),
    ) {
        Icon(
            imageVector = AppIcons.Video,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.5f),
        )
        if (preferences.shouldShowThumbnailField) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.uriString)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (preferences.shouldShowDurationField) {
            ThumbnailBadge(
                text = video.formattedDuration,
                modifier = Modifier
                    .padding(5.dp)
                    .align(Alignment.BottomEnd),
            )
        }

        if (preferences.shouldShowPlayedProgress && video.playedPercentage > 0) {
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.secondaryContainer),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(video.playedPercentage)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun VideoItemRecentlyPlayedPreview() {
    OnlyPlayerTheme {
        Surface {
            VideoListItem(
                video = Video.sample,
                preferences = ApplicationPreferences(),
                isRecentlyPlayedVideo = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun VideoItemPreview() {
    OnlyPlayerTheme {
        Surface {
            VideoListItem(
                video = Video.sample,
                preferences = ApplicationPreferences(),
                isRecentlyPlayedVideo = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun VideoGridItemPreview() {
    OnlyPlayerTheme {
        VideoGridItem(
            video = Video.sample,
            preferences = ApplicationPreferences(),
            isRecentlyPlayedVideo = true,
        )
    }
}
