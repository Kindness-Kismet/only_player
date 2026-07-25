# -*- coding: utf-8 -*-
"""Store decoder like resume position in media_state by URI; stamp MediaItem; resolve from DB/stamp."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
VM = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # rewrite resolveDecoderPriorityForMediaItem fully
    m = re.search(
        r"    private fun resolveDecoderPriorityForMediaItem\(mediaItem: MediaItem\): DecoderPriority \{[\s\S]*?\n    \}\n",
        t,
    )
    if not m:
        raise SystemExit("resolveDecoderPriorityForMediaItem not found")
    new_fn = """    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
        // 与续播 position 相同：MediaItem extras 盖章 →（盖章时已含 media_state）→ 扩展名 → 全局
        mediaItem.mediaMetadata.decoderPriorityName
            ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
            ?.let { return it }

        val uri = mediaItem.localConfiguration?.uri
        val pathCandidates = listOfNotNull(
            uri?.let { candidateUri ->
                when (candidateUri.scheme) {
                    ContentResolver.SCHEME_FILE -> candidateUri.path
                    ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                    else -> candidateUri.path
                }
            },
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            mediaItem.mediaId,
            mediaItem.mediaMetadata.title?.toString(),
        )
        val appPreferences = preferencesRepository.applicationPreferences.value
        for (candidate in pathCandidates) {
            val clean = candidate
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isNotBlank() && ext.length <= 10 && ext.all { it.isLetterOrDigit() }) {
                appPreferences.decoderPriorityForPath(clean)?.let { return it }
            }
        }
        return playerPreferences.decoderPriority
    }
"""
    t = t[: m.start()] + new_fn + t[m.end() :]

    # stamp from videoState.decoderPriority
    if "videoState?.decoderPriority" not in t:
        t = t.replace(
            """                // 像续播 position 一样，把解码/缩放盖到 MediaItem，切集时 O(1) 读取
                val stampedDecoder = resolveDecoderPriorityForMediaItem(
                    // 临时用当前 mediaItem 解析（此时 extras 可能还没有 stamp）
                    mediaItem,
                )
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)
""",
            """                // 与续播 position 相同：DB media_state.decoder_priority → 扩展名/全局，再盖章
                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val stampedDecoder = stateDecoder
                    ?: resolveDecoderPriorityForMediaItem(mediaItem)
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)
""",
        )
        if "videoState?.decoderPriority" not in t:
            t = t.replace(
                "val stampedDecoder = resolveDecoderPriorityForMediaItem(mediaItem)",
                "val stateDecoder = videoState?.decoderPriority\n"
                "                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }\n"
                "                val stampedDecoder = stateDecoder ?: resolveDecoderPriorityForMediaItem(mediaItem)",
                1,
            )
        print("stamp from videoState")
    else:
        print("stamp already uses videoState")

    # ensure setExtras includes decoderPriority = stampedDecoder.name
    if "decoderPriority = stampedDecoder.name" not in t:
        # try add into setExtras block of updatedMediaItemsWithMetadata
        if "decoderPriority = stampedDecoder" not in t:
            t = t.replace(
                "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n",
                "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n"
                "                                decoderPriority = stampedDecoder.name,\n"
                "                                contentScale = stampedScale?.name,\n",
                1,
            )
            print("added decoderPriority to setExtras")

    PS.write_text(t, encoding="utf-8")
    print("PlayerService ok")


def patch_viewmodel() -> None:
    t = VM.read_text(encoding="utf-8")
    # replace rememberDecoder* functions
    m = re.search(
        r"    fun rememberDecoderForFile\([\s\S]*?fun setRememberDecoderForFile\([\s\S]*?\n    \}\n",
        t,
    )
    new = """    /**
     * 记住解码：与续播进度相同，按媒体 URI 写入 media_state.decoder_priority。
     */
    fun rememberDecoderForMediaUri(mediaUri: String?, decoderPriority: DecoderPriority) {
        if (mediaUri.isNullOrBlank()) return
        viewModelScope.launch {
            mediaRepository.updateMediumDecoderPriority(mediaUri, decoderPriority.name)
        }
    }

    fun clearDecoderForMediaUri(mediaUri: String?) {
        if (mediaUri.isNullOrBlank()) return
        viewModelScope.launch {
            mediaRepository.updateMediumDecoderPriority(mediaUri, null)
        }
    }

    fun setRememberDecoderForMediaUri(
        mediaUri: String?,
        decoderPriority: DecoderPriority,
        isEnabled: Boolean,
    ) {
        if (isEnabled) {
            rememberDecoderForMediaUri(mediaUri, decoderPriority)
        } else {
            clearDecoderForMediaUri(mediaUri)
        }
    }

    fun rememberDecoderForFile(fileName: String?, decoderPriority: DecoderPriority) {
        rememberDecoderForMediaUri(fileName, decoderPriority)
    }

    fun clearDecoderForFile(fileName: String?) {
        clearDecoderForMediaUri(fileName)
    }

    fun setRememberDecoderForFile(
        fileName: String?,
        decoderPriority: DecoderPriority,
        isEnabled: Boolean,
    ) {
        setRememberDecoderForMediaUri(fileName, decoderPriority, isEnabled)
    }
"""
    if m:
        t = t[: m.start()] + new + t[m.end() :]
        print("ViewModel remember rewritten")
    elif "updateMediumDecoderPriority" in t:
        print("ViewModel already updated")
    else:
        raise SystemExit("rememberDecoderForFile block not found")

    if "private val mediaRepository" not in t and "mediaRepository:" not in t and "MediaRepository" in t:
        print("mediaRepository seems injected")
    elif "mediaRepository" not in t:
        print("WARNING mediaRepository may be missing in ViewModel")
    VM.write_text(t, encoding="utf-8")


def patch_media_player_screen() -> None:
    t = MS.read_text(encoding="utf-8")
    if "fun currentMediaUriString()" not in t:
        t = t.replace(
            "    fun currentMediaFileName(): String? {",
            """    fun currentMediaUriString(): String? {
        val mediaItem = player.currentMediaItem ?: return null
        return mediaItem.localConfiguration?.uri?.toString()
            ?: mediaItem.mediaId.takeIf { it.isNotBlank() }
    }

    fun currentMediaFileName(): String? {""",
            1,
        )
    t = t.replace(
        "viewModel.rememberDecoderForFile(fileName, priority)",
        "viewModel.rememberDecoderForMediaUri(currentMediaUriString(), priority)",
    )
    t = t.replace(
        """                                    viewModel.setRememberDecoderForFile(
                                        fileName = fileName,
                                        decoderPriority = selectedPriority,
                                        isEnabled = enabled,
                                    )""",
        """                                    viewModel.setRememberDecoderForMediaUri(
                                        mediaUri = currentMediaUriString(),
                                        decoderPriority = selectedPriority,
                                        isEnabled = enabled,
                                    )""",
    )
    # UI remember switch from metadata stamp
    t = t.replace(
        "val perFileDecoder = applicationPreferences.perFilePreferenceForPath(fileName)?.decoderPriority",
        "val perFileDecoder = player.currentMediaItem?.mediaMetadata?.decoderPriorityName\n"
        "                                ?.let { runCatching { one.only.player.core.model.DecoderPriority.valueOf(it) }.getOrNull() }\n"
        "                                ?: applicationPreferences.perFilePreferenceForPath(fileName)?.decoderPriority",
    )
    if "import one.only.player.feature.player.extensions.decoderPriorityName" not in t:
        t = t.replace(
            "package one.only.player.feature.player\n",
            "package one.only.player.feature.player\n\n"
            "import one.only.player.feature.player.extensions.decoderPriorityName\n",
            1,
        )
    MS.write_text(t, encoding="utf-8")
    print("MediaPlayerScreen updated")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    for a, b in [
        ("versionCode = 158", "versionCode = 159"),
        ("versionCode = 157", "versionCode = 159"),
        ('versionName = "1.0.157"', 'versionName = "1.0.158"'),
        ('versionName = "1.0.156"', 'versionName = "1.0.158"'),
    ]:
        t = t.replace(a, b)
    GRADLE.write_text(t, encoding="utf-8")
    print("version bumped")


def main() -> None:
    patch_player_service()
    patch_viewmodel()
    patch_media_player_screen()
    bump()
    print("done")


if __name__ == "__main__":
    main()
