package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import one.only.player.core.common.Utils
import one.only.player.core.media.extensions.storageRootLabelOf
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.ui.R
import one.only.player.core.ui.components.CardListItem
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderItem(
    folder: Folder,
    isRecentlyPlayedFolder: Boolean,
    preferences: ApplicationPreferences,
    layoutMode: MediaLayoutMode = preferences.folderLayoutMode,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    when (layoutMode) {
        MediaLayoutMode.LIST -> FolderListItem(
            folder = folder,
            isRecentlyPlayedFolder = isRecentlyPlayedFolder,
            preferences = preferences,
            modifier = modifier,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        MediaLayoutMode.GRID -> FolderGridItem(
            folder = folder,
            isRecentlyPlayedFolder = isRecentlyPlayedFolder,
            preferences = preferences,
            modifier = modifier,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun FolderListItem(
    folder: Folder,
    isRecentlyPlayedFolder: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    CardListItem(
        modifier = modifier.testTag("item_folder_${folder.name}"),
        isSelected = false,
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(MediaItemContentPadding),
        onClick = onClick,
        onLongClick = onLongClick,
        leadingContent = {
            FolderThumbnail()
        },
        trailingContent = {
            SelectionCheckIndicator(isSelected = isSelected)
        },
        content = {
            Text(
                text = folder.localizedName(),
                maxLines = 2,
                style = MiuixTheme.textStyles.title4,
                overflow = TextOverflow.Ellipsis,
                color = folderTitleColor(isRecentlyPlayedFolder, preferences),
            )
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (preferences.shouldShowPathField) {
                    Text(
                        text = folder.path.substringBeforeLast("/"),
                        maxLines = 1,
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Normal),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                MediaMetaText(parts = folder.metaParts(preferences))
            }
        },
    )
}

@Composable
private fun FolderGridItem(
    folder: Folder,
    isRecentlyPlayedFolder: Boolean,
    preferences: ApplicationPreferences,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    CardListItem(
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_folder_${folder.name}"),
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
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FolderGridThumbnail()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = folder.localizedName(),
                        maxLines = 2,
                        style = MiuixTheme.textStyles.title4,
                        overflow = TextOverflow.Ellipsis,
                        color = folderTitleColor(isRecentlyPlayedFolder, preferences),
                        textAlign = TextAlign.Center,
                    )
                    MediaMetaText(
                        parts = folder.metaParts(preferences),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}

@Composable
private fun Folder.localizedName(): String {
    val storageRootLabels = rememberStorageRootLabels()
    return remember(path, name, storageRootLabels) { storageRootLabels.storageRootLabelOf(path) ?: name }
}

@Composable
private fun folderTitleColor(
    isRecentlyPlayedFolder: Boolean,
    preferences: ApplicationPreferences,
): Color = if (isRecentlyPlayedFolder && preferences.shouldMarkLastPlayedMedia) {
    MiuixTheme.colorScheme.primary
} else {
    MiuixTheme.colorScheme.onSurface
}

@Composable
private fun Folder.metaParts(preferences: ApplicationPreferences): List<String> = buildList {
    if (mediaList.isNotEmpty()) {
        add("${mediaList.size} " + stringResource(id = R.string.video.takeIf { mediaList.size == 1 } ?: R.string.videos))
    }
    if (folderList.isNotEmpty()) {
        add("${folderList.size} " + stringResource(id = R.string.folder.takeIf { folderList.size == 1 } ?: R.string.folders))
    }
    if (preferences.shouldShowSizeField) {
        add(Utils.formatFileSize(mediaSize))
    }
}

@PreviewLightDark
@Composable
fun FolderItemRecentlyPlayedPreview() {
    OnlyPlayerTheme {
        FolderListItem(
            folder = Folder.sample,
            preferences = ApplicationPreferences(),
            isRecentlyPlayedFolder = true,
        )
    }
}

@PreviewLightDark
@Composable
fun FolderItemPreview() {
    OnlyPlayerTheme {
        FolderListItem(
            folder = Folder.sample.copy(folderList = listOf(Folder.sample)),
            preferences = ApplicationPreferences(),
            isRecentlyPlayedFolder = false,
        )
    }
}

@PreviewLightDark
@Composable
fun FolderGridViewPreview() {
    OnlyPlayerTheme {
        FolderGridItem(
            folder = Folder.sample.copy(folderList = listOf(Folder.sample)),
            preferences = ApplicationPreferences(),
            isRecentlyPlayedFolder = true,
        )
    }
}
