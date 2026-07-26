# -*- coding: utf-8 -*-
"""Instant apply for per-file decoder/scale on media transitions."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
PC = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerContentFrame.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    if "import androidx.media3.common.ForwardingPlayer" not in t:
        t = t.replace(
            "import androidx.media3.common.Player\n",
            "import androidx.media3.common.ForwardingPlayer\nimport androidx.media3.common.Player\n",
            1,
        )

    if "private fun unwrapExoPlayer" not in t:
        anchor = "    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n"
        if anchor not in t:
            raise SystemExit("activeDecoderPriority field missing")
        helpers = """    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC

    private fun unwrapExoPlayer(player: Player?): ExoPlayer? = when (player) {
        is ExoPlayer -> player
        is ForwardingPlayer -> player.wrappedPlayer as? ExoPlayer
        else -> null
    }

    private fun currentExoPlayer(): ExoPlayer? = unwrapExoPlayer(mediaSession?.player)

    /**
     * 下一首/上一首前同步解码：先按目标 item 重建解码器，再 seek，避免错误解码先播再延迟切换。
     */
    private fun seekToMediaItemWithDecoderReady(targetIndex: Int, positionMs: Long = C.TIME_UNSET) {
        val session = mediaSession ?: return
        val player = unwrapExoPlayer(session.player) ?: return
        if (targetIndex !in 0 until player.mediaItemCount) return
        val targetItem = player.getMediaItemAt(targetIndex)
        val targetDecoder = resolveDecoderPriorityForMediaItem(targetItem)
        if (targetDecoder != activeDecoderPriority) {
            Logger.info(
                TAG,
                "Pre-switch decoder before seek: index=$targetIndex from=${activeDecoderPriority.logName()} to=${targetDecoder.logName()}",
            )
            switchPlayerDecoderPriority(
                decoderPriority = targetDecoder,
                forcedIndex = targetIndex,
                forcedPositionMs = if (positionMs == C.TIME_UNSET) 0L else positionMs,
            )
            return
        }
        if (positionMs == C.TIME_UNSET) {
            player.seekToDefaultPosition(targetIndex)
        } else {
            player.seekTo(targetIndex, positionMs)
        }
        player.prepare()
    }

"""
        t = t.replace(anchor, helpers, 1)

    t = t.replace(
        "currentPlayerProvider = { mediaSession?.player as? ExoPlayer }",
        "currentPlayerProvider = { currentExoPlayer() }",
    )
    t = t.replace("mediaSession?.player as? ExoPlayer", "currentExoPlayer()")
    t = t.replace("session.player as? ExoPlayer", "unwrapExoPlayer(session.player)")
    # keep failedPlayer cast via unwrap
    t = t.replace(
        "val failedPlayer = unwrapExoPlayer(session.player) ?: return false",
        "val failedPlayer = unwrapExoPlayer(session.player) ?: return false",
    )

    old_switch = """    private fun switchPlayerDecoderPriority(decoderPriority: DecoderPriority) {
        if (decoderPriority == activeDecoderPriority) return
        val session = mediaSession ?: return
        val currentPlayer = unwrapExoPlayer(session.player) ?: return
        // 先停住旧解码器输出，避免错误解码先出几帧再黑屏切换
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        currentPlayer.playWhenReady = false
        runCatching { currentPlayer.pause() }
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
            currentPlayer.release()
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = currentPlayer.currentMediaItemIndex.coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = currentPlayer.currentPosition.coerceAtLeast(0L)
"""
    # if previous cast not replaced yet
    old_switch_alt = old_switch.replace(
        "val currentPlayer = unwrapExoPlayer(session.player) ?: return",
        "val currentPlayer = session.player as? ExoPlayer ?: return",
    )
    new_switch = """    private fun switchPlayerDecoderPriority(
        decoderPriority: DecoderPriority,
        forcedIndex: Int? = null,
        forcedPositionMs: Long? = null,
    ) {
        if (decoderPriority == activeDecoderPriority && forcedIndex == null) return
        val session = mediaSession ?: return
        val currentPlayer = unwrapExoPlayer(session.player) ?: return
        // 先停住旧解码器输出，避免错误解码先出几帧再黑屏切换
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        currentPlayer.playWhenReady = false
        runCatching { currentPlayer.pause() }
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
            session.player = DecoderAwarePlayer(nextPlayer)
            currentPlayer.release()
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = (forcedIndex ?: currentPlayer.currentMediaItemIndex).coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = (forcedPositionMs ?: currentPlayer.currentPosition).coerceAtLeast(0L)
"""
    if old_switch in t:
        t = t.replace(old_switch, new_switch, 1)
    elif old_switch_alt in t:
        t = t.replace(old_switch_alt, new_switch, 1)
    else:
        # already patched?
        if "forcedIndex: Int? = null" not in t:
            idx = t.find("private fun switchPlayerDecoderPriority")
            raise SystemExit("switchPlayerDecoderPriority block missing:\n" + t[idx : idx + 500])

    # wrap assignments
    t = t.replace("session.player = nextPlayer", "session.player = DecoderAwarePlayer(nextPlayer)")
    t = t.replace("session.player = retryPlayer", "session.player = DecoderAwarePlayer(retryPlayer)")
    # avoid double wrap
    t = t.replace(
        "session.player = DecoderAwarePlayer(DecoderAwarePlayer(nextPlayer))",
        "session.player = DecoderAwarePlayer(nextPlayer)",
    )
    t = t.replace(
        "session.player = DecoderAwarePlayer(DecoderAwarePlayer(retryPlayer))",
        "session.player = DecoderAwarePlayer(retryPlayer)",
    )

    t = t.replace(
        """        try {
            mediaSession = MediaSession.Builder(this, player).apply {
""",
        """        try {
            mediaSession = MediaSession.Builder(this, DecoderAwarePlayer(player)).apply {
""",
    )
    t = t.replace(
        "mediaSession = MediaSession.Builder(this, DecoderAwarePlayer(DecoderAwarePlayer(player))).apply {",
        "mediaSession = MediaSession.Builder(this, DecoderAwarePlayer(player)).apply {",
    )

    if "private inner class DecoderAwarePlayer" not in t:
        marker = """    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
"""
        if marker not in t:
            raise SystemExit("onGetSession marker missing")
        cls = """
    /**
     * 包装 ExoPlayer：切下一首/上一首前先按目标文件/扩展名准备解码，避免错误解码先播再延迟切换。
     */
    private inner class DecoderAwarePlayer(
        private val exo: ExoPlayer,
    ) : ForwardingPlayer(exo) {
        override fun seekToNextMediaItem() {
            if (!hasNextMediaItem()) return
            seekToMediaItemWithDecoderReady(currentMediaItemIndex + 1)
        }

        override fun seekToPreviousMediaItem() {
            if (!hasPreviousMediaItem()) return
            seekToMediaItemWithDecoderReady(currentMediaItemIndex - 1)
        }

        override fun seekToNext() {
            if (hasNextMediaItem()) {
                seekToNextMediaItem()
            } else {
                super.seekToNext()
            }
        }

        override fun seekToPrevious() {
            if (hasPreviousMediaItem()) {
                seekToPreviousMediaItem()
            } else {
                super.seekToPrevious()
            }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (mediaItemIndex != currentMediaItemIndex) {
                seekToMediaItemWithDecoderReady(mediaItemIndex, positionMs)
            } else {
                super.seekTo(mediaItemIndex, positionMs)
            }
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            if (mediaItemIndex != currentMediaItemIndex) {
                seekToMediaItemWithDecoderReady(mediaItemIndex, C.TIME_UNSET)
            } else {
                super.seekToDefaultPosition(mediaItemIndex)
            }
        }
    }

"""
        t = t.replace(marker, marker + cls, 1)

    PS.write_text(t, encoding="utf-8")
    print("PlayerService patched")


def patch_media_player_screen() -> None:
    t = MS.read_text(encoding="utf-8")
    old = """    DisposableEffect(player) {
        viewModel.updatePlaybackMarkMediaItem(player.currentMediaItem)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                viewModel.updatePlaybackMarkMediaItem(mediaItem)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
"""
    new = """    DisposableEffect(
        player,
        applicationPreferences.perFilePlaybackPreferences,
        playerPreferences.playerVideoZoom,
    ) {
        viewModel.updatePlaybackMarkMediaItem(player.currentMediaItem)
        fun applyScaleForCurrentItem() {
            val mediaItem = player.currentMediaItem
            val uri = mediaItem?.localConfiguration?.uri
            val candidates = listOfNotNull(
                mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
                mediaItem?.mediaMetadata?.remoteFilePath,
                uri?.path,
                uri?.lastPathSegment,
                mediaItem?.mediaMetadata?.title?.toString(),
                mediaItem?.requestMetadata?.mediaUri?.path,
                mediaItem?.requestMetadata?.mediaUri?.lastPathSegment,
                mediaItem?.mediaId,
                uri?.toString(),
            )
            var fileName: String? = null
            for (candidate in candidates) {
                val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
                if (!name.isNullOrBlank() && name.contains('.')) {
                    fileName = name
                    break
                }
            }
            if (fileName == null) {
                for (candidate in candidates) {
                    val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
                    if (!name.isNullOrBlank()) {
                        fileName = name
                        break
                    }
                }
            }
            val remembered = applicationPreferences.perFilePreferenceForPath(fileName)?.videoContentScale
            val target = remembered ?: playerPreferences.playerVideoZoom
            // 切文件立刻本地应用，不走全局写回
            videoZoomAndContentScaleState.applyContentScaleLocally(target)
        }
        applyScaleForCurrentItem()
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                viewModel.updatePlaybackMarkMediaItem(mediaItem)
                applyScaleForCurrentItem()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
"""
    if old not in t:
        if "applyScaleForCurrentItem" in t:
            print("MediaPlayerScreen already has applyScaleForCurrentItem")
        else:
            raise SystemExit("DisposableEffect listener block missing")
    else:
        t = t.replace(old, new, 1)
        print("MediaPlayerScreen scale listener patched")
    MS.write_text(t, encoding="utf-8")


def patch_surface_delay() -> None:
    t = PC.read_text(encoding="utf-8")
    old = """    LaunchedEffect(decoderPriority) {
        if (previousDecoderPriority == decoderPriority) return@LaunchedEffect
        previousDecoderPriority = decoderPriority
        surfaceRefreshKey++
        delay(120)
        surfaceRefreshKey++
    }
"""
    new = """    LaunchedEffect(decoderPriority) {
        if (previousDecoderPriority == decoderPriority) return@LaunchedEffect
        previousDecoderPriority = decoderPriority
        // 解码切换后尽快重建 surface；缩短二次 refresh，减少“几秒后才正常”
        surfaceRefreshKey++
        delay(40)
        surfaceRefreshKey++
    }
"""
    if old in t:
        PC.write_text(t.replace(old, new, 1), encoding="utf-8")
        print("surface delay shortened")
    else:
        print("surface delay already patched or missing")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 155", "versionCode = 156").replace(
        'versionName = "1.0.154"',
        'versionName = "1.0.155"',
    )
    if t2 == t:
        print("version not bumped", re.findall(r'versionCode = \d+|versionName = "[^"]+"', t))
    else:
        GRADLE.write_text(t2, encoding="utf-8")
        print("version 1.0.155 / 156")


def main() -> None:
    patch_player_service()
    patch_media_player_screen()
    patch_surface_delay()
    bump()
    ps = PS.read_text(encoding="utf-8")
    assert "DecoderAwarePlayer" in ps
    assert "seekToMediaItemWithDecoderReady" in ps
    assert "ForwardingPlayer" in ps
    print("sanity ok")


if __name__ == "__main__":
    main()
