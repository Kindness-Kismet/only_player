package one.next.player.debug

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import one.next.player.MainActivity
import one.next.player.core.common.AppLanguageManager
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.SubtitleFontRepository
import one.next.player.core.media.sync.MediaInfoSynchronizer
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.ControlButtonsPosition
import one.next.player.core.model.DecoderPriority
import one.next.player.core.model.DoubleTapGesture
import one.next.player.core.model.Font
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.Resume
import one.next.player.core.model.ScreenOrientation
import one.next.player.core.model.SubtitleColor
import one.next.player.core.model.SubtitleEdgeStyle
import one.next.player.core.model.ThemeConfig
import one.next.player.core.model.ThumbnailGenerationStrategy
import one.next.player.feature.player.PlayerDebugCommandBridge
import one.next.player.feature.player.service.CustomCommands
import one.next.player.feature.player.service.PlayerService
import one.next.player.feature.player.service.setTransientPlaybackSpeed
import one.next.player.navigation.DEBUG_ACTION_OPEN_PAGE
import one.next.player.navigation.DEBUG_EXTRA_PAGE
import one.next.player.navigation.DebugPageRoute

class DebugCommandProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle = when (method) {
        METHOD_PAGE_OPEN -> openPage(arg)
        METHOD_SETTINGS_SET -> runSettingsCommand(method, arg, extras) { setSetting(arg, extras) }
        METHOD_SETTINGS_TOGGLE -> runSettingsCommand(method, arg, extras) { toggleSetting(arg) }
        METHOD_SETTINGS_ACTION -> runSettingsCommand(method, arg, extras) { runSettingAction(arg) }
        METHOD_PLAYER_ACTION -> runPlayerAction(arg, extras)
        METHOD_PLAYER_GET -> runPlayerGet(arg)
        else -> result(
            isOk = false,
            message = "Unknown method: $method",
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun openPage(pageId: String?): Bundle {
        val pageRoute = DebugPageRoute.from(pageId) ?: return result(
            isOk = false,
            message = "Unknown page: $pageId",
        )
        val context = context ?: return result(
            isOk = false,
            message = "Context is not ready",
        )

        // page.open：打开静态页面，arg 使用 DebugPageRoute.id。
        val intent = Intent(context, MainActivity::class.java).apply {
            action = DEBUG_ACTION_OPEN_PAGE
            putExtra(DEBUG_EXTRA_PAGE, pageRoute.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)

        return result(
            isOk = true,
            message = "Opening page: ${pageRoute.id}",
            command = METHOD_PAGE_OPEN,
            target = pageRoute.id,
        )
    }

    private fun runPlayerAction(
        action: String?,
        extras: Bundle?,
    ): Bundle {
        val context = context ?: return result(
            isOk = false,
            message = "Context is not ready",
            command = METHOD_PLAYER_ACTION,
            target = action,
        )
        if (action in UI_PLAYER_ACTIONS) {
            return runPlayerUiAction(action)
        }

        val value = extras ?: Bundle.EMPTY
        return runCatching {
            runBlocking {
                context.withMediaController { controller ->
                    when (action) {
                        "play" -> controller.play()
                        "pause" -> controller.pause()
                        "toggle_play_pause" -> if (controller.isPlaying) controller.pause() else controller.play()
                        "next" -> controller.seekToNextMediaItem()
                        "previous" -> controller.seekToPreviousMediaItem()
                        "seek_to" -> controller.awaitSeekTo(value.requiredLongMillis(EXTRA_VALUE))
                        "seek_by" -> controller.awaitSeekTo((controller.currentPosition + value.requiredLongMillis(EXTRA_VALUE)).coerceAtLeast(0L))
                        "long_press_speed" -> runLongPressSpeed(controller, value)
                        "stop" -> controller.stop()
                        "shuffle" -> controller.shuffleModeEnabled = value.getBoolean(EXTRA_ENABLED, !controller.shuffleModeEnabled)
                        "loop" -> controller.repeatMode = value.optionalRepeatMode() ?: controller.repeatMode.nextRepeatMode()
                        else -> error("Unknown player action: $action")
                    }
                    controller.debugStateBundle(
                        command = METHOD_PLAYER_ACTION,
                        target = action,
                        value = extras?.debugValue(),
                    )
                }
            }
        }.getOrElse {
            result(
                isOk = false,
                message = it.message ?: "Failed to handle player action: $action",
                command = METHOD_PLAYER_ACTION,
                target = action,
            )
        }
    }

    private fun runPlayerUiAction(action: String?): Bundle {
        val didHandle = PlayerDebugCommandBridge.dispatch(action.orEmpty())
        return result(
            isOk = didHandle,
            message = if (didHandle) "Handled player UI action: $action" else "Player screen is not ready for action: $action",
            command = METHOD_PLAYER_ACTION,
            target = action,
        )
    }

    private fun runPlayerGet(target: String?): Bundle {
        val context = context ?: return result(
            isOk = false,
            message = "Context is not ready",
            command = METHOD_PLAYER_GET,
            target = target,
        )

        return runCatching {
            runBlocking {
                context.withMediaController { controller ->
                    when (target) {
                        "state" -> controller.debugStateBundle(
                            command = METHOD_PLAYER_GET,
                            target = target,
                        )
                        "duration" -> result(
                            isOk = true,
                            message = "Player duration: ${controller.duration.safeTime()} ms",
                            command = METHOD_PLAYER_GET,
                            target = target,
                            durationMs = controller.duration.safeTime(),
                            positionMs = controller.currentPosition.safeTime(),
                        )
                        "position" -> result(
                            isOk = true,
                            message = "Player position: ${controller.currentPosition.safeTime()} ms",
                            command = METHOD_PLAYER_GET,
                            target = target,
                            durationMs = controller.duration.safeTime(),
                            positionMs = controller.currentPosition.safeTime(),
                        )
                        else -> error("Unknown player info target: $target")
                    }
                }
            }
        }.getOrElse {
            result(
                isOk = false,
                message = it.message ?: "Failed to get player info: $target",
                command = METHOD_PLAYER_GET,
                target = target,
            )
        }
    }

    private suspend fun MediaController.awaitSeekTo(positionMs: Long) {
        val args = Bundle().apply {
            putLong(CustomCommands.SEEK_POSITION_MS_KEY, positionMs)
        }
        sendCustomCommand(CustomCommands.PRECISE_SEEK_TO.sessionCommand, args).await()
    }

    private suspend fun runLongPressSpeed(
        controller: MediaController,
        extras: Bundle,
    ) {
        val speed = extras.requiredFloat(EXTRA_VALUE).coerceIn(
            PlayerPreferences.MIN_LONG_PRESS_CONTROLS_SPEED,
            PlayerPreferences.MAX_LONG_PRESS_CONTROLS_SPEED,
        )
        val durationMs = extras.requiredLongMillis(EXTRA_DURATION_MS).coerceAtLeast(1L)
        val originalSpeed = controller.playbackParameters.speed
        try {
            if (!controller.isPlaying) controller.play()
            controller.setTransientPlaybackSpeed(speed)
            delay(durationMs)
        } finally {
            controller.setTransientPlaybackSpeed(originalSpeed)
        }
    }

    private fun runSettingsCommand(
        method: String,
        arg: String?,
        extras: Bundle?,
        command: suspend DebugCommandEntryPoint.() -> Unit,
    ): Bundle {
        val context = context ?: return result(
            isOk = false,
            message = "Context is not ready",
            command = method,
            target = arg,
        )
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugCommandEntryPoint::class.java,
        )

        return runCatching {
            runBlocking { entryPoint.command() }
        }.fold(
            onSuccess = {
                result(
                    isOk = true,
                    message = "Handled $method: $arg",
                    command = method,
                    target = arg,
                    value = extras?.debugValue(),
                )
            },
            onFailure = {
                result(
                    isOk = false,
                    message = it.message ?: "Failed to handle $method: $arg",
                    command = method,
                    target = arg,
                )
            },
        )
    }

    private suspend fun DebugCommandEntryPoint.setSetting(
        target: String?,
        extras: Bundle?,
    ) {
        val value = extras ?: Bundle.EMPTY
        when (target) {
            "appearance.theme" -> {
                val themeConfig = enumValue<ThemeConfig>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updateApplicationPreferences { it.copy(themeConfig = themeConfig) }
            }
            "appearance.language" -> {
                val languageTag = value.getString(EXTRA_VALUE).orEmpty()
                preferencesRepository().updateApplicationPreferences { it.copy(appLanguage = languageTag) }
                AppLanguageManager.applyToCurrent(languageTag)
            }
            "appearance.dynamic_colors" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldUseDynamicColors = isEnabled)
            }
            "appearance.title_long_press_home" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldNavigateHomeOnTitleLongPress = isEnabled)
            }
            "media.mark_last_played" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldMarkLastPlayedMedia = isEnabled)
            }
            "media.restore_last_played_in_folders" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldRestoreLastPlayedMediaInFolders = isEnabled)
            }
            "media.ignore_nomedia" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldIgnoreNoMediaFiles = isEnabled)
            }
            "media.recycle_bin" -> updateApplicationBoolean(value) { preferences, isEnabled ->
                preferences.copy(isRecycleBinEnabled = isEnabled)
            }
            "media.exclude_folder" -> {
                val path = value.requiredString(EXTRA_VALUE)
                val isEnabled = value.getBoolean(EXTRA_ENABLED, true)
                preferencesRepository().updateApplicationPreferences {
                    val updatedFolders = if (isEnabled) {
                        (it.excludeFolders + path).distinct()
                    } else {
                        it.excludeFolders - path
                    }
                    it.copy(excludeFolders = updatedFolders)
                }
            }
            "thumbnail.strategy" -> {
                val strategy = enumValue<ThumbnailGenerationStrategy>(value.requiredString(EXTRA_VALUE))
                val shouldClearCache = preferencesRepository().applicationPreferences.value.thumbnailGenerationStrategy != strategy
                preferencesRepository().updateApplicationPreferences { it.copy(thumbnailGenerationStrategy = strategy) }
                if (shouldClearCache) mediaInfoSynchronizer().clearThumbnailsCache()
            }
            "thumbnail.frame_position" -> {
                val position = value.requiredFloat(EXTRA_VALUE).coerceIn(0f, 1f)
                val shouldClearCache = preferencesRepository().applicationPreferences.value.thumbnailFramePosition != position
                preferencesRepository().updateApplicationPreferences { it.copy(thumbnailFramePosition = position) }
                if (shouldClearCache) mediaInfoSynchronizer().clearThumbnailsCache()
            }
            "player.controller_timeout" -> updatePlayerInt(value) { preferences, intValue ->
                preferences.copy(controllerAutoHideTimeout = intValue.coerceIn(1, 60))
            }
            "player.screen_orientation" -> {
                val orientation = enumValue<ScreenOrientation>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences {
                    it.copy(playerScreenOrientation = orientation, lastPlayerScreenOrientation = null)
                }
            }
            "player.remember_orientation" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldRememberPlayerScreenOrientation = isEnabled, lastPlayerScreenOrientation = null)
            }
            "player.classic_icons" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldUseClassicPlayerIcons = isEnabled)
            }
            "player.resume" -> {
                val resume = enumValue<Resume>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(resume = resume) }
            }
            "player.default_speed" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(defaultPlaybackSpeed = floatValue.coerceIn(0.2f, 4.0f))
            }
            "player.autoplay" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldAutoPlay = isEnabled)
            }
            "player.auto_pip" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldAutoEnterPip = isEnabled)
            }
            "player.background_play" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldAutoPlayInBackground = isEnabled)
            }
            "player.remember_brightness" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldRememberPlayerBrightness = isEnabled)
            }
            "player.control_buttons_position" -> {
                val position = enumValue<ControlButtonsPosition>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(controlButtonsPosition = position) }
            }
            "gesture.seek" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldUseSeekControls = isEnabled) }
            "gesture.seek_sensitivity" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(seekSensitivity = floatValue.coerceIn(0.1f, 2.0f))
            }
            "gesture.brightness" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isBrightnessSwipeGestureEnabled = isEnabled) }
            "gesture.brightness_sensitivity" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(brightnessGestureSensitivity = floatValue.coerceIn(0.1f, 2.0f))
            }
            "gesture.volume" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVolumeSwipeGestureEnabled = isEnabled) }
            "gesture.volume_sensitivity" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(volumeGestureSensitivity = floatValue.coerceIn(0.1f, 2.0f))
            }
            "gesture.double_tap" -> {
                val gesture = enumValue<DoubleTapGesture>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(doubleTapGesture = gesture) }
            }
            "gesture.long_press" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(
                    shouldUseLongPressControls = isEnabled,
                    shouldUseLongPressVariableSpeed = preferences.shouldUseLongPressVariableSpeed && isEnabled,
                )
            }
            "gesture.long_press_variable_speed" -> updatePlayerBoolean(value) { preferences, isEnabled ->
                preferences.copy(shouldUseLongPressVariableSpeed = isEnabled && preferences.shouldUseLongPressControls)
            }
            "gesture.long_press_speed" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(longPressControlsSpeed = floatValue.coerceIn(PlayerPreferences.MIN_LONG_PRESS_CONTROLS_SPEED, PlayerPreferences.MAX_LONG_PRESS_CONTROLS_SPEED))
            }
            "gesture.zoom" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldUseZoomControls = isEnabled) }
            "gesture.pan" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isPanGestureEnabled = isEnabled && preferences.shouldUseZoomControls) }
            "gesture.seek_increment" -> updatePlayerInt(value) { preferences, intValue ->
                preferences.copy(seekIncrement = intValue.coerceIn(1, PlayerPreferences.MAX_SEEK_INCREMENT))
            }
            "decoder.priority" -> {
                val priority = enumValue<DecoderPriority>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(decoderPriority = priority) }
            }
            "decoder.video_filters" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldApplyVideoFilters = isEnabled) }
            "decoder.brightness_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoBrightnessFilterEnabled = isEnabled) }
            "decoder.brightness" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoBrightness = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS))
            }
            "decoder.contrast_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoContrastFilterEnabled = isEnabled) }
            "decoder.contrast" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoContrast = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST))
            }
            "decoder.saturation_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoSaturationFilterEnabled = isEnabled) }
            "decoder.saturation" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoSaturation = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION))
            }
            "decoder.hue_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoHueFilterEnabled = isEnabled) }
            "decoder.hue" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoHue = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE))
            }
            "decoder.gamma_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoGammaFilterEnabled = isEnabled) }
            "decoder.gamma" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoGamma = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA))
            }
            "decoder.sharpening_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVideoSharpeningFilterEnabled = isEnabled) }
            "decoder.sharpening" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(videoSharpening = floatValue.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING))
            }
            "audio.language" -> updatePlayerString(value) { preferences, stringValue -> preferences.copy(preferredAudioLanguage = stringValue) }
            "audio.require_focus" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRequireAudioFocus = isEnabled) }
            "audio.pause_on_headset_disconnect" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldPauseOnHeadsetDisconnect = isEnabled) }
            "audio.system_volume_panel" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldShowSystemVolumePanel = isEnabled) }
            "audio.remember_volume" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRememberPlayerVolume = isEnabled) }
            "audio.initial_volume_limit" -> updatePlayerInt(value) { preferences, intValue ->
                preferences.copy(
                    maxInitialPlayerVolumePercentage = intValue.coerceIn(
                        PlayerPreferences.MIN_INITIAL_PLAYER_VOLUME_PERCENTAGE,
                        PlayerPreferences.MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE,
                    ),
                )
            }
            "audio.normalization" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVolumeNormalizationEnabled = isEnabled) }
            "audio.boost" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isVolumeBoostEnabled = isEnabled) }
            "subtitle.auto_load" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(isSubtitleAutoLoadEnabled = isEnabled) }
            "subtitle.language" -> updatePlayerString(value) { preferences, stringValue -> preferences.copy(preferredSubtitleLanguage = stringValue) }
            "subtitle.font" -> {
                val font = enumValue<Font>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(subtitleFont = font) }
            }
            "subtitle.bold" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldUseBoldSubtitleText = isEnabled) }
            "subtitle.size" -> updatePlayerInt(value) { preferences, intValue -> preferences.copy(subtitleTextSize = intValue) }
            "subtitle.background" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldShowSubtitleBackground = isEnabled) }
            "subtitle.embedded_styles" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldApplyEmbeddedStyles = isEnabled) }
            "subtitle.encoding" -> updatePlayerString(value) { preferences, stringValue -> preferences.copy(subtitleTextEncoding = stringValue) }
            "subtitle.system_caption_style" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldUseSystemCaptionStyle = isEnabled) }
            "subtitle.color" -> {
                val color = enumValue<SubtitleColor>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(subtitleColor = color) }
            }
            "subtitle.edge_style" -> {
                val edgeStyle = enumValue<SubtitleEdgeStyle>(value.requiredString(EXTRA_VALUE))
                preferencesRepository().updatePlayerPreferences { it.copy(subtitleEdgeStyle = edgeStyle) }
            }
            "subtitle.bottom_padding" -> updatePlayerFloat(value) { preferences, floatValue ->
                preferences.copy(subtitleBottomPaddingFraction = floatValue.coerceIn(PlayerPreferences.MIN_SUBTITLE_BOTTOM_PADDING_FRACTION, PlayerPreferences.MAX_SUBTITLE_BOTTOM_PADDING_FRACTION))
            }
            "privacy.prevent_screenshots" -> updateApplicationBoolean(value) { preferences, isEnabled -> preferences.copy(shouldPreventScreenshots = isEnabled) }
            "privacy.hide_in_recents" -> updateApplicationBoolean(value) { preferences, isEnabled -> preferences.copy(shouldHideInRecents = isEnabled) }
            "about.check_updates_on_startup" -> updateApplicationBoolean(value) { preferences, isEnabled -> preferences.copy(shouldCheckForUpdatesOnStartup = isEnabled) }
            else -> error("Unknown setting target: $target")
        }
    }

    private suspend fun DebugCommandEntryPoint.toggleSetting(target: String?) {
        when (target) {
            "appearance.dynamic_colors" -> toggleApplication { it.copy(shouldUseDynamicColors = !it.shouldUseDynamicColors) }
            "appearance.title_long_press_home" -> toggleApplication { it.copy(shouldNavigateHomeOnTitleLongPress = !it.shouldNavigateHomeOnTitleLongPress) }
            "media.mark_last_played" -> toggleApplication { it.copy(shouldMarkLastPlayedMedia = !it.shouldMarkLastPlayedMedia) }
            "media.restore_last_played_in_folders" -> toggleApplication { it.copy(shouldRestoreLastPlayedMediaInFolders = !it.shouldRestoreLastPlayedMediaInFolders) }
            "media.ignore_nomedia" -> toggleApplication { it.copy(shouldIgnoreNoMediaFiles = !it.shouldIgnoreNoMediaFiles) }
            "media.recycle_bin" -> toggleApplication { it.copy(isRecycleBinEnabled = !it.isRecycleBinEnabled) }
            "player.remember_orientation" -> togglePlayer { it.copy(shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation, lastPlayerScreenOrientation = null) }
            "player.classic_icons" -> togglePlayer { it.copy(shouldUseClassicPlayerIcons = !it.shouldUseClassicPlayerIcons) }
            "player.resume" -> togglePlayer { it.copy(resume = if (it.resume == Resume.YES) Resume.NO else Resume.YES) }
            "player.autoplay" -> togglePlayer { it.copy(shouldAutoPlay = !it.shouldAutoPlay) }
            "player.auto_pip" -> togglePlayer { it.copy(shouldAutoEnterPip = !it.shouldAutoEnterPip) }
            "player.background_play" -> togglePlayer { it.copy(shouldAutoPlayInBackground = !it.shouldAutoPlayInBackground) }
            "player.remember_brightness" -> togglePlayer { it.copy(shouldRememberPlayerBrightness = !it.shouldRememberPlayerBrightness) }
            "gesture.seek" -> togglePlayer { it.copy(shouldUseSeekControls = !it.shouldUseSeekControls) }
            "gesture.brightness" -> togglePlayer { it.copy(isBrightnessSwipeGestureEnabled = !it.isBrightnessSwipeGestureEnabled) }
            "gesture.volume" -> togglePlayer { it.copy(isVolumeSwipeGestureEnabled = !it.isVolumeSwipeGestureEnabled) }
            "gesture.double_tap" -> togglePlayer { it.copy(doubleTapGesture = if (it.doubleTapGesture == DoubleTapGesture.NONE) DoubleTapGesture.FAST_FORWARD_AND_REWIND else DoubleTapGesture.NONE) }
            "gesture.long_press" -> togglePlayer {
                val isEnabled = !it.shouldUseLongPressControls
                it.copy(shouldUseLongPressControls = isEnabled, shouldUseLongPressVariableSpeed = it.shouldUseLongPressVariableSpeed && isEnabled)
            }
            "gesture.long_press_variable_speed" -> togglePlayer { it.copy(shouldUseLongPressVariableSpeed = !it.shouldUseLongPressVariableSpeed && it.shouldUseLongPressControls) }
            "gesture.zoom" -> togglePlayer { it.copy(shouldUseZoomControls = !it.shouldUseZoomControls) }
            "gesture.pan" -> togglePlayer { it.copy(isPanGestureEnabled = !it.isPanGestureEnabled && it.shouldUseZoomControls) }
            "decoder.video_filters" -> togglePlayer { it.copy(shouldApplyVideoFilters = !it.shouldApplyVideoFilters) }
            "decoder.brightness_enabled" -> togglePlayer { it.copy(isVideoBrightnessFilterEnabled = !it.isVideoBrightnessFilterEnabled) }
            "decoder.contrast_enabled" -> togglePlayer { it.copy(isVideoContrastFilterEnabled = !it.isVideoContrastFilterEnabled) }
            "decoder.saturation_enabled" -> togglePlayer { it.copy(isVideoSaturationFilterEnabled = !it.isVideoSaturationFilterEnabled) }
            "decoder.hue_enabled" -> togglePlayer { it.copy(isVideoHueFilterEnabled = !it.isVideoHueFilterEnabled) }
            "decoder.gamma_enabled" -> togglePlayer { it.copy(isVideoGammaFilterEnabled = !it.isVideoGammaFilterEnabled) }
            "decoder.sharpening_enabled" -> togglePlayer { it.copy(isVideoSharpeningFilterEnabled = !it.isVideoSharpeningFilterEnabled) }
            "audio.require_focus" -> togglePlayer { it.copy(shouldRequireAudioFocus = !it.shouldRequireAudioFocus) }
            "audio.pause_on_headset_disconnect" -> togglePlayer { it.copy(shouldPauseOnHeadsetDisconnect = !it.shouldPauseOnHeadsetDisconnect) }
            "audio.system_volume_panel" -> togglePlayer { it.copy(shouldShowSystemVolumePanel = !it.shouldShowSystemVolumePanel) }
            "audio.remember_volume" -> togglePlayer { it.copy(shouldRememberPlayerVolume = !it.shouldRememberPlayerVolume) }
            "audio.normalization" -> togglePlayer { it.copy(isVolumeNormalizationEnabled = !it.isVolumeNormalizationEnabled) }
            "audio.boost" -> togglePlayer { it.copy(isVolumeBoostEnabled = !it.isVolumeBoostEnabled) }
            "subtitle.auto_load" -> togglePlayer { it.copy(isSubtitleAutoLoadEnabled = !it.isSubtitleAutoLoadEnabled) }
            "subtitle.bold" -> togglePlayer { it.copy(shouldUseBoldSubtitleText = !it.shouldUseBoldSubtitleText) }
            "subtitle.background" -> togglePlayer { it.copy(shouldShowSubtitleBackground = !it.shouldShowSubtitleBackground) }
            "subtitle.embedded_styles" -> togglePlayer { it.copy(shouldApplyEmbeddedStyles = !it.shouldApplyEmbeddedStyles) }
            "subtitle.system_caption_style" -> togglePlayer { it.copy(shouldUseSystemCaptionStyle = !it.shouldUseSystemCaptionStyle) }
            "privacy.prevent_screenshots" -> toggleApplication { it.copy(shouldPreventScreenshots = !it.shouldPreventScreenshots) }
            "privacy.hide_in_recents" -> toggleApplication { it.copy(shouldHideInRecents = !it.shouldHideInRecents) }
            "about.check_updates_on_startup" -> toggleApplication { it.copy(shouldCheckForUpdatesOnStartup = !it.shouldCheckForUpdatesOnStartup) }
            else -> error("Unknown toggle target: $target")
        }
    }

    private suspend fun DebugCommandEntryPoint.runSettingAction(target: String?) {
        when (target) {
            "general.clear_thumbnail_cache" -> mediaInfoSynchronizer().clearThumbnailsCache()
            "general.reset_settings" -> {
                preferencesRepository().resetPreferences()
                AppLanguageManager.applyToCurrent("")
            }
            "subtitle.clear_external_font" -> subtitleFontRepository().clearFont()
            else -> error("Unknown action target: $target")
        }
    }

    private suspend fun DebugCommandEntryPoint.updateApplicationBoolean(
        extras: Bundle,
        transform: (ApplicationPreferences, Boolean) -> ApplicationPreferences,
    ) {
        val isEnabled = extras.requiredBoolean(EXTRA_ENABLED)
        preferencesRepository().updateApplicationPreferences { transform(it, isEnabled) }
    }

    private suspend fun DebugCommandEntryPoint.updatePlayerBoolean(
        extras: Bundle,
        transform: (PlayerPreferences, Boolean) -> PlayerPreferences,
    ) {
        val isEnabled = extras.requiredBoolean(EXTRA_ENABLED)
        preferencesRepository().updatePlayerPreferences { transform(it, isEnabled) }
    }

    private suspend fun DebugCommandEntryPoint.updatePlayerFloat(
        extras: Bundle,
        transform: (PlayerPreferences, Float) -> PlayerPreferences,
    ) {
        val value = extras.requiredFloat(EXTRA_VALUE)
        preferencesRepository().updatePlayerPreferences { transform(it, value) }
    }

    private suspend fun DebugCommandEntryPoint.updatePlayerInt(
        extras: Bundle,
        transform: (PlayerPreferences, Int) -> PlayerPreferences,
    ) {
        val value = extras.requiredInt(EXTRA_VALUE)
        preferencesRepository().updatePlayerPreferences { transform(it, value) }
    }

    private suspend fun DebugCommandEntryPoint.updatePlayerString(
        extras: Bundle,
        transform: (PlayerPreferences, String) -> PlayerPreferences,
    ) {
        val value = extras.getString(EXTRA_VALUE).orEmpty()
        preferencesRepository().updatePlayerPreferences { transform(it, value) }
    }

    private suspend fun DebugCommandEntryPoint.toggleApplication(transform: (ApplicationPreferences) -> ApplicationPreferences) {
        preferencesRepository().updateApplicationPreferences(transform)
    }

    private suspend fun DebugCommandEntryPoint.togglePlayer(transform: (PlayerPreferences) -> PlayerPreferences) {
        preferencesRepository().updatePlayerPreferences(transform)
    }

    private fun Bundle.requiredString(key: String): String = getString(key)?.takeIf { it.isNotBlank() } ?: error("Missing string extra: $key")

    private fun Bundle.requiredBoolean(key: String): Boolean {
        if (!containsKey(key)) error("Missing boolean extra: $key")
        return getBoolean(key)
    }

    private fun Bundle.requiredFloat(key: String): Float {
        if (!containsKey(key)) error("Missing float extra: $key")
        getString(key)?.let { return it.toFloatOrNull() ?: error("Invalid float extra: $key") }
        return getFloat(key)
    }

    private fun Bundle.requiredInt(key: String): Int {
        if (!containsKey(key)) error("Missing int extra: $key")
        getString(key)?.let { return it.toIntOrNull() ?: error("Invalid int extra: $key") }
        return getInt(key)
    }

    private fun Bundle.requiredLongMillis(key: String): Long {
        if (!containsKey(key)) error("Missing time extra: $key")
        getString(key)?.let { return it.parseTimeMillisOrNull() ?: error("Invalid time extra: $key") }
        return getLong(key).takeIf { it != 0L } ?: getInt(key).toLong()
    }

    private fun Bundle.optionalRepeatMode(): Int? {
        val rawValue = getString(EXTRA_VALUE) ?: return null
        return when (rawValue.trim().lowercase().replace('-', '_')) {
            "off" -> Player.REPEAT_MODE_OFF
            "one" -> Player.REPEAT_MODE_ONE
            "all" -> Player.REPEAT_MODE_ALL
            else -> error("Unknown loop mode: $rawValue")
        }
    }

    private fun Int.nextRepeatMode(): Int = when (this) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
        else -> Player.REPEAT_MODE_OFF
    }

    private fun Long.safeTime(): Long = takeIf { it != C.TIME_UNSET } ?: 0L

    private fun String.parseTimeMillisOrNull(): Long? {
        val rawValue = trim()
        rawValue.toLongOrNull()?.let { return it }
        val unit = rawValue.takeLastWhile { it.isLetter() }.lowercase()
        val number = rawValue.dropLast(unit.length).toLongOrNull() ?: return null
        return when (unit) {
            "ms" -> number
            "s" -> number * 1_000L
            "m" -> number * 60_000L
            else -> null
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(rawValue: String): T {
        val normalizedValue = rawValue.trim().replace('-', '_').uppercase()
        return enumValues<T>().firstOrNull { it.name == normalizedValue } ?: error("Unknown ${T::class.simpleName}: $rawValue")
    }

    private fun Bundle.debugValue(): String? = when {
        containsKey(EXTRA_VALUE) -> getString(EXTRA_VALUE) ?: getInt(EXTRA_VALUE).takeIf { it != 0 }?.toString() ?: getFloat(EXTRA_VALUE).toString()
        containsKey(EXTRA_ENABLED) -> getBoolean(EXTRA_ENABLED).toString()
        else -> null
    }

    private suspend fun <T> android.content.Context.withMediaController(block: suspend (MediaController) -> T): T = withContext(Dispatchers.Main) {
        withTimeout(3_000L) {
            val token = SessionToken(applicationContext, ComponentName(applicationContext, PlayerService::class.java))
            val future = MediaController.Builder(applicationContext, token).buildAsync()
            try {
                block(future.await())
            } finally {
                MediaController.releaseFuture(future)
            }
        }
    }

    private fun MediaController.debugStateBundle(
        command: String,
        target: String?,
        value: String? = null,
    ): Bundle = result(
        isOk = true,
        message = "Player state: position=${currentPosition.safeTime()} duration=${duration.safeTime()} playing=$isPlaying",
        command = command,
        target = target,
        value = value,
        durationMs = duration.safeTime(),
        positionMs = currentPosition.safeTime(),
        isPlaying = isPlaying,
    )

    private fun result(
        isOk: Boolean,
        message: String,
        command: String? = null,
        target: String? = null,
        value: String? = null,
        durationMs: Long? = null,
        positionMs: Long? = null,
        isPlaying: Boolean? = null,
    ): Bundle = Bundle().apply {
        putBoolean(KEY_OK, isOk)
        putString(KEY_MESSAGE, message)
        putString(KEY_COMMAND, command)
        putString(KEY_TARGET, target)
        putString(KEY_VALUE, value)
        durationMs?.let { putLong(KEY_DURATION_MS, it) }
        positionMs?.let { putLong(KEY_POSITION_MS, it) }
        isPlaying?.let { putBoolean(KEY_IS_PLAYING, it) }
    }

    companion object {
        private const val METHOD_PAGE_OPEN = "page.open"
        private const val METHOD_SETTINGS_SET = "settings.set"
        private const val METHOD_SETTINGS_TOGGLE = "settings.toggle"
        private const val METHOD_SETTINGS_ACTION = "settings.action"
        private const val METHOD_PLAYER_ACTION = "player.action"
        private const val METHOD_PLAYER_GET = "player.get"

        private const val EXTRA_VALUE = "value"
        private const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_DURATION_MS = "duration_ms"

        private const val KEY_OK = "ok"
        private const val KEY_MESSAGE = "message"
        private const val KEY_COMMAND = "command"
        private const val KEY_TARGET = "target"
        private const val KEY_VALUE = "value"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_IS_PLAYING = "is_playing"

        private val UI_PLAYER_ACTIONS = setOf(
            PlayerDebugCommandBridge.ACTION_BACK,
            PlayerDebugCommandBridge.ACTION_ROTATE,
            PlayerDebugCommandBridge.ACTION_TOGGLE_AMBIENCE,
            PlayerDebugCommandBridge.ACTION_SHOW_PLAYLIST,
            PlayerDebugCommandBridge.ACTION_SHOW_SPEED,
            PlayerDebugCommandBridge.ACTION_SHOW_AUDIO,
            PlayerDebugCommandBridge.ACTION_SHOW_SUBTITLE,
            PlayerDebugCommandBridge.ACTION_LOCK,
            PlayerDebugCommandBridge.ACTION_UNLOCK,
            PlayerDebugCommandBridge.ACTION_TOGGLE_LOCK,
            PlayerDebugCommandBridge.ACTION_CYCLE_SCALE,
            PlayerDebugCommandBridge.ACTION_SHOW_SCALE,
            PlayerDebugCommandBridge.ACTION_SHOW_DECODER,
            PlayerDebugCommandBridge.ACTION_SHOW_VIDEO_FILTERS,
            PlayerDebugCommandBridge.ACTION_PIP,
            PlayerDebugCommandBridge.ACTION_SCREENSHOT,
            PlayerDebugCommandBridge.ACTION_BACKGROUND,
            PlayerDebugCommandBridge.ACTION_SHOW_SLEEP_TIMER,
            PlayerDebugCommandBridge.ACTION_TOGGLE_CUSTOMIZE_CONTROLS,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugCommandEntryPoint {
    fun preferencesRepository(): PreferencesRepository

    fun mediaInfoSynchronizer(): MediaInfoSynchronizer

    fun subtitleFontRepository(): SubtitleFontRepository
}
