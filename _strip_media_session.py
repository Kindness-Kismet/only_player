# -*- coding: utf-8 -*-
"""Strip custom system-media visibility logic for a clean baseline."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
SCREEN = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesScreen.kt"
VM = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesViewModel.kt"
MODEL = ROOT / "core/model/src/main/java/one/only/player/core/model/PlayerPreferences.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def must(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"MISSING: {label}")
    return text.replace(old, new, 1)


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # import
    t = t.replace("import one.only.player.core.model.MediaSessionVisibility\n", "")

    # onMediaItemTransition: remove media session visibility sync
    t = must(
        t,
        """            super.onMediaItemTransition(mediaItem, reason)
            // 媒体切换后同步系统媒体可见性（SHOW/HIDE）
            mediaSession?.let { session ->
                updateLegacyMediaSessionActive(session)
                onUpdateNotification(session, startInForegroundRequired = false)
            }
            hasPausedAtEndOfQueue = false
""",
        """            super.onMediaItemTransition(mediaItem, reason)
            hasPausedAtEndOfQueue = false
""",
        "onMediaItemTransition visibility block",
    )

    # Always load artwork
    t = t.replace(
        """            if (shouldPublishMediaSessionNotificationForVisibility()) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
""",
        """            artworkLoader.loadInBackground(updatedMediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
""",
    )
    t = t.replace(
        """            if (shouldPublishMediaSessionNotificationForVisibility()) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future updatedMediaItems.toMutableList()
""",
        """            artworkLoader.loadInBackground(updatedMediaItems)
            return@future updatedMediaItems.toMutableList()
""",
    )

    # Replace onGetSession through ensureNonMedia / legacy helpers until createLoadControl
    # Find start of onGetSession and end before createLoadControl
    start = t.find("    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession?")
    end = t.find("    private fun createLoadControl(): DefaultLoadControl")
    if start < 0 or end < 0 or end <= start:
        raise SystemExit(f"cannot locate onGetSession/createLoadControl range start={start} end={end}")

    replacement = """    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    """
    t = t[:start] + replacement + t[end:]

    # Remove preference collector for mediaSessionVisibility
    t = must(
        t,
        """        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.mediaSessionVisibility == new.mediaSessionVisibility }
                .collect {
                    mediaSession?.let { session ->
                        updateLegacyMediaSessionActive(session)
                        onUpdateNotification(session, startInForegroundRequired = false)
                    }
                }
        }
""",
        "",
        "mediaSessionVisibility collector",
    )

    # Simplify session build: remove updateLegacy + setMediaNotificationProvider block
    # Find from updateLegacyMediaSessionActive() after build to catch end of setMediaNotificationProvider
    marker = "            }.build()\n            updateLegacyMediaSessionActive()\n"
    if marker not in t:
        # maybe already different
        marker2 = "            }.build()\n"
        idx = t.find("            mediaSession = MediaSession.Builder")
        if idx < 0:
            raise SystemExit("MediaSession.Builder not found")
        # find setMediaNotificationProvider
        if "setMediaNotificationProvider(" not in t:
            print("setMediaNotificationProvider already removed")
        else:
            raise SystemExit("build marker not found for media session provider cleanup")
    else:
        # remove from updateLegacy... through setMediaNotificationProvider closing
        start = t.find(marker)
        # find setMediaNotificationProvider after start
        provider = t.find("            setMediaNotificationProvider(", start)
        if provider < 0:
            # only remove updateLegacy line
            t = t.replace("            updateLegacyMediaSessionActive()\n", "")
        else:
            # find the matching end: after provider object, "            )\n" before "} catch"
            catch = t.find("        } catch (e: Exception) {\n            Logger.error(TAG, \"Failed to create media session\"", provider)
            if catch < 0:
                raise SystemExit("catch after setMediaNotificationProvider not found")
            # keep ".build()\n" then jump to catch
            t = t[: start + len("            }.build()\n")] + t[catch:]

    # Safety: no leftover symbols
    for bad in [
        "hardHideFromSystemMedia",
        "ensureNonMediaForegroundIfNeeded",
        "updateLegacyMediaSessionActive",
        "resolveLegacyMediaSessionCompat",
        "shouldPublishMediaSessionNotification",
        "setMediaNotificationProvider",
        "player_service_quiet",
        "MediaSessionVisibility",
    ]:
        if bad in t:
            # MediaSessionVisibility import already removed; leftover uses fail compile
            print("WARNING still contains:", bad)

    PS.write_text(t, encoding="utf-8")
    print("PlayerService cleaned")


def patch_settings_screen() -> None:
    t = SCREEN.read_text(encoding="utf-8")
    # remove import if unused after
    # remove section ListSectionTitle media_session through its Column
    t = must(
        t,
        """            ListSectionTitle(text = stringResource(id = R.string.media_session_visibility))
            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                ClickablePreferenceItem(
                    modifier = Modifier.testTag("item_settings_player_media_session_visibility"),
                    title = stringResource(id = R.string.media_session_visibility),
                    description = when (uiState.preferences.mediaSessionVisibility) {
                        MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                        MediaSessionVisibility.SHOW,
                        MediaSessionVisibility.AUDIO_ONLY,
                        -> stringResource(R.string.media_session_visibility_show)
                    },
                    icon = NextIcons.Player,
                    onClick = {
                        onEvent(PlayerPreferencesUiEvent.ShowDialog(PlayerPreferenceDialog.MediaSessionVisibilityDialog))
                    },
                    isFirstItem = true,
                    isLastItem = true,
                )
            }

""",
        "",
        "settings media session section",
    )
    # remove dialog branch
    t = must(
        t,
        """                PlayerPreferenceDialog.MediaSessionVisibilityDialog -> {
                    OptionsDialog(
                        text = stringResource(id = R.string.media_session_visibility),
                        onDismissClick = { onEvent(PlayerPreferencesUiEvent.ShowDialog(null)) },
                    ) {
                        items(
                            listOf(
                                MediaSessionVisibility.SHOW,
                                MediaSessionVisibility.HIDE,
                            ),
                        ) {
                            RadioTextButton(
                                modifier = Modifier.testTag("option_settings_media_session_" + it.name.lowercase()),
                                text = when (it) {
                                    MediaSessionVisibility.SHOW -> stringResource(R.string.media_session_visibility_show)
                                    MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                                    MediaSessionVisibility.AUDIO_ONLY -> stringResource(R.string.media_session_visibility_show)
                                },
                                isSelected = when (uiState.preferences.mediaSessionVisibility) {
                                    MediaSessionVisibility.HIDE -> it == MediaSessionVisibility.HIDE
                                    MediaSessionVisibility.SHOW,
                                    MediaSessionVisibility.AUDIO_ONLY,
                                    -> it == MediaSessionVisibility.SHOW
                                },
                                onClick = {
                                    onEvent(PlayerPreferencesUiEvent.UpdateMediaSessionVisibility(it))
                                    onEvent(PlayerPreferencesUiEvent.ShowDialog(null))
                                },
                            )
                        }
                    }
                }
""",
        "",
        "settings media session dialog",
    )
    if "MediaSessionVisibility" not in t:
        t = t.replace("import one.only.player.core.model.MediaSessionVisibility\n", "")
    SCREEN.write_text(t, encoding="utf-8")
    print("settings screen cleaned")


def patch_settings_vm() -> None:
    t = VM.read_text(encoding="utf-8")
    t = t.replace(
        "            is PlayerPreferencesUiEvent.UpdateMediaSessionVisibility -> updateMediaSessionVisibility(event.value)\n",
        "",
    )
    # remove function
    t = re.sub(
        r"\n    private fun updateMediaSessionVisibility\(value: MediaSessionVisibility\) \{\n"
        r"        viewModelScope\.launch \{\n"
        r"            preferencesRepository\.updatePlayerPreferences \{\n"
        r"                it\.copy\(mediaSessionVisibility = value\)\n"
        r"            \}\n"
        r"        \}\n"
        r"    \}\n",
        "\n",
        t,
        count=1,
    )
    t = t.replace("    data object MediaSessionVisibilityDialog : PlayerPreferenceDialog\n", "")
    t = t.replace(
        "    data class UpdateMediaSessionVisibility(val value: MediaSessionVisibility) : PlayerPreferencesUiEvent\n",
        "",
    )
    if "MediaSessionVisibility" not in t:
        t = t.replace("import one.only.player.core.model.MediaSessionVisibility\n", "")
    VM.write_text(t, encoding="utf-8")
    print("settings VM cleaned")


def patch_model() -> None:
    t = MODEL.read_text(encoding="utf-8")
    # Keep field for DataStore serialization compatibility with existing user prefs,
    # but mark unused so service never reads it.
    t = t.replace(
        """    // 是否出现在系统媒体控件/通知
    val mediaSessionVisibility: MediaSessionVisibility = MediaSessionVisibility.SHOW,
""",
        """    // 兼容旧配置字段：系统媒体自定义逻辑已移除，服务端始终走 Media3 默认，不再读取此值
    val mediaSessionVisibility: MediaSessionVisibility = MediaSessionVisibility.SHOW,
""",
    )
    # Simplify enum - keep HIDE/AUDIO_ONLY for deserialize old values
    MODEL.write_text(t, encoding="utf-8")
    print("model comment updated (field kept for prefs compat)")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 154", "versionCode = 155").replace(
        'versionName = "1.0.153"',
        'versionName = "1.0.154"',
    )
    if t2 == t:
        t2 = t.replace("versionCode = 153", "versionCode = 155").replace(
            'versionName = "1.0.152"',
            'versionName = "1.0.154"',
        )
    if t2 == t:
        import re

        print("version bump failed", re.findall(r"versionCode = \d+|versionName = \"[^\"]+\"", t))
    else:
        GRADLE.write_text(t2, encoding="utf-8")
        print("version 1.0.154 / 155")


def main() -> None:
    patch_player_service()
    patch_settings_screen()
    patch_settings_vm()
    patch_model()
    bump()
    ps = PS.read_text(encoding="utf-8")
    for bad in [
        "hardHideFromSystemMedia",
        "ensureNonMediaForegroundIfNeeded",
        "updateLegacyMediaSessionActive",
        "setMediaNotificationProvider",
        "shouldPublishMediaSessionNotification",
    ]:
        if bad in ps:
            raise SystemExit(f"still in PlayerService: {bad}")
    print("sanity ok")


if __name__ == "__main__":
    main()
