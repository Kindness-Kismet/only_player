package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.designsystem.NextIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaMessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
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
                .clip(RoundedCornerShape(16.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
        action?.let {
            Spacer(modifier = Modifier.size(16.dp))
            it()
        }
    }
}

@Composable
fun NoVideosFound(contentPadding: PaddingValues) {
    MediaMessageState(
        icon = NextIcons.Video,
        title = stringResource(id = R.string.no_videos_found),
        contentPadding = contentPadding,
    )
}
