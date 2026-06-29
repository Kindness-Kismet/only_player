package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.only.player.core.model.RemoteServer
import one.only.player.core.ui.components.NextSegmentedListItem
import one.only.player.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PinnedServerItem(
    server: RemoteServer,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    NextSegmentedListItem(
        onClick = onClick,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier.testTag("pinned_server_item_${server.id}"),
        leadingContent = {
            Icon(
                imageVector = NextIcons.Cloud,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Icon(
                imageVector = NextIcons.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        supportingContent = {
            Text(
                text = "${server.protocol.name} · ${server.host}${server.port?.let { ":$it" } ?: ""}",
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Text(
                text = server.name.ifBlank { server.host },
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
