package one.only.player.feature.videopicker.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.plus

@Composable
fun MediaSkeletonLoading(
    preferences: ApplicationPreferences,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition()
    val shimmerOffset by transition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutScale = preferences.normalizedMediaLayoutScale()
        val horizontalPadding = 8.dp
        val itemSpacing = 2.dp
        val videoMinWidth = 160.dp * layoutScale
        val maxWidth = maxWidth - (horizontalPadding * 2) - itemSpacing
        val gridColumns = (maxWidth / videoMinWidth).toInt().coerceAtLeast(1)

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(if (preferences.mediaLayoutMode == MediaLayoutMode.GRID) gridColumns else 1),
            contentPadding = contentPadding + PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items(List(SKELETON_ITEM_COUNT) { it }) { index ->
                when (preferences.mediaLayoutMode) {
                    MediaLayoutMode.LIST -> MediaListSkeletonItem(shimmerOffset = shimmerOffset)
                    MediaLayoutMode.GRID -> MediaGridSkeletonItem(shimmerOffset = shimmerOffset)
                }
            }
        }
    }
}

@Composable
private fun MediaListSkeletonItem(
    shimmerOffset: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(minOf(150.dp, LocalConfiguration.current.screenWidthDp.dp * 0.35f))
                .aspectRatio(16f / 10f),
            shimmerOffset = shimmerOffset,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(18.dp),
                shimmerOffset = shimmerOffset,
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(12.dp),
                shimmerOffset = shimmerOffset,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(48.dp)
                        .height(24.dp),
                    shimmerOffset = shimmerOffset,
                    shape = MaterialTheme.shapes.small,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(58.dp)
                        .height(24.dp),
                    shimmerOffset = shimmerOffset,
                    shape = MaterialTheme.shapes.small,
                )
            }
        }
    }
}

@Composable
private fun MediaGridSkeletonItem(shimmerOffset: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            shimmerOffset = shimmerOffset,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(16.dp),
            shimmerOffset = shimmerOffset,
        )
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    shimmerOffset: Float,
    shape: Shape = MaterialTheme.shapes.small,
) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val softColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor)
            .drawWithCache {
                val width = size.width.coerceAtLeast(1f)
                val sweepWidth = width * 1.2f
                val startX = width * shimmerOffset
                val brush = Brush.linearGradient(
                    colors = listOf(
                        baseColor,
                        softColor,
                        highlightColor,
                        softColor,
                        baseColor,
                    ),
                    start = Offset(startX - sweepWidth, 0f),
                    end = Offset(startX + sweepWidth, size.height),
                )
                onDrawBehind {
                    drawRect(brush = brush)
                }
            },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoVideosFound(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(
                horizontal = 24.dp,
                vertical = 40.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Video,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(id = R.string.no_videos_found),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
    }
}

private const val SKELETON_ITEM_COUNT = 18
