package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SelectionMenuItem(
    text: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = text }
            .testTag(testTag),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    )
}
