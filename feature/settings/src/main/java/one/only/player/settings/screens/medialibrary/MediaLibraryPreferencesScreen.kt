package one.only.player.settings.screens.medialibrary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.createManageExternalStorageAccessIntent
import one.only.player.core.common.hasManageExternalStorageAccess
import one.only.player.core.model.ThumbnailGenerationStrategy
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppIconButton as MiuixIconButton
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.ClickablePreferenceItem
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceGroup
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.SettingsGroupGap
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaLibraryPreferencesScreen(
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit = {},
    onScanFolderSettingClick: () -> Unit = {},
    onThumbnailSettingClick: () -> Unit = {},
    viewModel: MediaLibraryPreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasAllFilesAccess by remember {
        mutableStateOf(hasManageExternalStorageAccess())
    }

    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        hasAllFilesAccess = hasManageExternalStorageAccess()
        if (hasAllFilesAccess) return@LifecycleEventEffect
        if (!uiState.preferences.shouldIgnoreNoMediaFiles && !uiState.preferences.isRecycleBinEnabled) {
            return@LifecycleEventEffect
        }

        viewModel.onEvent(MediaLibraryPreferencesUiEvent.ResetRestrictedFeatures)
    }

    MediaLibraryPreferencesContent(
        uiState = uiState,
        hasAllFilesAccess = hasAllFilesAccess,
        onNavigateUp = onNavigateUp,
        onFolderSettingClick = onFolderSettingClick,
        onScanFolderSettingClick = onScanFolderSettingClick,
        onThumbnailSettingClick = onThumbnailSettingClick,
        onOpenAllFilesAccessSettings = {
            context.startActivity(createManageExternalStorageAccessIntent(context))
        },
        onToggleIgnoreNoMediaFiles = {
            viewModel.onEvent(
                MediaLibraryPreferencesUiEvent.SetIgnoreNoMediaFiles(
                    shouldIgnoreNoMediaFiles = it,
                ),
            )
        },
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun MediaLibraryPreferencesContent(
    uiState: MediaLibraryPreferencesUiState,
    hasAllFilesAccess: Boolean,
    onNavigateUp: () -> Unit,
    onFolderSettingClick: () -> Unit,
    onScanFolderSettingClick: () -> Unit,
    onThumbnailSettingClick: () -> Unit,
    onOpenAllFilesAccessSettings: () -> Unit,
    onToggleIgnoreNoMediaFiles: (Boolean) -> Unit,
    onEvent: (MediaLibraryPreferencesUiEvent) -> Unit,
) {
    val preferences = uiState.preferences

    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(id = R.string.media_library),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_media_library_back"),
                    ) {
                        MiuixIcon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = PageContentTopPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsGroupGap),
        ) {
            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_media_all_files_access"),
                    title = stringResource(id = R.string.all_files_access_title),
                    description = stringResource(id = R.string.media_library_all_files_access_desc),
                    icon = AppIcons.Settings,
                    onClick = onOpenAllFilesAccessSettings,
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_media_ignore_nomedia"),
                    title = stringResource(id = R.string.ignore_nomedia_files),
                    description = stringResource(id = R.string.ignore_nomedia_files_desc),
                    icon = AppIcons.HideSource,
                    isEnabled = hasAllFilesAccess,
                    isChecked = preferences.shouldIgnoreNoMediaFiles,
                    onClick = {
                        onToggleIgnoreNoMediaFiles(!preferences.shouldIgnoreNoMediaFiles)
                    },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_media_recycle_bin"),
                    title = stringResource(id = R.string.recycle_bin),
                    description = stringResource(id = R.string.recycle_bin_desc),
                    icon = AppIcons.DeleteSweep,
                    isEnabled = hasAllFilesAccess,
                    isChecked = preferences.isRecycleBinEnabled,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleRecycleBinEnabled) },
                )
            }

            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_media_scan_folders"),
                    title = stringResource(id = R.string.scan_folders),
                    description = stringResource(id = R.string.scan_folders_desc),
                    icon = AppIcons.Folder,
                    onClick = onScanFolderSettingClick,
                )
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_media_folders"),
                    title = stringResource(id = R.string.manage_folders),
                    description = stringResource(id = R.string.manage_folders_desc),
                    icon = AppIcons.FolderOff,
                    onClick = onFolderSettingClick,
                )
            }

            PreferenceGroup {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_media_mark_last_played"),
                    title = stringResource(id = R.string.mark_last_played_media),
                    description = stringResource(
                        id = R.string.mark_last_played_media_desc,
                    ),
                    icon = AppIcons.Check,
                    isChecked = preferences.shouldMarkLastPlayedMedia,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia) },
                )
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_media_restore_last_played_in_folders"),
                    title = stringResource(id = R.string.restore_last_played_media_in_folders),
                    description = stringResource(id = R.string.restore_last_played_media_in_folders_desc),
                    icon = AppIcons.History,
                    isChecked = preferences.shouldRestoreLastPlayedMediaInFolders,
                    onClick = { onEvent(MediaLibraryPreferencesUiEvent.ToggleRestoreLastPlayedMediaInFolders) },
                )
            }

            PreferenceGroup {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_media_thumbnails"),
                    title = stringResource(id = R.string.thumbnail_generation),
                    description = when (preferences.thumbnailGenerationStrategy) {
                        ThumbnailGenerationStrategy.FIRST_FRAME -> stringResource(id = R.string.first_frame)
                        ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> stringResource(R.string.frame_at_position)
                        ThumbnailGenerationStrategy.HYBRID -> stringResource(id = R.string.hybrid)
                    },
                    icon = AppIcons.Image,
                    onClick = onThumbnailSettingClick,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MediaLibraryPreferencesScreenPreview() {
    OnlyPlayerTheme {
        MediaLibraryPreferencesContent(
            uiState = MediaLibraryPreferencesUiState(),
            hasAllFilesAccess = false,
            onNavigateUp = {},
            onFolderSettingClick = {},
            onScanFolderSettingClick = {},
            onThumbnailSettingClick = {},
            onOpenAllFilesAccessSettings = {},
            onToggleIgnoreNoMediaFiles = {},
            onEvent = {},
        )
    }
}
