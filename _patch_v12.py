# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"E:/Downloads/only_player_src")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        print(f"[miss] {label}")
        return text
    print(f"[ok] {label}")
    return text.replace(old, new, 1)


def patch_unlock_button() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt"
    t = path.read_text(encoding="utf-8")
    old = """                if (controlsVisibilityState.isControlsVisible && controlsVisibilityState.isControlsLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(top = 24.dp),
                    ) {
                        PlayerButton(onClick = { controlsVisibilityState.unlockControls() }) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }
                    }
                } else {"""
    new = """                if (controlsVisibilityState.isControlsVisible && controlsVisibilityState.isControlsLocked) {
                    // 解锁按钮与播放/上一/下一同水平，最右 22dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        PlayerButton(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 22.dp),
                            onClick = { controlsVisibilityState.unlockControls() },
                        ) {
                            Icon(
                                painter = painterResource(coreUiR.drawable.ic_lock),
                                contentDescription = stringResource(coreUiR.string.controls_unlock),
                            )
                        }
                    }
                } else {"""
    t = replace_once(t, old, new, "unlock button center-end 22dp")
    path.write_text(t, encoding="utf-8")


def patch_rotation_remember() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/state/RotationState.kt"
    t = path.read_text(encoding="utf-8")

    # Always remember manual rotate when switch is on, even if mode is VIDEO_ORIENTATION.
    old_rotate = """    fun rotate() {
        val newOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LastPlayerScreenOrientation.PORTRAIT
            else -> LastPlayerScreenOrientation.LANDSCAPE
        }
        activity.requestedOrientation = newOrientation.toActivityOrientation()
        if (shouldRememberScreenOrientation) {
            onLastScreenOrientationChange(newOrientation)
        }
    }"""
    new_rotate = """    fun rotate() {
        val newOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> LastPlayerScreenOrientation.PORTRAIT
            else -> LastPlayerScreenOrientation.LANDSCAPE
        }
        activity.requestedOrientation = newOrientation.toActivityOrientation()
        // 手动旋转后始终写回记住方向（开关开启时）
        if (shouldRememberScreenOrientation) {
            onLastScreenOrientationChange(newOrientation)
        }
    }"""
    t = replace_once(t, old_rotate, new_rotate, "rotate always save")

    old_set = """    private fun setOrientation(player: Player) {
        Log.d(TAG, "setOrientation: requestedOrientation=${activity.requestedOrientation}")
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return

        activity.requestedOrientation = lastScreenOrientation
            ?.takeIf { shouldRememberScreenOrientation && screenOrientation != ScreenOrientation.VIDEO_ORIENTATION }
            ?.toActivityOrientation()
            ?: when (screenOrientation) {
                ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenOrientation.VIDEO_ORIENTATION -> getVideoBasedOrientation(player)
            }
    }"""
    new_set = """    private fun setOrientation(player: Player) {
        Log.d(TAG, "setOrientation: requestedOrientation=${activity.requestedOrientation}")
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return

        // 优先使用记住的手动旋转方向（含“按视频方向”模式）
        val remembered = lastScreenOrientation
            ?.takeIf { shouldRememberScreenOrientation }
            ?.toActivityOrientation()
        if (remembered != null) {
            activity.requestedOrientation = remembered
            return
        }

        activity.requestedOrientation = when (screenOrientation) {
            ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.VIDEO_ORIENTATION -> getVideoBasedOrientation(player)
        }
    }"""
    t = replace_once(t, old_set, new_set, "setOrientation prefer remembered")

    old_maybe = """    private fun maybeApplyVideoOrientation(player: Player) {
        if (screenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        val orientation = getVideoBasedOrientation(player)
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            Log.d(TAG, "applyOrientation: $orientation")
            activity.requestedOrientation = orientation
        }
    }"""
    new_maybe = """    private fun maybeApplyVideoOrientation(player: Player) {
        if (screenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        // 已记住手动方向时，不要被视频宽高覆盖
        if (shouldRememberScreenOrientation && lastScreenOrientation != null) return
        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
        val orientation = getVideoBasedOrientation(player)
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            Log.d(TAG, "applyOrientation: $orientation")
            activity.requestedOrientation = orientation
        }
    }"""
    t = replace_once(t, old_maybe, new_maybe, "maybeApply skip when remembered")
    path.write_text(t, encoding="utf-8")

    # PlayerActivity launch also should prefer remembered even for VIDEO_ORIENTATION
    pa = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerActivity.kt"
    pt = pa.read_text(encoding="utf-8")
    old_cfg = """    private fun applyConfiguredOrientation() {
        val prefs = playerPreferences ?: return
        if (prefs.playerScreenOrientation == ScreenOrientation.VIDEO_ORIENTATION) return

        val orientation = prefs.lastPlayerScreenOrientation
            ?.takeIf { prefs.shouldRememberPlayerScreenOrientation }
            ?.toActivityOrientation()
            ?: prefs.playerScreenOrientation.toActivityOrientation()
        applyRequestedOrientation(orientation)
    }"""
    new_cfg = """    private fun applyConfiguredOrientation() {
        val prefs = playerPreferences ?: return
        val remembered = prefs.lastPlayerScreenOrientation
            ?.takeIf { prefs.shouldRememberPlayerScreenOrientation }
            ?.toActivityOrientation()
        if (remembered != null) {
            applyRequestedOrientation(remembered)
            return
        }
        if (prefs.playerScreenOrientation == ScreenOrientation.VIDEO_ORIENTATION) return
        applyRequestedOrientation(prefs.playerScreenOrientation.toActivityOrientation())
    }"""
    pt = replace_once(pt, old_cfg, new_cfg, "PlayerActivity remembered orientation")
    pa.write_text(pt, encoding="utf-8")

    # Don't clear last orientation when toggling remember on
    vm = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesViewModel.kt"
    vt = vm.read_text(encoding="utf-8")
    old_toggle = """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    lastPlayerScreenOrientation = null,
                )
            }
        }
    }"""
    new_toggle = """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    // 关闭时清空；开启时保留已有记录
                    lastPlayerScreenOrientation = if (shouldRememberPlayerScreenOrientation) {
                        it.lastPlayerScreenOrientation
                    } else {
                        null
                    },
                )
            }
        }
    }"""
    vt = replace_once(vt, old_toggle, new_toggle, "toggle remember keep history")
    vm.write_text(vt, encoding="utf-8")


def patch_remember_decoder_switch() -> None:
    prefs = ROOT / "core/model/src/main/java/one/only/player/core/model/PlayerPreferences.kt"
    t = prefs.read_text(encoding="utf-8")
    if "shouldRememberDecoderPerExtension" not in t:
        t = replace_once(
            t,
            "    // 解码偏好\n    val decoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,\n",
            "    // 解码偏好\n"
            "    val decoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,\n"
            "    // 是否记住按扩展名的解码方式（播放器内切换会写回扩展名配置）\n"
            "    val shouldRememberDecoderPerExtension: Boolean = true,\n",
            "add remember decoder flag",
        )
        prefs.write_text(t, encoding="utf-8")

    # strings
    for rel, title, desc in [
        (
            "core/ui/src/main/res/values-zh-rCN/strings.xml",
            "记住解码方式",
            "播放器内切换解码时，按文件扩展名分别记住",
        ),
        (
            "core/ui/src/main/res/values-zh-rTW/strings.xml",
            "記住解碼方式",
            "播放器內切換解碼時，依副檔名分別記住",
        ),
        (
            "core/ui/src/main/res/values/strings.xml",
            "Remember decoder per extension",
            "When changing decoder in player, remember it by file extension",
        ),
    ]:
        p = ROOT / rel
        s = p.read_text(encoding="utf-8")
        if "remember_decoder_per_extension" not in s:
            s = s.replace(
                "</resources>",
                f'    <string name="remember_decoder_per_extension">{title}</string>\n'
                f'    <string name="remember_decoder_per_extension_description">{desc}</string>\n'
                f"</resources>",
            )
            p.write_text(s, encoding="utf-8")
            print(f"[ok] strings {rel}")

    # decoder_desc fix
    for rel, old, new in [
        (
            "core/ui/src/main/res/values-zh-rCN/strings.xml",
            "<string name=\"decoder_desc\">解码器、视频滤镜选项</string>",
            "<string name=\"decoder_desc\">视频滤镜选项</string>",
        ),
        (
            "core/ui/src/main/res/values-zh-rTW/strings.xml",
            "<string name=\"decoder_desc\">解碼器、影片濾鏡選項</string>",
            "<string name=\"decoder_desc\">影片濾鏡選項</string>",
        ),
        (
            "core/ui/src/main/res/values/strings.xml",
            "<string name=\"decoder_desc\">Decoder priority and video filter options</string>",
            "<string name=\"decoder_desc\">Video filter options</string>",
        ),
    ]:
        p = ROOT / rel
        s = p.read_text(encoding="utf-8")
        if old in s:
            p.write_text(s.replace(old, new), encoding="utf-8")
            print(f"[ok] decoder_desc {rel}")

    # settings screen switch near orientation or decoder section - put under player prefs near media session or controls
    screen = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesScreen.kt"
    st = screen.read_text(encoding="utf-8")
    if "remember_decoder_per_extension" not in st:
        needle = """                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_orientation"),
                    title = stringResource(id = R.string.remember_player_screen_orientation),
                    description = stringResource(id = R.string.remember_player_screen_orientation_description),
                    icon = NextIcons.History,
                    isChecked = uiState.preferences.shouldRememberPlayerScreenOrientation,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation) },
                )"""
        insert = needle + """
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_player_remember_decoder"),
                    title = stringResource(id = R.string.remember_decoder_per_extension),
                    description = stringResource(id = R.string.remember_decoder_per_extension_description),
                    icon = NextIcons.Decoder,
                    isChecked = uiState.preferences.shouldRememberDecoderPerExtension,
                    onClick = { onEvent(PlayerPreferencesUiEvent.ToggleRememberDecoderPerExtension) },
                )"""
        st = replace_once(st, needle, insert, "settings remember decoder switch")
        screen.write_text(st, encoding="utf-8")

    vm = ROOT / "feature/settings/src/main/java/one/only/player/settings/screens/player/PlayerPreferencesViewModel.kt"
    vt = vm.read_text(encoding="utf-8")
    if "ToggleRememberDecoderPerExtension" not in vt:
        # event
        vt = replace_once(
            vt,
            "    data object ToggleRememberPlayerScreenOrientation : PlayerPreferencesUiEvent\n",
            "    data object ToggleRememberPlayerScreenOrientation : PlayerPreferencesUiEvent\n"
            "    data object ToggleRememberDecoderPerExtension : PlayerPreferencesUiEvent\n",
            "event toggle decoder",
        )
        # when
        vt = replace_once(
            vt,
            "            is PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation -> toggleRememberPlayerScreenOrientation()\n",
            "            is PlayerPreferencesUiEvent.ToggleRememberPlayerScreenOrientation -> toggleRememberPlayerScreenOrientation()\n"
            "            is PlayerPreferencesUiEvent.ToggleRememberDecoderPerExtension -> toggleRememberDecoderPerExtension()\n",
            "when toggle decoder",
        )
        # method after toggleRememberPlayerScreenOrientation
        vt = replace_once(
            vt,
            """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    // 关闭时清空；开启时保留已有记录
                    lastPlayerScreenOrientation = if (shouldRememberPlayerScreenOrientation) {
                        it.lastPlayerScreenOrientation
                    } else {
                        null
                    },
                )
            }
        }
    }
""",
            """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    // 关闭时清空；开启时保留已有记录
                    lastPlayerScreenOrientation = if (shouldRememberPlayerScreenOrientation) {
                        it.lastPlayerScreenOrientation
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun toggleRememberDecoderPerExtension() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldRememberDecoderPerExtension = !it.shouldRememberDecoderPerExtension)
            }
        }
    }
""",
            "method toggle decoder",
        )
        # fallback if old toggle block not updated yet
        if "toggleRememberDecoderPerExtension" not in vt:
            vt = replace_once(
                vt,
                """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    lastPlayerScreenOrientation = null,
                )
            }
        }
    }
""",
                """    private fun toggleRememberPlayerScreenOrientation() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                val shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation
                it.copy(
                    shouldRememberPlayerScreenOrientation = shouldRememberPlayerScreenOrientation,
                    lastPlayerScreenOrientation = if (shouldRememberPlayerScreenOrientation) {
                        it.lastPlayerScreenOrientation
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun toggleRememberDecoderPerExtension() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldRememberDecoderPerExtension = !it.shouldRememberDecoderPerExtension)
            }
        }
    }
""",
                "method toggle decoder fallback",
            )
        vm.write_text(vt, encoding="utf-8")

    # PlayerViewModel writeback only when switch on
    pvm = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
    pvt = pvm.read_text(encoding="utf-8")
    old_fn = """    fun updateDecoderPriorityForExtension(
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
"""
    new_fn = """    fun updateDecoderPriorityForExtension(
        extension: String?,
        decoderPriority: DecoderPriority,
    ) {
        val normalizedExtension = extension
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all(Char::isLetterOrDigit) }

        viewModelScope.launch {
            val shouldRemember = preferencesRepository.playerPreferences.value.shouldRememberDecoderPerExtension
            if (normalizedExtension == null || !shouldRemember) {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(decoderPriority = decoderPriority)
                }
                return@launch
            }

            preferencesRepository.updateApplicationPreferences { current ->
"""
    pvt = replace_once(pvt, old_fn, new_fn, "decoder remember switch gate")
    pvm.write_text(pvt, encoding="utf-8")


def patch_top_right_align() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsTopView.kt"
    t = path.read_text(encoding="utf-8")
    # bottom uses start=12 end=8 in column; user wants top right end align same as bottom end
    # bottom column: padding(start=12, end=8). So endPadding should be 8.dp landscape/portrait same.
    old = """    val endPadding = if (isLandscape) 16.dp else 8.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(start = 8.dp, end = endPadding)
            .padding(bottom = 16.dp),
"""
    new = """    // 右端与底部控件右间距对齐（底部 end=8.dp）
    val endPadding = 8.dp
    Row(
        modifier = modifier
            .padding(systemBarsPadding.copy(bottom = 0.dp))
            .padding(start = 8.dp, end = endPadding)
            .padding(bottom = 16.dp),
"""
    t = replace_once(t, old, new, "top end padding align bottom")
    # ensure maxVisibleCount 5
    t = t.replace(
        "val maxVisibleCount = if (isLandscape) 5 else 5",
        "val maxVisibleCount = 5",
    )
    path.write_text(t, encoding="utf-8")


def patch_above_seekbar_zone() -> None:
    # ensure zone always target even when not customizing so drag drop works, and min size
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/ui/controls/ControlsBottomView.kt"
    t = path.read_text(encoding="utf-8")
    old = """            Row(
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
"""
    new = """            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .heightIn(min = 40.dp)
                    .playerControlZoneTarget(
                        zone = PlayerControlZone.ABOVE_SEEKBAR_RIGHT,
                        zoneBounds = zoneBounds,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                // 无控件时在编辑模式显示可投放空位
                if (isCustomizingControls && aboveSeekbarRightControls.isEmpty()) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
                aboveSeekbarRightControls.forEach { control ->
"""
    t = replace_once(t, old, new, "above seekbar zone always droppable")
    path.write_text(t, encoding="utf-8")


def patch_media_session_hard() -> None:
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt"
    t = path.read_text(encoding="utf-8")

    # onGetSession: when HIDE, only return session to local app package
    old_get = "    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession\n"
    new_get = """    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // HIDE：对系统媒体中心等外部控制器不暴露 session，避免仍出现在系统媒体播放
        if (!shouldAcceptMediaController(controllerInfo)) {
            return null
        }
        return mediaSession
    }
"""
    t = replace_once(t, old_get, new_get, "onGetSession hide external")

    # Accept: for AUDIO_ONLY external controllers only when mp3; never block local
    # Also treat media3/systemui packages as external
    old_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
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
    new_accept = """    private fun shouldAcceptMediaController(controller: MediaSession.ControllerInfo): Boolean {
        val packageName = controller.packageName.orEmpty()
        val isLocalApp =
            packageName.isBlank() ||
                packageName == applicationContext.packageName ||
                controller.uid == applicationInfo.uid
        if (isLocalApp) return true

        // 外部控制器（系统媒体中心、蓝牙、其它 App）
        return when (playerPreferences.mediaSessionVisibility) {
            MediaSessionVisibility.HIDE -> false
            MediaSessionVisibility.SHOW -> true
            MediaSessionVisibility.AUDIO_ONLY -> isMp3MediaItem(mediaSession?.player?.currentMediaItem)
        }
    }"""
    t = replace_once(t, old_accept, new_accept, "accept local always")

    # setMediaNotificationProvider empty when hidden? Media3 API - use setMediaNotificationProvider that no-ops
    # After mediaSession build, we can call setMediaNotificationProvider
    if "setMediaNotificationProvider" not in t:
        old_build = """        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create media session", e)
        }
    }"""
        new_build = """        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
            // 自定义通知提供器：HIDE/非MP3 时不发布 MediaStyle 通知
            setMediaNotificationProvider(
                object : MediaSessionService.MediaNotificationProvider {
                    override fun createNotification(
                        mediaSession: MediaSession,
                        customLayout: com.google.common.collect.ImmutableList<CommandButton>,
                        actionFactory: MediaSessionService.MediaNotificationProvider.MediaNotificationActionFactory,
                        onNotificationChangedCallback: MediaSessionService.MediaNotificationProvider.NotificationChangedCallback,
                    ): MediaSessionService.MediaNotification {
                        if (!shouldPublishMediaSessionNotification(mediaSession)) {
                            // 返回空通知内容 + 使用 quiet foreground，避免进入系统媒体中心
                            val channelId = "player_service_quiet"
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                val manager = getSystemService(android.app.NotificationManager::class.java)
                                val channel = android.app.NotificationChannel(
                                    channelId,
                                    getString(coreUiR.string.notification_channel_player_name),
                                    android.app.NotificationManager.IMPORTANCE_MIN,
                                ).apply {
                                    setShowBadge(false)
                                    setSound(null, null)
                                }
                                manager?.createNotificationChannel(channel)
                            }
                            val notification = androidx.core.app.NotificationCompat.Builder(this@PlayerService, channelId)
                                .setContentTitle(getString(coreUiR.string.app_name))
                                .setContentText(getString(coreUiR.string.playing_in_background))
                                .setSmallIcon(coreUiR.drawable.ic_play)
                                .setOngoing(true)
                                .setSilent(true)
                                .setLocalOnly(true)
                                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                                .build()
                            return MediaSessionService.MediaNotification(0x4F504C59, notification)
                        }
                        return DefaultMediaNotificationProvider.Builder(this@PlayerService)
                            .build()
                            .createNotification(
                                mediaSession,
                                customLayout,
                                actionFactory,
                                onNotificationChangedCallback,
                            )
                    }

                    override fun handleCustomCommand(
                        session: MediaSession,
                        action: String,
                        extras: Bundle,
                    ): Boolean = false
                },
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to create media session", e)
        }
    }"""
        t = replace_once(t, old_build, new_build, "custom media notification provider")
        if "import androidx.media3.session.DefaultMediaNotificationProvider" not in t:
            t = t.replace(
                "import androidx.media3.session.MediaSessionService\n",
                "import androidx.media3.session.DefaultMediaNotificationProvider\n"
                "import androidx.media3.session.MediaSessionService\n",
                1,
            )
            print("[ok] import DefaultMediaNotificationProvider")

    path.write_text(t, encoding="utf-8")


def patch_rotation_persist_file() -> None:
    # Mirror last orientation into files/data for user visibility (like extension decoder)
    path = ROOT / "feature/player/src/main/java/one/only/player/feature/player/PlayerViewModel.kt"
    t = path.read_text(encoding="utf-8")
    # Keep simple: already in player_preferences.json datastore. Add optional mirror in updateLastPlayerScreenOrientation
    if "last_player_orientation.txt" not in t:
        old = """    fun updateLastPlayerScreenOrientation(value: LastPlayerScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { preferences ->
                if (!preferences.shouldRememberPlayerScreenOrientation) return@updatePlayerPreferences preferences
                preferences.copy(lastPlayerScreenOrientation = value)
            }
        }
    }"""
        # Need Context - ViewModel may not have it. Skip file mirror if no context; datastore is authority.
        # Instead document in comment only.
        print("[skip] orientation mirror needs context; datastore remains source of truth")
    # Ensure update always writes when called
    old2 = """    fun updateLastPlayerScreenOrientation(value: LastPlayerScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { preferences ->
                if (!preferences.shouldRememberPlayerScreenOrientation) return@updatePlayerPreferences preferences
                preferences.copy(lastPlayerScreenOrientation = value)
            }
        }
    }"""
    new2 = """    fun updateLastPlayerScreenOrientation(value: LastPlayerScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { preferences ->
                if (!preferences.shouldRememberPlayerScreenOrientation) return@updatePlayerPreferences preferences
                // 写入 player_preferences.json（files/datastore）
                preferences.copy(lastPlayerScreenOrientation = value)
            }
        }
    }"""
    t = replace_once(t, old2, new2, "orientation write comment")
    path.write_text(t, encoding="utf-8")


def main() -> None:
    patch_unlock_button()
    patch_rotation_remember()
    patch_remember_decoder_switch()
    patch_top_right_align()
    patch_above_seekbar_zone()
    patch_media_session_hard()
    patch_rotation_persist_file()
    print("v12 patches applied")


if __name__ == "__main__":
    main()
