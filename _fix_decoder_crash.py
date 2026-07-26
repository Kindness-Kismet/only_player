# -*- coding: utf-8 -*-
"""Fix decoder switch crash: remove ForwardingPlayer, safe player swap, custom next/prev."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
CC = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/CustomCommands.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")
    t = t.replace("import androidx.media3.common.ForwardingPlayer\n", "")

    # remove unwrap helpers + seekToMediaItemWithDecoderReady
    m = re.search(
        r"\n    private fun unwrapExoPlayer[\s\S]*?player\.prepare\(\)\n    \}\n\n",
        t,
    )
    if m:
        t = t[: m.start()] + "\n" + t[m.end() :]
        print("removed unwrap/seek helpers")
    else:
        print("helpers already removed?")

    # remove DecoderAwarePlayer class
    m = re.search(
        r"\n    /\*\*\n     \* 包装 ExoPlayer：[\s\S]*?private inner class DecoderAwarePlayer[\s\S]*?\n    \}\n",
        t,
    )
    if not m:
        m = re.search(r"\n    private inner class DecoderAwarePlayer[\s\S]*?\n    \}\n", t)
    if m:
        t = t[: m.start()] + "\n" + t[m.end() :]
        print("removed DecoderAwarePlayer")
    else:
        print("DecoderAwarePlayer missing")

    t = t.replace("currentExoPlayer()", "(mediaSession?.player as? ExoPlayer)")
    t = re.sub(r"unwrapExoPlayer\(([^)]+)\)", r"(\1 as? ExoPlayer)", t)
    t = re.sub(r"session\.player = DecoderAwarePlayer\(([^)]+)\)", r"session.player = \1", t)
    t = t.replace(
        "MediaSession.Builder(this, DecoderAwarePlayer(player))",
        "MediaSession.Builder(this, player)",
    )

    # replace switchPlayerDecoderPriority whole function
    m = re.search(
        r"    private fun switchPlayerDecoderPriority\([\s\S]*?\n    private fun applyAmbienceModeToPlayer",
        t,
    )
    if not m:
        raise SystemExit("switchPlayerDecoderPriority not found")
    new_switch = """    private fun switchPlayerDecoderPriority(
        decoderPriority: DecoderPriority,
        forcedIndex: Int? = null,
        forcedPositionMs: Long? = null,
    ) {
        if (decoderPriority == activeDecoderPriority && forcedIndex == null) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        val mediaItems = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
        if (mediaItems.isEmpty()) {
            Logger.info(TAG, "Switch decoder to ${decoderPriority.logName()} without active media items")
            val nextPlayer = createPlayer(
                decoderPriority = decoderPriority,
                assHandler = assHandler ?: return,
            )
            audioEffectsCoordinator.releaseLoudnessEnhancer()
            currentPlayer.removeListener(playbackStateListener)
            currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
            session.player = nextPlayer
            serviceScope.launch {
                kotlinx.coroutines.delay(50)
                runCatching { currentPlayer.release() }
            }
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = (forcedIndex ?: currentPlayer.currentMediaItemIndex).coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = (forcedPositionMs ?: currentPlayer.currentPosition).coerceAtLeast(0L)
        val playbackParameters = currentPlayer.playbackParameters
        val trackSelectionParameters = currentPlayer.trackSelectionParameters
        val shuffleModeEnabled = currentPlayer.shuffleModeEnabled
        val repeatMode = currentPlayer.repeatMode
        val isSkipSilenceEnabled = currentPlayer.isSkipSilenceEnabledForPlayer
        val subtitleDelayMilliseconds = currentPlayer.playerSpecificSubtitleDelayMilliseconds
        val subtitleSpeed = currentPlayer.playerSpecificSubtitleSpeed
        val currentDecoderPriority = activeDecoderPriority
        val nextPlayer = createPlayer(
            decoderPriority = decoderPriority,
            assHandler = assHandler ?: return,
        )
        Logger.info(
            TAG,
            "Switch decoder from ${currentDecoderPriority.logName()} to ${decoderPriority.logName()} at index=$currentIndex position=$playbackPosition",
        )

        // 先填好 playlist 并 prepare，再交给 session，避免 MediaController 读到空/半截 timeline 崩溃
        nextPlayer.setMediaItems(mediaItems, currentIndex, playbackPosition)
        nextPlayer.restoreRuntimeState(
            trackSelectionParameters = trackSelectionParameters,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            isSkipSilenceEnabled = isSkipSilenceEnabled,
            subtitleDelayMilliseconds = subtitleDelayMilliseconds,
            subtitleSpeed = subtitleSpeed,
            playbackParameters = playbackParameters,
            mediaItemIndex = currentIndex,
            positionMs = playbackPosition,
        )
        nextPlayer.playWhenReady = false
        nextPlayer.prepare()

        audioEffectsCoordinator.releaseLoudnessEnhancer()
        currentPlayer.removeListener(playbackStateListener)
        currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
        session.player = nextPlayer
        nextPlayer.playWhenReady = shouldPlayWhenReady
        videoEffectsCoordinator.updateAvailability(nextPlayer)
        applyAmbienceModeToPlayer(nextPlayer)

        // 延后释放旧实例，避免 PresentationState 读 RemotableTimeline 时空窗崩溃
        serviceScope.launch {
            kotlinx.coroutines.delay(100)
            runCatching {
                currentPlayer.clearMediaItems()
                currentPlayer.stop()
                currentPlayer.release()
            }
        }
    }

    private fun applyAmbienceModeToPlayer"""
    t = t[: m.start()] + new_switch + t[m.end() :]

    # add SEEK_TO_MEDIA_ITEM handler if missing
    if "CustomCommands.SEEK_TO_MEDIA_ITEM" not in t:
        needle = "                CustomCommands.PRECISE_SEEK_TO -> {"
        insert = """                CustomCommands.SEEK_TO_MEDIA_ITEM -> {
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
                    val target = player.getMediaItemAt(index)
                    val targetDecoder = resolveDecoderPriorityForMediaItem(target)
                    if (targetDecoder != activeDecoderPriority) {
                        switchPlayerDecoderPriority(
                            decoderPriority = targetDecoder,
                            forcedIndex = index,
                            forcedPositionMs = if (positionMs == C.TIME_UNSET) 0L else positionMs,
                        )
                    } else if (positionMs == C.TIME_UNSET) {
                        player.seekToDefaultPosition(index)
                        player.prepare()
                    } else {
                        player.seekTo(index, positionMs)
                        player.prepare()
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.PRECISE_SEEK_TO -> {"""
        if needle not in t:
            raise SystemExit("PRECISE_SEEK_TO missing")
        t = t.replace(needle, insert, 1)
        print("SEEK_TO_MEDIA_ITEM handler added")

    PS.write_text(t, encoding="utf-8")
    print("PlayerService updated")


def patch_custom_commands() -> None:
    t = CC.read_text(encoding="utf-8")
    if "SEEK_TO_MEDIA_ITEM" not in t:
        t = t.replace(
            '    GET_VIDEO_FORMAT(customAction = "GET_VIDEO_FORMAT"),\n    ;',
            '    GET_VIDEO_FORMAT(customAction = "GET_VIDEO_FORMAT"),\n'
            '    SEEK_TO_MEDIA_ITEM(customAction = "SEEK_TO_MEDIA_ITEM"),\n'
            "    ;",
        )
        t = t.replace(
            '        const val IS_VIDEO_EFFECTS_ACTIVE_KEY = "is_video_effects_active"\n    }',
            '        const val IS_VIDEO_EFFECTS_ACTIVE_KEY = "is_video_effects_active"\n'
            '        const val MEDIA_ITEM_INDEX_KEY = "media_item_index"\n'
            '        const val MEDIA_ITEM_POSITION_MS_KEY = "media_item_position_ms"\n'
            "    }",
        )
        t += """

fun MediaController.seekToMediaItemPrepared(index: Int, positionMs: Long = androidx.media3.common.C.TIME_UNSET) {
    val args = Bundle().apply {
        putInt(CustomCommands.MEDIA_ITEM_INDEX_KEY, index)
        putLong(CustomCommands.MEDIA_ITEM_POSITION_MS_KEY, positionMs)
    }
    sendCustomCommand(CustomCommands.SEEK_TO_MEDIA_ITEM.sessionCommand, args)
}

fun MediaController.seekToNextPrepared() {
    val next = currentMediaItemIndex + 1
    if (next < mediaItemCount) {
        seekToMediaItemPrepared(next)
    } else {
        seekToNext()
    }
}

fun MediaController.seekToPreviousPrepared() {
    val prev = currentMediaItemIndex - 1
    if (prev >= 0) {
        seekToMediaItemPrepared(prev)
    } else {
        seekToPrevious()
    }
}
"""
        CC.write_text(t, encoding="utf-8")
        print("CustomCommands updated")
    else:
        print("CustomCommands already has SEEK_TO_MEDIA_ITEM")


def patch_ui() -> None:
    t = MS.read_text(encoding="utf-8")
    if "seekToNextPrepared" not in t:
        if "import one.only.player.feature.player.service.seekToNextPrepared" not in t:
            t = (
                "import one.only.player.feature.player.service.seekToNextPrepared\n"
                "import one.only.player.feature.player.service.seekToPreviousPrepared\n"
                + t
            )
        t = t.replace(
            "onNextClick = { player.seekToNext() }",
            "onNextClick = {\n"
            "                                            val controller = player as? androidx.media3.session.MediaController\n"
            "                                            if (controller != null) controller.seekToNextPrepared() else player.seekToNext()\n"
            "                                        }",
        )
        t = t.replace(
            "onPreviousClick = { player.seekToPrevious() }",
            "onPreviousClick = {\n"
            "                                            val controller = player as? androidx.media3.session.MediaController\n"
            "                                            if (controller != null) controller.seekToPreviousPrepared() else player.seekToPrevious()\n"
            "                                        }",
        )
        t = t.replace(
            "player.seekToNext()",
            "(player as? androidx.media3.session.MediaController)?.seekToNextPrepared() ?: player.seekToNext()",
        )
        MS.write_text(t, encoding="utf-8")
        print("UI patched")
    else:
        print("UI already patched")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 156", "versionCode = 157").replace(
        'versionName = "1.0.155"',
        'versionName = "1.0.156"',
    )
    if t2 == t:
        print("version not bumped", re.findall(r'versionCode = \d+|versionName = "[^"]+"', t))
    else:
        GRADLE.write_text(t2, encoding="utf-8")
        print("version 1.0.156")


def main() -> None:
    patch_player_service()
    patch_custom_commands()
    patch_ui()
    bump()
    t = PS.read_text(encoding="utf-8")
    for bad in ["DecoderAwarePlayer", "ForwardingPlayer", "unwrapExoPlayer", "seekToMediaItemWithDecoderReady"]:
        if bad in t:
            raise SystemExit(f"still present: {bad}")
    assert "SEEK_TO_MEDIA_ITEM" in t
    assert "delay(100)" in t
    print("sanity ok")


if __name__ == "__main__":
    main()
