package one.only.player.feature.player.service.effects

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoFrameProcessor
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
    private var activeFilterEffect: VideoFiltersEffect? = null
    private var activeAmbientEffect: AmbientVideoEffect? = null
    private var isCurrentVideoHdr = false
    private var hasRenderedFirstFrameForCurrentItem = false
    private var pendingJob: Job? = null
    private var transition = VideoFilterTransition.default()

    var currentFormat: Format? = null
        private set
    var currentDecoderName: String? = null
        private set
    var activeDecoderPriority: DecoderPriority = initialDecoderPriority
        private set

    val isCurrentHdr: Boolean
        get() = isCurrentVideoHdr

    val isEffectActive: Boolean
        get() = isAvailable() &&
            ((activeFilterEffect != null && currentState.filters.shouldCreateEffect()) || activeAmbientEffect != null)

    fun setDecoderPriority(decoderPriority: DecoderPriority) {
        activeDecoderPriority = decoderPriority
    }

    fun resetForMediaItem(player: ExoPlayer?) {
        currentFormat = null
        currentDecoderName = null
        isCurrentVideoHdr = false
        hasRenderedFirstFrameForCurrentItem = false
        updateAvailability(player ?: return)
    }

    fun resetPipeline() {
        pendingJob?.cancel()
        pendingJob = null
        val wasAmbientEnabled = currentState.isAmbientEnabled
        val ambientTargetAspectRatio = currentState.ambientTargetAspectRatio
        currentState = VideoEffectsState(
            filters = VideoFilterPreferences.default(),
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = wasAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
        )
        activeFilterEffect = null
        activeAmbientEffect = null
        transition = VideoFilterTransition.default()
    }

    fun setDecoderName(decoderName: String) {
        currentDecoderName = decoderName
    }

    fun onVideoInputFormatChanged(
        player: ExoPlayer?,
        format: Format,
    ) {
        currentFormat = format
        isCurrentVideoHdr = format.isHdrVideoFormat()
        player?.let(::updateAvailability)
    }

    fun markFirstFrameRendered(
        player: ExoPlayer,
        format: Format?,
        preferences: PlayerPreferences,
    ) {
        isCurrentVideoHdr = format?.isHdrVideoFormat() == true
        if (hasRenderedFirstFrameForCurrentItem) return
        hasRenderedFirstFrameForCurrentItem = true
        apply(player, preferences, force = true)
    }

    fun apply(preferences: PlayerPreferences) {
        val player = currentPlayer() ?: return
        apply(player, preferences)
    }

    fun apply(
        player: ExoPlayer,
        preferences: PlayerPreferences,
        force: Boolean = false,
    ) {
        schedule(
            player = player,
            videoFilters = preferences.toVideoFilterPreferences(),
            isAmbientEnabled = currentState.isAmbientEnabled,
            ambientTargetAspectRatio = currentState.ambientTargetAspectRatio,
            delayMs = 0L,
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
            logPrefix = "Preview",
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
            videoFilters = currentPreferencesProvider().toVideoFilterPreferences(),
            isAmbientEnabled = isEnabled,
            ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio(targetAspectRatio),
            delayMs = 0L,
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

    fun isAvailable(): Boolean = shouldApplyVideoEffects(activeDecoderPriority, currentFormat)

    private fun schedule(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        delayMs: Long,
        logPrefix: String,
        force: Boolean = false,
    ) {
        pendingJob?.cancel()
        val normalizedAmbientTargetAspectRatio = normalizedAmbientTargetAspectRatio(ambientTargetAspectRatio)
        val targetState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = activeDecoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio,
            isPipelineInitialized = isAvailable() && videoFilters.shouldCreateEffect(),
        )
        if (!force && currentState == targetState) return

        // 播放器创建时同步初始化管线，确保先于 prepare 执行。
        if (delayMs == 0L) {
            applyScheduledEffects(
                player = player,
                videoFilters = videoFilters,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio,
                logPrefix = logPrefix,
            )
            return
        }

        pendingJob = scope.launch {
            delay(delayMs)
            applyScheduledEffects(
                player = player,
                videoFilters = videoFilters,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = normalizedAmbientTargetAspectRatio,
                logPrefix = logPrefix,
            )
        }.also { job ->
            job.invokeOnCompletion {
                if (pendingJob == job) pendingJob = null
            }
        }
    }

    private fun applyScheduledEffects(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        logPrefix: String,
    ) {
        val decoderPriority = activeDecoderPriority
        val transitionStartMs = android.os.SystemClock.elapsedRealtime()
        val nextTransition = if (player.playWhenReady) {
            transition.to(
                targetFilters = videoFilters,
                startMs = transitionStartMs,
                durationMs = VIDEO_FILTER_TRANSITION_DURATION_MS,
            )
        } else {
            VideoFilterTransition(
                startFilters = videoFilters,
                targetFilters = videoFilters,
                startMs = transitionStartMs,
            )
        }
        applyEffects(
            player = player,
            videoFilters = videoFilters,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
            decoderPriority = decoderPriority,
            nextTransition = nextTransition,
        )
        Logger.debug(TAG, "$logPrefix video effects: filters=$videoFilters ambient=$isAmbientEnabled effect=$isEffectActive")
    }

    private fun applyEffects(
        player: ExoPlayer,
        videoFilters: VideoFilterPreferences,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
        decoderPriority: DecoderPriority,
        nextTransition: VideoFilterTransition,
    ) {
        if (!isAvailable()) {
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
            )
            updateAvailability(player)
            return
        }

        val filterEffect = activeFilterEffect
        val shouldUseAmbientEffect = shouldUseAmbientEffect(isAmbientEnabled, decoderPriority)
        // 开启期间复用效果实例；关闭时移除效果，触发原生输出重选。
        val canUpdateActiveFilterEffect = filterEffect != null &&
            shouldUseFilterEffect(videoFilters, decoderPriority) &&
            (activeAmbientEffect != null) == shouldUseAmbientEffect &&
            currentState.isAmbientEnabled == isAmbientEnabled &&
            currentState.ambientTargetAspectRatio == ambientTargetAspectRatio
        if (canUpdateActiveFilterEffect) {
            transition = nextTransition
            filterEffect.updateTransition(nextTransition)
            currentState = VideoEffectsState(
                filters = videoFilters,
                decoderPriority = decoderPriority,
                isAmbientEnabled = isAmbientEnabled,
                ambientTargetAspectRatio = ambientTargetAspectRatio,
                isPipelineInitialized = true,
            )
            refreshPausedFrame(player)
            updateAvailability(player)
            return
        }

        val effects = buildEffects(
            nextTransition = nextTransition,
            decoderPriority = decoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
        )
        val hasOutputPathChanged = currentState.isPipelineInitialized != effects.isNotEmpty()
        transition = if (effects.isEmpty()) VideoFilterTransition.default() else nextTransition
        currentState = VideoEffectsState(
            filters = videoFilters,
            decoderPriority = decoderPriority,
            isAmbientEnabled = isAmbientEnabled,
            ambientTargetAspectRatio = ambientTargetAspectRatio,
            isPipelineInitialized = effects.isNotEmpty(),
        )
        activeFilterEffect = effects.filterIsInstance<VideoFiltersEffect>().firstOrNull()
        activeAmbientEffect = effects.filterIsInstance<AmbientVideoEffect>().firstOrNull()
        player.setVideoEffects(effects)
        // 路径切换由轨道重选重新送帧；同一路径更换效果链才主动刷新。
        if (!hasOutputPathChanged && effects.isNotEmpty()) refreshPausedFrame(player, shouldReloadFrame = true)
        updateAvailability(player)
    }

    private fun refreshPausedFrame(
        player: ExoPlayer,
        shouldReloadFrame: Boolean = false,
    ) {
        if (player.playWhenReady) return
        if (player.playbackState != Player.STATE_READY) return
        // 新效果链需要重新送入当前帧，已有实例只需重绘参数。
        if (!shouldReloadFrame) {
            player.setVideoEffects(VideoFrameProcessor.REDRAW)
            return
        }
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = duration
            ?.let { (position + PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtMost(it) }
            ?.takeIf { it != position }
            ?: (position - PAUSED_FRAME_REFRESH_OFFSET_MS).coerceAtLeast(0L)
        if (targetPosition == position) return
        player.seekTo(targetPosition)
        player.seekTo(position)
    }

    private fun buildEffects(
        nextTransition: VideoFilterTransition,
        decoderPriority: DecoderPriority,
        isAmbientEnabled: Boolean,
        ambientTargetAspectRatio: Float,
    ): List<Effect> {
        val effects = mutableListOf<Effect>()
        if (shouldUseFilterEffect(nextTransition.targetFilters, decoderPriority)) {
            effects += VideoFiltersEffect(
                transition = nextTransition,
                transitionDurationMs = VIDEO_FILTER_TRANSITION_DURATION_MS,
            )
        }
        if (shouldUseAmbientEffect(isAmbientEnabled, decoderPriority)) {
            effects += AmbientVideoEffect(targetAspectRatio = ambientTargetAspectRatio)
        }
        return effects
    }

    private fun shouldUseFilterEffect(
        filters: VideoFilterPreferences,
        decoderPriority: DecoderPriority,
    ): Boolean = shouldApplyVideoEffects(decoderPriority) && filters.shouldCreateEffect()

    private fun shouldUseAmbientEffect(
        isEnabled: Boolean,
        decoderPriority: DecoderPriority,
    ): Boolean {
        // 氛围背景由 UI 独立绘制，不能改写主视频输出
        return false
    }

    private fun normalizedAmbientTargetAspectRatio(targetAspectRatio: Float): Float = targetAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_AMBIENT_TARGET_ASPECT_RATIO

    private fun currentPlayer(): ExoPlayer? = currentPlayerProvider()

    private companion object {
        private const val TAG = "VideoEffectsCoordinator"
        private const val VIDEO_FILTER_PREVIEW_DELAY_MS = 40L
        private const val VIDEO_FILTER_TRANSITION_DURATION_MS = 160L
        private const val PAUSED_FRAME_REFRESH_OFFSET_MS = 50L
        private const val DEFAULT_AMBIENT_TARGET_ASPECT_RATIO = 16f / 9f
    }
}

internal fun PlayerPreferences.toVideoFilterPreferences(): VideoFilterPreferences {
    if (!shouldApplyVideoFilters) return VideoFilterPreferences.default()

    return VideoFilterPreferences(
        shouldApply = true,
        brightness = videoBrightness.coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS),
        contrast = videoContrast.coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST),
        saturation = videoSaturation.coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION),
        hue = videoHue.coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE),
        gamma = videoGamma.coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA),
        sharpening = videoSharpening.coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING),
    )
}

internal fun Format.isHdrVideoFormat(): Boolean {
    val transfer = colorInfo?.colorTransfer
    return isDolbyVisionVideoFormat() || transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
}
