from pathlib import Path

# Search delete cleanup
p = Path(r"E:/Downloads/only_player_src/feature/videopicker/src/main/java/one/only/player/feature/videopicker/screens/search/SearchViewModel.kt")
t = p.read_text(encoding="utf-8")
old = """    private fun permanentlyDeleteVideos(uris: List<String>) {
        viewModelScope.launch {
            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.removeDeleted(uris)
                mediaSynchronizer.refresh()
            }
"""
new = """    private fun permanentlyDeleteVideos(uris: List<String>) {
        viewModelScope.launch {
            val isDeletionSuccessful = mediaService.deleteMedia(uris.map { it.toUri() })
            if (isDeletionSuccessful) {
                mediaSynchronizer.removeDeleted(uris)
                mediaSynchronizer.refresh()
                val names = uris.mapNotNull { uriString ->
                    uriString.substringAfterLast('/').substringAfterLast('\\\\').ifBlank { null }
                }
                if (names.isNotEmpty()) {
                    preferencesRepository.updateApplicationPreferences { prefs ->
                        prefs.withoutPerFilePreferences(names)
                    }
                }
            }
"""
if old in t:
    p.write_text(t.replace(old, new), encoding="utf-8")
    print("search delete cleanup ok")
else:
    print("search delete miss")

# Home search AnimatedContent
ms = Path(r"E:/Downloads/only_player_src/feature/videopicker/src/main/java/one/only/player/feature/videopicker/screens/mediapicker/MediaPickerScreen.kt")
mt = ms.read_text(encoding="utf-8")
if "import androidx.compose.animation.AnimatedContent" not in mt:
    mt = mt.replace(
        "import androidx.compose.foundation.layout.Box\n",
        "import androidx.compose.animation.AnimatedContent\nimport androidx.compose.foundation.layout.Box\n",
        1,
    )
    print("AnimatedContent import")

old_top = """        topBar = {
            if (isSearchActive && !selectionManager.isInSelectionMode && !isMoveMode) {
                NextSearchTopAppBar(
                    query = searchQuery,
                    placeholder = stringResource(R.string.search_videos_and_folders),
                    searchFieldTestTag = "input_media_picker_search_query",
                    clearButtonTestTag = "btn_media_picker_search_clear",
                    closeButtonTestTag = "btn_media_picker_search_close",
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                )
            } else {
                MediaPickerTopAppBar(
"""
new_top = """        topBar = {
            AnimatedContent(
                targetState = isSearchActive && !selectionManager.isInSelectionMode && !isMoveMode,
                label = "media_picker_top_bar",
            ) { searching ->
            if (searching) {
                NextSearchTopAppBar(
                    query = searchQuery,
                    placeholder = stringResource(R.string.search_videos_and_folders),
                    searchFieldTestTag = "input_media_picker_search_query",
                    clearButtonTestTag = "btn_media_picker_search_clear",
                    closeButtonTestTag = "btn_media_picker_search_close",
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                )
            } else {
                MediaPickerTopAppBar(
"""
if old_top in mt:
    mt = mt.replace(old_top, new_top, 1)
    mt = mt.replace(
        "        },\n        contentWindowInsets = WindowInsets.displayCutout,",
        "            }\n            }\n        },\n        contentWindowInsets = WindowInsets.displayCutout,",
        1,
    )
    print("home search AnimatedContent wired")
else:
    print("topBar structure miss")
ms.write_text(mt, encoding="utf-8")

# Quick settings spacing
qd = Path(r"E:/Downloads/only_player_src/feature/videopicker/src/main/java/one/only/player/feature/videopicker/composables/QuickSettingsDialog.kt")
qt = qd.read_text(encoding="utf-8")
qt = qt.replace(
    """private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title4,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}""",
    """private fun DialogSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title4,
        // 对齐设置页应用语言下拉的紧凑间距
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
    )
}""",
)
if ".padding(horizontal = 4.dp)" not in qt:
    qt = qt.replace(
        ".verticalScroll(rememberScrollState()),",
        ".verticalScroll(rememberScrollState())\n                    .padding(horizontal = 4.dp),",
        1,
    )
qd.write_text(qt, encoding="utf-8")
print("quick settings spacing")

# per-file mirror in FileExtensionPreferencesViewModel
fe = Path(r"E:/Downloads/only_player_src/feature/settings/src/main/java/one/only/player/settings/screens/medialibrary/FileExtensionPreferencesViewModel.kt")
ft = fe.read_text(encoding="utf-8")
if "per_file_playback_preferences.json" not in ft:
    ft = ft.replace(
        """                // 同步导出一份可读配置到 data/files，便于确认按后缀解码已落盘
                persistExtensionDecoderMirror(normalized)
""",
        """                // 同步导出可读配置到 data/files
                persistExtensionDecoderMirror(normalized)
                persistPerFilePlaybackMirror(prefs.normalizedPerFilePlaybackPreferences())
""",
    )
    # older comment variant
    ft = ft.replace(
        """                persistExtensionDecoderMirror(normalized)
            }
        }
    }
""",
        """                persistExtensionDecoderMirror(normalized)
                persistPerFilePlaybackMirror(prefs.normalizedPerFilePlaybackPreferences())
            }
        }
    }
""",
    )
    helper = '''
    private fun persistPerFilePlaybackMirror(preferences: List<one.only.player.core.model.PerFilePlaybackPreference>) {
        runCatching {
            val dir = File(appContext.filesDir, "data")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "per_file_playback_preferences.json")
            val lines = preferences.joinToString(separator = ",\\n") { item ->
                val decoder = item.decoderPriority?.name ?: "null"
                val orientation = item.screenOrientation?.name ?: "null"
                "    {\\"fileName\\": \\"${item.fileName}\\", \\"decoderPriority\\": \\"$decoder\\", \\"screenOrientation\\": \\"$orientation\\"}"
            }
            val body = "{\\n  \\"files\\": [\\n$lines\\n  ]\\n}\\n"
            file.writeText(body)
        }
    }

'''
    if "persistPerFilePlaybackMirror" not in ft:
        ft = ft.replace(
            "    private fun persistExtensionDecoderMirror",
            helper + "    private fun persistExtensionDecoderMirror",
            1,
        )
    fe.write_text(ft, encoding="utf-8")
    print("per-file mirror writer")
else:
    print("mirror exists")

print("done")
