package one.only.player.feature.player.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await
import one.only.player.core.model.PlayerPreferences
import one.only.player.feature.player.model.VideoChapter
import one.only.player.feature.player.model.toVideoChapter

enum class CustomCommands(val customAction: String) {
    ADD_SUBTITLE_TRACK(customAction = "ADD_SUBTITLE_TRACK"),
    REMOVE_SUBTITLE_TRACK(customAction = "REMOVE_SUBTITLE_TRACK"),
    PRECISE_SEEK_TO(customAction = "PRECISE_SEEK_TO"),
    SET_SKIP_SILENCE_ENABLED(customAction = "SET_SKIP_SILENCE_ENABLED"),
    GET_SKIP_SILENCE_ENABLED(customAction = "GET_SKIP_SILENCE_ENABLED"),
    SET_IS_SCRUBBING_MODE_ENABLED(customAction = "SET_IS_SCRUBBING_MODE_ENABLED"),
    SET_IS_SEEK_PREVIEW_ENABLED(customAction = "SET_IS_SEEK_PREVIEW_ENABLED"),
    SET_PERSISTENT_PLAYBACK_SPEED(customAction = "SET_PERSISTENT_PLAYBACK_SPEED"),
    SET_TRANSIENT_PLAYBACK_SPEED(customAction = "SET_TRANSIENT_PLAYBACK_SPEED"),
    GET_SUBTITLE_DELAY(customAction = "GET_SUBTITLE_DELAY"),
    SET_SUBTITLE_DELAY(customAction = "SET_SUBTITLE_DELAY"),
    GET_SUBTITLE_SPEED(customAction = "GET_SUBTITLE_SPEED"),
    SET_SUBTITLE_SPEED(customAction = "SET_SUBTITLE_SPEED"),
    RESET_SUBTITLE_CALIBRATION(customAction = "RESET_SUBTITLE_CALIBRATION"),
    STOP_PLAYER_SESSION(customAction = "STOP_PLAYER_SESSION"),
    SHOW_CUSTOM_PIP(customAction = "SHOW_CUSTOM_PIP"),
    HIDE_CUSTOM_PIP(customAction = "HIDE_CUSTOM_PIP"),
    IS_LOUDNESS_GAIN_SUPPORTED(customAction = "IS_LOUDNESS_GAIN_SUPPORTED"),
    SET_LOUDNESS_GAIN(customAction = "SET_LOUDNESS_GAIN"),
    GET_LOUDNESS_GAIN(customAction = "GET_LOUDNESS_GAIN"),
    PREVIEW_VIDEO_FILTERS(customAction = "PREVIEW_VIDEO_FILTERS"),
    SET_AMBIENCE_MODE_ENABLED(customAction = "SET_AMBIENCE_MODE_ENABLED"),
    GET_VIDEO_FORMAT(customAction = "GET_VIDEO_FORMAT"),
    GET_STALL_METRICS(customAction = "GET_STALL_METRICS"),
    GET_VIDEO_CHAPTERS(customAction = "GET_VIDEO_CHAPTERS"),
    ;

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? = entries.find { it.customAction == sessionCommand.customAction }

        fun asSessionCommands(): List<SessionCommand> = entries.map { it.sessionCommand }

        const val SUBTITLE_TRACK_URI_KEY = "subtitle_track_uri"
        const val SUBTITLE_MEDIA_ID_KEY = "subtitle_media_id"
        const val SEEK_POSITION_MS_KEY = "seek_position_ms"
        const val SEEK_WAS_APPLIED_KEY = "seek_was_applied"
        const val SKIP_SILENCE_ENABLED_KEY = "skip_silence_enabled"
        const val VIDEO_OUTPUT_GENERATION_KEY = "video_output_generation"
        const val IS_SCRUBBING_MODE_ENABLED_KEY = "is_scrubbing_mode_enabled"
        const val IS_SEEK_PREVIEW_ENABLED_KEY = "is_seek_preview_enabled"
        const val PLAYBACK_SPEED_KEY = "playback_speed"
        const val SUBTITLE_DELAY_KEY = "subtitle_delay"
        const val SUBTITLE_SPEED_KEY = "subtitle_speed"
        const val LOUDNESS_GAIN_KEY = "loudness_gain"
        const val IS_LOUDNESS_GAIN_SUPPORTED_KEY = "is_loudness_gain_supported"
        const val SHOULD_APPLY_VIDEO_FILTERS_KEY = "should_apply_video_filters"
        const val VIDEO_BRIGHTNESS_KEY = "video_brightness"
        const val VIDEO_CONTRAST_KEY = "video_contrast"
        const val VIDEO_SATURATION_KEY = "video_saturation"
        const val VIDEO_HUE_KEY = "video_hue"
        const val VIDEO_GAMMA_KEY = "video_gamma"
        const val VIDEO_SHARPENING_KEY = "video_sharpening"
        const val IS_AMBIENCE_MODE_ENABLED_KEY = "is_ambience_mode_enabled"
        const val AMBIENCE_TARGET_ASPECT_RATIO_KEY = "ambience_target_aspect_ratio"
        const val VIDEO_DECODER_PRIORITY_KEY = "video_decoder_priority"
        const val VIDEO_DECODER_NAME_KEY = "video_decoder_name"
        const val VIDEO_WIDTH_KEY = "video_width"
        const val VIDEO_HEIGHT_KEY = "video_height"
        const val VIDEO_COLOR_TRANSFER_KEY = "video_color_transfer"
        const val VIDEO_COLOR_STANDARD_KEY = "video_color_standard"
        const val VIDEO_COLOR_RANGE_KEY = "video_color_range"
        const val IS_VIDEO_HDR_KEY = "is_video_hdr"
        const val IS_VIDEO_EFFECTS_AVAILABLE_KEY = "is_video_effects_available"
        const val IS_VIDEO_EFFECTS_ACTIVE_KEY = "is_video_effects_active"
        const val STALL_COUNT_KEY = "stall_count"
        const val CURRENT_STALL_DURATION_MS_KEY = "current_stall_duration_ms"
        const val TOTAL_STALL_DURATION_MS_KEY = "total_stall_duration_ms"
        const val VIDEO_CHAPTERS_KEY = "video_chapters"
    }
}

data class VideoFormatInfo(
    val decoderName: String?,
    val width: Int,
    val height: Int,
    val isHdr: Boolean,
)

data class PlaybackStallMetrics(
    val count: Int,
    val currentDurationMs: Long,
    val totalDurationMs: Long,
)

fun MediaController.addSubtitleTrack(
    uri: Uri,
    mediaId: String,
): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, uri.toString())
        putString(CustomCommands.SUBTITLE_MEDIA_ID_KEY, mediaId)
    }
    return sendCustomCommand(CustomCommands.ADD_SUBTITLE_TRACK.sessionCommand, args)
}

fun MediaController.preciseSeekTo(positionMs: Long): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putLong(CustomCommands.SEEK_POSITION_MS_KEY, positionMs)
    }
    return sendCustomCommand(CustomCommands.PRECISE_SEEK_TO.sessionCommand, args)
}

fun MediaController.removeSubtitleTrack(
    subtitleId: String,
    mediaId: String,
): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, subtitleId)
        putString(CustomCommands.SUBTITLE_MEDIA_ID_KEY, mediaId)
    }
    return sendCustomCommand(CustomCommands.REMOVE_SUBTITLE_TRACK.sessionCommand, args)
}

suspend fun MediaController.setSkipSilenceEnabled(isEnabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, isEnabled)
    }
    sendCustomCommand(CustomCommands.SET_SKIP_SILENCE_ENABLED.sessionCommand, args).await()
}

fun MediaController.setMediaControllerIsScrubbingModeEnabled(isEnabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY, isEnabled)
    }
    sendCustomCommand(CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED.sessionCommand, args)
}

fun MediaController.setMediaControllerIsSeekPreviewEnabled(isEnabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.IS_SEEK_PREVIEW_ENABLED_KEY, isEnabled)
    }
    sendCustomCommand(CustomCommands.SET_IS_SEEK_PREVIEW_ENABLED.sessionCommand, args)
}

fun MediaController.setPersistentPlaybackSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.PLAYBACK_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_PERSISTENT_PLAYBACK_SPEED.sessionCommand, args)
}

fun MediaController.setTransientPlaybackSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.PLAYBACK_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_TRANSIENT_PLAYBACK_SPEED.sessionCommand, args)
}

suspend fun MediaController.isSkipSilenceEnabled(): Boolean {
    val result = sendCustomCommand(CustomCommands.GET_SKIP_SILENCE_ENABLED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, false)
}

suspend fun MediaController.setSubtitleDelayMilliseconds(delayMillis: Long) {
    val args = Bundle().apply {
        putLong(CustomCommands.SUBTITLE_DELAY_KEY, delayMillis)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_DELAY.sessionCommand, args).await()
}

suspend fun MediaController.getSubtitleDelayMilliseconds(): Long {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_DELAY.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getLong(CustomCommands.SUBTITLE_DELAY_KEY, 0L)
}

suspend fun MediaController.setSubtitleSpeed(speed: Float) {
    val args = Bundle().apply {
        putFloat(CustomCommands.SUBTITLE_SPEED_KEY, speed)
    }
    sendCustomCommand(CustomCommands.SET_SUBTITLE_SPEED.sessionCommand, args).await()
}

suspend fun MediaController.getSubtitleSpeed(): Float {
    val result = sendCustomCommand(CustomCommands.GET_SUBTITLE_SPEED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getFloat(CustomCommands.SUBTITLE_SPEED_KEY, 1f)
}

suspend fun MediaController.resetSubtitleCalibration() {
    sendCustomCommand(CustomCommands.RESET_SUBTITLE_CALIBRATION.sessionCommand, Bundle.EMPTY).await()
}

fun MediaController.stopPlayerSession() {
    sendCustomCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand, Bundle.EMPTY)
}

fun MediaController.showCustomPictureInPicture() = sendCustomCommand(
    CustomCommands.SHOW_CUSTOM_PIP.sessionCommand,
    Bundle.EMPTY,
)

fun MediaController.hideCustomPictureInPicture() {
    sendCustomCommand(CustomCommands.HIDE_CUSTOM_PIP.sessionCommand, Bundle.EMPTY)
}

fun MediaController.setLoudnessGain(gain: Int) {
    val args = Bundle().apply {
        putInt(CustomCommands.LOUDNESS_GAIN_KEY, gain)
    }
    sendCustomCommand(CustomCommands.SET_LOUDNESS_GAIN.sessionCommand, args)
}

fun MediaController.previewVideoFilters(preferences: PlayerPreferences) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.SHOULD_APPLY_VIDEO_FILTERS_KEY, preferences.shouldApplyVideoFilters)
        putFloat(CustomCommands.VIDEO_BRIGHTNESS_KEY, preferences.videoBrightness)
        putFloat(CustomCommands.VIDEO_CONTRAST_KEY, preferences.videoContrast)
        putFloat(CustomCommands.VIDEO_SATURATION_KEY, preferences.videoSaturation)
        putFloat(CustomCommands.VIDEO_HUE_KEY, preferences.videoHue)
        putFloat(CustomCommands.VIDEO_GAMMA_KEY, preferences.videoGamma)
        putFloat(CustomCommands.VIDEO_SHARPENING_KEY, preferences.videoSharpening)
    }
    sendCustomCommand(CustomCommands.PREVIEW_VIDEO_FILTERS.sessionCommand, args)
}

fun MediaController.setPlayerAmbienceModeEnabled(
    isEnabled: Boolean,
    targetAspectRatio: Float,
) {
    val args = Bundle().apply {
        putBoolean(CustomCommands.IS_AMBIENCE_MODE_ENABLED_KEY, isEnabled)
        putFloat(CustomCommands.AMBIENCE_TARGET_ASPECT_RATIO_KEY, targetAspectRatio)
    }
    sendCustomCommand(CustomCommands.SET_AMBIENCE_MODE_ENABLED.sessionCommand, args)
}

suspend fun MediaController.getVideoFormatDebugInfo(): SessionResult = sendCustomCommand(
    CustomCommands.GET_VIDEO_FORMAT.sessionCommand,
    Bundle.EMPTY,
).await()

suspend fun MediaController.getPlaybackStallMetrics(): PlaybackStallMetrics {
    val result = sendCustomCommand(CustomCommands.GET_STALL_METRICS.sessionCommand, Bundle.EMPTY).await()
    if (result.resultCode != SessionResult.RESULT_SUCCESS) {
        error("Stall metrics command failed: ${result.resultCode}")
    }
    return PlaybackStallMetrics(
        count = result.extras.getInt(CustomCommands.STALL_COUNT_KEY),
        currentDurationMs = result.extras.getLong(CustomCommands.CURRENT_STALL_DURATION_MS_KEY),
        totalDurationMs = result.extras.getLong(CustomCommands.TOTAL_STALL_DURATION_MS_KEY),
    )
}

suspend fun MediaController.getVideoFormatInfo(): VideoFormatInfo? {
    val result = getVideoFormatDebugInfo()
    if (result.resultCode != SessionResult.RESULT_SUCCESS) return null
    return VideoFormatInfo(
        decoderName = result.extras.getString(CustomCommands.VIDEO_DECODER_NAME_KEY),
        width = result.extras.getInt(CustomCommands.VIDEO_WIDTH_KEY),
        height = result.extras.getInt(CustomCommands.VIDEO_HEIGHT_KEY),
        isHdr = result.extras.getBoolean(CustomCommands.IS_VIDEO_HDR_KEY),
    )
}

@Suppress("DEPRECATION")
suspend fun MediaController.getVideoChapters(): List<VideoChapter> {
    val result = sendCustomCommand(CustomCommands.GET_VIDEO_CHAPTERS.sessionCommand, Bundle.EMPTY).await()
    if (result.resultCode != SessionResult.RESULT_SUCCESS) return emptyList()
    return result.extras
        .getParcelableArrayList<Bundle>(CustomCommands.VIDEO_CHAPTERS_KEY)
        .orEmpty()
        .map(Bundle::toVideoChapter)
}

suspend fun MediaController.getLoudnessGain(): Int {
    val result = sendCustomCommand(CustomCommands.GET_LOUDNESS_GAIN.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
}

suspend fun MediaController.isLoudnessGainSupported(): Boolean {
    val result = sendCustomCommand(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED.sessionCommand, Bundle.EMPTY)
    return result.await().extras.getBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, false)
}
