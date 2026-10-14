package one.only.player.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val shouldRememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val minDurationForFastSeek: Long = 120000L,
    val shouldRememberAudioTrack: Boolean = true,
    val shouldRememberSubtitleTrack: Boolean = true,
    val playerScreenOrientation: ScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION,
    val shouldRememberPlayerScreenOrientation: Boolean = false,
    val lastPlayerScreenOrientation: LastPlayerScreenOrientation? = null,
    val playerVideoZoom: VideoContentScale = VideoContentScale.BEST_FIT,
    val defaultPlaybackSpeed: Float = 1.0f,
    val shouldApplyVideoFilters: Boolean = false,
    val videoBrightness: Float = DEFAULT_VIDEO_BRIGHTNESS,
    val videoContrast: Float = DEFAULT_VIDEO_CONTRAST,
    val videoSaturation: Float = DEFAULT_VIDEO_SATURATION,
    val videoHue: Float = DEFAULT_VIDEO_HUE,
    val videoGamma: Float = DEFAULT_VIDEO_GAMMA,
    val videoSharpening: Float = DEFAULT_VIDEO_SHARPENING,
    val videoFilterPresets: List<VideoFilterPreset> = emptyList(),
    val shouldAutoPlay: Boolean = true,
    val shouldPauseAtEndOfQueue: Boolean = false,
    val shouldAutoEnterPip: Boolean = true,
    val pictureInPictureMode: PictureInPictureMode = PictureInPictureMode.NATIVE,
    val shouldAutoPlayInBackground: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,

    // 手势控制
    @Deprecated(message = "Use individual isVolumeSwipeGestureEnabled and isBrightnessSwipeGestureEnabled instead")
    val shouldUseSwipeControls: Boolean = true,
    val isVolumeSwipeGestureEnabled: Boolean = true,
    val isBrightnessSwipeGestureEnabled: Boolean = true,
    val shouldUseSeekControls: Boolean = true,
    val isSeekPreviewFrameEnabled: Boolean = false,
    val shouldUseZoomControls: Boolean = true,
    val isPanGestureEnabled: Boolean = true,
    val doubleTapGesture: DoubleTapGesture = DoubleTapGesture.BOTH,
    val shouldUseLongPressControls: Boolean = false,
    val shouldUseLongPressVariableSpeed: Boolean = false,
    val isDebugLongPressOverlayVisible: Boolean = false,
    val longPressControlsSpeed: Float = DEFAULT_LONG_PRESS_CONTROLS_SPEED,
    val seekIncrement: Int = DEFAULT_SEEK_INCREMENT,
    val seekSensitivity: Float = DEFAULT_SEEK_SENSITIVITY,
    val volumeGestureSensitivity: Float = DEFAULT_VOLUME_GESTURE_SENSITIVITY,
    val brightnessGestureSensitivity: Float = DEFAULT_BRIGHTNESS_GESTURE_SENSITIVITY,

    // 播放器界面
    val isAmbienceModeEnabled: Boolean = false,
    val controllerAutoHidePreset: ControllerAutoHidePreset = ControllerAutoHidePreset.CUSTOM,
    val controllerAutoHideTimeout: Int = DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT,
    val shouldDimVideoWhenControlsVisible: Boolean = true,
    val controlsArrangement: PlayerControlsArrangement = PlayerControlsArrangement(),
    val shouldHidePlayerButtonsBackground: Boolean = false,
    val shouldHidePlayerControlLabels: Boolean = false,

    // 音频偏好
    val preferredAudioLanguage: String = "",
    val shouldPauseOnHeadsetDisconnect: Boolean = true,
    val shouldRequireAudioFocus: Boolean = true,
    val shouldShowSystemVolumePanel: Boolean = true,
    val isVolumeBoostEnabled: Boolean = false,
    val shouldRememberPlayerVolume: Boolean = false,
    val playerVolumePercentage: Int = DEFAULT_PLAYER_VOLUME_PERCENTAGE,
    val maxInitialPlayerVolumePercentage: Int = DEFAULT_MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE,
    val isVolumeNormalizationEnabled: Boolean = false,
    val isSpatialAudioEnabled: Boolean = true,
    val shouldApplyAudioEqualizer: Boolean = false,
    val audioEqualizerBandLevels: List<Int> = DEFAULT_AUDIO_EQUALIZER_BAND_LEVELS,
    val audioEqualizerPresets: List<AudioEqualizerPreset> = emptyList(),

    // 字幕偏好
    val isSubtitleAutoLoadEnabled: Boolean = true,
    val shouldUseSystemCaptionStyle: Boolean = false,
    val preferredSubtitleLanguage: String = "",
    val onlineSubtitleSearchPreferences: OnlineSubtitleSearchPreferences = OnlineSubtitleSearchPreferences(),
    val subtitleTextEncoding: String = "",
    val subtitleTextSize: Float = DEFAULT_SUBTITLE_TEXT_SIZE,
    val shouldShowSubtitleBackground: Boolean = false,
    val subtitleFont: Font = Font.DEFAULT,
    val shouldUseBoldSubtitleText: Boolean = true,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
    val subtitleEdgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.DROP_SHADOW,
    val subtitleOutlineThickness: Float = DEFAULT_SUBTITLE_OUTLINE_THICKNESS,
    val subtitleShadowStrength: Float = DEFAULT_SUBTITLE_SHADOW_STRENGTH,
    val subtitleBottomPaddingFraction: Float = DEFAULT_SUBTITLE_BOTTOM_PADDING_FRACTION,
    val shouldApplyEmbeddedStyles: Boolean = true,
    val subtitleScale: Float = DEFAULT_SUBTITLE_SCALE,

    // 解码偏好
    val decoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,
) {

    companion object {
        const val DEFAULT_SEEK_INCREMENT = 10
        const val DEFAULT_SEEK_SENSITIVITY = 0.50f
        const val DEFAULT_VOLUME_GESTURE_SENSITIVITY = 0.50f
        const val DEFAULT_BRIGHTNESS_GESTURE_SENSITIVITY = 0.50f
        const val DEFAULT_LONG_PRESS_CONTROLS_SPEED = 2.0f
        const val DEFAULT_VIDEO_BRIGHTNESS = 0f
        const val MIN_VIDEO_BRIGHTNESS = -1f
        const val MAX_VIDEO_BRIGHTNESS = 1f
        const val DEFAULT_VIDEO_CONTRAST = 0f
        const val MIN_VIDEO_CONTRAST = -1f
        const val MAX_VIDEO_CONTRAST = 1f
        const val DEFAULT_VIDEO_SATURATION = 0f
        const val MIN_VIDEO_SATURATION = -100f
        const val MAX_VIDEO_SATURATION = 100f
        const val DEFAULT_VIDEO_HUE = 0f
        const val MIN_VIDEO_HUE = -180f
        const val MAX_VIDEO_HUE = 180f
        const val DEFAULT_VIDEO_GAMMA = 1f
        const val MIN_VIDEO_GAMMA = 0.1f
        const val MAX_VIDEO_GAMMA = 3f
        const val DEFAULT_VIDEO_SHARPENING = 0f
        const val MAX_VIDEO_SHARPENING = 1f
        const val MIN_LONG_PRESS_CONTROLS_SPEED = 0.2f
        const val MAX_LONG_PRESS_CONTROLS_SPEED = 4.0f
        const val DEFAULT_SUBTITLE_TEXT_SIZE = 16f
        const val MIN_SUBTITLE_TEXT_SIZE = 10f
        const val MAX_SUBTITLE_TEXT_SIZE = 60f
        const val SUBTITLE_TEXT_SIZE_STEP = 0.1f
        const val DEFAULT_SUBTITLE_OUTLINE_THICKNESS = 2f
        const val MIN_SUBTITLE_OUTLINE_THICKNESS = 0f
        const val MAX_SUBTITLE_OUTLINE_THICKNESS = 8f
        const val DEFAULT_SUBTITLE_SHADOW_STRENGTH = 4f
        const val MIN_SUBTITLE_SHADOW_STRENGTH = 0f
        const val MAX_SUBTITLE_SHADOW_STRENGTH = 12f
        const val DEFAULT_SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08f
        const val MIN_SUBTITLE_BOTTOM_PADDING_FRACTION = 0f
        const val MAX_SUBTITLE_BOTTOM_PADDING_FRACTION = 0.4f
        const val SUBTITLE_BOTTOM_PADDING_FRACTION_STEP = 0.001f
        const val DEFAULT_SUBTITLE_SCALE = 1f
        const val MIN_SUBTITLE_SCALE = 0.5f
        const val MAX_SUBTITLE_SCALE = 3f
        const val SUBTITLE_SCALE_STEP = 0.05f
        const val MIN_AUDIO_EQUALIZER_GAIN_DB = -12
        const val MAX_AUDIO_EQUALIZER_GAIN_DB = 12
        const val DEFAULT_AUDIO_EQUALIZER_GAIN_DB = 0
        val DEFAULT_AUDIO_EQUALIZER_BAND_LEVELS: List<Int> = List(AudioEqualizerBand.entries.size) { DEFAULT_AUDIO_EQUALIZER_GAIN_DB }
        const val DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT = 4
        const val DEFAULT_PLAYER_VOLUME_PERCENTAGE = 100
        const val MAX_PLAYER_VOLUME_PERCENTAGE = 200
        const val DEFAULT_MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE = MAX_PLAYER_VOLUME_PERCENTAGE
        const val MIN_INITIAL_PLAYER_VOLUME_PERCENTAGE = 10
        const val MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE = MAX_PLAYER_VOLUME_PERCENTAGE
        const val MAX_SEEK_INCREMENT = 120
    }
}

fun PlayerPreferences.withSubtitleStyleFrom(preferences: PlayerPreferences): PlayerPreferences = copy(
    shouldUseBoldSubtitleText = preferences.shouldUseBoldSubtitleText,
    subtitleTextSize = preferences.subtitleTextSize,
    shouldShowSubtitleBackground = preferences.shouldShowSubtitleBackground,
    subtitleColor = preferences.subtitleColor,
    subtitleEdgeStyle = preferences.subtitleEdgeStyle,
    subtitleOutlineThickness = preferences.subtitleOutlineThickness,
    subtitleShadowStrength = preferences.subtitleShadowStrength,
    subtitleBottomPaddingFraction = preferences.subtitleBottomPaddingFraction,
    subtitleScale = preferences.subtitleScale,
)

fun PlayerPreferences.withVideoFiltersFrom(preferences: PlayerPreferences): PlayerPreferences = copy(
    shouldApplyVideoFilters = preferences.shouldApplyVideoFilters,
    videoBrightness = preferences.videoBrightness,
    videoContrast = preferences.videoContrast,
    videoSaturation = preferences.videoSaturation,
    videoHue = preferences.videoHue,
    videoGamma = preferences.videoGamma,
    videoSharpening = preferences.videoSharpening,
    videoFilterPresets = preferences.videoFilterPresets,
)

fun PlayerPreferences.withVideoFilterAdjustment(
    transform: (PlayerPreferences) -> PlayerPreferences,
): PlayerPreferences {
    if (!shouldApplyVideoFilters) return this
    return transform(this)
}

fun PlayerPreferences.withVideoSharpening(value: Float): PlayerPreferences {
    if (!shouldApplyVideoFilters) return this
    val normalizedValue = value.coerceIn(
        minimumValue = PlayerPreferences.DEFAULT_VIDEO_SHARPENING,
        maximumValue = PlayerPreferences.MAX_VIDEO_SHARPENING,
    )
    return copy(videoSharpening = normalizedValue)
}

fun PlayerPreferences.withVideoFilterPresetApplied(preset: VideoFilterPreset): PlayerPreferences = copy(
    shouldApplyVideoFilters = true,
    videoBrightness = preset.brightness.coerceIn(
        PlayerPreferences.MIN_VIDEO_BRIGHTNESS,
        PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
    ),
    videoContrast = preset.contrast.coerceIn(
        PlayerPreferences.MIN_VIDEO_CONTRAST,
        PlayerPreferences.MAX_VIDEO_CONTRAST,
    ),
    videoSaturation = preset.saturation.coerceIn(
        PlayerPreferences.MIN_VIDEO_SATURATION,
        PlayerPreferences.MAX_VIDEO_SATURATION,
    ),
    videoHue = preset.hue.coerceIn(
        PlayerPreferences.MIN_VIDEO_HUE,
        PlayerPreferences.MAX_VIDEO_HUE,
    ),
    videoGamma = preset.gamma.coerceIn(
        PlayerPreferences.MIN_VIDEO_GAMMA,
        PlayerPreferences.MAX_VIDEO_GAMMA,
    ),
    videoSharpening = preset.sharpening.coerceIn(
        PlayerPreferences.DEFAULT_VIDEO_SHARPENING,
        PlayerPreferences.MAX_VIDEO_SHARPENING,
    ),
)

fun PlayerPreferences.toVideoFilterPreset(name: String, id: Long): VideoFilterPreset = VideoFilterPreset(
    id = id,
    name = name,
    brightness = videoBrightness,
    contrast = videoContrast,
    saturation = videoSaturation,
    hue = videoHue,
    gamma = videoGamma,
    sharpening = videoSharpening,
)

fun PlayerPreferences.withVideoFilterPresetSaved(preset: VideoFilterPreset): PlayerPreferences = copy(
    videoFilterPresets = videoFilterPresets
        .filterNot { it.id == preset.id || it.name == preset.name } + preset,
)

fun PlayerPreferences.withVideoFilterPresetDeleted(preset: VideoFilterPreset): PlayerPreferences = copy(
    videoFilterPresets = videoFilterPresets.filterNot { it.id == preset.id },
)

fun PlayerPreferences.playerControls(slot: PlayerControlSlot): List<PlayerControl> = controlsArrangement.controlsIn(slot)

fun PlayerPreferences.withControlMoved(
    control: PlayerControl,
    slot: PlayerControlSlot,
): PlayerPreferences = copy(controlsArrangement = controlsArrangement.withControlMoved(control, slot))

fun PlayerPreferences.withControlShifted(
    control: PlayerControl,
    offset: Int,
): PlayerPreferences = copy(controlsArrangement = controlsArrangement.withControlShifted(control, offset))

fun PlayerPreferences.controllerAutoHideTimeoutSecondsOrNull(): Int? = when (controllerAutoHidePreset) {
    ControllerAutoHidePreset.DISABLED -> null
    ControllerAutoHidePreset.FIFTEEN_SECONDS -> 15
    ControllerAutoHidePreset.ONE_MINUTE -> 60
    ControllerAutoHidePreset.CUSTOM -> controllerAutoHideTimeout.coerceAtLeast(1)
}

@Serializable
enum class PlayerControl {
    BACK,
    PLAYLIST,
    PLAYBACK_SPEED,
    AUDIO,
    AUDIO_EQUALIZER,
    SUBTITLE,
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    LOCK,
    MUTE,
    MARK,
    CHAPTERS,
    SCALE,
    DECODER,
    AMBIENCE_MODE,
    VIDEO_FILTERS,
    PIP,
    SCREENSHOT,
    BACKGROUND_PLAY,
    LOOP,
    SHUFFLE,
    SLEEP_TIMER,
    ROTATE,
    MIRROR_VIDEO,
    VIDEO_INFO,
}

@Serializable
enum class ControllerAutoHidePreset {
    DISABLED,
    FIFTEEN_SECONDS,
    ONE_MINUTE,
    CUSTOM,
}

@Serializable
enum class PictureInPictureMode {
    NATIVE,
    CUSTOM,
}
