# -*- coding: utf-8 -*-
"""1) Fix in-player decoder change via custom command force switch.
2) Rewrite scale remember to media_state.content_scale (URI) like position/zoom.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def patch_db() -> None:
    ent = ROOT / "core/database/src/main/java/one/only/player/core/database/entities/MediumStateEntity.kt"
    t = ent.read_text(encoding="utf-8")
    if "content_scale" not in t:
        t = t.replace(
            """    @ColumnInfo(name = "decoder_priority")
    val decoderPriority: String? = null,
)
""",
            """    @ColumnInfo(name = "decoder_priority")
    val decoderPriority: String? = null,
    @ColumnInfo(name = "content_scale")
    val contentScale: String? = null,
)
""",
        )
        ent.write_text(t, encoding="utf-8")
        print("entity content_scale")
    db = ROOT / "core/database/src/main/java/one/only/player/core/database/MediaDatabase.kt"
    t = db.read_text(encoding="utf-8")
    t = t.replace("version = 11,", "version = 12,")
    if "MIGRATION_11_12" not in t:
        t = t.replace(
            """        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 解码记住与续播 position 同表同 URI
                db.execSQL("ALTER TABLE media_state ADD COLUMN decoder_priority TEXT DEFAULT NULL")
            }
        }
    }
}
""",
            """        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_state ADD COLUMN decoder_priority TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 缩放模式与续播 position 同表同 URI（BEST_FIT/CROP/...）
                db.execSQL("ALTER TABLE media_state ADD COLUMN content_scale TEXT DEFAULT NULL")
            }
        }
    }
}
""",
        )
        print("migration 11_12")
    db.write_text(t, encoding="utf-8")
    mod = ROOT / "core/database/src/main/java/one/only/player/core/database/DatabaseModule.kt"
    mt = mod.read_text(encoding="utf-8")
    if "MIGRATION_11_12" not in mt:
        mt = mt.replace(
            "MediaDatabase.MIGRATION_10_11,\n        )",
            "MediaDatabase.MIGRATION_10_11,\n            MediaDatabase.MIGRATION_11_12,\n        )",
        )
        mod.write_text(mt, encoding="utf-8")
        print("module migration")

    vs = ROOT / "core/data/src/main/java/one/only/player/core/data/models/VideoState.kt"
    t = vs.read_text(encoding="utf-8")
    if "contentScale" not in t:
        t = t.replace(
            "    val decoderPriority: String? = null,\n)",
            "    val decoderPriority: String? = null,\n    val contentScale: String? = null,\n)",
        )
        vs.write_text(t, encoding="utf-8")
    mapper = ROOT / "core/data/src/main/java/one/only/player/core/data/mappers/ToVideoState.kt"
    t = mapper.read_text(encoding="utf-8")
    if "contentScale =" not in t:
        t = t.replace(
            "    decoderPriority = decoderPriority?.takeIf { it.isNotBlank() },\n)",
            "    decoderPriority = decoderPriority?.takeIf { it.isNotBlank() },\n"
            "    contentScale = contentScale?.takeIf { it.isNotBlank() },\n)",
        )
        mapper.write_text(t, encoding="utf-8")

    repo = ROOT / "core/data/src/main/java/one/only/player/core/data/repository/MediaRepository.kt"
    t = repo.read_text(encoding="utf-8")
    if "updateMediumContentScale" not in t:
        t = t.replace(
            "    suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?)\n",
            "    suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?)\n"
            "    suspend fun updateMediumContentScale(uri: String, contentScale: String?)\n",
        )
        repo.write_text(t, encoding="utf-8")
    local = ROOT / "core/data/src/main/java/one/only/player/core/data/repository/LocalMediaRepository.kt"
    t = local.read_text(encoding="utf-8")
    if "updateMediumContentScale" not in t:
        t = t.replace(
            """    override suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                decoderPriority = decoderPriority?.takeIf { it.isNotBlank() },
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }
""",
            """    override suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                decoderPriority = decoderPriority?.takeIf { it.isNotBlank() },
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumContentScale(uri: String, contentScale: String?) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                contentScale = contentScale?.takeIf { it.isNotBlank() },
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }
""",
        )
        local.write_text(t, encoding="utf-8")
    fake = ROOT / "core/data/src/main/java/one/only/player/core/data/repository/fake/FakeMediaRepository.kt"
    t = fake.read_text(encoding="utf-8")
    if "updateMediumContentScale" not in t:
        t = t.replace(
            "override suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?) = Unit\n",
            "override suspend fun updateMediumDecoderPriority(uri: String, decoderPriority: String?) = Unit\n"
            "    override suspend fun updateMediumContentScale(uri: String, contentScale: String?) = Unit\n",
        )
        fake.write_text(t, encoding="utf-8")
    print("db/content_scale ok")


def patch_custom_commands() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/CustomCommands.kt"
    t = p.read_text(encoding="utf-8")
    if "SET_DECODER_PRIORITY" not in t:
        t = t.replace(
            '    SEEK_TO_MEDIA_ITEM(customAction = "SEEK_TO_MEDIA_ITEM"),\n    ;',
            '    SEEK_TO_MEDIA_ITEM(customAction = "SEEK_TO_MEDIA_ITEM"),\n'
            '    SET_DECODER_PRIORITY(customAction = "SET_DECODER_PRIORITY"),\n'
            "    ;",
        )
        t = t.replace(
            '        const val MEDIA_ITEM_POSITION_MS_KEY = "media_item_position_ms"\n    }',
            '        const val MEDIA_ITEM_POSITION_MS_KEY = "media_item_position_ms"\n'
            '        const val DECODER_PRIORITY_NAME_KEY = "decoder_priority_name"\n'
            "    }",
        )
        t += """

fun MediaController.setDecoderPriorityNow(priorityName: String) {
    val args = Bundle().apply {
        putString(CustomCommands.DECODER_PRIORITY_NAME_KEY, priorityName)
    }
    sendCustomCommand(CustomCommands.SET_DECODER_PRIORITY.sessionCommand, args)
}
"""
        p.write_text(t, encoding="utf-8")
        print("CustomCommands SET_DECODER")
    else:
        print("SET_DECODER exists")


def patch_player_service() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    t = p.read_text(encoding="utf-8")

    # Handler for SET_DECODER_PRIORITY - force switch without extension resolve
    if "CustomCommands.SET_DECODER_PRIORITY" not in t:
        needle = "                CustomCommands.SEEK_TO_MEDIA_ITEM -> {"
        insert = """                CustomCommands.SET_DECODER_PRIORITY -> {
                    val name = args.getString(CustomCommands.DECODER_PRIORITY_NAME_KEY).orEmpty()
                    val priority = runCatching { DecoderPriority.valueOf(name) }.getOrNull()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    // 立即按所选解码重建当前播放（不经扩展名覆盖）
                    switchPlayerDecoderPriority(priority)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SEEK_TO_MEDIA_ITEM -> {"""
        if needle not in t:
            raise SystemExit("SEEK_TO handler missing")
        t = t.replace(needle, insert, 1)
        print("SET_DECODER handler")

    # Stamp content scale from videoState when building metadata
    if "videoState?.contentScale" not in t and "contentScale = videoState" not in t:
        # After videoScale = ... stamp content scale into setExtras
        # Find setExtras in updatedMediaItemsWithMetadata and add contentScale from videoState
        if "contentScale = " not in t[t.find("setSubtitleConfigurations(mergedSubConfigurations)") : t.find("setSubtitleConfigurations(mergedSubConfigurations)") + 2500]:
            # inject before mediaItem.buildUpon
            marker = "                mediaItem.buildUpon().apply {\n                    setSubtitleConfigurations(mergedSubConfigurations)"
            if marker in t:
                t = t.replace(
                    marker,
                    """                val stampedContentScale = videoState?.contentScale
                    ?.takeIf { it.isNotBlank() }

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(mergedSubConfigurations)""",
                    1,
                )
            # add to setExtras if contentScale param exists
            t = t.replace(
                "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n",
                "remoteDirectoryPath = mediaItem.mediaMetadata.remoteDirectoryPath,\n"
                "                                contentScale = stampedContentScale\n"
                "                                    ?: mediaItem.mediaMetadata.contentScaleName,\n",
                1,
            )
            # ensure contentScaleName import
            if "import one.only.player.feature.player.extensions.contentScaleName" not in t:
                t = t.replace(
                    "import one.only.player.feature.player.extensions.remoteFilePath\n",
                    "import one.only.player.feature.player.extensions.remoteFilePath\n"
                    "import one.only.player.feature.player.extensions.contentScaleName\n",
                )
            print("stamped content scale")
        else:
            print("content scale stamp maybe present")

    p.write_text(t, encoding="utf-8")
    print("PlayerService ok")


def patch_viewmodel() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
    t = p.read_text(encoding="utf-8")
    # rewrite scale remember to media_state
    t = re.sub(
        r"    fun rememberVideoContentScaleForFile\([\s\S]*?fun setRememberVideoContentScaleForFile\([\s\S]*?\n    \}\n",
        """    fun rememberVideoContentScaleForMediaUri(mediaUri: String?, contentScale: VideoContentScale) {
        if (mediaUri.isNullOrBlank()) return
        viewModelScope.launch {
            mediaRepository.updateMediumContentScale(mediaUri, contentScale.name)
        }
    }

    fun clearVideoContentScaleForMediaUri(mediaUri: String?) {
        if (mediaUri.isNullOrBlank()) return
        viewModelScope.launch {
            mediaRepository.updateMediumContentScale(mediaUri, null)
        }
    }

    fun setRememberVideoContentScaleForMediaUri(
        mediaUri: String?,
        contentScale: VideoContentScale,
        isEnabled: Boolean,
    ) {
        if (isEnabled) {
            rememberVideoContentScaleForMediaUri(mediaUri, contentScale)
        } else {
            clearVideoContentScaleForMediaUri(mediaUri)
        }
    }

    fun rememberVideoContentScaleForFile(fileName: String?, contentScale: VideoContentScale) {
        rememberVideoContentScaleForMediaUri(fileName, contentScale)
    }

    fun clearVideoContentScaleForFile(fileName: String?) {
        clearVideoContentScaleForMediaUri(fileName)
    }

    fun setRememberVideoContentScaleForFile(
        fileName: String?,
        contentScale: VideoContentScale,
        isEnabled: Boolean,
    ) {
        setRememberVideoContentScaleForMediaUri(fileName, contentScale, isEnabled)
    }
""",
        t,
        count=1,
    )
    p.write_text(t, encoding="utf-8")
    print("ViewModel scale ok")


def patch_media_player_screen() -> None:
    p = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    t = p.read_text(encoding="utf-8")

    # imports
    if "import one.only.player.feature.player.service.setDecoderPriorityNow" not in t:
        t = t.replace(
            "import one.only.player.feature.player.service.seekToNextPrepared\n",
            "import one.only.player.feature.player.service.seekToNextPrepared\n"
            "import one.only.player.feature.player.service.setDecoderPriorityNow\n",
        )
    if "import one.only.player.feature.player.extensions.contentScaleName" not in t:
        t = t.replace(
            "package one.only.player.feature.player\n",
            "package one.only.player.feature.player\n\n"
            "import one.only.player.feature.player.extensions.contentScaleName\n",
            1,
        )

    # currentMediaUriString helper
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

    # Decoder menu: force apply via custom command
    old_dec = """                        MenuRoute.Decoder -> {
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
    new_dec = """                        MenuRoute.Decoder -> {
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
                            var selectedPriority by remember(
                                player.currentMediaItem?.mediaId,
                                playerPreferences.decoderPriority,
                                initial,
                            ) {
                                mutableStateOf(initial)
                            }
                            DecoderPrioritySelectorContent(
                                currentDecoderPriority = selectedPriority,
                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    // 1) 写全局默认 2) 立刻用所选解码重建当前播放（扩展名设置页仍管扩展名）
                                    viewModel.updateDecoderPriority(priority)
                                    val controller = player as? androidx.media3.session.MediaController
                                    controller?.setDecoderPriorityNow(priority.name)
                                },
                                isRememberForThisFileEnabled = false,
                                onRememberForThisFileChanged = null,
                                onDismiss = ::dismissOverlay,
                            )
"""
    if old_dec in t:
        t = t.replace(old_dec, new_dec, 1)
        print("decoder menu force apply")
    else:
        print("decoder menu block mismatch")

    # Scale menu: media_state content_scale
    old_scale = re.search(
        r"                        MenuRoute\.VideoContentScale -> \{[\s\S]*?onDismiss = ::dismissOverlay,\n                            \)\n                        \}\n",
        t,
    )
    new_scale = """                        MenuRoute.VideoContentScale -> {
                            val mediaUri = currentMediaUriString()
                            val stampedName = player.currentMediaItem?.mediaMetadata?.contentScaleName
                            val stampedScale = stampedName?.let {
                                runCatching { one.only.player.core.model.VideoContentScale.valueOf(it) }.getOrNull()
                            }
                            // 开关：MediaItem 上有 content_scale 盖章（来自 media_state）才算记住
                            val isRemembered = stampedScale != null
                            var selectedScale by remember(
                                player.currentMediaItem?.mediaId,
                                stampedScale,
                                playerPreferences.playerVideoZoom,
                            ) {
                                mutableStateOf(stampedScale ?: playerPreferences.playerVideoZoom)
                            }
                            var isRememberScaleForThisFile by remember(
                                player.currentMediaItem?.mediaId,
                                isRemembered,
                            ) {
                                mutableStateOf(isRemembered)
                            }
                            VideoContentScaleSelectorContent(
                                videoContentScale = selectedScale,
                                isCustomZoomActive = !videoZoomAndContentScaleState.zoom.isDefaultVideoZoom() &&
                                    !isRememberScaleForThisFile,
                                onVideoContentScaleChanged = { scale ->
                                    selectedScale = scale
                                    videoZoomAndContentScaleState.onVideoContentScaleChanged(
                                        newContentScale = scale,
                                        shouldPersistGlobal = !isRememberScaleForThisFile,
                                    )
                                    if (isRememberScaleForThisFile) {
                                        viewModel.rememberVideoContentScaleForMediaUri(mediaUri, scale)
                                    } else {
                                        viewModel.updateVideoContentScale(scale)
                                    }
                                },
                                isRememberForThisFileEnabled = isRememberScaleForThisFile,
                                onRememberForThisFileChanged = { enabled ->
                                    isRememberScaleForThisFile = enabled
                                    viewModel.setRememberVideoContentScaleForMediaUri(
                                        mediaUri = mediaUri,
                                        contentScale = selectedScale,
                                        isEnabled = enabled,
                                    )
                                },
                                onShowVideoFilters = null,
                                onDismiss = ::dismissOverlay,
                            )
                        }
"""
    if old_scale:
        t = t[: old_scale.start()] + new_scale + t[old_scale.end() :]
        print("scale menu rewritten")
    else:
        print("scale menu not found")

    # applyScaleForCurrentItem: use contentScaleName from mediaItem, fallback global
    old_apply = re.search(
        r"        fun applyScaleForCurrentItem\(\) \{[\s\S]*?videoZoomAndContentScaleState\.applyContentScaleLocally\(target\)\n        \}\n",
        t,
    )
    new_apply = """        fun applyScaleForCurrentItem() {
            val mediaItem = player.currentMediaItem
            val stamped = mediaItem?.mediaMetadata?.contentScaleName
                ?.let { runCatching { one.only.player.core.model.VideoContentScale.valueOf(it) }.getOrNull() }
            val target = stamped ?: playerPreferences.playerVideoZoom
            videoZoomAndContentScaleState.applyContentScaleLocally(target)
        }
"""
    if old_apply:
        t = t[: old_apply.start()] + new_apply + t[old_apply.end() :]
        print("applyScale rewritten")
    else:
        print("applyScale not found")

    # DisposableEffect keys - remove perFilePlaybackPreferences dependency for scale
    t = t.replace(
        """    DisposableEffect(
        player,
        applicationPreferences.perFilePlaybackPreferences,
        playerPreferences.playerVideoZoom,
    ) {""",
        """    DisposableEffect(
        player,
        playerPreferences.playerVideoZoom,
    ) {""",
    )

    p.write_text(t, encoding="utf-8")
    print("MediaPlayerScreen ok")


def bump() -> None:
    p = ROOT / "app/build.gradle.kts"
    t = p.read_text(encoding="utf-8")
    t = t.replace("versionCode = 161", "versionCode = 162")
    t = t.replace('versionName = "1.0.160"', 'versionName = "1.0.161"')
    p.write_text(t, encoding="utf-8")
    print("version 1.0.161")


def main() -> None:
    patch_db()
    patch_custom_commands()
    patch_player_service()
    patch_viewmodel()
    patch_media_player_screen()
    bump()
    print("done")


if __name__ == "__main__":
    main()
