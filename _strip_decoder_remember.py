# -*- coding: utf-8 -*-
"""Strip broken per-item decoder remember + mid-playlist ExoPlayer recreate.
Keep only global/extension decoder at player create (MX-like: pick decoder before play).
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


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # remove imports for stamp helpers
    t = t.replace("import one.only.player.feature.player.extensions.decoderPriorityName\n", "")
    t = t.replace("import one.only.player.feature.player.extensions.isDecoderRemembered\n", "")
    t = t.replace("import one.only.player.feature.player.extensions.contentScaleName\n", "")

    # remove isDecoderSwitchInFlight field
    t = t.replace("    private var isDecoderSwitchInFlight: Boolean = false\n", "")

    # simplify onMediaItemTransition - no decoder switch
    t = re.sub(
        r"        override fun onMediaItemTransition\(mediaItem: MediaItem\?, reason: Int\) \{\n"
        r"            // 先按[\s\S]*?super\.onMediaItemTransition\(mediaItem, reason\)\n",
        """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
""",
        t,
        count=1,
    )
    # if still has switch inside transition, force simple version
    if "switchPlayerDecoderPriority" in t[t.find("onMediaItemTransition") : t.find("onMediaItemTransition") + 800]:
        t = re.sub(
            r"        override fun onMediaItemTransition\(mediaItem: MediaItem\?, reason: Int\) \{[\s\S]*?"
            r"            hasPausedAtEndOfQueue = false\n",
            """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            hasPausedAtEndOfQueue = false
""",
            t,
            count=1,
        )
        print("transition stripped")

    # remove maybePreSwitch calls and method
    t = t.replace("                maybePreSwitchDecoderForUpcomingItem(player)\n", "")
    t = re.sub(
        r"\n        override fun onEvents\(player: Player, events: Player\.Events\) \{\n"
        r"            super\.onEvents\(player, events\)\n"
        r"            if \(events\.contains\(Player\.EVENT_PLAYBACK_STATE_CHANGED\) \|\|\n"
        r"                events\.contains\(Player\.EVENT_POSITION_DISCONTINUITY\) \|\|\n"
        r"                events\.contains\(Player\.EVENT_IS_PLAYING_CHANGED\)\n"
        r"            \) \{\n"
        r"                maybePreSwitchDecoderForUpcomingItem\(player\)\n"
        r"            \}\n"
        r"        \}\n",
        "\n",
        t,
        count=1,
    )
    t = re.sub(
        r"\n    /\*\*\n     \* 自动连播前：[\s\S]*?private fun maybePreSwitchDecoderForUpcomingItem\(player: Player\) \{[\s\S]*?\n    \}\n",
        "\n",
        t,
        count=1,
    )
    t = re.sub(
        r"\n    private fun maybePreSwitchDecoderForUpcomingItem\(player: Player\) \{[\s\S]*?\n    \}\n",
        "\n",
        t,
        count=1,
    )

    # simplify applyExtensionDecoder - still allow preference change for CURRENT only via simple recreate
    # Replace switchPlayerDecoderPriority with safe full-playlist synchronous recreate (no single-item corruption)
    m = re.search(
        r"    private fun switchPlayerDecoderPriority\([\s\S]*?\n    private fun applyAmbienceModeToPlayer",
        t,
    )
    if m:
        new_switch = """    /**
     * 仅在全局/扩展名解码偏好变更时重建。中途切条不再换解码器（避免黑屏）。
     * 同步整表 setMediaItems，不做 single-item 半残 playlist。
     */
    private fun switchPlayerDecoderPriority(decoderPriority: DecoderPriority) {
        if (decoderPriority == activeDecoderPriority) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        val mediaItems = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
        val currentIndex = currentPlayer.currentMediaItemIndex.coerceAtLeast(0)
        val playbackPosition = currentPlayer.currentPosition.coerceAtLeast(0L)
        val trackSelectionParameters = currentPlayer.trackSelectionParameters
        val shuffleModeEnabled = currentPlayer.shuffleModeEnabled
        val repeatMode = currentPlayer.repeatMode
        val isSkipSilenceEnabled = currentPlayer.isSkipSilenceEnabledForPlayer
        val subtitleDelayMilliseconds = currentPlayer.playerSpecificSubtitleDelayMilliseconds
        val subtitleSpeed = currentPlayer.playerSpecificSubtitleSpeed
        val playbackParameters = currentPlayer.playbackParameters
        Logger.info(
            TAG,
            "Recreate player for preference decoder=${decoderPriority.logName()} index=$currentIndex",
        )
        val nextPlayer = createPlayer(
            decoderPriority = decoderPriority,
            assHandler = assHandler ?: return,
        )
        if (mediaItems.isNotEmpty()) {
            val idx = currentIndex.coerceIn(0, mediaItems.lastIndex)
            nextPlayer.setMediaItems(mediaItems, idx, playbackPosition)
            nextPlayer.trackSelectionParameters = trackSelectionParameters
            nextPlayer.shuffleModeEnabled = shuffleModeEnabled
            nextPlayer.repeatMode = repeatMode
            nextPlayer.playbackParameters = playbackParameters
            nextPlayer.isSkipSilenceEnabledForPlayer = isSkipSilenceEnabled
            nextPlayer.playerSpecificSubtitleDelayMilliseconds = subtitleDelayMilliseconds
            nextPlayer.playerSpecificSubtitleSpeed = subtitleSpeed
        }
        nextPlayer.playWhenReady = shouldPlayWhenReady
        nextPlayer.prepare()
        audioEffectsCoordinator.releaseLoudnessEnhancer()
        currentPlayer.removeListener(playbackStateListener)
        currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
        session.player = nextPlayer
        videoEffectsCoordinator.updateAvailability(nextPlayer)
        applyAmbienceModeToPlayer(nextPlayer)
        runCatching {
            currentPlayer.clearMediaItems()
            currentPlayer.stop()
            currentPlayer.release()
        }
    }

    private fun applyAmbienceModeToPlayer"""
        t = t[: m.start()] + new_switch + t[m.end() :]
        print("switch simplified full-playlist only")

    # applyExtensionDecoderForMediaItem - only switch if preference-driven, NOT on every transition
    # Keep for preference collectors; make it call switch without forced index
    t = t.replace(
        """    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) return
        val target = resolveDecoderPriorityForMediaItem(mediaItem)
        if (target != activeDecoderPriority) {
            switchPlayerDecoderPriority(target)
        }
    }
""",
        """    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {
        // 仅用于偏好变更时对齐当前解码，不在切条路径调用
        if (mediaItem == null) return
        val target = resolveDecoderPriorityForMediaItem(mediaItem)
        if (target != activeDecoderPriority) {
            switchPlayerDecoderPriority(target)
        }
    }
""",
    )

    # resolve: NO mediaItem stamp / NO videoState stamp - only extension + global
    m = re.search(
        r"    private fun resolveDecoderPriorityForMediaItem\(mediaItem: MediaItem\): DecoderPriority \{[\s\S]*?\n    \}\n",
        t,
    )
    if m:
        t = t[: m.start()] + """    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
        // 干净路径：扩展名配置 → 全局（不再用文件记住/盖章，避免切条重建黑屏）
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
""" + t[m.end() :]
        print("resolve cleaned")

    # remove resolveContentScaleForMediaItem if present
    t = re.sub(
        r"\n    private fun resolveContentScaleForMediaItem\(mediaItem: MediaItem\): VideoContentScale\? \{[\s\S]*?\n    \}\n",
        "\n",
        t,
        count=1,
    )

    # remove stamp block in updatedMediaItemsWithMetadata
    t = re.sub(
        r"\n                // 与续播 position 相同：[\s\S]*?val stampedScale = resolveContentScaleForMediaItem\(mediaItem\)\n",
        "\n",
        t,
        count=1,
    )
    t = re.sub(
        r"\n                // 像续播 position 一样[\s\S]*?val stampedScale = resolveContentScaleForMediaItem\(mediaItem\)\n",
        "\n",
        t,
        count=1,
    )
    t = re.sub(
        r"\n                val stateDecoder = videoState\?\.decoderPriority[\s\S]*?val stampedDecoder = stateDecoder \?: resolveDecoderPriorityForMediaItem\(mediaItem\)\n"
        r"(?:                val stampedScale = resolveContentScaleForMediaItem\(mediaItem\)\n)?",
        "\n",
        t,
        count=1,
    )
    # fix setExtras that reference stampedDecoder
    t = t.replace(
        "isVideoEffectsAvailable = shouldApplyVideoEffects(stampedDecoder),",
        "isVideoEffectsAvailable = shouldApplyVideoEffects(activeDecoderPriority),",
    )
    t = re.sub(
        r"\n\s*decoderPriority = stampedDecoder\.name,\n\s*contentScale = stampedScale\?\.name,\n(?:\s*isDecoderRemembered = isDecoderRememberedForItem,\n)?",
        "\n",
        t,
    )
    t = re.sub(
        r"\n\s*decoderPriority = stampedDecoder\.name,\n",
        "\n",
        t,
    )
    t = re.sub(
        r"\n\s*isDecoderRemembered = isDecoderRememberedForItem,\n",
        "\n",
        t,
    )
    t = re.sub(
        r"\n\s*contentScale = stampedScale\?\.name,\n",
        "\n",
        t,
    )

    # SEEK_TO_MEDIA_ITEM: plain seek, no decoder switch
    t = re.sub(
        r"                CustomCommands\.SEEK_TO_MEDIA_ITEM -> \{[\s\S]*?return@future SessionResult\(SessionResult\.RESULT_SUCCESS\)\n                \}\n\n",
        """                CustomCommands.SEEK_TO_MEDIA_ITEM -> {
                    val index = args.getInt(CustomCommands.MEDIA_ITEM_INDEX_KEY, -1)
                    val positionMs = args.getLong(
                        CustomCommands.MEDIA_ITEM_POSITION_MS_KEY,
                        C.TIME_UNSET,
                    )
                    val player = mediaSession?.player as? ExoPlayer
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    if (index !in 0 until player.mediaItemCount) {
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }
                    if (positionMs == C.TIME_UNSET) {
                        player.seekToDefaultPosition(index)
                    } else {
                        player.seekTo(index, positionMs)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

""",
        t,
        count=1,
    )

    # remove VideoContentScale import if unused
    if "VideoContentScale" not in t:
        t = t.replace("import one.only.player.core.model.VideoContentScale\n", "")

    PS.write_text(t, encoding="utf-8")
    print("PlayerService cleaned")


def patch_media_player_screen() -> None:
    t = MS.read_text(encoding="utf-8")
    t = t.replace("import one.only.player.feature.player.extensions.decoderPriorityName\n", "")
    t = t.replace("import one.only.player.feature.player.extensions.isDecoderRemembered\n", "")
    # simplify decoder menu: no remember switch wiring to DB - keep UI switch as no-op local only or hide callbacks
    old = re.search(
        r"                        MenuRoute\.Decoder -> \{[\s\S]*?onDismiss = ::dismissOverlay,\n                            \)\n",
        t,
    )
    if old:
        new = """                        MenuRoute.Decoder -> {
                            val fileName = currentMediaFileName()
                            val mediaExtension = fileName
                                ?.substringAfterLast('.', missingDelimiterValue = "")
                                ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                ?.lowercase()
                            val initial = mediaExtension?.let { ext ->
                                applicationPreferences.normalizedExtensionDecoderPreferences()
                                    .firstOrNull { it.extension == ext }
                                    ?.decoderPriority
                            } ?: playerPreferences.decoderPriority
                            var selectedPriority by remember(fileName, playerPreferences.decoderPriority) {
                                mutableStateOf(initial)
                            }
                            DecoderPrioritySelectorContent(
                                currentDecoderPriority = selectedPriority,
                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    // 仅改全局默认；扩展名请在设置里改。不再做 per-file 中途换解码。
                                    viewModel.updateDecoderPriority(priority)
                                },
                                isRememberForThisFileEnabled = false,
                                onRememberForThisFileChanged = null,
                                onDismiss = ::dismissOverlay,
                            )
"""
        t = t[: old.start()] + new + t[old.end() :]
        print("decoder menu simplified")
    else:
        print("decoder menu block not found")

    # remove currentMediaUriString if only used for decoder remember
    t = re.sub(
        r"\n    fun currentMediaUriString\(\): String\? \{\n"
        r"        val mediaItem = player\.currentMediaItem \?: return null\n"
        r"        return mediaItem\.localConfiguration\?\.uri\?\.toString\(\)\n"
        r"            \?: mediaItem\.mediaId\.takeIf \{ it\.isNotBlank\(\) \}\n"
        r"    \}\n",
        "\n",
        t,
        count=1,
    )
    MS.write_text(t, encoding="utf-8")
    print("MediaPlayerScreen cleaned")


def patch_viewmodel() -> None:
    t = VM.read_text(encoding="utf-8")
    # strip remember decoder methods to no-ops or remove body calls
    t = re.sub(
        r"    /\*\*\n     \* 记住解码：[\s\S]*?fun setRememberDecoderForFile\([\s\S]*?\n    \}\n",
        """    // per-file 解码记住已移除（中途换解码会黑屏）；保留空实现避免旧调用编译失败
    fun rememberDecoderForMediaUri(mediaUri: String?, decoderPriority: DecoderPriority) = Unit
    fun clearDecoderForMediaUri(mediaUri: String?) = Unit
    fun setRememberDecoderForMediaUri(
        mediaUri: String?,
        decoderPriority: DecoderPriority,
        isEnabled: Boolean,
    ) = Unit
    fun rememberDecoderForFile(fileName: String?, decoderPriority: DecoderPriority) = Unit
    fun clearDecoderForFile(fileName: String?) = Unit
    fun setRememberDecoderForFile(
        fileName: String?,
        decoderPriority: DecoderPriority,
        isEnabled: Boolean,
    ) = Unit

""",
        t,
        count=1,
    )
    VM.write_text(t, encoding="utf-8")
    print("ViewModel remember no-op")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t = t.replace("versionCode = 160", "versionCode = 161")
    t = t.replace('versionName = "1.0.159"', 'versionName = "1.0.160"')
    GRADLE.write_text(t, encoding="utf-8")
    print("version 1.0.160")


def main() -> None:
    patch_player_service()
    patch_media_player_screen()
    patch_viewmodel()
    bump()
    ps = PS.read_text(encoding="utf-8")
    for bad in [
        "maybePreSwitch",
        "isDecoderSwitchInFlight",
        "stampedDecoder",
        "single-item first",
        "isDecoderRememberedForItem",
    ]:
        if bad in ps:
            print("WARN still in service:", bad)
    print("done")


if __name__ == "__main__":
    main()
