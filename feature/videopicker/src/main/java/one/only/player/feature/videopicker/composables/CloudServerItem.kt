package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.RemoteServer
import one.only.player.core.ui.R
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.designsystem.NextIcons

@Composable
fun CloudServerItem(
    server: RemoteServer,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    onClick: () -> Unit = {},
) {
    when (preferences.mediaLayoutMode) {
        MediaLayoutMode.LIST -> CloudServerListItem(
            server = server,
            modifier = modifier,
            isFirstItem = isFirstItem,
            isLastItem = isLastItem,
            onClick = onClick,
        )
        MediaLayoutMode.GRID -> CloudServerGridItem(
            server = server,
            modifier = modifier,
            isFirstItem = isFirstItem,
            isLastItem = isLastItem,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CloudServerListItem(
    server: RemoteServer,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    onClick: () -> Unit = {},
) {
    NextSegmentedListItem(
        modifier = modifier.testTag("item_home_cloud_server_${server.id}"),
        contentPadding = PaddingValues(8.dp),
        colors = ListItemDefaults.segmentedColors(),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        leadingContent = {
            CloudServerThumb(
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        },
        content = {
            Text(
                text = server.displayName(),
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = server.description(),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    InfoChip(text = server.protocol.name)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CloudServerGridItem(
    server: RemoteServer,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false,
    onClick: () -> Unit = {},
) {
    NextSegmentedListItem(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .testTag("item_home_cloud_server_${server.id}"),
        contentPadding = PaddingValues(8.dp),
        colors = ListItemDefaults.segmentedColors(),
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
        onClick = onClick,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CloudServerThumb()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = server.displayName(),
                        maxLines = 2,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = server.host,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    InfoChip(text = server.protocol.name)
                }
            }
        },
    )
}

@Composable
private fun CloudServerThumb(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(min(90.dp, LocalConfiguration.current.screenWidthDp.dp * 0.3f))
            .aspectRatio(20 / 17f),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.folder_thumb),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = NextIcons.Cloud,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 6.dp)
                .size(28.dp),
        )
    }
}

private fun RemoteServer.displayName(): String = name.ifBlank { host }

private fun RemoteServer.description(): String {
    val portSuffix = port?.let { ":$it" }.orEmpty()
    val pathSuffix = path.takeIf { it.isNotBlank() && it != "/" }?.let { " · $it" }.orEmpty()
    return "$host$portSuffix$pathSuffix"
}
