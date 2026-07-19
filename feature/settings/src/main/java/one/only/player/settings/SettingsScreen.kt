package one.only.player.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.components.NextSearchTopAppBar
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateUp: (() -> Unit)? = null,
    onItemClick: (Setting) -> Unit,
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = MiuixScrollBehavior()

    // resolve 标题、描述和子设置项文本，全部用于搜索匹配
    val resolvedRows = SettingRow.entries.map { row ->
        val subTexts = row.subSettingResIds.map { stringResource(it) }
        ResolvedSettingRow(
            row = row,
            title = stringResource(row.titleResId),
            description = stringResource(row.descriptionResId),
            searchableTexts = subTexts,
        )
    }

    val filteredRows = remember(searchQuery, resolvedRows) {
        if (searchQuery.isBlank()) {
            resolvedRows
        } else {
            val query = searchQuery.lowercase()
            resolvedRows.filter { it.matches(query) }
        }
    }
    val filteredRowsByType = remember(filteredRows) {
        filteredRows.associateBy { it.row }
    }
    val visibleSections = SettingSection.entries.mapNotNull { section ->
        val sectionRows = section.rows.mapNotNull { filteredRowsByType[it] }
        ResolvedSettingSection(section = section, rows = sectionRows).takeIf { sectionRows.isNotEmpty() }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "settings_top_bar",
            ) { isSearching ->
                if (isSearching) {
                    NextSearchTopAppBar(
                        query = searchQuery,
                        placeholder = stringResource(R.string.search_settings),
                        searchFieldTestTag = "settings_search_field",
                        clearButtonTestTag = "btn_settings_search_clear",
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                    )
                } else {
                    TopAppBar(
                        title = stringResource(id = R.string.settings),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = if (onNavigateUp != null) {
                            {
                                MiuixIconButton(
                                    onClick = onNavigateUp,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .testTag("button_settings_back"),
                                ) {
                                    MiuixIcon(
                                        imageVector = NextIcons.ArrowBack,
                                        contentDescription = stringResource(id = R.string.navigate_up),
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        } else {
                            {}
                        },
                        actions = {
                            MiuixIconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("btn_settings_search"),
                            ) {
                                MiuixIcon(
                                    imageVector = NextIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            }
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visibleSections.forEach { section ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    section.rows.forEach { resolved ->
                        ArrowPreference(
                            modifier = Modifier.testTag("item_settings_${resolved.row.setting.name.lowercase()}"),
                            title = resolved.title,
                            summary = resolved.description,
                            startAction = {
                                MiuixIcon(
                                    imageVector = resolved.row.icon,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            },
                            onClick = { onItemClick(resolved.row.setting) },
                        )
                    }
                }
            }
        }
    }
}

private data class ResolvedSettingRow(
    val row: SettingRow,
    val title: String,
    val description: String,
    val searchableTexts: List<String>,
) {
    fun matches(query: String): Boolean = title.lowercase().contains(query) ||
        description.lowercase().contains(query) ||
        searchableTexts.any { it.lowercase().contains(query) }
}

private data class ResolvedSettingSection(
    val section: SettingSection,
    val rows: List<ResolvedSettingRow>,
)

enum class Setting {
    APPEARANCE,
    MEDIA_LIBRARY,
    PLAYER,
    GESTURES,
    DECODER,
    AUDIO,
    SUBTITLE,
    PRIVACY,
    GENERAL,
    ABOUT,
}

private enum class SettingSection(
    val rows: List<SettingRow>,
) {
    APP_AND_LIBRARY(
        rows = listOf(
            SettingRow.APPEARANCE,
            SettingRow.MEDIA_LIBRARY,
        ),
    ),
    PLAYBACK(
        rows = listOf(
            SettingRow.PLAYER,
            SettingRow.GESTURES,
            SettingRow.DECODER,
            SettingRow.AUDIO,
            SettingRow.SUBTITLE,
        ),
    ),
    SYSTEM_AND_SUPPORT(
        rows = listOf(
            SettingRow.PRIVACY,
            SettingRow.GENERAL,
            SettingRow.ABOUT,
        ),
    ),
}

// 子设置项的字符串资源 ID，用于搜索索引
internal enum class SettingRow(
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
    val setting: Setting,
    val subSettingResIds: List<Int> = emptyList(),
) {
    APPEARANCE(
        titleResId = R.string.appearance_name,
        descriptionResId = R.string.appearance_description,
        icon = NextIcons.Appearance,
        setting = Setting.APPEARANCE,
        subSettingResIds = listOf(
            R.string.dark_theme,
            R.string.dynamic_theme,
            R.string.app_language,
            R.string.app_language_description,
            R.string.home_title_long_press_to_root,
            R.string.home_title_long_press_to_root_description,
            R.string.floating_navigation_bar,
            R.string.floating_navigation_bar_description,
            R.string.floating_navigation_bar_blur,
            R.string.floating_navigation_bar_blur_description,
        ),
    ),
    MEDIA_LIBRARY(
        titleResId = R.string.media_library,
        descriptionResId = R.string.media_library_description,
        icon = NextIcons.Movie,
        setting = Setting.MEDIA_LIBRARY,
        subSettingResIds = listOf(
            R.string.manage_folders,
            R.string.force_rescan_storage,
            R.string.ignore_nomedia_files,
            R.string.thumbnail_generation,
            R.string.mark_last_played_media,
            R.string.recycle_bin,
        ),
    ),
    PLAYER(
        titleResId = R.string.player_name,
        descriptionResId = R.string.player_description,
        icon = NextIcons.Player,
        setting = Setting.PLAYER,
        subSettingResIds = listOf(
            R.string.resume,
            R.string.default_playback_speed,
            R.string.autoplay_settings,
            R.string.pause_at_end_of_queue,
            R.string.pip_settings,
            R.string.background_play,
            R.string.fast_seek,
            R.string.screen_rotation,
            R.string.player_screen_orientation,
            R.string.controller_timeout,
            R.string.player_control_labels,
            R.string.player_icon_style,
            R.string.player_icon_style_tonal,
            R.string.player_icon_style_classic,
            R.string.player_icon_style_translucent,
            R.string.seek_increment,
            R.string.loop_mode,
            R.string.skip_silence,
        ),
    ),
    GESTURES(
        titleResId = R.string.gestures_name,
        descriptionResId = R.string.gestures_description,
        icon = NextIcons.SwipeHorizontal,
        setting = Setting.GESTURES,
        subSettingResIds = listOf(
            R.string.double_tap,
            R.string.long_press_gesture,
            R.string.seek_gesture,
            R.string.zoom_gesture,
            R.string.volume_gesture,
            R.string.brightness_gesture,
            R.string.pan_gesture,
        ),
    ),
    DECODER(
        titleResId = R.string.video_processing,
        descriptionResId = R.string.decoder_desc,
        icon = NextIcons.Decoder,
        setting = Setting.DECODER,
        subSettingResIds = listOf(
            R.string.decoder_priority,
            R.string.video_filters,
            R.string.enable_video_filters,
            R.string.video_brightness,
            R.string.video_contrast,
            R.string.video_saturation,
            R.string.video_hue,
            R.string.video_gamma,
            R.string.video_sharpening,
        ),
    ),
    AUDIO(
        titleResId = R.string.audio,
        descriptionResId = R.string.audio_desc,
        icon = NextIcons.Audio,
        setting = Setting.AUDIO,
        subSettingResIds = listOf(
            R.string.preferred_audio_lang,
            R.string.require_audio_focus,
            R.string.pause_on_headset_disconnect,
            R.string.system_volume_panel,
            R.string.volume_boost,
        ),
    ),
    SUBTITLE(
        titleResId = R.string.subtitle,
        descriptionResId = R.string.subtitle_desc,
        icon = NextIcons.Subtitle,
        setting = Setting.SUBTITLE,
        subSettingResIds = listOf(
            R.string.preferred_subtitle_lang,
            R.string.subtitle_auto_load,
            R.string.subtitle_font,
            R.string.subtitle_text_size,
            R.string.subtitle_position,
            R.string.subtitle_text_bold,
            R.string.subtitle_text_color,
            R.string.subtitle_edge_style,
            R.string.subtitle_text_encoding,
            R.string.subtitle_background,
            R.string.embedded_styles,
            R.string.system_caption_style,
        ),
    ),
    PRIVACY(
        titleResId = R.string.privacy_protection,
        descriptionResId = R.string.privacy_protection_description,
        icon = NextIcons.HideSource,
        setting = Setting.PRIVACY,
        subSettingResIds = listOf(
            R.string.prevent_screenshots,
            R.string.hide_in_recents,
        ),
    ),
    GENERAL(
        titleResId = R.string.general_name,
        descriptionResId = R.string.general_description,
        icon = NextIcons.ExtraSettings,
        setting = Setting.GENERAL,
        subSettingResIds = listOf(
            R.string.delete_video_cache,
            R.string.reset_settings,
            R.string.backup_settings,
            R.string.restore_settings,
        ),
    ),
    ABOUT(
        titleResId = R.string.about_name,
        descriptionResId = R.string.about_description,
        icon = NextIcons.Info,
        setting = Setting.ABOUT,
        subSettingResIds = listOf(
            R.string.device_info,
            R.string.libraries,
            R.string.check_for_updates,
        ),
    ),
}
