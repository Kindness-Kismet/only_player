from pathlib import Path

ROOT = Path(r"E:/Downloads/only_player_src")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"[miss] {label}")
        return text
    print(f"[ok] {label}")
    return text.replace(old, new, 1)


def patch_player_service() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    text = path.read_text(encoding="utf-8")

    if "private fun shouldAcceptMediaController" not in text:
        insert = """
    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
        // 本应用控制器始终允许；系统媒体中心/蓝牙等在非 HIDE 时允许
        val packageName = controller.packageName
        if (packageName.isNullOrBlank() || packageName == applicationContext.packageName) {
            return true
        }
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.AUDIO_ONLY -> {
                val item = mediaSession?.player?.currentMediaItem
                val name = listOfNotNull(
                    item?.localConfiguration?.uri?.lastPathSegment,
                    item?.localConfiguration?.uri?.path,
                    item?.mediaId,
                    item?.mediaMetadata?.title?.toString(),
                ).joinToString(" ")
                name.substringAfterLast('.', missingDelimiterValue = "").equals("mp3", ignoreCase = true)
            }
        }
    }

"""
        marker = "    private fun shouldPublishMediaSessionNotification(session: MediaSession): Boolean {"
        text = replace_once(text, marker, insert + marker, "shouldAcceptMediaController")

    old_apply = """    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) return
        val uri = mediaItem.localConfiguration?.uri
        val pathCandidates = listOfNotNull(
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            mediaItem.mediaId,
            mediaItem.requestMetadata?.mediaUri?.toString(),
            mediaItem.mediaMetadata.extras?.getString("media_metadata_remote_file_path"),
        )
        var target = DecoderPriority.AUTOMATIC
        for (candidate in pathCandidates) {
            val ext = candidate.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isNotBlank() && ext.length <= 8) {
                target = resolveDecoderPriorityForPath(candidate)
                break
            }
        }
        if (target != activeDecoderPriority) {
            switchPlayerDecoderPriority(target)
        }
    }"""

    new_apply = """    private fun updateExtensionDecoderFromManualSelection(
        mediaItem: MediaItem,
        decoderPriority: DecoderPriority,
    ) {
        val uri = mediaItem.localConfiguration?.uri
        val resolvedPath = uri?.let { candidateUri ->
            when (candidateUri.scheme) {
                ContentResolver.SCHEME_FILE -> candidateUri.path
                ContentResolver.SCHEME_CONTENT -> getPath(candidateUri)
                else -> candidateUri.path
            }
        }
        val candidates = listOfNotNull(
            resolvedPath,
            mediaItem.mediaMetadata.remoteFilePath,
            uri?.lastPathSegment,
            mediaItem.mediaId,
            mediaItem.mediaMetadata.title?.toString(),
        )
        var extension: String? = null
        for (candidate in candidates) {
            val clean = candidate
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isNotBlank() && ext.length <= 10 && ext.all { it.isLetterOrDigit() }) {
                extension = ext.lowercase()
                break
            }
        }
        if (extension.isNullOrBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            preferencesRepository.updateApplicationPreferences { current ->
                val list = current.normalizedExtensionDecoderPreferences()
                if (list.none { it.extension == extension }) {
                    current.withExtensionDecoderPreferences(
                        list + one.only.player.core.model.ExtensionDecoderPreference(
                            extension = extension,
                            decoderPriority = decoderPriority,
                            isBuiltIn = false,
                        ),
                    )
                } else {
                    current.withExtensionDecoderPreferences(
                        list.map { item ->
                            if (item.extension == extension) {
                                item.copy(decoderPriority = decoderPriority)
                            } else {
                                item
                            }
                        },
                    )
                }
            }
        }
    }

    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) return
        val target = resolveDecoderPriorityForMediaItem(mediaItem)
        if (target != activeDecoderPriority) {
            switchPlayerDecoderPriority(target)
        }
    }

    private fun resolveDecoderPriorityForMediaItem(mediaItem: MediaItem): DecoderPriority {
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
        for (candidate in pathCandidates) {
            val clean = candidate
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.isNotBlank() && ext.length <= 10 && ext.all { it.isLetterOrDigit() }) {
                return resolveDecoderPriorityForPath(clean)
            }
        }
        // 无扩展名时回退到全局播放器解码设置
        return playerPreferences.decoderPriority
    }"""

    if "private fun resolveDecoderPriorityForMediaItem" not in text:
        text = replace_once(text, old_apply, new_apply, "apply/resolve decoder")

    old_collect = """        serviceScope.launch {
            // 全局 decoderPriority 不再主导；扩展名配置变更时按当前媒体重新应用
            preferencesRepository.applicationPreferences
                .distinctUntilChanged { old, new ->
                    old.normalizedExtensionDecoderPreferences() == new.normalizedExtensionDecoderPreferences()
                }
                .collect {
                    val current = mediaSession?.player?.currentMediaItem
                    applyExtensionDecoderForMediaItem(current)
                }
        }"""
    new_collect = """        serviceScope.launch {
            // 扩展名配置变更时，按当前媒体重新应用
            preferencesRepository.applicationPreferences
                .distinctUntilChanged { old, new ->
                    old.normalizedExtensionDecoderPreferences() == new.normalizedExtensionDecoderPreferences()
                }
                .collect {
                    val current = mediaSession?.player?.currentMediaItem
                    applyExtensionDecoderForMediaItem(current)
                }
        }
        serviceScope.launch {
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }
                .collect {
                    val current = mediaSession?.player?.currentMediaItem
                    // 播放器内改解码：同步到当前文件扩展名配置，并立即切换
                    if (current != null) {
                        updateExtensionDecoderFromManualSelection(current, it.decoderPriority)
                    }
                    applyExtensionDecoderForMediaItem(current)
                }
        }"""
    if "updateExtensionDecoderFromManualSelection(current, it.decoderPriority)" not in text:
        text = replace_once(text, old_collect, new_collect, "decoder collect")

    # Improve artwork loader trigger: always load when publishing media session
    old_art1 = """            if (playerPreferences.mediaSessionVisibility != MediaSessionVisibility.HIDE) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)"""
    new_art1 = """            if (shouldPublishMediaSessionNotificationForVisibility()) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)"""
    text = replace_once(text, old_art1, new_art1, "artwork onSetMediaItems")

    old_art2 = """            if (playerPreferences.mediaSessionVisibility != MediaSessionVisibility.HIDE) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future updatedMediaItems.toMutableList()"""
    new_art2 = """            if (shouldPublishMediaSessionNotificationForVisibility()) {
                artworkLoader.loadInBackground(updatedMediaItems)
            }
            return@future updatedMediaItems.toMutableList()"""
    text = replace_once(text, old_art2, new_art2, "artwork onAddMediaItems")

    if "private fun shouldPublishMediaSessionNotificationForVisibility" not in text:
        helper = """
    private fun shouldPublishMediaSessionNotificationForVisibility(): Boolean {
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> true
        }
    }

"""
        marker = "    private fun shouldPublishMediaSessionNotification(session: MediaSession): Boolean {"
        text = replace_once(text, marker, helper + marker, "visibility helper")

    path.write_text(text, encoding="utf-8")
    print("PlayerService patched")


def patch_file_extension_screen() -> None:
    path = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/medialibrary/FileExtensionPreferencesScreen.kt"
    text = path.read_text(encoding="utf-8")

    # Prefer core OptionsDialog with title param if settings wrapper issues remain;
    # Keep current, but force clickable combinedClickable by ensuring onLongClick always non-null.
    old_row = """    ClickablePreferenceItem(
        modifier = Modifier
            .testTag(
                if (preference.isBuiltIn) {
                    "item_file_extension_${preference.extension}"
                } else {
                    "item_file_extension_custom_${preference.extension}"
                },
            ),
        title = ".${preference.extension}",
        description = if (isBatchMode && isSelected) {
            stringResource(R.string.file_extensions_selected) + " · " + preference.decoderPriority.name()
        } else {
            preference.decoderPriority.name()
        },
        icon = if (isBatchMode) {
            if (isSelected) NextIcons.CheckBox else NextIcons.CheckBoxOutline
        } else {
            NextIcons.Decoder
        },
        onClick = onClick,
        onLongClick = onLongClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
    )"""
    new_row = """    ClickablePreferenceItem(
        modifier = Modifier
            .testTag(
                if (preference.isBuiltIn) {
                    "item_file_extension_${preference.extension}"
                } else {
                    "item_file_extension_custom_${preference.extension}"
                },
            ),
        title = ".${preference.extension}",
        description = if (isBatchMode && isSelected) {
            stringResource(R.string.file_extensions_selected) + " · " + preference.decoderPriority.name()
        } else {
            preference.decoderPriority.name()
        },
        icon = if (isBatchMode) {
            if (isSelected) NextIcons.CheckBox else NextIcons.CheckBoxOutline
        } else {
            NextIcons.Decoder
        },
        onClick = onClick,
        // 始终传非空 longClick，保证 combinedClickable 长按批量可用
        onLongClick = onLongClick,
        isFirstItem = isFirstItem,
        isLastItem = isLastItem,
    )"""
    text = replace_once(text, old_row, new_row, "extension row longClick comment")

    # Make PreferenceItem clicks more robust: already using combinedClickable.
    # Also ensure editing dialog always opens by using OptionsDialog title from core path.
    # Replace settings OptionsDialog import usage remains fine.

    # Add top-bar batch entry even outside batch mode for discoverability
    if 'btn_file_extensions_enter_batch' not in text:
        old_actions_else = """                    } else {
                        MiuixIconButton(
                            onClick = {
                                addInput = ""
                                addError = null
                                hasAllFilesAccess = hasManageExternalStorageAccess()
                                shouldShowAddDialog = true
                            },
                            modifier = Modifier.testTag("btn_file_extensions_add"),
                        ) {
                            MiuixIcon(
                                imageVector = NextIcons.Add,
                                contentDescription = stringResource(id = R.string.file_extensions_add),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }"""
        new_actions_else = """                    } else {
                        MiuixIconButton(
                            onClick = { enterBatchMode() },
                            modifier = Modifier.testTag("btn_file_extensions_enter_batch"),
                        ) {
                            MiuixIcon(
                                imageVector = NextIcons.SelectAll,
                                contentDescription = stringResource(id = R.string.file_extensions_batch_set_decoder),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        MiuixIconButton(
                            onClick = {
                                addInput = ""
                                addError = null
                                hasAllFilesAccess = hasManageExternalStorageAccess()
                                shouldShowAddDialog = true
                            },
                            modifier = Modifier.testTag("btn_file_extensions_add"),
                        ) {
                            MiuixIcon(
                                imageVector = NextIcons.Add,
                                contentDescription = stringResource(id = R.string.file_extensions_add),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }"""
        text = replace_once(text, old_actions_else, new_actions_else, "batch entry button")

    path.write_text(text, encoding="utf-8")
    print("FileExtensionPreferencesScreen patched")


def patch_preference_item() -> None:
    path = ROOT / "core/ui/src/main/java/one/only/player/core/ui/components/PreferenceItem.kt"
    text = path.read_text(encoding="utf-8")
    old = """    // 始终用 combinedClickable 处理点击/长按，避免 ArrowPreference 吞掉 long-click
    val clickModifier = if (isEnabled) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier.then(clickModifier),
    ) {
        BasicComponent(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            endActions = {
                trailingContent()
                if (showArrow) {
                    MiuixIcon(
                        imageVector = NextIcons.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
            onClick = null,
            enabled = isEnabled,
        )
    }"""
    new = """    // 始终用 combinedClickable 处理点击/长按，避免 ArrowPreference 吞掉 long-click
    val clickModifier = if (isEnabled) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier.then(clickModifier),
    ) {
        BasicComponent(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            endActions = {
                trailingContent()
                if (showArrow) {
                    MiuixIcon(
                        imageVector = NextIcons.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
            // 点击/长按交给外层 combinedClickable，避免双重消费
            onClick = null,
            enabled = isEnabled,
            holdDownState = false,
        )
    }"""
    # holdDownState may not exist; keep safer version without unknown params
    new = """    // 始终用 combinedClickable 处理点击/长按，避免 ArrowPreference 吞掉 long-click
    val clickModifier = if (isEnabled) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Surface(
        shape = preferenceSegmentShape(isFirstItem, isLastItem),
        color = MiuixTheme.colorScheme.surfaceContainer,
        modifier = modifier.then(clickModifier),
    ) {
        // 不走 BasicComponent 的 onClick，避免长按失效
        BasicComponent(
            title = title,
            summary = description,
            startAction = icon?.let { { PreferenceIcon(it, isEnabled) } },
            endActions = {
                trailingContent()
                if (showArrow) {
                    MiuixIcon(
                        imageVector = NextIcons.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
            onClick = null,
            enabled = isEnabled,
        )
    }"""
    if "// 不走 BasicComponent 的 onClick" not in text:
        text = replace_once(text, old, new, "PreferenceItem comment")
    path.write_text(text, encoding="utf-8")
    print("PreferenceItem patched")


def patch_player_button_transparent() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/buttons/PlayerButton.kt"
    text = path.read_text(encoding="utf-8")
    old = """                PlayerIconStyle.TRANSPARENT -> {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isOutlineOnly) colorScheme.primary else Color.White,
                        LocalRippleConfiguration provides whiteRippleConfiguration,
                    ) {
                        IconButton(
                            onClick = {},
                            enabled = isEnabled,
                            modifier = Modifier.size(buttonSize).then(if (isOutlineOnly) outlineModifier else Modifier),
                            interactionSource = interactionSource,
                            content = buttonContent,
                        )
                    }
                }"""
    new = """                PlayerIconStyle.TRANSPARENT -> {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isOutlineOnly) colorScheme.primary else Color.White,
                        LocalRippleConfiguration provides whiteRippleConfiguration,
                    ) {
                        // 全透：无圆形背景，仅图标
                        IconButton(
                            onClick = {},
                            enabled = isEnabled,
                            modifier = Modifier
                                .size(buttonSize)
                                .then(if (isOutlineOnly) outlineModifier else Modifier)
                                .background(Color.Transparent),
                            interactionSource = interactionSource,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isOutlineOnly) colorScheme.primary else Color.White,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = Color.White.copy(alpha = 0.5f),
                            ),
                            content = buttonContent,
                        )
                    }
                }"""
    text = replace_once(text, old, new, "transparent icon style")
    path.write_text(text, encoding="utf-8")
    print("PlayerButton patched")


def patch_controls_middle_spacing() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    text = path.read_text(encoding="utf-8")
    old = """    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    new = """    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        // 上一/下一尽量往两边放，中心保留播放按钮
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    text = replace_once(text, old, new, "prev/next spacing comment")
    path.write_text(text, encoding="utf-8")
    print("ControlsMiddle spacing noted")


def patch_customize_drag_always() -> None:
    # CUSTOMIZE already in customizableControls and drag source.
    # Ensure toggleControlVisibility doesn't hide CUSTOMIZE permanently unintentionally.
    # Make CUSTOMIZE drag-enabled even if user tries to hide it: keep it visible during customize.
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    text = path.read_text(encoding="utf-8")
    old = """                                        onCustomizeControlsClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.CUSTOMIZE)
                                            } else {
                                                enterControlCustomization()
                                            }
                                        },"""
    new = """                                        onCustomizeControlsClick = {
                                            if (isCustomizingControls) {
                                                // 自定义按钮也可隐藏/显示，并支持长按拖动排序
                                                toggleControlVisibility(PlayerControl.CUSTOMIZE)
                                            } else {
                                                enterControlCustomization()
                                            }
                                        },"""
    text = replace_once(text, old, new, "customize click comment")
    path.write_text(text, encoding="utf-8")
    print("customize click noted")


def patch_artwork_loader() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/artwork/PlaybackArtworkLoader.kt"
    text = path.read_text(encoding="utf-8")
    old = """    fun loadInBackground(mediaItems: List<MediaItem>) {
        scope.launch(Dispatchers.Default) {
            mediaItems.forEach { mediaItem ->
                launch {
                    val artworkData = loadArtworkForUri(mediaItem.mediaId.toUri()) ?: return@launch

                    withContext(Dispatchers.Main) {
                        val (player, index, currentMediaItem) = findMediaItem(mediaItem.mediaId) ?: return@withContext
                        val updatedMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(
                                currentMediaItem.mediaMetadata.buildUpon()
                                    .setArtworkUri(null)
                                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build(),
                            )
                            .build()
                        player.replaceMediaItem(index, updatedMediaItem)
                    }
                }
            }
        }
    }

    private suspend fun loadArtworkForUri(uri: Uri): ByteArray? = try {
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(uri)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()?.toByteArray()
    } catch (_: Exception) {
        null
    }"""
    new = """    fun loadInBackground(mediaItems: List<MediaItem>) {
        scope.launch(Dispatchers.Default) {
            mediaItems.forEach { mediaItem ->
                launch {
                    val artworkData = loadArtworkForMediaItem(mediaItem) ?: return@launch

                    withContext(Dispatchers.Main) {
                        val (player, index, currentMediaItem) = findMediaItem(mediaItem.mediaId) ?: return@withContext
                        val updatedMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(
                                currentMediaItem.mediaMetadata.buildUpon()
                                    // 系统媒体控件优先使用 artworkData 显示封面
                                    .setArtworkUri(null)
                                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build(),
                            )
                            .build()
                        player.replaceMediaItem(index, updatedMediaItem)
                    }
                }
            }
        }
    }

    private suspend fun loadArtworkForMediaItem(mediaItem: MediaItem): ByteArray? {
        val candidates = buildList {
            add(mediaItem.mediaId.toUri())
            mediaItem.localConfiguration?.uri?.let(::add)
            mediaItem.requestMetadata.mediaUri?.let(::add)
            mediaItem.mediaMetadata.artworkUri?.let(::add)
        }.distinct()
        for (uri in candidates) {
            loadArtworkForUri(uri)?.let { return it }
        }
        return null
    }

    private suspend fun loadArtworkForUri(uri: Uri): ByteArray? = try {
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(uri)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()?.toByteArray()
    } catch (_: Exception) {
        null
    }"""
    text = replace_once(text, old, new, "artwork multi-uri")
    path.write_text(text, encoding="utf-8")
    print("PlaybackArtworkLoader patched")


def main() -> None:
    patch_player_service()
    patch_file_extension_screen()
    patch_preference_item()
    patch_player_button_transparent()
    patch_controls_middle_spacing()
    patch_customize_drag_always()
    patch_artwork_loader()
    print("all patches done")


if __name__ == "__main__":
    main()
