from pathlib import Path

# ViewModel
p = Path(
    r"E:/Downloads/only_player_src/feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesViewModel.kt"
)
t = p.read_text(encoding="utf-8")
if "VolumeBrightnessIndicatorStyle" not in t:
    t = t.replace(
        "import one.only.player.core.model.MediaSessionVisibility\n",
        "import one.only.player.core.model.MediaSessionVisibility\n"
        "import one.only.player.core.model.VolumeBrightnessIndicatorStyle\n",
    )
if "UpdateVolumeBrightnessIndicatorStyle" not in t:
    t = t.replace(
        "            is PlayerPreferencesUiEvent.UpdateMediaSessionVisibility -> updateMediaSessionVisibility(event.value)\n",
        "            is PlayerPreferencesUiEvent.UpdateMediaSessionVisibility -> updateMediaSessionVisibility(event.value)\n"
        "            is PlayerPreferencesUiEvent.UpdateVolumeBrightnessIndicatorStyle -> updateVolumeBrightnessIndicatorStyle(event.value)\n",
    )
    t = t.replace(
        "    private fun updateMediaSessionVisibility(value: MediaSessionVisibility) {\n"
        "        viewModelScope.launch {\n"
        "            preferencesRepository.updatePlayerPreferences {\n"
        "                it.copy(mediaSessionVisibility = value)\n"
        "            }\n"
        "        }\n"
        "    }\n",
        "    private fun updateMediaSessionVisibility(value: MediaSessionVisibility) {\n"
        "        viewModelScope.launch {\n"
        "            preferencesRepository.updatePlayerPreferences {\n"
        "                it.copy(mediaSessionVisibility = value)\n"
        "            }\n"
        "        }\n"
        "    }\n\n"
        "    private fun updateVolumeBrightnessIndicatorStyle(value: VolumeBrightnessIndicatorStyle) {\n"
        "        viewModelScope.launch {\n"
        "            preferencesRepository.updatePlayerPreferences {\n"
        "                it.copy(volumeBrightnessIndicatorStyle = value)\n"
        "            }\n"
        "        }\n"
        "    }\n",
    )
    t = t.replace(
        "    data object MediaSessionVisibilityDialog : PlayerPreferenceDialog\n}",
        "    data object MediaSessionVisibilityDialog : PlayerPreferenceDialog\n"
        "    data object VolumeBrightnessIndicatorDialog : PlayerPreferenceDialog\n}",
    )
    t = t.replace(
        "    data class UpdateMediaSessionVisibility(val value: MediaSessionVisibility) : PlayerPreferencesUiEvent\n",
        "    data class UpdateMediaSessionVisibility(val value: MediaSessionVisibility) : PlayerPreferencesUiEvent\n"
        "    data class UpdateVolumeBrightnessIndicatorStyle(val value: VolumeBrightnessIndicatorStyle) : PlayerPreferencesUiEvent\n",
    )
    p.write_text(t, encoding="utf-8")
    print("vm indicator")
else:
    print("vm has indicator")

# Screen
p = Path(
    r"E:/Downloads/only_player_src/feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesScreen.kt"
)
t = p.read_text(encoding="utf-8")
if "VolumeBrightnessIndicatorStyle" not in t:
    t = t.replace(
        "import one.only.player.core.model.MediaSessionVisibility\n",
        "import one.only.player.core.model.MediaSessionVisibility\n"
        "import one.only.player.core.model.VolumeBrightnessIndicatorStyle\n",
    )
if "item_settings_player_volume_brightness_indicator" not in t:
    needle = '            ListSectionTitle(text = stringResource(id = R.string.media_session_visibility))'
    insert = """            ListSectionTitle(text = stringResource(id = R.string.volume_brightness_indicator))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_volume_brightness_indicator"),
                    title = stringResource(id = R.string.volume_brightness_indicator),
                    description = when (uiState.preferences.volumeBrightnessIndicatorStyle) {
                        VolumeBrightnessIndicatorStyle.BAR -> stringResource(R.string.volume_brightness_indicator_bar)
                        VolumeBrightnessIndicatorStyle.CENTER_TEXT -> stringResource(R.string.volume_brightness_indicator_center_text)
                    },
                    icon = NextIcons.Brightness,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.VolumeBrightnessIndicatorDialog))
                    },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

            ListSectionTitle(text = stringResource(id = R.string.media_session_visibility))"""
    if needle in t:
        t = t.replace(needle, insert)
        print("indicator item")
    else:
        print("media session title missing")

if "VolumeBrightnessIndicatorDialog ->" not in t:
    dialog = """                PlayerPreferenceDialog.VolumeBrightnessIndicatorDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.volume_brightness_indicator),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(VolumeBrightnessIndicatorStyle.entries.toTypedArray()) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_vb_indicator_" + it.name.lowercase()),
                                text = when (it) {
                                    VolumeBrightnessIndicatorStyle.BAR -> stringResource(R.string.volume_brightness_indicator_bar)
                                    VolumeBrightnessIndicatorStyle.CENTER_TEXT -> stringResource(R.string.volume_brightness_indicator_center_text)
                                },
                                isSelected = it == uiState.preferences.volumeBrightnessIndicatorStyle,
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdateVolumeBrightnessIndicatorStyle(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }

"""
    if "PlayerPreferenceDialog.MediaSessionVisibilityDialog ->" in t:
        t = t.replace(
            "                PlayerPreferenceDialog.MediaSessionVisibilityDialog -> {",
            dialog + "                PlayerPreferenceDialog.MediaSessionVisibilityDialog -> {",
        )
        print("indicator dialog")
    else:
        print("no media session dialog branch")

p.write_text(t, encoding="utf-8")
print("screen ok")
