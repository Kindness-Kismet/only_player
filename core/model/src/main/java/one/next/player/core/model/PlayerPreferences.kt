package one.next.player.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPreferences(
    val resume: Resume = Resume.YES,
    val shouldRememberPlayerBrightness: Boolean = false,
    val playerBrightness: Float = 0.5f,
    val minDurationForFastSeek: Long = 120000L,
    val shouldRememberSelections: Boolean = true,
    val playerScreenOrientation: ScreenOrientation = ScreenOrientation.VIDEO_ORIENTATION,
    val playerVideoZoom: VideoContentScale = VideoContentScale.BEST_FIT,
    val defaultPlaybackSpeed: Float = 1.0f,
    val videoBrightness: Float = DEFAULT_VIDEO_BRIGHTNESS,
    val videoContrast: Float = DEFAULT_VIDEO_CONTRAST,
    val videoSaturation: Float = DEFAULT_VIDEO_SATURATION,
    val videoHue: Float = DEFAULT_VIDEO_HUE,
    val videoGamma: Float = DEFAULT_VIDEO_GAMMA,
    val videoSharpening: Float = DEFAULT_VIDEO_SHARPENING,
    val shouldAutoPlay: Boolean = true,
    val shouldAutoEnterPip: Boolean = true,
    val shouldAutoPlayInBackground: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,

    // 手势控制
    @Deprecated(message = "Use individual isVolumeSwipeGestureEnabled and isBrightnessSwipeGestureEnabled instead")
    val shouldUseSwipeControls: Boolean = true,
    val isVolumeSwipeGestureEnabled: Boolean = true,
    val isBrightnessSwipeGestureEnabled: Boolean = true,
    val shouldUseSeekControls: Boolean = true,
    val shouldUseZoomControls: Boolean = true,
    val isPanGestureEnabled: Boolean = false,
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
    val controllerAutoHideTimeout: Int = DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT,
    val controlButtonsPosition: ControlButtonsPosition = ControlButtonsPosition.LEFT,
    val playerControlsLayout: PlayerControlsLayout = PlayerControlsLayout(),
    val hiddenPlayerControls: Set<PlayerControl> = emptySet(),
    val shouldHidePlayerButtonsBackground: Boolean = false,

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

    // 字幕偏好
    val isSubtitleAutoLoadEnabled: Boolean = true,
    val shouldUseSystemCaptionStyle: Boolean = false,
    val preferredSubtitleLanguage: String = "",
    val subtitleTextEncoding: String = "",
    val subtitleTextSize: Int = DEFAULT_SUBTITLE_TEXT_SIZE,
    val shouldShowSubtitleBackground: Boolean = false,
    val subtitleFont: Font = Font.DEFAULT,
    val shouldUseBoldSubtitleText: Boolean = true,
    val shouldApplyEmbeddedStyles: Boolean = true,

    // 解码偏好
    val decoderPriority: DecoderPriority = DecoderPriority.PREFER_DEVICE,
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
        const val DEFAULT_SUBTITLE_TEXT_SIZE = 20
        const val DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT = 4
        const val DEFAULT_PLAYER_VOLUME_PERCENTAGE = 100
        const val DEFAULT_MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE = 80
        const val MIN_INITIAL_PLAYER_VOLUME_PERCENTAGE = 10
        const val MAX_INITIAL_PLAYER_VOLUME_PERCENTAGE = 100
        const val MAX_PLAYER_VOLUME_PERCENTAGE = 200
        const val MAX_SEEK_INCREMENT = 120
    }
}

@Serializable
@Suppress("MagicNumber")
enum class PlayerControl {
    BACK,
    PLAYLIST,
    PLAYBACK_SPEED,
    AUDIO,
    SUBTITLE,
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    LOCK,
    SCALE,
    VIDEO_FILTERS,
    PIP,
    SCREENSHOT,
    BACKGROUND_PLAY,
    LOOP,
    SHUFFLE,
    SLEEP_TIMER,
    ROTATE,
}
