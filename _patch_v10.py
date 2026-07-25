# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"E:/Downloads/only_player_src")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"[miss] {label}")
        return text
    print(f"[ok] {label}")
    return text.replace(old, new, 1)


def patch_player_viewmodel() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
    text = path.read_text(encoding="utf-8")

    old = """    fun updateDecoderPriority(decoderPriority: DecoderPriority) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(decoderPriority = decoderPriority)
            }
        }
    }"""
    new = """    fun updateDecoderPriority(decoderPriority: DecoderPriority) {
        // 兼容旧调用：无扩展名上下文时，仅更新全局默认
        updateDecoderPriorityForExtension(extension = null, decoderPriority = decoderPriority)
    }

    /**
     * 播放器内切换解码：优先写回当前文件扩展名配置（app_preferences.json），
     * 没有扩展名时才落到全局 decoderPriority。
     */
    fun updateDecoderPriorityForExtension(
        extension: String?,
        decoderPriority: DecoderPriority,
    ) {
        val normalizedExtension = extension
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all(Char::isLetterOrDigit) }

        viewModelScope.launch {
            if (normalizedExtension == null) {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(decoderPriority = decoderPriority)
                }
                return@launch
            }

            preferencesRepository.updateApplicationPreferences { current ->
                val list = current.normalizedExtensionDecoderPreferences()
                val found = list.firstOrNull { it.extension == normalizedExtension }
                if (found?.decoderPriority == decoderPriority) {
                    current
                } else if (found == null) {
                    current.withExtensionDecoderPreferences(
                        list + one.only.player.core.model.ExtensionDecoderPreference(
                            extension = normalizedExtension,
                            decoderPriority = decoderPriority,
                            isBuiltIn = false,
                        ),
                    )
                } else {
                    current.withExtensionDecoderPreferences(
                        list.map { item ->
                            if (item.extension == normalizedExtension) {
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

    fun decoderPriorityForPath(pathOrName: String?): DecoderPriority {
        val fallback = preferencesRepository.playerPreferences.value.decoderPriority
        if (pathOrName.isNullOrBlank()) return fallback
        return preferencesRepository.applicationPreferences.value.decoderPriorityForPath(pathOrName)
            ?: fallback
    }"""
    text = replace_once(text, old, new, "PlayerViewModel decoder for extension")
    path.write_text(text, encoding="utf-8")


def patch_media_player_screen() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    text = path.read_text(encoding="utf-8")

    # Ensure imports for painterResource / Icon if needed later
    if "import androidx.compose.ui.res.painterResource" not in text:
        text = text.replace(
            "import androidx.compose.ui.Modifier\n",
            "import androidx.compose.ui.modifier\nimport androidx.compose.ui.res.painterResource\n",
            1,
        )
        print("[ok] painterResource import")

    if "import androidx.compose.material3.Icon" not in text and "androidx.compose.material3.Icon(" not in text:
        # may already use Icon elsewhere via star/other imports; check usage later
        pass

    # Center indicator with icons
    old_center = """                    if (useCenterTextIndicator) {
                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.Center),
                            visible = volumeAndBrightnessGestureState.activeGesture != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            val isVolume =
                                volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME
                            val value = if (isVolume) {
                                volumeState.volumePercentage
                            } else {
                                brightnessState.brightnessPercentage
                            }
                            val label = "$value%"
                            androidx.compose.material3.Text(
                                text = label,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    } else {"""
    new_center = """                    if (useCenterTextIndicator) {
                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.Center),
                            visible = volumeAndBrightnessGestureState.activeGesture != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            val isVolume =
                                volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME
                            val value = if (isVolume) {
                                volumeState.volumePercentage
                            } else {
                                brightnessState.brightnessPercentage
                            }
                            val iconRes = if (isVolume) {
                                coreUiR.drawable.ic_volume
                            } else {
                                coreUiR.drawable.ic_brightness
                            }
                            Row(
                                modifier = Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                                androidx.compose.material3.Text(
                                    text = "$value%",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                            }
                        }
                    } else {"""
    text = replace_once(text, old_center, new_center, "center vb indicator icons")

    # Prev/next 150dp
    old_mid = """    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        // 上一/下一尽量往两边放，中心保留播放按钮
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    new_mid = """    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 150.dp),
        // 上一/下一尽量往两边放，中心保留播放按钮
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    text = replace_once(text, old_mid, new_mid, "prev/next 150dp")

    # Decoder selector: use current media extension priority, write back extension config
    old_decoder = """                        MenuRoute.Decoder -> DecoderPrioritySelectorContent(
                            currentDecoderPriority = playerPreferences.decoderPriority,
                            onDecoderPriorityClick = {
                                viewModel.updateDecoderPriority(it)
                                dismissOverlay()
                            },
                            onDismiss = ::dismissOverlay,
                        )"""
    new_decoder = """                        MenuRoute.Decoder -> {
                            val mediaItem = player.currentMediaItem
                            val extensionCandidates = listOfNotNull(
                                mediaItem?.localConfiguration?.uri?.lastPathSegment,
                                mediaItem?.localConfiguration?.uri?.path,
                                mediaItem?.mediaMetadata?.title?.toString(),
                                mediaItem?.mediaId,
                                mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
                            )
                            var mediaExtension: String? = null
                            for (candidate in extensionCandidates) {
                                val clean = candidate
                                    .substringAfterLast('/')
                                    .substringBefore('?')
                                    .substringBefore('#')
                                val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
                                if (ext.isNotBlank() && ext.length <= 10 && ext.all { ch -> ch.isLetterOrDigit() }) {
                                    mediaExtension = ext.lowercase()
                                    break
                                }
                            }
                            val currentPriority = if (mediaExtension != null) {
                                applicationPreferences.normalizedExtensionDecoderPreferences()
                                    .firstOrNull { it.extension == mediaExtension }
                                    ?.decoderPriority
                                    ?: playerPreferences.decoderPriority
                            } else {
                                playerPreferences.decoderPriority
                            }
                            DecoderPrioritySelectorContent(
                                currentDecoderPriority = currentPriority,
                                onDecoderPriorityClick = {
                                    viewModel.updateDecoderPriorityForExtension(
                                        extension = mediaExtension,
                                        decoderPriority = it,
                                    )
                                    dismissOverlay()
                                },
                                onDismiss = ::dismissOverlay,
                            )
                        }"""
    text = replace_once(text, old_decoder, new_decoder, "decoder panel per-extension")

    # PlayerContentFrame decoderPriority should follow active/extension priority for surface refresh.
    # Keep using playerPreferences for now but better use resolved priority if available.
    # We'll compute currentEffectiveDecoder near where playerPreferences is used for decoderPriority.
    old_surface = """                    decoderPriority = playerPreferences.decoderPriority,"""
    # Need a local effective priority; inject near composition if not present.
    if "val effectiveDecoderPriority" not in text:
        # Find a stable insertion point: after activePlayerPreferences
        needle = "    val activePlayerPreferences = subtitleStylePreviewPreferences ?: playerPreferences\n"
        insert = """    val activePlayerPreferences = subtitleStylePreviewPreferences ?: playerPreferences
    val effectiveDecoderPriority = run {
        val mediaItem = player.currentMediaItem
        val candidates = listOfNotNull(
            mediaItem?.localConfiguration?.uri?.lastPathSegment,
            mediaItem?.localConfiguration?.uri?.path,
            mediaItem?.mediaMetadata?.title?.toString(),
            mediaItem?.mediaId,
            mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
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
        if (extension != null) {
            applicationPreferences.normalizedExtensionDecoderPreferences()
                .firstOrNull { it.extension == extension }
                ?.decoderPriority
                ?: playerPreferences.decoderPriority
        } else {
            playerPreferences.decoderPriority
        }
    }
"""
        if needle in text:
            text = text.replace(needle, insert, 1)
            print("[ok] effectiveDecoderPriority computed")
        else:
            print("[miss] activePlayerPreferences anchor")

    text = replace_once(
        text,
        old_surface,
        "                    decoderPriority = effectiveDecoderPriority,",
        "surface decoder uses effective",
    )

    # Also fix OverlayShowView decoder callback path if present in this file
    old_overlay = """                    onDecoderPriorityChanged = {
                        viewModel.updateDecoderPriority(it)
"""
    new_overlay = """                    onDecoderPriorityChanged = {
                        val mediaItem = player.currentMediaItem
                        val candidates = listOfNotNull(
                            mediaItem?.localConfiguration?.uri?.lastPathSegment,
                            mediaItem?.localConfiguration?.uri?.path,
                            mediaItem?.mediaMetadata?.title?.toString(),
                            mediaItem?.mediaId,
                        )
                        var extension: String? = null
                        for (candidate in candidates) {
                            val clean = candidate.substringAfterLast('/').substringBefore('?')
                            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
                            if (ext.isNotBlank() && ext.length <= 10 && ext.all { ch -> ch.isLetterOrDigit() }) {
                                extension = ext.lowercase()
                                break
                            }
                        }
                        viewModel.updateDecoderPriorityForExtension(extension, it)
"""
    text = replace_once(text, old_overlay, new_overlay, "overlay decoder per-extension")

    # Ensure applicationPreferences is available in MediaPlayerScreen
    if "applicationPreferences" not in text:
        print("[warn] applicationPreferences symbol may be missing in MediaPlayerScreen")
    else:
        # check local val
        if "val applicationPreferences" not in text and "applicationPreferences =" not in text:
            # likely from uiState
            print("[info] applicationPreferences references exist")

    path.write_text(text, encoding="utf-8")


def patch_media_player_screen_app_prefs_binding() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    text = path.read_text(encoding="utf-8")

    # Ensure we read applicationPreferences from uiState
    if "val applicationPreferences" not in text:
        # common pattern: val playerPreferences = uiState.playerPreferences ?: return / collect
        candidates = [
            "    val playerPreferences = uiState.playerPreferences ?: PlayerPreferences()\n",
            "    val playerPreferences = uiState.playerPreferences!!\n",
            "    val playerPreferences = uiState.playerPreferences ?: return\n",
        ]
        inserted = False
        for c in candidates:
            if c in text:
                text = text.replace(
                    c,
                    c + "    val applicationPreferences = uiState.applicationPreferences\n",
                    1,
                )
                print(f"[ok] bound applicationPreferences after playerPreferences")
                inserted = True
                break
        if not inserted:
            # try broader search
            idx = text.find("playerPreferences")
            print("[miss] could not auto-bind applicationPreferences; snippet:")
            print(text[max(0, idx - 200): idx + 300])
    path.write_text(text, encoding="utf-8")


def patch_controls_scroll() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsBottomView.kt"
    text = path.read_text(encoding="utf-8")

    # Add scroll state and disable user scroll when content fits / no overflow needed.
    # Replace always-on horizontalScroll with conditional.
    if "rememberScrollState" not in text:
        print("[miss] rememberScrollState import/use")
        return

    old = """        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (isCustomizingControls) {
                        true ->
                            Modifier
                                .playerControlZoneTarget(
                                    zone = PlayerControlZone.BOTTOM_LEFT,
                                    zoneBounds = zoneBounds,
                                )
                                .heightIn(min = 72.dp)
                        false -> Modifier
                    },
                )
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = when (isCustomizingControls) {
                true -> Alignment.Top
                false -> Alignment.CenterVertically
            },
            horizontalArrangement = when (controlButtonsPosition) {
                ControlButtonsPosition.LEFT -> Arrangement.spacedBy(8.dp, Alignment.Start)
                ControlButtonsPosition.RIGHT -> Arrangement.spacedBy(8.dp, Alignment.End)
            },
        ) {
            bottomLeftControls.forEach { control ->
                val isHidden = control !in visiblePlayerControls
                if (!isCustomizingControls && isHidden && !shouldKeepHiddenControlSlots) return@forEach
"""
    new = """        val visibleBottomControls = bottomLeftControls.filter { control ->
            val isHidden = control !in visiblePlayerControls
            isCustomizingControls || !isHidden || shouldKeepHiddenControlSlots
        }
        // 可见按钮少时禁用横滑，避免只剩自定义按钮还能右滑空滑
        val shouldAllowHorizontalScroll = isCustomizingControls || visibleBottomControls.size > 6
        val bottomControlsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (isCustomizingControls) {
                        true ->
                            Modifier
                                .playerControlZoneTarget(
                                    zone = PlayerControlZone.BOTTOM_LEFT,
                                    zoneBounds = zoneBounds,
                                )
                                .heightIn(min = 72.dp)
                        false -> Modifier
                    },
                )
                .then(
                    if (shouldAllowHorizontalScroll) {
                        Modifier.horizontalScroll(bottomControlsScrollState)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = when (isCustomizingControls) {
                true -> Alignment.Top
                false -> Alignment.CenterVertically
            },
            horizontalArrangement = when (controlButtonsPosition) {
                ControlButtonsPosition.LEFT -> Arrangement.spacedBy(8.dp, Alignment.Start)
                ControlButtonsPosition.RIGHT -> Arrangement.spacedBy(8.dp, Alignment.End)
            },
        ) {
            bottomLeftControls.forEach { control ->
                val isHidden = control !in visiblePlayerControls
                if (!isCustomizingControls && isHidden && !shouldKeepHiddenControlSlots) return@forEach
"""
    text = replace_once(text, old, new, "bottom controls conditional scroll")
    path.write_text(text, encoding="utf-8")


def patch_player_button_shadow() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/buttons/PlayerButton.kt"
    text = path.read_text(encoding="utf-8")

    if "import androidx.compose.ui.draw.shadow" not in text:
        text = text.replace(
            "import androidx.compose.ui.draw.alpha\n",
            "import androidx.compose.ui.draw.alpha\nimport androidx.compose.ui.draw.shadow\n",
            1,
        )
        print("[ok] shadow import")

    old = """                PlayerIconStyle.TRANSPARENT -> {
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
    new = """                PlayerIconStyle.TRANSPARENT -> {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isOutlineOnly) colorScheme.primary else Color.White,
                        LocalRippleConfiguration provides whiteRippleConfiguration,
                    ) {
                        // 全透：无圆形背景；轻微增强图标阴影，提升可见度
                        IconButton(
                            onClick = {},
                            enabled = isEnabled,
                            modifier = Modifier
                                .size(buttonSize)
                                .then(if (isOutlineOnly) outlineModifier else Modifier)
                                .background(Color.Transparent)
                                .shadow(
                                    elevation = 3.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = Color.Black.copy(alpha = 0.55f),
                                    spotColor = Color.Black.copy(alpha = 0.75f),
                                ),
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
    text = replace_once(text, old, new, "transparent icon stronger shadow")
    path.write_text(text, encoding="utf-8")


def patch_player_service_media_and_decoder() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    text = path.read_text(encoding="utf-8")

    # Shared mp3 detection helper
    if "private fun isMp3MediaItem" not in text:
        helper = """
    private fun isMp3MediaItem(mediaItem: MediaItem?): Boolean {
        if (mediaItem == null) return false
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
            uri?.path,
            mediaItem.mediaId,
            mediaItem.mediaMetadata.title?.toString(),
            mediaItem.requestMetadata?.mediaUri?.toString(),
        )
        for (candidate in candidates) {
            val clean = candidate
                .substringAfterLast('/')
                .substringBefore('?')
                .substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            if (ext.equals("mp3", ignoreCase = true)) return true
        }
        val mime = mediaItem.localConfiguration?.mimeType
        if (mime != null && mime.contains("mpeg", ignoreCase = true) && mime.contains("audio", ignoreCase = true)) {
            return true
        }
        return false
    }

"""
        marker = "    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {"
        text = replace_once(text, marker, helper + marker, "isMp3MediaItem helper")

    # Fix shouldAccept / shouldPublish with real mp3 detection
    old_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
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
    }"""
    new_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
        // 本应用控制器始终允许；HIDE 时拒绝系统媒体中心/蓝牙等外部控制器
        val packageName = controller.packageName
        if (packageName.isNullOrBlank() || packageName == applicationContext.packageName) {
            return true
        }
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(mediaSession?.player?.currentMediaItem)
        }
    }"""
    text = replace_once(text, old_accept, new_accept, "shouldAccept mp3")

    old_publish_vis = """    private fun shouldPublishMediaSessionNotificationForVisibility(): Boolean {
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> true
        }
    }

    private fun shouldPublishMediaSessionNotification(session: MediaSession): Boolean {
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.AUDIO_ONLY -> {
                val item = session.player.currentMediaItem
                val name = listOfNotNull(
                    item?.localConfiguration?.uri?.lastPathSegment,
                    item?.localConfiguration?.uri?.path,
                    item?.mediaId,
                ).joinToString(" ")
                name.substringAfterLast('.', missingDelimiterValue = "").equals("mp3", ignoreCase = true)
            }
        }
    }"""
    new_publish_vis = """    private fun shouldPublishMediaSessionNotificationForVisibility(): Boolean {
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
    text = replace_once(text, old_publish_vis, new_publish_vis, "publish visibility mp3")

    # Also clear notification when HIDE by calling super carefully and maybe setMediaNotificationProvider?
    # Media3: returning early from onUpdateNotification may leave old notification.
    old_update = """    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!shouldPublishMediaSessionNotification(session)) {
            // 不向系统媒体会话中心/通知栏发布播放控件
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }"""
    new_update = """    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!shouldPublishMediaSessionNotification(session)) {
            // 完全不出现在系统媒体播放控件/通知
            // startInForegroundRequired=false 且不调用 super，避免系统媒体会话中心收录
            if (startInForegroundRequired) {
                // 仍需保活前台服务时，走默认通知，但外部系统媒体中心已通过 shouldAccept 拒绝
                super.onUpdateNotification(session, true)
            }
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }"""
    text = replace_once(text, old_update, new_update, "onUpdateNotification hide")

    # Stop writing extension config from global playerPreferences collector.
    # Manual selection from player UI now goes through ViewModel -> applicationPreferences.
    old_collect = """        serviceScope.launch {
            var isFirstDecoderPreferenceEmission = true
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }
                .collect {
                    val current = mediaSession?.player?.currentMediaItem
                    if (isFirstDecoderPreferenceEmission) {
                        // 启动时只按扩展名配置应用，不回写扩展名
                        isFirstDecoderPreferenceEmission = false
                        applyExtensionDecoderForMediaItem(current)
                        return@collect
                    }
                    // 播放器内改解码：同步到当前文件扩展名配置，并立即切换
                    if (current != null) {
                        updateExtensionDecoderFromManualSelection(current, it.decoderPriority)
                    }
                    applyExtensionDecoderForMediaItem(current)
                }
        }"""
    new_collect = """        serviceScope.launch {
            // 全局默认解码仅在“无扩展名配置”时作为回退；扩展名配置变化已在 applicationPreferences 流中处理
            preferencesRepository.playerPreferences
                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }
                .collect {
                    val current = mediaSession?.player?.currentMediaItem
                    applyExtensionDecoderForMediaItem(current)
                }
        }"""
    text = replace_once(text, old_collect, new_collect, "remove auto writeback from global decoder")

    # Keep updateExtensionDecoderFromManualSelection for safety but unused is fine.

    # Improve resolveDecoderPriorityForCurrentQueue to use resolveDecoderPriorityForMediaItem
    old_queue = """    private fun resolveDecoderPriorityForCurrentQueue(
        mediaItems: List<MediaItem>,
        fallback: DecoderPriority = DecoderPriority.AUTOMATIC,
    ): DecoderPriority {
        val current = mediaItems.firstOrNull() ?: return fallback
        val uri = current.localConfiguration?.uri
        val pathCandidates = listOfNotNull(
            uri?.lastPathSegment,
            uri?.path,
            uri?.toString(),
            current.mediaId,
            current.requestMetadata?.mediaUri?.toString(),
            current.mediaMetadata.extras?.getString("media_metadata_remote_file_path"),
        )
        for (candidate in pathCandidates) {
            val priority = resolveDecoderPriorityForPath(candidate)
            // 命中扩展名配置（非完全空白路径）就用
            if (!candidate.isNullOrBlank()) {
                val ext = candidate.substringAfterLast('.', missingDelimiterValue = "")
                if (ext.isNotBlank() && ext.length <= 8) {
                    return priority
                }
            }
        }
        return fallback
    }"""
    new_queue = """    private fun resolveDecoderPriorityForCurrentQueue(
        mediaItems: List<MediaItem>,
        fallback: DecoderPriority = DecoderPriority.AUTOMATIC,
    ): DecoderPriority {
        val current = mediaItems.firstOrNull() ?: return fallback
        return resolveDecoderPriorityForMediaItem(current).takeIf { true } ?: fallback
    }"""
    text = replace_once(text, old_queue, new_queue, "queue uses media item resolve")

    # Also re-apply when media session visibility changes? optional.

    # After HIDE, reject external controllers is enough. For AUDIO_ONLY re-evaluate on media transition:
    old_transition = """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            applyExtensionDecoderForMediaItem(mediaItem)
"""
    new_transition = """        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            applyExtensionDecoderForMediaItem(mediaItem)
            // 媒体切换后按当前文件重新判定系统媒体控件可见性（仅 MP3 模式）
            mediaSession?.let { session ->
                onUpdateNotification(session, startInForegroundRequired = false)
            }
"""
    text = replace_once(text, old_transition, new_transition, "refresh media session on transition")

    path.write_text(text, encoding="utf-8")


def patch_datastore_path_comment() -> None:
    # User asked for data folder config file. DataStore already writes:
    # files/datastore/app_preferences.json which includes extensionDecoderPreferences.
    # Optionally also mirror a readable json under files/extension_decoder_preferences.json.
    path = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/medialibrary/FileExtensionPreferencesViewModel.kt"
    text = path.read_text(encoding="utf-8")
    if "extension_decoder_preferences.json" in text:
        print("[skip] mirror already")
        return

    # Add mirror write after updates. Keep simple: inject helper methods.
    old_init = """    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update {
                    it.copy(preferences = prefs.normalizedExtensionDecoderPreferences())
                }
            }
        }
    }"""
    new_init = """    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                val normalized = prefs.normalizedExtensionDecoderPreferences()
                uiStateInternal.update {
                    it.copy(preferences = normalized)
                }
                // 同步导出一份可读配置到 data/files，便于确认按后缀解码已落盘
                persistExtensionDecoderMirror(normalized)
            }
        }
    }"""
    text = replace_once(text, old_init, new_init, "mirror collect")

    # Add imports + helper. Need Application context via AndroidViewModel? Currently plain ViewModel.
    # Use preferencesRepository only; write via app data dir through injected Application?
    # Avoid Android deps in settings ViewModel if none. Check existing injects.

    if "@ApplicationContext" not in text and "Context" not in text:
        # Convert lightly: inject Context
        text = text.replace(
            "import androidx.lifecycle.ViewModel\n",
            "import android.content.Context\n"
            "import androidx.lifecycle.ViewModel\n"
            "import dagger.hilt.android.qualifiers.ApplicationContext\n"
            "import java.io.File\n"
            "import kotlinx.serialization.json.Json\n",
            1,
        )
        text = text.replace(
            "class FileExtensionPreferencesViewModel @Inject constructor(\n"
            "    private val preferencesRepository: PreferencesRepository,\n"
            "    private val mediaSynchronizer: MediaSynchronizer,\n"
            ") : ViewModel() {",
            "class FileExtensionPreferencesViewModel @Inject constructor(\n"
            "    @ApplicationContext private val appContext: Context,\n"
            "    private val preferencesRepository: PreferencesRepository,\n"
            "    private val mediaSynchronizer: MediaSynchronizer,\n"
            ") : ViewModel() {",
            1,
        )
        helper = """
    private val mirrorJson = Json { prettyPrint = true; encodeDefaults = true }

    private fun persistExtensionDecoderMirror(preferences: List<ExtensionDecoderPreference>) {
        runCatching {
            val dir = File(appContext.filesDir, "data")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "extension_decoder_preferences.json")
            file.writeText(mirrorJson.encodeToString(preferences))
        }
    }

"""
        text = text.replace(
            "    private val uiStateInternal = MutableStateFlow(FileExtensionPreferencesUiState())\n",
            helper + "    private val uiStateInternal = MutableStateFlow(FileExtensionPreferencesUiState())\n",
            1,
        )
        # Need encodeToString serializer import
        if "import kotlinx.serialization.encodeToString" not in text:
            text = text.replace(
                "import kotlinx.serialization.json.Json\n",
                "import kotlinx.serialization.encodeToString\nimport kotlinx.serialization.json.Json\n",
                1,
            )
        print("[ok] mirror file writer")
    path.write_text(text, encoding="utf-8")


def patch_overlay_show_view() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/OverlayShowView.kt"
    text = path.read_text(encoding="utf-8")
    # If it still uses playerPreferences.decoderPriority for display, leave to MediaPlayerScreen path.
    print("OverlayShowView decoder refs:", text.count("decoderPriority"))


def main() -> None:
    patch_player_viewmodel()
    patch_media_player_screen()
    patch_media_player_screen_app_prefs_binding()
    patch_controls_scroll()
    patch_player_button_shadow()
    patch_player_service_media_and_decoder()
    patch_datastore_path_comment()
    patch_overlay_show_view()
    print("v10 patches applied")


if __name__ == "__main__":
    main()
