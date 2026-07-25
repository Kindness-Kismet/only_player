# -*- coding: utf-8 -*-
"""v18: remove AUDIO_ONLY; keep HIDE stable; top end 24dp; scale remember only per-file."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent


def must_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"MISSING: {label}")
    return text.replace(old, new, 1)


def patch_player_preferences_model() -> None:
    p = ROOT / "core/model/src/main/java/one/only/player/core/model/PlayerPreferences.kt"
    t = p.read_text(encoding="utf-8")
    old = """enum class MediaSessionVisibility {
    SHOW,
    HIDE,
    AUDIO_ONLY,
}"""
    new = """enum class MediaSessionVisibility {
    SHOW,
    HIDE,
}"""
    t = must_replace(t, old, new, "MediaSessionVisibility enum")
    p.write_text(t, encoding="utf-8")
    print("model: AUDIO_ONLY removed")


def patch_player_service() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    t = p.read_text(encoding="utf-8")

    # Remove mp3 helpers entirely
    old_helpers = """    /**
     * 与扩展名解码配置同样的路径候选：file/content/remote/title，只认后缀 .mp3。
     */
    private fun mediaItemPathCandidates(mediaItem: MediaItem?): List<String> {
        if (mediaItem == null) return emptyList()
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        return listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.title?.toString(),
        )
    }

    private fun pathLooksLikeMp3(pathOrName: String?): Boolean {
        if (pathOrName.isNullOrBlank()) return false
        val clean = pathOrName
            .substringAfterLast('/')
            .substringAfterLast('\\\\')
            .substringBefore('?')
            .substringBefore('#')
        val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        return ext.equals("mp3", ignoreCase = true)
    }

    private fun isMp3MediaItem(mediaItem: MediaItem?): Boolean {
        // 仅按 .mp3 后缀（与扩展名解码同一套候选），不看 mime
        return mediaItemPathCandidates(mediaItem).any(::pathLooksLikeMp3)
    }

"""
    # try both escaped and single-backslash forms that may exist in file
    if old_helpers not in t:
        old_helpers = """    /**
     * 与扩展名解码配置同样的路径候选：file/content/remote/title，只认后缀 .mp3。
     */
    private fun mediaItemPathCandidates(mediaItem: MediaItem?): List<String> {
        if (mediaItem == null) return emptyList()
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        return listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.title?.toString(),
        )
    }

    private fun pathLooksLikeMp3(pathOrName: String?): Boolean {
        if (pathOrName.isNullOrBlank()) return false
        val clean = pathOrName
            .substringAfterLast('/')
            .substringAfterLast('\\\\')
            .substringBefore('?')
            .substringBefore('#')
        val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        return ext.equals("mp3", ignoreCase = true)
    }

    private fun isMp3MediaItem(mediaItem: MediaItem?): Boolean {
        // 仅按 .mp3 后缀（与扩展名解码同一套候选），不看 mime
        return mediaItemPathCandidates(mediaItem).any(::pathLooksLikeMp3)
    }

"""
    # Use regex remove from mediaItemPathCandidates through isMp3MediaItem
    t2, n = re.subn(
        r"\n    /\*\*\n     \* 与扩展名解码配置同样的路径候选：[\s\S]*?private fun isMp3MediaItem\(mediaItem: MediaItem\?\): Boolean \{\n(?:.*\n)*?    \}\n\n",
        "\n",
        t,
        count=1,
    )
    if n != 1:
        # fallback: line-based
        lines = t.splitlines(keepends=True)
        out = []
        skip = False
        removed = False
        for i, line in enumerate(lines):
            if "private fun mediaItemPathCandidates" in line or (
                "与扩展名解码配置同样的路径候选" in line and not removed
            ):
                # if comment block starts a few lines earlier, drop comment
                # drop from previous non-empty blank already added
                skip = True
                removed = True
                # also drop preceding comment block if just written
                while out and (out[-1].strip().startswith("*") or out[-1].strip().startswith("/**") or out[-1].strip() == "" or out[-1].strip() == "*/"):
                    if out[-1].strip().startswith("/**") or (out[-1].strip() == "" and len(out) > 1 and "路径候选" in "".join(out[-5:])):
                        # pop comment
                        # simpler: pop until blank before comment
                        pass
                    if "/**" in out[-1] or out[-1].strip() == "/**":
                        out.pop()
                        # pop blanks
                        while out and out[-1].strip() == "":
                            out.pop()
                        break
                    if out[-1].strip().startswith("*") or out[-1].strip() == "*/":
                        out.pop()
                        continue
                    break
                continue
            if skip:
                if line.startswith("    private fun ") and "isMp3MediaItem" not in line and "pathLooksLikeMp3" not in line and "mediaItemPathCandidates" not in line:
                    skip = False
                    out.append(line)
                elif line.startswith("    /**") and removed and "真正从系统媒体中心消失" in "".join(lines[i:i+5]):
                    skip = False
                    out.append(line)
                continue
            out.append(line)
        t = "".join(out)
        print("service: removed mp3 helpers via scan")
    else:
        t = t2
        print("service: removed mp3 helpers via regex")

    # Simplify publish helpers to SHOW/HIDE only
    old_pub = """    private fun shouldPublishMediaSessionNotificationForVisibility(): Boolean {
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(mediaSession?.player?.currentMediaItem)
        }
    }

    private fun shouldPublishMediaSessionNotification(session: MediaSession): Boolean {
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(session.player.currentMediaItem)
        }
    }"""
    new_pub = """    private fun shouldPublishMediaSessionNotificationForVisibility(): Boolean {
        // 仅 SHOW / HIDE。仅 MP3 已删除。
        return playerPreferences.mediaSessionVisibility == MediaSessionVisibility.SHOW
    }

    private fun shouldPublishMediaSessionNotification(session: MediaSession): Boolean {
        // session 参数保留以兼容调用点；可见性只看设置。
        return playerPreferences.mediaSessionVisibility == MediaSessionVisibility.SHOW
    }"""
    t = must_replace(t, old_pub, new_pub, "publish helpers")

    # Clean comments mentioning 非 MP3
    t = t.replace("HIDE / 非 MP3：", "HIDE：")
    t = t.replace("HIDE/非MP3", "HIDE")
    t = t.replace("HIDE / 非 MP3", "HIDE")

    # onMediaItemTransition no longer needs mp3-specific recheck but keep updateLegacy — fine

    p.write_text(t, encoding="utf-8")
    print("PlayerService done")


def patch_settings_ui() -> None:
    p = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesScreen.kt"
    t = p.read_text(encoding="utf-8")
    t = must_replace(
        t,
        """                    description = when (uiState.preferences.mediaSessionVisibility) {
                        MediaSessionVisibility.SHOW -> stringResource(R.string.media_session_visibility_show)
                        MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                        MediaSessionVisibility.AUDIO_ONLY -> stringResource(R.string.media_session_visibility_audio_only)
                    },""",
        """                    description = when (uiState.preferences.mediaSessionVisibility) {
                        MediaSessionVisibility.SHOW -> stringResource(R.string.media_session_visibility_show)
                        MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                    },""",
        "settings description",
    )
    t = must_replace(
        t,
        """                                text = when (it) {
                                    MediaSessionVisibility.SHOW -> stringResource(R.string.media_session_visibility_show)
                                    MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                                    MediaSessionVisibility.AUDIO_ONLY -> stringResource(R.string.media_session_visibility_audio_only)
                                },""",
        """                                text = when (it) {
                                    MediaSessionVisibility.SHOW -> stringResource(R.string.media_session_visibility_show)
                                    MediaSessionVisibility.HIDE -> stringResource(R.string.media_session_visibility_hide)
                                },""",
        "settings dialog options",
    )
    p.write_text(t, encoding="utf-8")
    print("settings UI done")


def patch_scale_remember() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
    t = p.read_text(encoding="utf-8")
    # Match decoder: when remembering for file, do NOT write global playerVideoZoom
    # Decoder still writes playerPreferences for decoder because service listens to it;
    # for scale, global write causes all files to change. Only write per-file.
    old = """    fun rememberVideoContentScaleForFile(fileName: String?, contentScale: VideoContentScale) {
        val key = PerFilePlaybackPreference.fromPathOrName(fileName) ?: return
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { current ->
                current.withPerFileVideoContentScale(key, contentScale)
            }
            preferencesRepository.updatePlayerPreferences {
                it.copy(playerVideoZoom = contentScale)
            }
        }
    }"""
    new = """    fun rememberVideoContentScaleForFile(fileName: String?, contentScale: VideoContentScale) {
        val key = PerFilePlaybackPreference.fromPathOrName(fileName) ?: return
        viewModelScope.launch {
            // 只写 per-file 缓存，不改全局 playerVideoZoom，避免所有文件一起变
            preferencesRepository.updateApplicationPreferences { current ->
                current.withPerFileVideoContentScale(key, contentScale)
            }
        }
    }"""
    t = must_replace(t, old, new, "rememberVideoContentScaleForFile")

    # When not remembering, updateVideoContentScale still writes global — good.
    # When remember toggle off, clear file and do not force global.
    p.write_text(t, encoding="utf-8")
    print("ViewModel scale remember fixed")

    # MediaPlayerScreen: when remember ON and change scale, only call remember; when OFF call update global
    # Also apply per-file without writing global on media change (already only applies to state)
    ms = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    mt = ms.read_text(encoding="utf-8")
    old_click = """                                onVideoContentScaleChanged = { scale ->
                                    selectedScale = scale
                                    videoZoomAndContentScaleState.onVideoContentScaleChanged(scale)
                                    if (isRememberScaleForThisFile) {
                                        viewModel.rememberVideoContentScaleForFile(fileName, scale)
                                    } else {
                                        viewModel.updateVideoContentScale(scale)
                                    }
                                },"""
    new_click = """                                onVideoContentScaleChanged = { scale ->
                                    selectedScale = scale
                                    // 当前画面立即生效
                                    videoZoomAndContentScaleState.onVideoContentScaleChanged(scale)
                                    if (isRememberScaleForThisFile) {
                                        // 仅按文件名写入 per-file 配置，不改全局默认缩放
                                        viewModel.rememberVideoContentScaleForFile(fileName, scale)
                                    } else {
                                        // 未开启记住：写全局默认
                                        viewModel.updateVideoContentScale(scale)
                                    }
                                },"""
    mt = must_replace(mt, old_click, new_click, "scale click wiring")
    ms.write_text(mt, encoding="utf-8")
    print("MediaPlayerScreen scale wiring")


def patch_top_padding() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsTopView.kt"
    t = p.read_text(encoding="utf-8")
    t = t.replace(".padding(start = 8.dp, end = 26.dp)", ".padding(start = 8.dp, end = 24.dp)")
    t = t.replace("旧版右上角最右再加 10dp（相对进度条上方有效 16dp → 26dp）", "旧版右上角最右间距 24dp")
    p.write_text(t, encoding="utf-8")
    print("top end=24dp")


def bump_version() -> None:
    p = ROOT / "app/build.gradle.kts"
    t = p.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 151", "versionCode = 152").replace(
        'versionName = "1.0.150"',
        'versionName = "1.0.151"',
    )
    if t2 == t:
        print("version not bumped", re.findall(r"versionCode = \d+|versionName = \"[^\"]+\"", t))
    else:
        p.write_text(t2, encoding="utf-8")
        print("version 1.0.151 / 152")


def main() -> None:
    patch_player_preferences_model()
    patch_player_service()
    patch_settings_ui()
    patch_scale_remember()
    patch_top_padding()
    bump_version()
    # sanity
    ps = (ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt").read_text(encoding="utf-8")
    assert "AUDIO_ONLY" not in ps, "AUDIO_ONLY still in PlayerService"
    assert "isMp3MediaItem" not in ps, "isMp3 still present"
    assert "shouldPublishMediaSessionNotification" in ps
    assert "updateLegacyMediaSessionActive" in ps
    print("sanity ok")


if __name__ == "__main__":
    main()
