# -*- coding: utf-8 -*-
"""
Playback decoder panel writes extension decoder settings (sync both ways).
Per-file remember writes media_state.decoder_priority like resume position.
Resolve: media_state URI > extension > global.
Switch on panel change only (not on every playlist hop).
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
VM = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
CC = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/CustomCommands.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_custom_commands() -> None:
    t = CC.read_text(encoding="utf-8")
    if "REMEMBER_FOR_FILE_KEY" not in t:
        t = t.replace(
            '        const val DECODER_PRIORITY_NAME_KEY = "decoder_priority_name"\n    }',
            '        const val DECODER_PRIORITY_NAME_KEY = "decoder_priority_name"\n'
            '        const val REMEMBER_FOR_FILE_KEY = "remember_for_file"\n'
            "    }",
        )
        t = t.replace(
            """fun MediaController.setDecoderPriorityNow(priorityName: String) {
    val args = Bundle().apply {
        putString(CustomCommands.DECODER_PRIORITY_NAME_KEY, priorityName)
    }
    sendCustomCommand(CustomCommands.SET_DECODER_PRIORITY.sessionCommand, args)
}
""",
            """fun MediaController.setDecoderPriorityNow(
    priorityName: String,
    rememberForThisFile: Boolean = false,
) {
    val args = Bundle().apply {
        putString(CustomCommands.DECODER_PRIORITY_NAME_KEY, priorityName)
        putBoolean(CustomCommands.REMEMBER_FOR_FILE_KEY, rememberForThisFile)
    }
    sendCustomCommand(CustomCommands.SET_DECODER_PRIORITY.sessionCommand, args)
}
""",
        )
        CC.write_text(t, encoding="utf-8")
        print("CustomCommands args")
    else:
        print("CustomCommands already has remember flag")


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # resolve: media_state stamp/name > extension > global
    # Keep simple resolve but ensure MediaItem stamp is used when present
    m = re.search(
        r"    private fun resolveDecoderPriorityForMediaItem\(mediaItem: MediaItem\): DecoderPriority \{[\s\S]*?\n    \}\n",
        t,
    )
    if m:
        t = (
            t[: m.start()]
            + """    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
        // 优先级：该文件(media_state 盖章) > 扩展名设置 > 全局
        mediaItem.mediaMetadata.decoderPriorityName
            ?.takeIf { mediaItem.mediaMetadata.isDecoderRemembered }
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
            + t[m.end() :]
        )
        print("resolve priority order fixed")

    # imports for stamp props
    if "import one.only.player.feature.player.extensions.decoderPriorityName" not in t:
        t = t.replace(
            "import one.only.player.feature.player.extensions.remoteFilePath\n",
            "import one.only.player.feature.player.extensions.remoteFilePath\n"
            "import one.only.player.feature.player.extensions.decoderPriorityName\n"
            "import one.only.player.feature.player.extensions.isDecoderRemembered\n",
        )

    # SET_DECODER_PRIORITY: write extension + optional file remember + switch
    old_set = """                CustomCommands.SET_DECODER_PRIORITY -> {
                    val name = args.getString(CustomCommands.DECODER_PRIORITY_NAME_KEY).orEmpty()
                    val priority = runCatching { DecoderPriority.valueOf(name) }.getOrNull()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    // 立即按所选解码重建当前播放（不经扩展名覆盖）
                    switchPlayerDecoderPriority(priority)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
"""
    new_set = """                CustomCommands.SET_DECODER_PRIORITY -> {
                    val name = args.getString(CustomCommands.DECODER_PRIORITY_NAME_KEY).orEmpty()
                    val priority = runCatching { DecoderPriority.valueOf(name) }.getOrNull()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    val rememberForFile = args.getBoolean(CustomCommands.REMEMBER_FOR_FILE_KEY, false)
                    val player = mediaSession?.player as? ExoPlayer
                    val mediaItem = player?.currentMediaItem
                    if (mediaItem != null) {
                        // 控件改解码：同步写回扩展名设置（双向同步）
                        updateExtensionDecoderFromManualSelection(mediaItem, priority)
                        if (rememberForFile) {
                            // 与续播进度相同：按 URI 写 media_state.decoder_priority
                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)
                            mediaRepository.updateMediumDecoderPriority(
                                uri = playbackStateUri,
                                decoderPriority = priority.name,
                            )
                        } else {
                            // 关记住：清掉该文件的 DB 解码
                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)
                            mediaRepository.updateMediumDecoderPriority(
                                uri = playbackStateUri,
                                decoderPriority = null,
                            )
                        }
                    }
                    // 立即按所选值重建当前播放
                    switchPlayerDecoderPriority(priority)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
"""
    if old_set in t:
        t = t.replace(old_set, new_set, 1)
        print("SET_DECODER writes extension+file")
    else:
        print("SET_DECODER block missing exact")

    # stamp from videoState when building items (per-file)
    if "isDecoderRememberedForItem" not in t:
        # inject near mediaItem.buildUpon in updatedMediaItemsWithMetadata
        marker = "                mediaItem.buildUpon().apply {\n                    setSubtitleConfigurations(mergedSubConfigurations)"
        if marker in t:
            t = t.replace(
                marker,
                """                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val isDecoderRememberedForItem = stateDecoder != null
                val stampedDecoderName = stateDecoder?.name

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(mergedSubConfigurations)""",
                1,
            )
            # add extras if possible
            if "decoderPriority = stampedDecoderName" not in t:
                t = t.replace(
                    "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n",
                    "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n"
                    "                                decoderPriority = stampedDecoderName,\n"
                    "                                isDecoderRemembered = isDecoderRememberedForItem,\n",
                    1,
                )
            print("stamp media_state decoder on MediaItem")
        else:
            print("WARN stamp marker missing")

    # Global prefs collector: still exact value (ok). Extension collector applies resolve.
    # When extension list changes, apply for current is fine.

    PS.write_text(t, encoding="utf-8")
    print("PlayerService ok")


def patch_viewmodel() -> None:
    t = VM.read_text(encoding="utf-8")
    # restore real remember implementations
    t = re.sub(
        r"    // per-file 解码记住已移除[\s\S]*?fun setRememberDecoderForFile\([\s\S]*?\) = Unit\n\n",
        """    fun rememberDecoderForMediaUri(mediaUri: String?, decoderPriority: DecoderPriority) {
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

""",
        t,
        count=1,
    )
    VM.write_text(t, encoding="utf-8")
    print("ViewModel remember restored")


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
    if "import one.only.player.feature.player.extensions.decoderPriorityName" not in t:
        t = t.replace(
            "package one.only.player.feature.player\n",
            "package one.only.player.feature.player\n\n"
            "import one.only.player.feature.player.extensions.decoderPriorityName\n"
            "import one.only.player.feature.player.extensions.isDecoderRemembered\n",
            1,
        )

    # replace decoder menu fully
    m = re.search(
        r"                        MenuRoute\.Decoder -> \{[\s\S]*?onDismiss = ::dismissOverlay,\n                            \)\n",
        t,
    )
    new = """                        MenuRoute.Decoder -> {
                            val fileName = currentMediaFileName()
                            val mediaUri = currentMediaUriString()
                            val mediaExtension = fileName
                                ?.substringAfterLast('.', missingDelimiterValue = "")
                                ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                ?.lowercase()
                            // 显示优先级：该文件记住 > 扩展名设置 > 全局
                            val remembered = player.currentMediaItem?.mediaMetadata
                                ?.takeIf { it.isDecoderRemembered }
                                ?.decoderPriorityName
                                ?.let { runCatching { one.only.player.core.model.DecoderPriority.valueOf(it) }.getOrNull() }
                            val extensionPriority = mediaExtension?.let { ext ->
                                applicationPreferences.normalizedExtensionDecoderPreferences()
                                    .firstOrNull { it.extension == ext }
                                    ?.decoderPriority
                            }
                            val initial = remembered
                                ?: extensionPriority
                                ?: playerPreferences.decoderPriority
                            var selectedPriority by remember(player.currentMediaItem?.mediaId) {
                                mutableStateOf(initial)
                            }
                            var isRememberForThisFile by remember(player.currentMediaItem?.mediaId) {
                                mutableStateOf(player.currentMediaItem?.mediaMetadata?.isDecoderRemembered == true)
                            }
                            DecoderPrioritySelectorContent(
                                currentDecoderPriority = selectedPriority,
                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    // 写入扩展名设置（双向同步）+ 可选该文件 + 立刻重建
                                    val controller = player as? androidx.media3.session.MediaController
                                    controller?.setDecoderPriorityNow(
                                        priorityName = priority.name,
                                        rememberForThisFile = isRememberForThisFile,
                                    )
                                    // 同步全局默认，方便设置页一致
                                    viewModel.updateDecoderPriority(priority)
                                },
                                isRememberForThisFileEnabled = isRememberForThisFile,
                                onRememberForThisFileChanged = { enabled ->
                                    isRememberForThisFile = enabled
                                    val controller = player as? androidx.media3.session.MediaController
                                    // 开关变化：按当前选项重新提交一次（写/清 media_state，并写扩展名）
                                    controller?.setDecoderPriorityNow(
                                        priorityName = selectedPriority.name,
                                        rememberForThisFile = enabled,
                                    )
                                    if (enabled) {
                                        viewModel.rememberDecoderForMediaUri(mediaUri, selectedPriority)
                                    } else {
                                        viewModel.clearDecoderForMediaUri(mediaUri)
                                    }
                                },
                                onDismiss = ::dismissOverlay,
                            )
"""
    if m:
        t = t[: m.start()] + new + t[m.end() :]
        print("decoder menu rewired")
    else:
        print("decoder menu not found")

    MS.write_text(t, encoding="utf-8")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t = t.replace("versionCode = 163", "versionCode = 164")
    t = t.replace('versionName = "1.0.162"', 'versionName = "1.0.163"')
    GRADLE.write_text(t, encoding="utf-8")
    print("version 1.0.163")


def main() -> None:
    patch_custom_commands()
    patch_player_service()
    patch_viewmodel()
    patch_media_player_screen()
    bump()
    print("done")


if __name__ == "__main__":
    main()
