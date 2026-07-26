package one.only.player.feature.player.service.effects

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.PlayerPreferences
import one.only.player.feature.player.extensions.copy
import one.only.player.feature.player.extensions.isVideoEffectsAvailable

/**
 * 视频滤镜协调：
 * - 使用 Media3 内置 Brightness / Contrast / HslAdjustment（自定义 GlEffect 在 SurfaceView 路径上常无可见效果）
 * - 预览写内存 [currentState.filters]；首帧回调优先用内存态，避免 DataStore 旧值冲掉预览
 * - 播放中不 seek 刷新，避免开关黑屏
 */
internal class VideoEffectsCoordinator(
    private val scope: CoroutineScope,
    private val currentPreferencesProvider: () -> PlayerPreferences,
    private val currentPlayerProvider: () -> ExoPlayer?,
    initialDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC,
) {

    private var currentState = VideoEffectsState(
        filters = VideoFilterPreferences.default(),
        decoderPriority = initialDecoderPriority,
    )
    private var isPipelineAttached: Boolean = false
    private var isCurrentVideoHdr = false
    private var hasRenderedFirstFrameForCurrentItem = false
    private var pendingJob: Job? = null
    private var generation: Long = 0L

    var currentFormat: Format? = null
        private set
    var currentDecoderName: String? = null
        private set
    var activeDecoderPriority: DecoderPriority = initialDecoderPriority
        private set

    val isCurrentHdr: Boolean
        get() = isCurrentVideoHdr

    val isEffectActive: Boolean
        get() = isPipelineAttached && currentState.filters.shouldCreateEffect()

    fun setDecoderPriority(decoderPriority: DecoderPriority) {
        activeDecoderPriority = decoderPriority
    }

    fun resetForMediaItem(player: ExoPlayer?) {
        currentFormat = null
        currentDecoderName = null
        isCurrentVideoHdr = false
        hasRenderedFirstFrameForCurrentItem = false
        // 保留滤镜参数；切条后首帧再挂管线
        isPipelineAttached = false
        updateAvailability(player ?: return)
    }

    fun resetPipeline() {
        val wasAmbientEnabled = currentState.isAmbientEnabled
        val ambientTargetAspectRatio = currentState.ambientTargetAspectRatio
        currentState = VideoEffectsState(
            filters = VideoFilterPreferences.default(),
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = wasAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
        )
        isPipelineAttached = false
    }

    fun setDecoderName(decoderName: String) {
        currentDecoderName = decoderName
    }

    fun onVideoInputFormatChanged(
        player: ExoPlayer?,
        format: Format,
    ) {
        val wasVideoHdr = isCurrentVideoHdr
        currentFormat = format
        isCurrentVideoHdr = format.isHdrVideoFormat()
        if (wasVideoHdr != isCurrentVideoHdr || isPipelineAttached) {
            player?.let { apply(it, resolveFiltersForApply(currentPreferencesProvider()), force = true) }
        }
    }

    fun markFirstFrameRendered(
        player: ExoPlayer,
        format: Format?,
        preferences: PlayerPreferences,
    ) {
        isCurrentVideoHdr = format?.isHdrVideoFormat() == true
        hasRenderedFirstFrameForCurrentItem = true
        // 关键：用内存滤镜态（含面板预览），不要只用 DataStore 旧值冲掉预览
        apply(player, resolveFiltersForApply(preferences), force = true)
    }

    fun apply(preferences: PlayerPreferences) {
        val player = currentPlayer() ?: return
        apply(player, preferences.toVideoFilterPreferences())
    }

    fun apply(
        player: ExoPlayer,
        preferences: PlayerPreferences,
        force: Boolean = false,
    ) {
        apply(player, preferences.toVideoFilterPreferences(), force = force)
    }

    private fun apply(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        force: Boolean = false,
    ) {
        schedule(
            player = player,
            videoFilters = videoFilters,
            isAmbientEnabled = currentState.isAmbientEnabled,
            ambientTargetAspectRatio = currentState.ambientTargetAspectRatio,
            delayMs = 0L,
            shouldSkipStalePreferences = false,
            logPrefix = "Apply",
            force = force,
        )
    }

    fun preview(
        player: ExoPlayer?,
        preferences: PlayerPreferences,
    ) {
        if (player == null) return
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            isAmbientEnabled = currentState.isAmbientEnabled,
            ambientTargetAspectRatio = currentState.ambientTargetAspectRatio,
            delayMs = VIDEO_FILTER_PREVIEW_DELAY_MS,
            shouldSkipStalePreferences = false,
            logPrefix = "Preview",
            force = true,
        )
    }

    fun setAmbientMode(
        player: ExoPlayer?,
        isEnabled: Boolean,
        targetAspectRatio: Float,
    ) {
        val currentPlayer = player ?: currentPlayer() ?: return
        schedule(
            player = currentPlayer,
            videoFilters = resolveFiltersForApply(currentPreferencesProvider()),
            isAmbientEnabled = isEnabled,
            ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio(targetAspectRatio),
            delayMs = 0L,
            shouldSkipStalePreferences = false,
            logPrefix = "Apply",
            force = true,
        )
    }

    fun updateAvailability(player: ExoPlayer) {
        val currentMediaItem = player.currentMediaItem ?: return
        val isVideoEffectsAvailable = isAvailable()
        if (currentMediaItem.mediaMetadata.isVideoEffectsAvailable == isVideoEffectsAvailable) return

        player.replaceMediaItem(
            player.currentMediaItemIndex,
            currentMediaItem.copy(isVideoEffectsAvailable = isVideoEffectsAvailable),
        )
        Logger.debug(TAG, "Video effects availability: available=$isVideoEffectsAvailable decoder=$activeDecoderPriority")
    }

    fun isAvailable(): Boolean = shouldApplyVideoEffects(activeDecoderPriority) && !isCurrentVideoHdr

    /**
     * 内存里已有有效/已挂管线的滤镜时优先用内存（预览），否则用传入偏好（通常 DataStore）。
     */
    private fun resolveFiltersForApply(preferences: PlayerPreferences): VideoFilterPreferences {
        val memory = currentState.filters
        if (memory.shouldCreateEffect() || isPipelineAttached) {
            return memory
        }
        return preferences.toVideoFilterPreferences()
    }

    private fun schedule(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        delayMs: Long,
        shouldSkipStalePreferences: Boolean,
        logPrefix: String,
        force: Boolean = false,
    ) {
        pendingJob?.cancel()
        val normalizedAmbient = normalizedAmbientTargetAspectRatio(ambientTargetAspectRatio)
        val targetState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = normalizedAmbient,
            isPipelineInitialized = true,
        )
        if (!force && currentState == targetState && isPipelineAttached) return

        val jobGeneration = ++generation
        pendingJob = scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (jobGeneration != generation) return@launch

            applyEffects(
                player = player,
                videoFilters = videoFilters,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = normalizedAmbient,
                decoderPriority = activeDecoderPriority,
            )
            Logger.debug(
                TAG,
                "$logPrefix video effects: filters=$videoFilters ambient=$isAmbientEnabled " +
                    "effect=$isEffectActive pipeline=$isPipelineAttached",
            )
        }.also { job ->
            job.invokeOnCompletion {
                if (pendingJob == job) pendingJob = null
            }
        }
    }

    private fun applyEffects(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        decoderPriority: DecoderPriority,
    ) {
        if (!shouldApplyVideoEffects(decoderPriority) || isCurrentVideoHdr) {
            if (isPipelineAttached) {
                runCatching { player.setVideoEffects(emptyList()) }
                isPipelineAttached = false
            }
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
                isPipelineInitialized = false,
            )
            updateAvailability(player)
            Logger.debug(TAG, "Filters unsupported hdr=$isCurrentVideoHdr decoder=$decoderPriority")
            return
        }

        if (!hasRenderedFirstFrameForCurrentItem) {
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
                isPipelineInitialized = false,
            )
            Logger.debug(TAG, "Defer setVideoEffects until first frame")
            return
        }

        val effects = buildMedia3Effects(videoFilters)
        currentState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = decoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
            isPipelineInitialized = true,
        )
        runCatching {
            player.setVideoEffects(effects)
            isPipelineAttached = effects.isNotEmpty()
        }.onFailure { error ->
            Logger.error(TAG, "setVideoEffects failed", error)
            isPipelineAttached = false
        }
        // 仅暂停时微 seek 刷一帧；播放中不 seek
        refreshPausedFrame(player)
        updateAvailability(player)
        Logger.debug(
            TAG,
            "setVideoEffects count=${effects.size} shouldCreate=${videoFilters.shouldCreateEffect()} " +
                "pipeline=$isPipelineAttached",
        )
    }

    /** Media3 内置 effect；关滤镜时返回 empty（不再走自定义 GL）。 */
    private fun buildMedia3Effects(filters: VideoFilterPreferences): List<Effect> {
        if (!filters.shouldCreateEffect()) return emptyList()
        val effects = mutableListOf<Effect>()

        if (filters.isBrightnessEnabled &&
            filters.brightness != PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS
        ) {
            effects += Brightness(
                filters.brightness.coerceIn(
                    PlayerPreferences.MIN_VIDEO_BRIGHTNESS,
                    PlayerPreferences.MAX_VIDEO_BRIGHTNESS,
                ),
            )
        }
        if (filters.isContrastEnabled &&
            filters.contrast != PlayerPreferences.DEFAULT_VIDEO_CONTRAST
        ) {
            effects += Contrast(
                filters.contrast.coerceIn(
                    PlayerPreferences.MIN_VIDEO_CONTRAST,
                    PlayerPreferences.MAX_VIDEO_CONTRAST,
                ),
            )
        }

        val hue = if (filters.isHueEnabled) filters.hue else 0f
        val saturation = if (filters.isSaturationEnabled) filters.saturation else 0f
        val needsHsl = (filters.isHueEnabled && hue != PlayerPreferences.DEFAULT_VIDEO_HUE) ||
            (filters.isSaturationEnabled && saturation != PlayerPreferences.DEFAULT_VIDEO_SATURATION)
        if (needsHsl) {
            val builder = HslAdjustment.Builder()
            if (filters.isHueEnabled && hue != PlayerPreferences.DEFAULT_VIDEO_HUE) {
                builder.adjustHue(
                    hue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE),
                )
            }
            if (filters.isSaturationEnabled && saturation != PlayerPreferences.DEFAULT_VIDEO_SATURATION) {
                // 面板饱和度约 -100..100，与 HslAdjustment 一致
                builder.adjustSaturation(
                    saturation.coerceIn(
                        PlayerPreferences.MIN_VIDEO_SATURATION,
                        PlayerPreferences.MAX_VIDEO_SATURATION,
                    ),
                )
            }
            effects += builder.build()
        }

        // gamma / sharpening：Media3 无直接等价内置项；亮度/对比/色相先保证可见
        // 若仅调 gamma/锐化，退回极小亮度扰动无意义，故暂不模拟

        return effects
    }

    private fun refreshPausedFrame(player: ExoPlayer) {
        if (player.playWhenReady) return
        if (player.playbackState != Player.STATE_READY) return
        val position = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = duration
            ?.let { (position + PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtMost(it) }
            ?.takeIf { it != position }
            ?: (position - PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtLeast(0L)
        if (targetPosition == position) return
        player.seekTo(targetPosition)
        player.seekTo(position)
    }

    private fun shouldUseAmbientEffect(
        isEnabled: Boolean,
        decoderPriority: DecoderPriority,
    ): Boolean {
        // 氛围背景由 UI 独立绘制
        return false
    }

    private fun normalizedAmbientTargetAspectRatio(targetAspectRatio: Float): Float = targetAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_AMBIENT_TARGET_ASPECT_RATIO

    private fun currentPlayer(): ExoPlayer? = currentPlayerProvider()

    private companion object {
        private const val TAG = "VideoEffectsCoordinator"
        private const val VIDEO_FILTER_PREVIEW_DELAY_MS = 16L
        private const val PAUSED_FRAME_REFRESH_OFFSET_MS = 50L
        private const val DEFAULT_AMBIENT_TARGET_ASPECT_RATIO = 16f / 9f
    }
}

internal fun PlayerPreferences.toVideoFilterPreferences(): VideoFilterPreferences {
    if (!shouldApplyVideoFilters) return VideoFilterPreferences.default()

    val filters = VideoFilterPreferences(
        shouldApply = true,
        isBrightnessEnabled = isVideoBrightnessFilterEnabled,
        brightness = if (isVideoBrightnessFilterEnabled) {
            videoBrightness.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_BRIGHTNESS
        },
        isContrastEnabled = isVideoContrastFilterEnabled,
        contrast = if (isVideoContrastFilterEnabled) {
            videoContrast.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_CONTRAST
        },
        isSaturationEnabled = isVideoSaturationFilterEnabled,
        saturation = if (isVideoSaturationFilterEnabled) {
            videoSaturation.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_SATURATION
        },
        isHueEnabled = isVideoHueFilterEnabled,
        hue = if (isVideoHueFilterEnabled) {
            videoHue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_HUE
        },
        isGammaEnabled = isVideoGammaFilterEnabled,
        gamma = if (isVideoGammaFilterEnabled) {
            videoGamma.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_GAMMA
        },
        isSharpeningEnabled = isVideoSharpeningFilterEnabled,
        sharpening = if (isVideoSharpeningFilterEnabled) {
            videoSharpening.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING)
        } else {
            PlayerPreferences.DEFAULT_VIDEO_SHARPENING
        },
    )
    return if (filters.shouldCreateEffect()) filters else VideoFilterPreferences.default()
}

internal fun Format.isHdrVideoFormat(): Boolean {
    val transfer = colorInfo?.colorTransfer
    return transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
}

