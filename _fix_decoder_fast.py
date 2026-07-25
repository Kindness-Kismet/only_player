# -*- coding: utf-8 -*-
"""Fast decoder switch (current item first) + remember switch only when DB-remembered."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MI = ROOT / "feature/player/src/main/java/one/only/player/feature/player/extensions/MediaItem.kt"
PS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
MS = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def patch_media_item() -> None:
    t = MI.read_text(encoding="utf-8")
    if "MEDIA_METADATA_DECODER_REMEMBERED_KEY" in t:
        print("MediaItem flag exists")
        return
    t = t.replace(
        'private const val MEDIA_METADATA_CONTENT_SCALE_KEY = "media_metadata_content_scale"\n',
        'private const val MEDIA_METADATA_CONTENT_SCALE_KEY = "media_metadata_content_scale"\n'
        'private const val MEDIA_METADATA_DECODER_REMEMBERED_KEY = "media_metadata_decoder_remembered"\n',
    )
    t = t.replace(
        "    contentScale: String? = null,\n) = apply {",
        "    contentScale: String? = null,\n    isDecoderRemembered: Boolean? = null,\n) = apply {",
    )
    t = t.replace(
        "    contentScale?.let { putString(MEDIA_METADATA_CONTENT_SCALE_KEY, it) }\n}",
        "    contentScale?.let { putString(MEDIA_METADATA_CONTENT_SCALE_KEY, it) }\n"
        "    isDecoderRemembered?.let { putBoolean(MEDIA_METADATA_DECODER_REMEMBERED_KEY, it) }\n}",
    )
    t = t.replace(
        "    contentScale: String? = null,\n): MediaMetadata.Builder = setExtras(",
        "    contentScale: String? = null,\n    isDecoderRemembered: Boolean? = null,\n): MediaMetadata.Builder = setExtras(",
    )
    t = t.replace(
        "        contentScale = contentScale,\n    ).apply {",
        "        contentScale = contentScale,\n        isDecoderRemembered = isDecoderRemembered,\n    ).apply {",
    )
    t = t.replace(
        """val MediaMetadata.contentScaleName: String?
    get() = extras?.getString(MEDIA_METADATA_CONTENT_SCALE_KEY)
        ?.takeIf(String::isNotBlank)

fun MediaItem.copy(
""",
        """val MediaMetadata.contentScaleName: String?
    get() = extras?.getString(MEDIA_METADATA_CONTENT_SCALE_KEY)
        ?.takeIf(String::isNotBlank)

val MediaMetadata.isDecoderRemembered: Boolean
    get() = extras?.getBoolean(MEDIA_METADATA_DECODER_REMEMBERED_KEY, false) == true

fun MediaItem.copy(
""",
    )
    t = t.replace(
        "    contentScaleName: String? = this.mediaMetadata.contentScaleName,\n): MediaItem = buildUpon()",
        "    contentScaleName: String? = this.mediaMetadata.contentScaleName,\n"
        "    isDecoderRemembered: Boolean? = this.mediaMetadata.isDecoderRemembered,\n"
        "): MediaItem = buildUpon()",
    )
    t = t.replace(
        "                contentScale = contentScaleName,\n            ).apply {",
        "                contentScale = contentScaleName,\n"
        "                isDecoderRemembered = isDecoderRemembered,\n"
        "            ).apply {",
    )
    MI.write_text(t, encoding="utf-8")
    print("MediaItem flag ok")


def patch_player_service() -> None:
    t = PS.read_text(encoding="utf-8")
    if "import one.only.player.feature.player.extensions.isDecoderRemembered" not in t:
        t = t.replace(
            "import one.only.player.feature.player.extensions.decoderPriorityName\n",
            "import one.only.player.feature.player.extensions.decoderPriorityName\n"
            "import one.only.player.feature.player.extensions.isDecoderRemembered\n",
        )

    # stamp remembered flag
    if "isDecoderRememberedForItem" not in t:
        t = t.replace(
            """                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val stampedDecoder = stateDecoder
                    ?: resolveDecoderPriorityForMediaItem(mediaItem)
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)
""",
            """                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val isDecoderRememberedForItem = stateDecoder != null
                val stampedDecoder = stateDecoder
                    ?: resolveDecoderPriorityForMediaItem(mediaItem)
                val stampedScale = resolveContentScaleForMediaItem(mediaItem)
""",
        )
        if "isDecoderRememberedForItem" not in t:
            t = t.replace(
                """                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val stampedDecoder = stateDecoder ?: resolveDecoderPriorityForMediaItem(mediaItem)
""",
                """                val stateDecoder = videoState?.decoderPriority
                    ?.let { name -> runCatching { DecoderPriority.valueOf(name) }.getOrNull() }
                val isDecoderRememberedForItem = stateDecoder != null
                val stampedDecoder = stateDecoder ?: resolveDecoderPriorityForMediaItem(mediaItem)
""",
                1,
            )
        print("stamp flag")
    if "isDecoderRemembered = isDecoderRememberedForItem" not in t and "isDecoderRememberedForItem" in t:
        t = t.replace(
            "decoderPriority = stampedDecoder.name,\n"
            "                                contentScale = stampedScale?.name,\n",
            "decoderPriority = stampedDecoder.name,\n"
            "                                contentScale = stampedScale?.name,\n"
            "                                isDecoderRemembered = isDecoderRememberedForItem,\n",
            1,
        )
        print("extras flag")

    # transition: force switch at current index
    old_tr = """            if (mediaItem != null) {
                val target = resolveDecoderPriorityForMediaItem(mediaItem)
                Logger.info(
                    TAG,
                    "MediaItemTransition reason=$reason decoder=${target.logName()} active=${activeDecoderPriority.logName()} media=${mediaItem.mediaId.toPrivateMediaLogSummary()}",
                )
                if (target != activeDecoderPriority && !isDecoderSwitchInFlight) {
                    applyExtensionDecoderForMediaItem(mediaItem)
                }
            }
"""
    new_tr = """            if (mediaItem != null) {
                val target = resolveDecoderPriorityForMediaItem(mediaItem)
                val player = mediaSession?.player
                Logger.info(
                    TAG,
                    "MediaItemTransition reason=$reason decoder=${target.logName()} active=${activeDecoderPriority.logName()} media=${mediaItem.mediaId.toPrivateMediaLogSummary()}",
                )
                if (target != activeDecoderPriority && !isDecoderSwitchInFlight && player != null) {
                    switchPlayerDecoderPriority(
                        decoderPriority = target,
                        forcedIndex = player.currentMediaItemIndex,
                        forcedPositionMs = player.currentPosition.coerceAtLeast(0L),
                    )
                }
            }
"""
    if old_tr in t:
        t = t.replace(old_tr, new_tr, 1)
        print("transition forced")
    else:
        print("transition already changed or mismatch")

    m = re.search(
        r"    private fun switchPlayerDecoderPriority\([\s\S]*?\n    private fun applyAmbienceModeToPlayer",
        t,
    )
    if not m:
        raise SystemExit("switch not found")
    new_switch = """    private fun switchPlayerDecoderPriority(
        decoderPriority: DecoderPriority,
        forcedIndex: Int? = null,
        forcedPositionMs: Long? = null,
    ) {
        if (decoderPriority == activeDecoderPriority && forcedIndex == null) return
        if (isDecoderSwitchInFlight) return
        val session = mediaSession ?: return
        val currentPlayer = session.player as? ExoPlayer ?: return
        isDecoderSwitchInFlight = true
        val shouldPlayWhenReady = currentPlayer.playWhenReady
        val mediaItems = (0 until currentPlayer.mediaItemCount).map { currentPlayer.getMediaItemAt(it) }
        if (mediaItems.isEmpty()) {
            Logger.info(TAG, "Switch decoder to ${decoderPriority.logName()} without items")
            val nextPlayer = createPlayer(decoderPriority = decoderPriority, assHandler = assHandler ?: return)
            audioEffectsCoordinator.releaseLoudnessEnhancer()
            currentPlayer.removeListener(playbackStateListener)
            currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
            session.player = nextPlayer
            serviceScope.launch {
                kotlinx.coroutines.delay(30)
                runCatching { currentPlayer.release() }
                isDecoderSwitchInFlight = false
            }
            applyAmbienceModeToPlayer(nextPlayer)
            return
        }

        val currentIndex = (forcedIndex ?: currentPlayer.currentMediaItemIndex).coerceIn(0, mediaItems.lastIndex)
        val playbackPosition = (forcedPositionMs ?: currentPlayer.currentPosition).coerceAtLeast(0L)
        val trackSelectionParameters = currentPlayer.trackSelectionParameters
        val shuffleModeEnabled = currentPlayer.shuffleModeEnabled
        val repeatMode = currentPlayer.repeatMode
        val isSkipSilenceEnabled = currentPlayer.isSkipSilenceEnabledForPlayer
        val subtitleDelayMilliseconds = currentPlayer.playerSpecificSubtitleDelayMilliseconds
        val subtitleSpeed = currentPlayer.playerSpecificSubtitleSpeed
        val playbackParameters = currentPlayer.playbackParameters
        val from = activeDecoderPriority
        val nextPlayer = createPlayer(decoderPriority = decoderPriority, assHandler = assHandler ?: return)
        Logger.info(
            TAG,
            "Switch decoder from ${from.logName()} to ${decoderPriority.logName()} at index=$currentIndex position=$playbackPosition (single-item first)",
        )

        // 先只加载目标条目并立刻开播，再异步补全列表（对齐续播秒开）
        val targetItem = mediaItems[currentIndex]
        nextPlayer.setMediaItem(targetItem, playbackPosition)
        nextPlayer.trackSelectionParameters = trackSelectionParameters
        nextPlayer.shuffleModeEnabled = shuffleModeEnabled
        nextPlayer.repeatMode = repeatMode
        nextPlayer.playbackParameters = playbackParameters
        nextPlayer.isSkipSilenceEnabledForPlayer = isSkipSilenceEnabled
        nextPlayer.playerSpecificSubtitleDelayMilliseconds = subtitleDelayMilliseconds
        nextPlayer.playerSpecificSubtitleSpeed = subtitleSpeed
        nextPlayer.playWhenReady = shouldPlayWhenReady
        nextPlayer.prepare()

        audioEffectsCoordinator.releaseLoudnessEnhancer()
        currentPlayer.removeListener(playbackStateListener)
        currentPlayer.removeAnalyticsListener(startupAnalyticsListener)
        session.player = nextPlayer
        videoEffectsCoordinator.updateAvailability(nextPlayer)
        applyAmbienceModeToPlayer(nextPlayer)

        if (mediaItems.size > 1) {
            serviceScope.launch {
                runCatching {
                    val before = mediaItems.take(currentIndex)
                    val after = mediaItems.drop(currentIndex + 1)
                    if (before.isNotEmpty()) nextPlayer.addMediaItems(0, before)
                    if (after.isNotEmpty()) nextPlayer.addMediaItems(after)
                }
            }
        }

        serviceScope.launch {
            kotlinx.coroutines.delay(30)
            runCatching {
                currentPlayer.clearMediaItems()
                currentPlayer.stop()
                currentPlayer.release()
            }
            isDecoderSwitchInFlight = false
        }
    }

    private fun applyAmbienceModeToPlayer"""
    t = t[: m.start()] + new_switch + t[m.end() :]
    PS.write_text(t, encoding="utf-8")
    print("PlayerService switch rewritten")


def patch_ui() -> None:
    t = MS.read_text(encoding="utf-8")
    if "import one.only.player.feature.player.extensions.isDecoderRemembered" not in t:
        t = t.replace(
            "import one.only.player.feature.player.extensions.decoderPriorityName\n",
            "import one.only.player.feature.player.extensions.decoderPriorityName\n"
            "import one.only.player.feature.player.extensions.isDecoderRemembered\n",
        )
    old = """                            val perFileDecoder = player.currentMediaItem?.mediaMetadata?.decoderPriorityName
                                ?.let { runCatching { one.only.player.core.model.DecoderPriority.valueOf(it) }.getOrNull() }
                                ?: applicationPreferences.perFilePreferenceForPath(fileName)?.decoderPriority
                            var selectedPriority by remember(fileName, perFileDecoder, playerPreferences.decoderPriority) {
                                val mediaExtension = fileName
                                    ?.substringAfterLast('.', missingDelimiterValue = "")
                                    ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                    ?.lowercase()
                                val initial = perFileDecoder
                                    ?: mediaExtension?.let { ext ->
                                        applicationPreferences.normalizedExtensionDecoderPreferences()
                                            .firstOrNull { it.extension == ext }
                                            ?.decoderPriority
                                    }
                                    ?: playerPreferences.decoderPriority
                                mutableStateOf(initial)
                            }
                            var isRememberForThisFile by remember(fileName, perFileDecoder) {
                                mutableStateOf(perFileDecoder != null)
                            }
"""
    new = """                            val stampedDecoder = player.currentMediaItem?.mediaMetadata?.decoderPriorityName
                                ?.let { runCatching { one.only.player.core.model.DecoderPriority.valueOf(it) }.getOrNull() }
                            // 仅 media_state 真正记住时开关为开；扩展名/全局默认不算“记住该文件”
                            val isRememberedInDb = player.currentMediaItem?.mediaMetadata?.isDecoderRemembered == true
                            var selectedPriority by remember(
                                player.currentMediaItem?.mediaId,
                                stampedDecoder,
                                playerPreferences.decoderPriority,
                            ) {
                                val mediaExtension = fileName
                                    ?.substringAfterLast('.', missingDelimiterValue = "")
                                    ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                    ?.lowercase()
                                val initial = stampedDecoder
                                    ?: mediaExtension?.let { ext ->
                                        applicationPreferences.normalizedExtensionDecoderPreferences()
                                            .firstOrNull { it.extension == ext }
                                            ?.decoderPriority
                                    }
                                    ?: playerPreferences.decoderPriority
                                mutableStateOf(initial)
                            }
                            var isRememberForThisFile by remember(
                                player.currentMediaItem?.mediaId,
                                isRememberedInDb,
                            ) {
                                mutableStateOf(isRememberedInDb)
                            }
"""
    if old in t:
        t = t.replace(old, new, 1)
        print("UI remember switch fixed")
    else:
        print("UI block mismatch")
    MS.write_text(t, encoding="utf-8")


def bump() -> None:
    t = GRADLE.read_text(encoding="utf-8")
    t = t.replace("versionCode = 159", "versionCode = 160")
    t = t.replace('versionName = "1.0.158"', 'versionName = "1.0.159"')
    GRADLE.write_text(t, encoding="utf-8")
    print("version 1.0.159")


def main() -> None:
    patch_media_item()
    patch_player_service()
    patch_ui()
    bump()
    print("done")


if __name__ == "__main__":
    main()
