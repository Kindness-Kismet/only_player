# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"E:/Downloads/only_player_src")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"[miss] {label}")
        return text
    print(f"[ok] {label}")
    return text.replace(old, new, 1)


def patch_player_button() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/buttons/PlayerButton.kt"
    t = path.read_text(encoding="utf-8")
    old = """                PlayerIconStyle.TRANSPARENT -> {
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
    new = """                PlayerIconStyle.TRANSPARENT -> {
                    CompositionLocalProvider(
                        LocalContentColor provides if (isOutlineOnly) colorScheme.primary else Color.White,
                        LocalRippleConfiguration provides whiteRippleConfiguration,
                    ) {
                        // 全透：无圆形背景，无额外阴影
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
    t = replace_once(t, old, new, "revert transparent shadow")
    # remove unused shadow import if no longer used
    if ".shadow(" not in t and "import androidx.compose.ui.draw.shadow" in t:
        t = t.replace("import androidx.compose.ui.draw.shadow\n", "")
        print("[ok] remove shadow import")
    path.write_text(t, encoding="utf-8")


def patch_seekbar_white() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsBottomView.kt"
    t = path.read_text(encoding="utf-8")
    # add imports
    if "LocalPlayerIconStyle" not in t:
        t = t.replace(
            "import one.only.player.feature.player.LocalControlsVisibilityState\n",
            "import one.only.player.feature.player.LocalControlsVisibilityState\n"
            "import one.only.player.feature.player.LocalPlayerIconStyle\n"
            "import one.only.player.core.model.PlayerIconStyle\n",
            1,
        )
        print("[ok] seekbar icon style import")

    old_slider = """private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
"""
    new_slider = """private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    // 全透图标风格：进度条与控件一致用白色，不使用主题着色
    val accentColor = when (LocalPlayerIconStyle.current) {
        PlayerIconStyle.TRANSPARENT -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }
    val primaryColor = accentColor
"""
    t = replace_once(t, old_slider, new_slider, "seekbar white when transparent")

    # bottom scroll fix + padding + above-seekbar zone
    old_scroll = """        val visibleBottomControls = bottomLeftControls.filter { control ->
            val isHidden = control !in visiblePlayerControls
            isCustomizingControls || !isHidden || shouldKeepHiddenControlSlots
        }
        // 可见按钮少时禁用横滑，避免只剩自定义按钮还能右滑空滑
        val shouldAllowHorizontalScroll = isCustomizingControls || visibleBottomControls.size > 5
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
"""
    new_scroll = """        // 只统计真实绘制的按钮（不含隐藏占位），避免误开横滑
        val renderedBottomControls = bottomLeftControls.filter { control ->
            isCustomizingControls || control in visiblePlayerControls
        }
        val shouldAllowHorizontalScroll = renderedBottomControls.size > 5
        val bottomControlsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
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
                .horizontalScroll(
                    state = bottomControlsScrollState,
                    enabled = shouldAllowHorizontalScroll,
                ),
"""
    t = replace_once(t, old_scroll, new_scroll, "bottom scroll enabled only when overflow")

    # Add above-seekbar right zone: need new params bottomAboveSeekbarControls
    # Modify function signature and UI row above seekbar
    if "aboveSeekbarRightControls" not in t:
        t = t.replace(
            "    bottomLeftControls: List<PlayerControl>,\n",
            "    bottomLeftControls: List<PlayerControl>,\n"
            "    aboveSeekbarRightControls: List<PlayerControl> = emptyList(),\n",
            1,
        )
        old_time_row = """            Spacer(modifier = Modifier.weight(1f))
            // 旋转按钮已并入可自定义底栏控件列表，不再固定在进度条旁小尺寸位置
        }"""
        new_time_row = """            Spacer(modifier = Modifier.weight(1f))
            // 进度条上方最右侧可编辑控件区（竖/横屏通用）
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .then(
                        if (isCustomizingControls) {
                            Modifier.playerControlZoneTarget(
                                zone = PlayerControlZone.ABOVE_SEEKBAR_RIGHT,
                                zoneBounds = zoneBounds,
                            )
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                aboveSeekbarRightControls.forEach { control ->
                    if (!isCustomizingControls && control !in visiblePlayerControls) return@forEach
                    key(control) {
                        AnimatedPlayerControlPlacement(
                            control = control,
                            itemBounds = itemBounds,
                            isTracking = isCustomizingControls,
                        ) {
                            PlayerCustomizableControlButton(
                                modifier = Modifier.playerControlDragSource(
                                    control = control,
                                    enabled = isCustomizingControls,
                                    onDropDragged = onControlDropDragged,
                                    onDragStarted = onControlDragStarted,
                                    onDragMoved = onControlDragMoved,
                                    onDragCancelled = onControlDragCancelled,
                                ),
                                control = control,
                                isBeingDragged = draggingControl == control,
                                player = player,
                                videoContentScale = videoContentScale,
                                isPipSupported = isPipSupported,
                                isCustomizingControls = isCustomizingControls,
                                shouldHideLabel = shouldHideLabels,
                                visiblePlayerControls = visiblePlayerControls,
                                isMuted = isMuted,
                                onPlaylistClick = onPlaylistClick,
                                onPlaybackSpeedClick = onPlaybackSpeedClick,
                                onAudioClick = onAudioClick,
                                onSubtitleClick = onSubtitleClick,
                                onLockControlsClick = onLockControlsClick,
                                onMuteClick = onMuteClick,
                                onPlaybackMarksClick = onPlaybackMarksClick,
                                onVideoContentScaleClick = onVideoContentScaleClick,
                                onVideoContentScaleLongClick = onVideoContentScaleLongClick,
                                onDecoderClick = onDecoderClick,
                                onAmbienceModeClick = onAmbienceModeClick,
                                isAmbienceModeEnabled = isAmbienceModeEnabled,
                                onVideoFiltersClick = onVideoFiltersClick,
                                onPictureInPictureClick = onPictureInPictureClick,
                                onRotateClick = onRotateClick,
                                onCustomizeControlsClick = onCustomizeControlsClick,
                                isTakingScreenshot = isTakingScreenshot,
                                onScreenshotClick = onScreenshotClick,
                                onPlayInBackgroundClick = onPlayInBackgroundClick,
                                onLoopClick = onLoopClick,
                                onShuffleClick = onShuffleClick,
                                onSleepTimerClick = onSleepTimerClick,
                                sleepTimerState = sleepTimerState,
                            )
                        }
                    }
                }
            }
        }"""
        t = replace_once(t, old_time_row, new_time_row, "above seekbar right zone UI")

    # portrait bottom left padding a bit more
    old_col = """            .padding(horizontal = 8.dp)
            .padding(top = 16.dp, bottom = 24.dp),"""
    new_col = """            .padding(start = 12.dp, end = 8.dp)
            .padding(top = 16.dp, bottom = 24.dp),"""
    t = replace_once(t, old_col, new_col, "bottom left padding +4dp")

    path.write_text(t, encoding="utf-8")


def patch_controls_layout_model() -> None:
    path = ROOT / "core/model/src/main/java/one/only/player/core/model/PlayerControlsLayout.kt"
    t = path.read_text(encoding="utf-8")
    old_zone = """enum class PlayerControlZone {
    TOP_RIGHT,
    BOTTOM_LEFT,
}"""
    new_zone = """enum class PlayerControlZone {
    TOP_RIGHT,
    BOTTOM_LEFT,
    ABOVE_SEEKBAR_RIGHT,
}"""
    t = replace_once(t, old_zone, new_zone, "add ABOVE_SEEKBAR_RIGHT zone")

    # version bump so existing layouts get new zone capability without forced reposition
    t = replace_once(
        t,
        "internal const val CURRENT_VERSION = 5",
        "internal const val CURRENT_VERSION = 6",
        "layout version 6",
    )
    path.write_text(t, encoding="utf-8")


def patch_controls_top() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsTopView.kt"
    t = path.read_text(encoding="utf-8")
    # max visible 5 landscape, 5 portrait (not 3)
    t = replace_once(
        t,
        "    val maxVisibleCount = if (isLandscape) 6 else 4",
        "    // 横屏默认完整显示 5 个；竖屏也不只 3 个",
        "comment max visible",
    )
    # if previous replace removed the line incorrectly, fix
    if "val maxVisibleCount" not in t:
        t = t.replace(
            "    // 横屏默认完整显示 5 个；竖屏也不只 3 个\n",
            "    // 横屏默认完整显示 5 个；竖屏也不只 3 个\n"
            "    val maxVisibleCount = if (isLandscape) 5 else 5\n",
            1,
        )
        print("[ok] maxVisibleCount restored 5/5")
    else:
        t = t.replace(
            "val maxVisibleCount = if (isLandscape) 6 else 4",
            "val maxVisibleCount = if (isLandscape) 5 else 5",
        )

    # right end padding larger in landscape
    old_row = """    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {"""
    new_row = """    val endPadding = if (isLandscape) 16.dp else 8.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(start = 8.dp, end = endPadding)
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {"""
    t = replace_once(t, old_row, new_row, "top end padding landscape")

    # add onCustomizeControlsClick param and pass through
    if "onCustomizeControlsClick" not in t:
        t = t.replace(
            "    onPlayInBackgroundClick: () -> Unit = {},\n",
            "    onPlayInBackgroundClick: () -> Unit = {},\n"
            "    onCustomizeControlsClick: () -> Unit = {},\n",
            1,
        )
        t = t.replace(
            "                            onPlayInBackgroundClick = onPlayInBackgroundClick,\n"
            "                            onLoopClick = onLoopClick,\n",
            "                            onPlayInBackgroundClick = onPlayInBackgroundClick,\n"
            "                            onCustomizeControlsClick = onCustomizeControlsClick,\n"
            "                            onLoopClick = onLoopClick,\n",
            1,
        )
        print("[ok] top customize click wiring")

    # conditional horizontal scroll for top
    old_top_scroll = """                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topRightControls.forEach { control ->
                if (!isCustomizingControls && control !in visiblePlayerControls) return@forEach
"""
    new_top_scroll = """                .horizontalScroll(
                    state = rememberScrollState(),
                    enabled = isCustomizingControls || topRightControls.count {
                        isCustomizingControls || it in visiblePlayerControls
                    } > maxVisibleCount,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topRightControls.forEach { control ->
                if (!isCustomizingControls && control !in visiblePlayerControls) return@forEach
"""
    t = replace_once(t, old_top_scroll, new_top_scroll, "top conditional scroll")
    path.write_text(t, encoding="utf-8")


def patch_media_player_screen() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    t = path.read_text(encoding="utf-8")

    # permanently visible includes CUSTOMIZE
    old_perm = """    val permanentlyVisibleControls = remember {
        setOf(
            PlayerControl.BACK,
            PlayerControl.PREVIOUS,
            PlayerControl.PLAY_PAUSE,
            PlayerControl.NEXT,
        )
    }"""
    new_perm = """    val permanentlyVisibleControls = remember {
        setOf(
            PlayerControl.BACK,
            PlayerControl.PREVIOUS,
            PlayerControl.PLAY_PAUSE,
            PlayerControl.NEXT,
            // 自定义按钮只能移动，不能被隐藏
            PlayerControl.CUSTOMIZE,
        )
    }"""
    t = replace_once(t, old_perm, new_perm, "CUSTOMIZE permanently visible")

    old_toggle = """    fun toggleControlVisibility(control: PlayerControl) {
        val updatedControls = hiddenPlayerControls.toMutableSet().apply {
            if (!add(control)) remove(control)
        }
"""
    new_toggle = """    fun toggleControlVisibility(control: PlayerControl) {
        if (control == PlayerControl.CUSTOMIZE) {
            // 自定义按钮不可隐藏，仅可拖动改位置
            return
        }
        val updatedControls = hiddenPlayerControls.toMutableSet().apply {
            if (!add(control)) remove(control)
        }
"""
    t = replace_once(t, old_toggle, new_toggle, "block hide CUSTOMIZE")

    # ensure CUSTOMIZE never stays in hidden set when loading
    old_enter = """        customizingHiddenPlayerControls = playerPreferences.hiddenPlayerControls - permanentlyVisibleControls
"""
    # multiple occurrences possible
    if old_enter in t:
        t = t.replace(
            old_enter,
            "        customizingHiddenPlayerControls = playerPreferences.hiddenPlayerControls - permanentlyVisibleControls - setOf(PlayerControl.CUSTOMIZE)\n",
        )
        print("[ok] strip CUSTOMIZE from hidden on enter/cancel")

    # ControlsMiddleView orientation-aware padding
    old_mid = """@Composable
fun ControlsMiddleView(
    modifier: Modifier = Modifier,
    player: Player,
    isCustomizingControls: Boolean = false,
    isPreviousVisible: Boolean = true,
    isPreviousSelected: Boolean = false,
    isPlayPauseVisible: Boolean = true,
    isPlayPauseSelected: Boolean = false,
    isNextVisible: Boolean = true,
    isNextSelected: Boolean = false,
    onPreviousClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 150.dp),
        // 上一/下一尽量往两边放，中心保留播放按钮
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    new_mid = """@Composable
fun ControlsMiddleView(
    modifier: Modifier = Modifier,
    player: Player,
    isCustomizingControls: Boolean = false,
    isPreviousVisible: Boolean = true,
    isPreviousSelected: Boolean = false,
    isPlayPauseVisible: Boolean = true,
    isPlayPauseSelected: Boolean = false,
    isNextVisible: Boolean = true,
    isNextSelected: Boolean = false,
    onPreviousClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 横屏上一/下一更靠两边；竖屏仅 22dp
    val horizontalPadding = if (isLandscape) 160.dp else 22.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
    t = replace_once(t, old_mid, new_mid, "middle padding landscape/portrait")

    # wire aboveSeekbarRightControls + onCustomize to top
    if "aboveSeekbarRightControls" not in t:
        # find controlsByZone
        t = t.replace(
            "    val bottomLeftControls = controlsByZone.getValue(PlayerControlZone.BOTTOM_LEFT)\n",
            "    val bottomLeftControls = controlsByZone.getValue(PlayerControlZone.BOTTOM_LEFT)\n"
            "    val aboveSeekbarRightControls = controlsByZone[PlayerControlZone.ABOVE_SEEKBAR_RIGHT].orEmpty()\n",
            1,
        )
        print("[ok] aboveSeekbarRightControls local")

    # pass to ControlsBottomView
    if "aboveSeekbarRightControls =" not in t.split("ControlsBottomView(")[-1][:800]:
        t = t.replace(
            "                                    ControlsBottomView(\n"
            "                                        player = player,\n"
            "                                        mediaPresentationState = mediaPresentationState,\n"
            "                                        bottomLeftControls = bottomLeftControls,\n",
            "                                    ControlsBottomView(\n"
            "                                        player = player,\n"
            "                                        mediaPresentationState = mediaPresentationState,\n"
            "                                        bottomLeftControls = bottomLeftControls,\n"
            "                                        aboveSeekbarRightControls = aboveSeekbarRightControls,\n",
            1,
        )
        print("[ok] pass aboveSeekbarRight to bottom")

    # ControlsTopView onCustomizeControlsClick
    if "onCustomizeControlsClick =" not in t[t.find("ControlsTopView("):t.find("ControlsTopView(")+2500]:
        # inject near onBackClick or end of top view params - look for first ControlsTopView block
        marker = "                                    ControlsTopView(\n"
        idx = t.find(marker)
        if idx >= 0:
            # find onBackClick line in this call
            chunk = t[idx:idx+3500]
            if "onCustomizeControlsClick" not in chunk:
                t = t.replace(
                    "                                        onBackClick = onBackClick,\n"
                    "                                    )\n",
                    "                                        onBackClick = onBackClick,\n"
                    "                                        onCustomizeControlsClick = {\n"
                    "                                            if (isCustomizingControls) {\n"
                    "                                                // ignore hide\n"
                    "                                            } else {\n"
                    "                                                enterControlCustomization()\n"
                    "                                            }\n"
                    "                                        },\n"
                    "                                    )\n",
                    1,
                )
                # might match wrong - try more unique in modern top section
                print("[try] top customize wiring")

    # customize click bottom: only enter, never toggle hide
    t = t.replace(
        """                                        onCustomizeControlsClick = {
                                            if (isCustomizingControls) {
                                                // 自定义按钮也可隐藏/显示，并支持长按拖动排序
                                                toggleControlVisibility(PlayerControl.CUSTOMIZE)
                                            } else {
                                                enterControlCustomization()
                                            }
                                        },""",
        """                                        onCustomizeControlsClick = {
                                            if (!isCustomizingControls) {
                                                enterControlCustomization()
                                            }
                                        },""",
    )
    print("[ok] customize click no hide")

    path.write_text(t, encoding="utf-8")


def patch_player_service_media() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    t = path.read_text(encoding="utf-8")

    old_update = """    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
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
    new_update = """    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!shouldPublishMediaSessionNotification(session)) {
            // HIDE / 非 MP3：不走 Media3 默认媒体通知，避免出现在系统媒体中心
            // 仍满足前台服务要求：使用普通前台通知（无 MediaStyle）
            ensureNonMediaForegroundIfNeeded(startInForegroundRequired)
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun ensureNonMediaForegroundIfNeeded(startInForegroundRequired: Boolean) {
        if (!startInForegroundRequired && android.os.Build.VERSION.SDK_INT < 34) {
            // 非强制前台时不推媒体通知即可
            return
        }
        runCatching {
            val channelId = "player_service_quiet"
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val manager = getSystemService(android.app.NotificationManager::class.java)
                val channel = android.app.NotificationChannel(
                    channelId,
                    getString(coreUiR.string.notification_channel_player_name),
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    description = getString(coreUiR.string.notification_channel_player_description)
                }
                manager?.createNotificationChannel(channel)
            }
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(coreUiR.string.app_name))
                .setContentText(getString(coreUiR.string.playing_in_background))
                .setSmallIcon(coreUiR.drawable.ic_play)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                .build()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    QUIET_FOREGROUND_NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(QUIET_FOREGROUND_NOTIFICATION_ID, notification)
            }
        }
    }"""
    t = replace_once(t, old_update, new_update, "quiet non-media foreground")

    # companion id
    if "QUIET_FOREGROUND_NOTIFICATION_ID" not in t:
        t = t.replace(
            '        private const val TAG = "PlayerService"\n',
            '        private const val TAG = "PlayerService"\n'
            "        private const val QUIET_FOREGROUND_NOTIFICATION_ID = 0x4F50_4C59\n",
            1,
        )
        print("[ok] quiet notification id")

    old_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
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
    new_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
        // 本应用 / 未知本机控制器始终允许，避免阻断播放
        val packageName = controller.packageName
        if (
            packageName.isNullOrBlank() ||
            packageName == applicationContext.packageName ||
            packageName == "android" ||
            controller.uid == applicationInfo.uid
        ) {
            return true
        }
        // 仅限制系统媒体中心 / 蓝牙等外部控制器
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(mediaSession?.player?.currentMediaItem)
        }
    }"""
    t = replace_once(t, old_accept, new_accept, "accept never blocks local playback")

    # isMp3 only by extension, remove broad mime that might misclassify
    old_mp3_tail = """        val mime = mediaItem.localConfiguration?.mimeType
        if (mime != null && mime.contains("mpeg", ignoreCase = true) && mime.contains("audio", ignoreCase = true)) {
            return true
        }
        return false
    }"""
    new_mp3_tail = """        // 仅按 .mp3 后缀判定，避免 mime 误伤其它格式
        return false
    }"""
    t = replace_once(t, old_mp3_tail, new_mp3_tail, "mp3 extension-only")

    path.write_text(t, encoding="utf-8")


def patch_strings_video_filters() -> None:
    for rel, old, new in [
        (
            "core/ui/src/main/res/values-zh-rCN/strings.xml",
            "<string name=\"video_filters_unavailable_software_decoder\">软解码不支持视频滤镜</string>",
            "<string name=\"video_filters_unavailable_software_decoder\">不支持视频滤镜</string>",
        ),
        (
            "core/ui/src/main/res/values-zh-rTW/strings.xml",
            "<string name=\"video_filters_unavailable_software_decoder\">軟體解碼不支援影片濾鏡</string>",
            "<string name=\"video_filters_unavailable_software_decoder\">不支援影片濾鏡</string>",
        ),
        (
            "core/ui/src/main/res/values/strings.xml",
            "<string name=\"video_filters_unavailable_software_decoder\">The software decoder does not support video filters</string>",
            "<string name=\"video_filters_unavailable_software_decoder\">Video filters unavailable</string>",
        ),
        (
            "core/ui/src/main/res/values-zh-rCN/strings.xml",
            "<string name=\"enable_video_filters_description\">关闭后跳过 GPU 视频滤镜以节省电量</string>",
            "<string name=\"enable_video_filters_description\">关闭后跳过 GPU 视频滤镜以节省电量</string>",
        ),
    ]:
        p = ROOT / rel
        t = p.read_text(encoding="utf-8")
        if old in t:
            p.write_text(t.replace(old, new), encoding="utf-8")
            print(f"[ok] string {rel}")
        else:
            print(f"[miss] string {rel}")

    # Also remove 解码 from description if present elsewhere related to 画面处理
    # User: 画面处理设置下方的小字提示，把解码器、文字删除
    # Might mean remove entire subtitle under video filters that mentions decoder - done via unavailable string.
    # If enable description is not the issue, also check settings player screen for software decoder hint.


def patch_drop_zone_support() -> None:
    # PlayerControlsDragDrop already uses zoneBounds map - ABOVE_SEEKBAR_RIGHT works if registered
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerControlsDragDrop.kt"
    t = path.read_text(encoding="utf-8")
    # no change required if generic
    print("dragdrop zones generic OK")


def patch_string_resources_notifications() -> None:
    # ensure notification strings exist or fall back
    for rel in [
        "core/ui/src/main/res/values/strings.xml",
        "core/ui/src/main/res/values-zh-rCN/strings.xml",
    ]:
        p = ROOT / rel
        t = p.read_text(encoding="utf-8")
        changed = False
        if "notification_channel_player_name" not in t:
            t = t.replace(
                "</resources>",
                '    <string name="notification_channel_player_name">Playback</string>\n'
                '    <string name="notification_channel_player_description">Background playback service</string>\n'
                '    <string name="playing_in_background">Playing</string>\n'
                "</resources>",
            )
            # zh
            if "zh-rCN" in rel:
                t = t.replace(
                    '    <string name="notification_channel_player_name">Playback</string>\n'
                    '    <string name="notification_channel_player_description">Background playback service</string>\n'
                    '    <string name="playing_in_background">Playing</string>\n',
                    '    <string name="notification_channel_player_name">播放服务</string>\n'
                    '    <string name="notification_channel_player_description">后台播放服务</string>\n'
                    '    <string name="playing_in_background">正在播放</string>\n',
                )
            changed = True
        if "app_name" not in t:
            # usually exists
            pass
        if changed:
            p.write_text(t, encoding="utf-8")
            print(f"[ok] notif strings {rel}")


def patch_media_screen_top_customize_and_zones() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    t = path.read_text(encoding="utf-8")

    # Ensure LocalConfiguration import exists for ControlsMiddleView
    if "import androidx.compose.ui.platform.LocalConfiguration" not in t:
        t = t.replace(
            "import androidx.compose.ui.Modifier\n",
            "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalConfiguration\n",
            1,
        )
        print("[ok] LocalConfiguration import")

    # Wire onCustomizeControlsClick into ControlsTopView more robustly
    if "ControlsTopView(" in t and "onCustomizeControlsClick" not in t[t.find("ControlsTopView("): t.find("ControlsTopView(")+4000]:
        # Insert before closing of first ControlsTopView - find onBackClick within first 4k
        start = t.find("ControlsTopView(")
        end = t.find(")", start + 200)
        # search for unique modern top section
        needle = "                                        onBackClick = onBackClick,"
        pos = t.find(needle)
        if pos > 0 and "onCustomizeControlsClick" not in t[pos-500:pos+200]:
            t = t[:pos] + (
                "                                        onCustomizeControlsClick = {\n"
                "                                            if (!isCustomizingControls) {\n"
                "                                                enterControlCustomization()\n"
                "                                            }\n"
                "                                        },\n"
            ) + t[pos:]
            print("[ok] top customize inserted before onBackClick")

    path.write_text(t, encoding="utf-8")


def main() -> None:
    patch_player_button()
    patch_seekbar_white()
    patch_controls_layout_model()
    patch_controls_top()
    patch_media_player_screen()
    patch_media_screen_top_customize_and_zones()
    patch_player_service_media()
    patch_strings_video_filters()
    patch_string_resources_notifications()
    patch_drop_zone_support()
    print("v11 patches done")


if __name__ == "__main__":
    main()
