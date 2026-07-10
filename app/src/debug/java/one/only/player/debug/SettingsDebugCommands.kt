package one.only.player.debug

import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import one.only.player.core.common.AppLanguageManager
import one.only.player.core.common.AppThemeMode
import one.only.player.core.common.AppThemeModeManager
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.ControlButtonsPosition
import one.only.player.core.model.ControllerAutoHidePreset
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.DoubleTapGesture
import one.only.player.core.model.Font
import one.only.player.core.model.PlayerControlsStyle
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Resume
import one.only.player.core.model.ScreenOrientation
import one.only.player.core.model.SubtitleColor
import one.only.player.core.model.SubtitleEdgeStyle
import one.only.player.core.model.ThemeConfig
import one.only.player.core.model.ThumbnailGenerationStrategy
import one.only.player.core.model.withVideoFilterAdjustment
import one.only.player.core.model.withVideoSharpening
import one.only.player.core.model.withVideoSharpeningFilterEnabled

internal fun Context.runSettingsCommand(
    method: String,
    arg: String?,
    extras: Bundle?,
    command: suspend DebugCommandEntryPoint.() -> Unit,
): Bundle {
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )

    return runCatching {
        runBlocking { entryPoint.command() }
    }.fold(
        onSuccess = {
            debugResult(
                isOk = true,
                message = "Handled $method: $arg",
                command = method,
                target = arg,
                value = extras?.debugValue(),
            )
        },
        onFailure = {
            debugResult(
                isOk = false,
                message = it.message ?: "Failed to handle $method: $arg",
                command = method,
                target = arg,
            )
        },
    )
}

internal suspend fun DebugCommandEntryPoint.setSetting(
    context: Context,
    target: String?,
    extras: Bundle?,
) {
    val value = extras ?: Bundle.EMPTY
    when (target) {
        "appearance.theme" -> {
            val themeConfig = enumValue<ThemeConfig>(value.requiredString(EXTRA_VALUE))
            preferencesRepository().updateApplicationPreferences { it.copy(themeConfig = themeConfig) }
            AppThemeModeManager.applyToCurrent(
                context = context,
                mode = themeConfig.toAppThemeMode(),
            )
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
        "appearance.floating_navigation_bar" -> updateApplicationBoolean(value) { preferences, isEnabled ->
            preferences.copy(shouldUseFloatingNavigationBar = isEnabled)
        }
        "appearance.floating_navigation_bar_blur" -> updateApplicationBoolean(value) { preferences, isEnabled ->
            preferences.copy(shouldBlurFloatingNavigationBar = isEnabled)
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
        "player.controller_timeout" -> updateControllerAutoHideTimeout(value)
        "player.controller_timeout_preset" -> {
            val preset = enumValue<ControllerAutoHidePreset>(value.requiredString(EXTRA_VALUE))
            preferencesRepository().updatePlayerPreferences { it.copy(controllerAutoHidePreset = preset) }
        }
        "player.dim_video_controls" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.copy(shouldDimVideoWhenControlsVisible = isEnabled)
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
        "player.icon_style" -> {
            val style = enumValue<PlayerIconStyle>(value.requiredString(EXTRA_VALUE))
            preferencesRepository().updatePlayerPreferences { it.copy(playerIconStyle = style) }
        }
        "player.controls_style" -> {
            val style = enumValue<PlayerControlsStyle>(value.requiredString(EXTRA_VALUE))
            preferencesRepository().updatePlayerPreferences { it.copy(controlsStyle = style) }
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
        "player.pause_at_end_of_queue" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.copy(shouldPauseAtEndOfQueue = isEnabled)
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
        "player.control_labels" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.copy(shouldHidePlayerControlLabels = !isEnabled)
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
        "decoder.brightness_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoFilterAdjustment { it.copy(isVideoBrightnessFilterEnabled = isEnabled) }
        }
        "decoder.brightness" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoFilterAdjustment {
                it.copy(videoBrightness = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS))
            }
        }
        "decoder.contrast_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoFilterAdjustment { it.copy(isVideoContrastFilterEnabled = isEnabled) }
        }
        "decoder.contrast" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoFilterAdjustment {
                it.copy(videoContrast = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST))
            }
        }
        "decoder.saturation_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoFilterAdjustment { it.copy(isVideoSaturationFilterEnabled = isEnabled) }
        }
        "decoder.saturation" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoFilterAdjustment {
                it.copy(videoSaturation = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION))
            }
        }
        "decoder.hue_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoFilterAdjustment { it.copy(isVideoHueFilterEnabled = isEnabled) }
        }
        "decoder.hue" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoFilterAdjustment {
                it.copy(videoHue = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE))
            }
        }
        "decoder.gamma_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoFilterAdjustment { it.copy(isVideoGammaFilterEnabled = isEnabled) }
        }
        "decoder.gamma" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoFilterAdjustment {
                it.copy(videoGamma = floatValue.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA))
            }
        }
        "decoder.sharpening_enabled" -> updatePlayerBoolean(value) { preferences, isEnabled ->
            preferences.withVideoSharpeningFilterEnabled(isEnabled)
        }
        "decoder.sharpening" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.withVideoSharpening(
                floatValue.coerceIn(
                    PlayerPreferences.DEFAULT_VIDEO_SHARPENING,
                    PlayerPreferences.MAX_VIDEO_SHARPENING,
                ),
            )
        }
        "audio.language" -> updatePlayerString(value) { preferences, stringValue -> preferences.copy(preferredAudioLanguage = stringValue) }
        "audio.require_focus" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRequireAudioFocus = isEnabled) }
        "audio.pause_on_headset_disconnect" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldPauseOnHeadsetDisconnect = isEnabled) }
        "audio.system_volume_panel" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldShowSystemVolumePanel = isEnabled) }
        "audio.remember_volume" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRememberPlayerVolume = isEnabled) }
        "audio.remember_track" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRememberAudioTrack = isEnabled) }
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
        "subtitle.remember_track" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldRememberSubtitleTrack = isEnabled) }
        "subtitle.language" -> updatePlayerString(value) { preferences, stringValue -> preferences.copy(preferredSubtitleLanguage = stringValue) }
        "subtitle.font" -> {
            val font = enumValue<Font>(value.requiredString(EXTRA_VALUE))
            preferencesRepository().updatePlayerPreferences { it.copy(subtitleFont = font) }
        }
        "subtitle.bold" -> updatePlayerBoolean(value) { preferences, isEnabled -> preferences.copy(shouldUseBoldSubtitleText = isEnabled) }
        "subtitle.size" -> updatePlayerFloat(value) { preferences, floatValue -> preferences.copy(subtitleTextSize = floatValue) }
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
        "subtitle.outline_thickness" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.copy(
                subtitleOutlineThickness = floatValue.coerceIn(
                    PlayerPreferences.MIN_SUBTITLE_OUTLINE_THICKNESS,
                    PlayerPreferences.MAX_SUBTITLE_OUTLINE_THICKNESS,
                ),
            )
        }
        "subtitle.shadow_strength" -> updatePlayerFloat(value) { preferences, floatValue ->
            preferences.copy(
                subtitleShadowStrength = floatValue.coerceIn(
                    PlayerPreferences.MIN_SUBTITLE_SHADOW_STRENGTH,
                    PlayerPreferences.MAX_SUBTITLE_SHADOW_STRENGTH,
                ),
            )
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

internal suspend fun DebugCommandEntryPoint.toggleSetting(target: String?) {
    when (target) {
        "appearance.dynamic_colors" -> toggleApplication { it.copy(shouldUseDynamicColors = !it.shouldUseDynamicColors) }
        "appearance.title_long_press_home" -> toggleApplication { it.copy(shouldNavigateHomeOnTitleLongPress = !it.shouldNavigateHomeOnTitleLongPress) }
        "appearance.floating_navigation_bar" -> toggleApplication { it.copy(shouldUseFloatingNavigationBar = !it.shouldUseFloatingNavigationBar) }
        "appearance.floating_navigation_bar_blur" -> toggleApplication { it.copy(shouldBlurFloatingNavigationBar = !it.shouldBlurFloatingNavigationBar) }
        "media.mark_last_played" -> toggleApplication { it.copy(shouldMarkLastPlayedMedia = !it.shouldMarkLastPlayedMedia) }
        "media.restore_last_played_in_folders" -> toggleApplication { it.copy(shouldRestoreLastPlayedMediaInFolders = !it.shouldRestoreLastPlayedMediaInFolders) }
        "media.ignore_nomedia" -> toggleApplication { it.copy(shouldIgnoreNoMediaFiles = !it.shouldIgnoreNoMediaFiles) }
        "media.recycle_bin" -> toggleApplication { it.copy(isRecycleBinEnabled = !it.isRecycleBinEnabled) }
        "player.remember_orientation" -> togglePlayer { it.copy(shouldRememberPlayerScreenOrientation = !it.shouldRememberPlayerScreenOrientation, lastPlayerScreenOrientation = null) }
        "player.resume" -> togglePlayer { it.copy(resume = if (it.resume == Resume.YES) Resume.NO else Resume.YES) }
        "player.autoplay" -> togglePlayer { it.copy(shouldAutoPlay = !it.shouldAutoPlay) }
        "player.pause_at_end_of_queue" -> togglePlayer { it.copy(shouldPauseAtEndOfQueue = !it.shouldPauseAtEndOfQueue) }
        "player.auto_pip" -> togglePlayer { it.copy(shouldAutoEnterPip = !it.shouldAutoEnterPip) }
        "player.background_play" -> togglePlayer { it.copy(shouldAutoPlayInBackground = !it.shouldAutoPlayInBackground) }
        "player.remember_brightness" -> togglePlayer { it.copy(shouldRememberPlayerBrightness = !it.shouldRememberPlayerBrightness) }
        "player.control_labels" -> togglePlayer { it.copy(shouldHidePlayerControlLabels = !it.shouldHidePlayerControlLabels) }
        "player.dim_video_controls" -> togglePlayer { it.copy(shouldDimVideoWhenControlsVisible = !it.shouldDimVideoWhenControlsVisible) }
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
        "decoder.brightness_enabled" -> togglePlayer {
            it.withVideoFilterAdjustment { preferences -> preferences.copy(isVideoBrightnessFilterEnabled = !preferences.isVideoBrightnessFilterEnabled) }
        }
        "decoder.contrast_enabled" -> togglePlayer {
            it.withVideoFilterAdjustment { preferences -> preferences.copy(isVideoContrastFilterEnabled = !preferences.isVideoContrastFilterEnabled) }
        }
        "decoder.saturation_enabled" -> togglePlayer {
            it.withVideoFilterAdjustment { preferences -> preferences.copy(isVideoSaturationFilterEnabled = !preferences.isVideoSaturationFilterEnabled) }
        }
        "decoder.hue_enabled" -> togglePlayer {
            it.withVideoFilterAdjustment { preferences -> preferences.copy(isVideoHueFilterEnabled = !preferences.isVideoHueFilterEnabled) }
        }
        "decoder.gamma_enabled" -> togglePlayer {
            it.withVideoFilterAdjustment { preferences -> preferences.copy(isVideoGammaFilterEnabled = !preferences.isVideoGammaFilterEnabled) }
        }
        "decoder.sharpening_enabled" -> togglePlayer {
            it.withVideoSharpeningFilterEnabled(!it.isVideoSharpeningFilterEnabled)
        }
        "audio.require_focus" -> togglePlayer { it.copy(shouldRequireAudioFocus = !it.shouldRequireAudioFocus) }
        "audio.pause_on_headset_disconnect" -> togglePlayer { it.copy(shouldPauseOnHeadsetDisconnect = !it.shouldPauseOnHeadsetDisconnect) }
        "audio.system_volume_panel" -> togglePlayer { it.copy(shouldShowSystemVolumePanel = !it.shouldShowSystemVolumePanel) }
        "audio.remember_volume" -> togglePlayer { it.copy(shouldRememberPlayerVolume = !it.shouldRememberPlayerVolume) }
        "audio.remember_track" -> togglePlayer { it.copy(shouldRememberAudioTrack = !it.shouldRememberAudioTrack) }
        "audio.normalization" -> togglePlayer { it.copy(isVolumeNormalizationEnabled = !it.isVolumeNormalizationEnabled) }
        "audio.boost" -> togglePlayer { it.copy(isVolumeBoostEnabled = !it.isVolumeBoostEnabled) }
        "subtitle.auto_load" -> togglePlayer { it.copy(isSubtitleAutoLoadEnabled = !it.isSubtitleAutoLoadEnabled) }
        "subtitle.remember_track" -> togglePlayer { it.copy(shouldRememberSubtitleTrack = !it.shouldRememberSubtitleTrack) }
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

internal suspend fun DebugCommandEntryPoint.runSettingAction(
    context: Context,
    target: String?,
) {
    when (target) {
        "general.clear_thumbnail_cache" -> mediaInfoSynchronizer().clearThumbnailsCache()
        "general.clear_video_cache" -> mediaInfoSynchronizer().clearVideoCache()
        "general.reset_settings" -> {
            preferencesRepository().resetPreferences()
            AppLanguageManager.applyToCurrent("")
            AppThemeModeManager.applyToCurrent(
                context = context,
                mode = AppThemeMode.FOLLOW_SYSTEM,
            )
        }
        "subtitle.clear_external_font" -> subtitleFontRepository().clearFont()
        "media.layout_scale_reset" -> preferencesRepository().updateApplicationPreferences {
            it.withMediaLayoutScale(ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE)
        }
        else -> error("Unknown action target: $target")
    }
}

private fun ThemeConfig.toAppThemeMode(): AppThemeMode = when (this) {
    ThemeConfig.SYSTEM -> AppThemeMode.FOLLOW_SYSTEM
    ThemeConfig.OFF -> AppThemeMode.LIGHT
    ThemeConfig.ON -> AppThemeMode.DARK
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

private suspend fun DebugCommandEntryPoint.updateControllerAutoHideTimeout(extras: Bundle) {
    val rawValue = extras.getString(EXTRA_VALUE)?.trim().orEmpty()
    if (rawValue.isBlank()) {
        updateCustomControllerAutoHideTimeout(extras.requiredInt(EXTRA_VALUE).toString())
        return
    }
    val normalizedValue = rawValue.lowercase().replace("-", "_")
    if (normalizedValue.startsWith("custom:") || normalizedValue.startsWith("custom_")) {
        updateCustomControllerAutoHideTimeout(rawValue.substringAfter(':').substringAfter('_'))
        return
    }

    val preset = when (normalizedValue) {
        "disabled",
        "disable",
        "off",
        "none",
        -> ControllerAutoHidePreset.DISABLED
        "15",
        "15s",
        "fifteen_seconds",
        -> ControllerAutoHidePreset.FIFTEEN_SECONDS
        "60",
        "60s",
        "1m",
        "one_minute",
        -> ControllerAutoHidePreset.ONE_MINUTE
        "custom",
        -> ControllerAutoHidePreset.CUSTOM
        else -> null
    }
    if (preset != null) {
        preferencesRepository().updatePlayerPreferences { it.copy(controllerAutoHidePreset = preset) }
        return
    }

    updateCustomControllerAutoHideTimeout(rawValue)
}

private suspend fun DebugCommandEntryPoint.updateCustomControllerAutoHideTimeout(rawValue: String) {
    val seconds = rawValue.toIntOrNull()?.coerceAtLeast(1) ?: error("Invalid custom controller timeout: $rawValue")
    preferencesRepository().updatePlayerPreferences {
        it.copy(
            controllerAutoHidePreset = ControllerAutoHidePreset.CUSTOM,
            controllerAutoHideTimeout = seconds,
        )
    }
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
