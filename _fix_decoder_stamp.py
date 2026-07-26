# -*- coding: utf-8 -*-
"""Rewrite decoder/scale apply: stamp on MediaItem like resume position; prebuild next player."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MI = ROOT / "feature/player/src/main/java/one/only/player/feature/player/extensions/MediaItem.kt"
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_media_item() -> None:
    t = MI.read_text(encoding="utf-8")
    if "MEDIA_METADATA_DECODER_PRIORITY_KEY" not in t:
        t = t.replace(
            'private const val MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY = "media_metadata_remote_directory_path"\n',
            'private const val MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY = "media_metadata_remote_directory_path"\n'
            'private const val MEDIA_METADATA_DECODER_PRIORITY_KEY = "media_metadata_decoder_priority"\n'
            'private const val MEDIA_METADATA_CONTENT_SCALE_KEY = "media_metadata_content_scale"\n',
        )
        # Bundle.setExtras params
        t = t.replace(
            """    remoteDirectoryPath: String? = null,
) = apply {
""",
            """    remoteDirectoryPath: String? = null,
    decoderPriority: String? = null,
    contentScale: String? = null,
) = apply {
""",
        )
        t = t.replace(
            """    remoteDirectoryPath?.let { putString(MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY, it) }
}
""",
            """    remoteDirectoryPath?.let { putString(MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY, it) }
    decoderPriority?.let { putString(MEDIA_METADATA_DECODER_PRIORITY_KEY, it) }
    contentScale?.let { putString(MEDIA_METADATA_CONTENT_SCALE_KEY, it) }
}
""",
        )
        # Builder.setExtras params + pass-through
        t = t.replace(
            """    remoteDirectoryPath: String? = null,
): MediaMetadata.Builder = setExtras(
    Bundle().setExtras(
""",
            """    remoteDirectoryPath: String? = null,
    decoderPriority: String? = null,
    contentScale: String? = null,
): MediaMetadata.Builder = setExtras(
    Bundle().setExtras(
""",
        )
        t = t.replace(
            """        remoteDirectoryPath = remoteDirectoryPath,
    ).apply {
        requestHeaders.forEach { (key, value) ->
""",
            """        remoteDirectoryPath = remoteDirectoryPath,
        decoderPriority = decoderPriority,
        contentScale = contentScale,
    ).apply {
        requestHeaders.forEach { (key, value) ->
""",
        )
        # accessors after remoteDirectoryPath
        t = t.replace(
            """val MediaMetadata.remoteDirectoryPath: String?
    get() = extras?.getString(MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY)
        ?.takeIf(String::isNotBlank)

fun MediaItem.copy(
""",
            """val MediaMetadata.remoteDirectoryPath: String?
    get() = extras?.getString(MEDIA_METADATA_REMOTE_DIRECTORY_PATH_KEY)
        ?.takeIf(String::isNotBlank)

val MediaMetadata.decoderPriorityName: String?
    get() = extras?.getString(MEDIA_METADATA_DECODER_PRIORITY_KEY)
        ?.takeIf(String::isNotBlank)

val MediaMetadata.contentScaleName: String?
    get() = extras?.getString(MEDIA_METADATA_CONTENT_SCALE_KEY)
        ?.takeIf(String::isNotBlank)

fun MediaItem.copy(
""",
        )
        # MediaItem.copy params + setExtras
        t = t.replace(
            """    remoteDirectoryPath: String? = this.mediaMetadata.remoteDirectoryPath,
): MediaItem = buildUpon().setMediaMetadata(
""",
            """    remoteDirectoryPath: String? = this.mediaMetadata.remoteDirectoryPath,
    decoderPriorityName: String? = this.mediaMetadata.decoderPriorityName,
    contentScaleName: String? = this.mediaMetadata.contentScaleName,
): MediaItem = buildUpon().setMediaMetadata(
""",
        )
        t = t.replace(
            """                remoteDirectoryPath = remoteDirectoryPath,
            ).apply {
                requestHeaders.forEach { (key, value) ->
                    putString("$MEDIA_METADATA_REQUEST_HEADERS_PREFIX$key", value)
                }
            },
        ).build(),
).build()
""",
            """                remoteDirectoryPath = remoteDirectoryPath,
                decoderPriority = decoderPriorityName,
                contentScale = contentScaleName,
            ).apply {
                requestHeaders.forEach { (key, value) ->
                    putString("$MEDIA_METADATA_REQUEST_HEADERS_PREFIX$key", value)
                }
            },
        ).build(),
).build()
""",
        )
        MI.write_text(t, encoding="utf-8")
        print("MediaItem extras stamped")
    else:
        print("MediaItem already stamped")


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")

    # ensure imports for VideoContentScale
    if "import one.only.player.core.model.VideoContentScale" not in t:
        t = t.replace(
            "import one.only.player.core.model.DecoderPriority\n",
            "import one.only.player.core.model.DecoderPriority\n"
            "import one.only.player.core.model.VideoContentScale\n",
        )
    if "import one.only.player.feature.player.extensions.contentScaleName" not in t:
        # extensions used via mediaMetadata.decoderPriorityName - need import for property
        if "import one.only.player.feature.player.extensions.remoteFilePath" in t:
            t = t.replace(
                "import one.only.player.feature.player.extensions.remoteFilePath\n",
                "import one.only.player.feature.player.extensions.remoteFilePath\n"
                "import one.only.player.feature.player.extensions.decoderPriorityName\n"
                "import one.only.player.feature.player.extensions.contentScaleName\n",
            )
        else:
            # add after package imports area - find first feature.player.extensions import
            m = re.search(r"import one\.only\.player\.feature\.player\.extensions\.\w+\n", t)
            if m:
                t = t[: m.end()] + (
                    "import one.only.player.feature.player.extensions.decoderPriorityName\n"
                    "import one.only.player.feature.player.extensions.contentScaleName\n"
                ) + t[m.end() :]
            else:
                t = t.replace(
                    "import one.only.player.feature.player.service.",
                    "import one.only.player.feature.player.extensions.decoderPriorityName\n"
                    "import one.only.player.feature.player.extensions.contentScaleName\n"
                    "import one.only.player.feature.player.service.",
                    1,
                )

    # rewrite resolveDecoderPriorityForMediaItem to prefer metadata stamp
    old_resolve = """    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        val pathCandidates = listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.title?.toString(),
        )
        val appPreferences = preferencesRepository.applicationPreferences.value
        // 1) 文件名级配置优先
        for (candidate in pathCandidates) {
            val fileName = PerFilePlaybackPreference.fromPathOrName(candidate) ?: continue
            appPreferences.perFilePreferenceForPath(fileName)?.decoderPriority?.let { return it }
        }
        // 2) 扩展名配置
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
        // 3) 全局默认
        return playerPreferences.decoderPriority
    }
"""
    new_resolve = """    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
        // 0) 与续播 position 一样：优先读 MediaItem 上已盖章的解码配置（秒级命中，不靠事后猜路径）
        mediaItem.mediaMetadata.decoderPriorityName
            ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
            ?.let { return it }

        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        val pathCandidates = listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.title?.toString(),
        )
        val appPreferences = preferencesRepository.applicationPreferences.value
        // 1) 文件名级配置
        for (candidate in pathCandidates) {
            val fileName = PerFilePlaybackPreference.fromPathOrName(candidate) ?: continue
            appPreferences.perFilePreferenceForPath(fileName)?.decoderPriority?.let { return it }
        }
        // 2) 扩展名配置
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
        // 3) 全局默认
        return playerPreferences.decoderPriority
    }

    private fun resolveContentScaleForMediaItem(mediaItem: MediaItem): VideoContentScale? {
        mediaItem.mediaMetadata.contentScaleName
            ?.let { name -> runCatching { VideoContentScale.valueOf(name) }.getOrNull() }
            ?.let { return it }
        val uri = mediaItem.localConfiguration?.uri
        val candidates = listOfNotNull(
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.path,
            uri?.lastPathSegment,
            mediaItem.mediaMetadata.title?.toString(),
            mediaItem.mediaId,
        )
        val app = preferencesRepository.applicationPreferences.value
        for (candidate in candidates) {
            val fileName = PerFilePlaybackPreference.fromPathOrName(candidate) ?: continue
            app.perFilePreferenceForPath(fileName)?.videoContentScale?.let { return it }
        }
        return null
    }
"""
    if old_resolve not in t:
        if "decoderPriorityName" in t and "0) 与续播" in t:
            print("resolve already rewritten")
        else:
            raise SystemExit("resolveDecoderPriorityForMediaItem block not found")
    else:
        t = t.replace(old_resolve, new_resolve, 1)
        print("resolve rewritten")

    # Stamp decoder/scale when building metadata in updatedMediaItemsWithMetadata
    # Find setMediaMetadata block with setTitle(title) and inject stamp before build
    stamp_snippet = """                val stampedDecoder = resolveDecoderPriorityForMediaItem(mediaItem)
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)
"""
    if "stampedDecoder" not in t:
        # insert before mediaItem.buildUpon in updatedMediaItemsWithMetadata
        needle = "                mediaItem.buildUpon().apply {\n                    setSubtitleConfigurations(mergedSubConfigurations)\n                    setMediaMetadata(\n                        MediaMetadata.Builder().apply {\n                            setTitle(title)\n"
        if needle not in t:
            # try find approximate
            idx = t.find("setSubtitleConfigurations(mergedSubConfigurations)")
            print("needle context:", t[idx - 200 : idx + 300] if idx >= 0 else "none")
            raise SystemExit("stamp needle missing")
        insert_before = """                // 像续播 position 一样，把解码/缩放盖到 MediaItem，切集时 O(1) 读取
                val stampedDecoder = resolveDecoderPriorityForMediaItem(
                    // 临时用当前 mediaItem 解析（此时 extras 可能还没有 stamp）
                    mediaItem,
                )
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(mergedSubConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
"""
        # Wait, resolve uses path which works before stamp - good.
        # Need to pass decoderPriority to setExtras call below
        t = t.replace(needle, insert_before, 1)

        # add to setExtras call inside that builder
        # Find setExtras after setTitle in that function - the one with positionMs = positionMs
        # There may be multiple; replace carefully within updatedMediaItemsWithMetadata
        # Look for isApproximateSeekEnabled = isApproximateSeekEnabled in that block and add after
        old_extras_tail = """                                isApproximateSeekEnabled = isApproximateSeekEnabled,
                                isVideoEffectsAvailable = shouldApplyVideoEffects(activeDecoderPriority),
                                requestHeaders = mediaItem.mediaMetadata.requestHeaders,
                                remoteServerId = mediaItem.mediaMetadata.remoteServerId,
                                remoteFilePath = mediaItem.mediaMetadata.remoteFilePath,
                                remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
                                localParentPath = mediaItem.mediaMetadata.localParentPath,
                                remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,
"""
        new_extras_tail = """                                isApproximateSeekEnabled = isApproximateSeekEnabled,
                                isVideoEffectsAvailable = shouldApplyVideoEffects(stampedDecoder),
                                requestHeaders = mediaItem.mediaMetadata.requestHeaders,
                                remoteServerId = mediaItem.mediaMetadata.remoteServerId,
                                remoteFilePath = mediaItem.mediaMetadata.remoteFilePath,
                                remoteProtocol = mediaItem.mediaMetadata.remoteProtocol,
                                localParentPath = mediaItem.mediaMetadata.localParentPath,
                                remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,
                                decoderPriority = stampedDecoder.name,
                                contentScale = stampedScale?.name,
"""
        if old_extras_tail not in t:
            raise SystemExit("extras tail missing")
        t = t.replace(old_extras_tail, new_extras_tail, 1)
        print("stamped extras in updatedMediaItemsWithMetadata")

    # Pre-warm next item decoder near end + faster switch
    # Add field pending next decoder switch
    if "private var isDecoderSwitchInFlight" not in t:
        t = t.replace(
            "    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n",
            "    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n"
            "    private var isDecoderSwitchInFlight: Boolean = false\n",
        )

    # Improve onMediaItemTransition to always apply decoder first and log resolution
    old_trans = """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 先按文件/扩展名切解码，再 super，减少错误解码先起播导致的黑屏
            applyExtensionDecoderForMediaItem(mediaItem)
            super.onMediaItemTransition(mediaItem, reason)
"""
    new_trans = """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 先按 MediaItem 盖章/文件/扩展名切到正确解码，再 super
            if (mediaItem != null) {
                val target = resolveDecoderPriorityForMediaItem(mediaItem)
                Logger.info(
                    TAG,
                    "MediaItemTransition reason=$reason decoder=${target.logName()} active=${activeDecoderPriority.logName()} media=${mediaItem.mediaId.toPrivateMediaLogSummary()}",
                )
                if (target != activeDecoderPriority && !isDecoderSwitchInFlight) {
                    applyExtensionDecoderForMediaItem(mediaItem)
                }
            }
            super.onMediaItemTransition(mediaItem, reason)
"""
    if old_trans in t:
        t = t.replace(old_trans, new_trans, 1)
        print("transition updated")
    else:
        print("transition already different")

    # Guard switchPlayerDecoderPriority with isDecoderSwitchInFlight
    if "isDecoderSwitchInFlight = true" not in t:
        t = t.replace(
            """    private fun switchPlayerDecoderPriority(
        decoderPriority: DecoderPriority,
        forcedIndex: Int? = null,
        forcedPositionMs: Long? = null,
    ) {
        if (decoderPriority == activeDecoderPriority && forcedIndex == null) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
""",
            """    private fun switchPlayerDecoderPriority(
        decoderPriority: DecoderPriority,
        forcedIndex: Int? = null,
        forcedPositionMs: Long? = null,
    ) {
        if (decoderPriority == activeDecoderPriority && forcedIndex == null) return
        if (isDecoderSwitchInFlight) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        isDecoderSwitchInFlight = true
""",
        )
        # clear flag at end of switch before closing brace of function - after delayed release launch
        t = t.replace(
            """        serviceScope.launch {
            kotlinx.coroutines.delay(100)
            runCatching {
                currentPlayer.clearMediaItems()
                currentPlayer.stop()
                currentPlayer.release()
            }
        }
    }

    private fun applyAmbienceModeToPlayer""",
            """        serviceScope.launch {
            kotlinx.coroutines.delay(50)
            runCatching {
                currentPlayer.clearMediaItems()
                currentPlayer.stop()
                currentPlayer.release()
            }
            isDecoderSwitchInFlight = false
        }
    }

    private fun applyAmbienceModeToPlayer""",
        )
        # also clear on empty mediaItems path
        t = t.replace(
            """            serviceScope.launch {
                kotlinx.coroutines.delay(50)
                runCatching { currentPlayer.release() }
            }
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = (forcedIndex ?: currentPlayer.currentMediaItemIndex).coerceIn(0, mediaItems.lastIndex)
""",
            """            serviceScope.launch {
                kotlinx.coroutines.delay(50)
                runCatching { currentPlayer.release() }
                isDecoderSwitchInFlight = false
            }
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = (forcedIndex ?: currentPlayer.currentMediaItemIndex).coerceIn(0, mediaItems.lastIndex)
""",
        )
        print("switch guarded")

    # On READY, pre-check next item decoder and warm if needed when near end - add in onPlaybackStateChanged READY and use position listener is hard.
    # Simpler: in onMediaItemTransition after super, if has next and next decoder differs, log only.
    # For auto advance: use onPositionDiscontinuity or check in onPlaybackStateChanged when remaining small.

    # Add near-end pre-switch using onEvents - append to playbackStateListener onIsPlayingChanged or use existing onPlaybackStateChanged READY to register nothing.
    # Better: override in listener onTimelineChanged no...

    # Add to onPlaybackStateChanged after READY block a check isn't enough.
    # Use: when STATE_READY, launch a job that polls remaining until < 1.5s
    if "maybePreSwitchDecoderForUpcomingItem" not in t:
        # add method after applyExtensionDecoderForMediaItem
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
        if (mediaItem == null) return
        val target = resolveDecoderPriorityForMediaItem(mediaItem)
        if (target != activeDecoderPriority) {
            switchPlayerDecoderPriority(target)
        }
    }

    /**
     * 自动连播前：若下一项解码不同，提前切换到下一项（正确解码 + 从头），避免先用错误解码播几秒再重建。
     */
    private fun maybePreSwitchDecoderForUpcomingItem(player: Player) {
        if (isDecoderSwitchInFlight) return
        if (!player.hasNextMediaItem()) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val remaining = duration - player.currentPosition
        if (remaining > 1_200L) return
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex >= player.mediaItemCount) return
        val nextItem = player.getMediaItemAt(nextIndex)
        val nextDecoder = resolveDecoderPriorityForMediaItem(nextItem)
        if (nextDecoder == activeDecoderPriority) return
        Logger.info(
            TAG,
            "Pre-switch for upcoming item index=$nextIndex decoder=${nextDecoder.logName()} remainingMs=$remaining",
        )
        // 直接切到下一项并用正确解码起播（牺牲当前片尾 ~1s，换无长黑屏）
        switchPlayerDecoderPriority(
            decoderPriority = nextDecoder,
            forcedIndex = nextIndex,
            forcedPositionMs = 0L,
        )
    }
""",
        )
        # call from onPlaybackStateChanged when READY and from position - use onEvents
        # In playbackStateListener, add onEvents
        if "maybePreSwitchDecoderForUpcomingItem" in t and "override fun onEvents" not in t[t.find("private val playbackStateListener") : t.find("private val playbackStateListener") + 2500]:
            # insert onEvents after onPlayWhenReadyChanged start - find onPlaybackStateChanged end READY section
            t = t.replace(
                """            if (playbackState == Player.STATE_READY) {
                val player = mediaSession?.player ?: return
                val currentMediaItem = player.currentMediaItem ?: return
                serviceScope.launch {
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                    mediaRepository.updateMediumLastPlayedTime(
                        uri = playbackStateUri,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                }
                folderPlaybackAnchorUpdater.update(currentMediaItem)
            }
        }
""",
                """            if (playbackState == Player.STATE_READY) {
                val player = mediaSession?.player ?: return
                val currentMediaItem = player.currentMediaItem ?: return
                serviceScope.launch {
                    val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(currentMediaItem)
                    mediaRepository.updateMediumLastPlayedTime(
                        uri = playbackStateUri,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                }
                folderPlaybackAnchorUpdater.update(currentMediaItem)
                maybePreSwitchDecoderForUpcomingItem(player)
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) {
                maybePreSwitchDecoderForUpcomingItem(player)
            }
        }
""",
            )
            print("pre-switch hooks added")

    PS.write_text(t, encoding="utf-8")
    print("PlayerService saved")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t2 = t.replace("versionCode = 157", "versionCode = 158").replace(
        'versionName = "1.0.156"',
        'versionName = "1.0.157"',
    )
    if t2 == t:
        print("version not bumped", re.findall(r'versionCode = \d+|versionName = "[^"]+"', t))
    else:
        GRADLE.write_text(t2, encoding="utf-8")
        print("version 1.0.157")


def main() -> None:
    patch_media_item()
    patch_player_service()
    bump()
    ps = PS.read_text(encoding="utf-8")
    assert "decoderPriorityName" in ps
    assert "maybePreSwitchDecoderForUpcomingItem" in ps
    assert "stampedDecoder" in ps or "stampedDecoder" in MI.read_text(encoding="utf-8") or "stampedDecoder" in ps
    print("sanity ok")


if __name__ == "__main__":
    main()
