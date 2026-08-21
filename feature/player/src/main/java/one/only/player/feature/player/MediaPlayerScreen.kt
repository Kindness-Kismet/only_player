package one.only.player.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import one.only.player.core.common.Logger
import one.only.player.core.data.repository.ExternalSubtitleFontSource
import one.only.player.core.model.PictureInPictureMode
import one.only.player.core.model.PlaybackMark
import one.only.player.core.model.PlayerControlSlot
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.Video
import one.only.player.core.model.controllerAutoHideTimeoutSecondsOrNull
import one.only.player.core.model.playerControls
import one.only.player.core.ui.R as coreUiR
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.VideoFiltersPanel
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.playerCornerControlsCapacity
import one.only.player.feature.player.buttons.PlayerButton
import one.only.player.feature.player.extensions.nameRes
import one.only.player.feature.player.extensions.noRippleClickable
import one.only.player.feature.player.extensions.seekByRequestedOffset
import one.only.player.feature.player.extensions.seekToRequestedPosition
import one.only.player.feature.player.input.PlayerKeyboardController
import one.only.player.feature.player.model.VideoChapter
import one.only.player.feature.player.service.previewVideoFilters
import one.only.player.feature.player.service.showCustomPictureInPicture
import one.only.player.feature.player.state.ControlsVisibilityState
import one.only.player.feature.player.state.VerticalGesture
import one.only.player.feature.player.state.rememberBrightnessState
import one.only.player.feature.player.state.rememberChaptersState
import one.only.player.feature.player.state.rememberControlsVisibilityState
import one.only.player.feature.player.state.rememberErrorState
import one.only.player.feature.player.state.rememberMediaPresentationState
import one.only.player.feature.player.state.rememberMetadataState
import one.only.player.feature.player.state.rememberPictureInPictureState
import one.only.player.feature.player.state.rememberPlaylistState
import one.only.player.feature.player.state.rememberRotationState
import one.only.player.feature.player.state.rememberSeekGestureState
import one.only.player.feature.player.state.rememberSleepTimerState
import one.only.player.feature.player.state.rememberTapGestureState
import one.only.player.feature.player.state.rememberVideoZoomAndContentScaleState
import one.only.player.feature.player.state.rememberVolumeAndBrightnessGestureState
import one.only.player.feature.player.state.rememberVolumeState
import one.only.player.feature.player.state.seekAmountFormatted
import one.only.player.feature.player.state.seekToPositionFormated
import one.only.player.feature.player.ui.AudioTrackSelectorContent
import one.only.player.feature.player.ui.ChapterSwipeDirection
import one.only.player.feature.player.ui.ChapterSwitchIndicator
import one.only.player.feature.player.ui.ChaptersContent
import one.only.player.feature.player.ui.DecoderPrioritySelectorContent
import one.only.player.feature.player.ui.DoubleTapIndicator
import one.only.player.feature.player.ui.LoopModeSelectorContent
import one.only.player.feature.player.ui.MenuOverlayView
import one.only.player.feature.player.ui.MenuRootContent
import one.only.player.feature.player.ui.MenuRoute
import one.only.player.feature.player.ui.PlaybackMarksContent
import one.only.player.feature.player.ui.PlaybackSpeedSelectorContent
import one.only.player.feature.player.ui.PlaylistContent
import one.only.player.feature.player.ui.ShuffleModeSelectorContent
import one.only.player.feature.player.ui.SleepTimerSelectorContent
import one.only.player.feature.player.ui.SubtitleConfiguration
import one.only.player.feature.player.ui.SubtitleSelectorContent
import one.only.player.feature.player.ui.ToggleOptionSelectorContent
import one.only.player.feature.player.ui.VerticalProgressView
import one.only.player.feature.player.ui.VideoContentScaleSelectorContent
import one.only.player.feature.player.ui.VideoInfoContent
import one.only.player.feature.player.ui.controls.ControlsBottomModernView
import one.only.player.feature.player.ui.controls.ControlsTopModernView
import one.only.player.feature.player.ui.panel.rememberFloatingPlayerPanelState
import one.only.player.feature.player.ui.playerControlBindings
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton

private const val TAG = "MediaPlayerScreen"
private const val AMBIENCE_FRAME_CAPTURE_MAX_SIZE = 240
private const val AMBIENCE_FRAME_CAPTURE_PLAYING_INTERVAL_MS = 300L
private const val AMBIENCE_FRAME_CAPTURE_PAUSED_INTERVAL_MS = 1_000L
private const val AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS = 250L
private const val AMBIENCE_FRAME_CAPTURE_MEDIA_TRANSITION_DELAY_MS = 350L
private const val AMBIENCE_FRAME_CAPTURE_SEEK_RESUME_DELAY_MS = 350L
private const val AMBIENCE_FRAME_NEAR_BLACK_CONFIRM_COUNT = 10
private const val AMBIENCE_FRAME_NEAR_BLACK_AVERAGE_LUMA = 6f
private const val AMBIENCE_FRAME_NEAR_BLACK_MAX_LUMA = 18f
private const val AMBIENCE_VISIBLE_ALPHA_THRESHOLD = 16
private const val CHAPTER_SWITCH_FEEDBACK_DURATION_MS = 1_400L

val LocalControlsVisibilityState = compositionLocalOf<ControlsVisibilityState?> { null }

internal data class LongPressOverlayUiState(
    val speedText: String,
)

internal fun resolveLongPressOverlayUiState(
    isLongPressGestureInAction: Boolean,
    isDebugLongPressOverlayVisible: Boolean,
    longPressSpeed: Float,
    shouldShowOverlay: Boolean,
): LongPressOverlayUiState? {
    if (!shouldShowOverlay && !isDebugLongPressOverlayVisible) return null
    if (!isLongPressGestureInAction && !isDebugLongPressOverlayVisible) return null

    return LongPressOverlayUiState(
        speedText = String.format(Locale.US, "%.1fx", longPressSpeed),
    )
}

@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayerScreen(
    player: Player?,
    viewModel: PlayerViewModel,
    playerPreferences: PlayerPreferences,
    isAmbienceModeEnabled: Boolean,
    externalSubtitleFontSource: ExternalSubtitleFontSource?,
    modifier: Modifier = Modifier,
    onSelectSubtitleClick: () -> Unit,
    onAddOnlineSubtitleClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    isTakingScreenshot: Boolean = false,
    onScreenshotClick: () -> Unit,
    onKeyboardEventHandlerChanged: ((KeyEvent) -> Boolean) -> Unit = {},
) {
    val volumeState = rememberVolumeState(
        player = player,
        shouldShowVolumePanelIfHeadsetIsOn = playerPreferences.shouldShowSystemVolumePanel,
        isVolumeBoostEnabled = playerPreferences.isVolumeBoostEnabled,
    )
    player ?: return
    val playbackMarks by viewModel.playbackMarks.collectAsStateWithLifecycle()
    val metadataState = rememberMetadataState(player)
    val chaptersState = rememberChaptersState(player)
    val mediaPresentationState = rememberMediaPresentationState(player)
    val controlsVisibilityState = rememberControlsVisibilityState(
        player = player,
        hideAfter = playerPreferences.controllerAutoHideTimeoutSecondsOrNull()?.seconds,
    )
    val tapGestureState = rememberTapGestureState(
        player = player,
        doubleTapGesture = playerPreferences.doubleTapGesture,
        seekIncrementMillis = playerPreferences.seekIncrement.seconds.inWholeMilliseconds,
        shouldUseLongPressGesture = playerPreferences.shouldUseLongPressControls,
        shouldUseLongPressVariableSpeed = playerPreferences.shouldUseLongPressVariableSpeed,
        longPressSpeed = playerPreferences.longPressControlsSpeed,
    )
    val seekGestureState = rememberSeekGestureState(
        player = player,
        sensitivity = playerPreferences.seekSensitivity,
        isSeekGestureEnabled = playerPreferences.shouldUseSeekControls,
        onSeekCommitted = mediaPresentationState::onSeekCommitted,
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        shouldAutoEnter = playerPreferences.shouldAutoEnterPip && playerPreferences.pictureInPictureMode == PictureInPictureMode.NATIVE,
    )
    val videoZoomAndContentScaleState = rememberVideoZoomAndContentScaleState(
        player = player,
        initialContentScale = playerPreferences.playerVideoZoom,
        isZoomGestureEnabled = playerPreferences.shouldUseZoomControls,
        isPanGestureEnabled = playerPreferences.isPanGestureEnabled,
        onEvent = viewModel::onVideoZoomEvent,
    )
    val brightnessState = rememberBrightnessState()
    val volumeAndBrightnessGestureState = rememberVolumeAndBrightnessGestureState(
        volumeState = volumeState,
        brightnessState = brightnessState,
        isVolumeGestureEnabled = playerPreferences.isVolumeSwipeGestureEnabled,
        isBrightnessGestureEnabled = playerPreferences.isBrightnessSwipeGestureEnabled,
        volumeGestureSensitivity = playerPreferences.volumeGestureSensitivity,
        brightnessGestureSensitivity = playerPreferences.brightnessGestureSensitivity,
    )
    val rotationState = rememberRotationState(
        player = player,
        screenOrientation = playerPreferences.playerScreenOrientation,
        shouldRememberScreenOrientation = playerPreferences.shouldRememberPlayerScreenOrientation,
        lastScreenOrientation = playerPreferences.lastPlayerScreenOrientation,
        onLastScreenOrientationChange = viewModel::updateLastPlayerScreenOrientation,
    )
    var restoredVolumeMediaItemIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastSavedVolumePercentage by remember { mutableIntStateOf(volumeState.volumePercentage) }
    var pendingRestoredVolumePercentage by remember { mutableStateOf<Int?>(null) }
    var chapterSwitchFeedback by remember { mutableStateOf<VideoChapter?>(null) }
    val errorState = rememberErrorState(player = player)

    LaunchedEffect(chapterSwitchFeedback) {
        if (chapterSwitchFeedback == null) return@LaunchedEffect
        delay(CHAPTER_SWITCH_FEEDBACK_DURATION_MS)
        chapterSwitchFeedback = null
    }

    DisposableEffect(player) {
        viewModel.updatePlaybackMarkMediaItem(player.currentMediaItem)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                viewModel.updatePlaybackMarkMediaItem(mediaItem)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(pictureInPictureState.isInPictureInPictureMode) {
        if (pictureInPictureState.isInPictureInPictureMode) {
            controlsVisibilityState.hideControls()
            brightnessState.suspendBrightnessOverride()
        } else {
            brightnessState.resumeBrightnessOverride()
        }
    }

    LaunchedEffect(tapGestureState.isLongPressGestureInAction) {
        if (tapGestureState.isLongPressGestureInAction) {
            controlsVisibilityState.hideControls()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (playerPreferences.shouldRememberPlayerBrightness) {
            brightnessState.setBrightness(playerPreferences.playerBrightness)
        }
        if (playerPreferences.shouldRememberPlayerVolume && restoredVolumeMediaItemIndex != player.currentMediaItemIndex) {
            restoredVolumeMediaItemIndex = player.currentMediaItemIndex
            val savedVolumePercentage = playerPreferences.playerVolumePercentage
            val restoredVolumePercentage = savedVolumePercentage.coerceAtMost(playerPreferences.maxInitialPlayerVolumePercentage)
            Logger.debug(
                TAG,
                "Restore player volume: saved=$savedVolumePercentage, " +
                    "limit=${playerPreferences.maxInitialPlayerVolumePercentage}, applied=$restoredVolumePercentage",
            )
            volumeState.updateVolumePercentage(restoredVolumePercentage)
            pendingRestoredVolumePercentage = volumeState.volumePercentage
        }
    }

    LaunchedEffect(brightnessState.currentBrightness) {
        if (playerPreferences.shouldRememberPlayerBrightness) {
            viewModel.updatePlayerBrightness(brightnessState.currentBrightness)
        }
    }

    LaunchedEffect(volumeState.volumePercentage) {
        if (!playerPreferences.shouldRememberPlayerVolume) return@LaunchedEffect
        if (pendingRestoredVolumePercentage == volumeState.volumePercentage) {
            pendingRestoredVolumePercentage = null
            lastSavedVolumePercentage = volumeState.volumePercentage
            return@LaunchedEffect
        }
        pendingRestoredVolumePercentage = null
        if (lastSavedVolumePercentage == volumeState.volumePercentage) return@LaunchedEffect

        lastSavedVolumePercentage = volumeState.volumePercentage
        viewModel.updatePlayerVolume(volumeState.volumePercentage)
    }

    val floatingPanelState = rememberFloatingPlayerPanelState()
    var menuRouteStack by remember { mutableStateOf<List<MenuRoute>>(emptyList()) }
    val playlistState = rememberPlaylistState(player)
    var currentVideoInfo by remember { mutableStateOf<Video?>(null) }
    val currentMediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        ?: player.currentMediaItem?.requestMetadata?.mediaUri?.toString()
    LaunchedEffect(menuRouteStack.lastOrNull(), currentMediaUri) {
        if (menuRouteStack.lastOrNull() != MenuRoute.VideoInfo) return@LaunchedEffect
        currentVideoInfo = null
        currentVideoInfo = currentMediaUri?.let { viewModel.getVideoByUri(it) }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val shouldShowPlayerTitle = !isPortrait
    val cornerControlsCapacity = playerCornerControlsCapacity(isPortrait = isPortrait)
    val sleepTimerState = rememberSleepTimerState(player = player)
    var shouldShowOverlay by remember { mutableStateOf(false) }
    var shouldAttachActivityVideoOutput by remember { mutableStateOf(true) }
    var videoFiltersInitialPreferences by remember { mutableStateOf<PlayerPreferences?>(null) }
    var subtitleStylePreviewPreferences by remember { mutableStateOf<PlayerPreferences?>(null) }
    var isVideoMirrored by remember { mutableStateOf(false) }
    val activePlayerPreferences = subtitleStylePreviewPreferences ?: playerPreferences
    val videoFiltersUnavailableMessage = stringResource(coreUiR.string.video_filters_unavailable_software_decoder)
    fun restoreVideoFiltersPreview() {
        videoFiltersInitialPreferences?.let { initialPreferences ->
            (player as? androidx.media3.session.MediaController)?.previewVideoFilters(initialPreferences)
        }
        videoFiltersInitialPreferences = null
    }
    fun updateSubtitleStyle(preferences: PlayerPreferences) {
        subtitleStylePreviewPreferences = preferences
        viewModel.updateSubtitleStyle(preferences)
    }
    fun openOverlayPanel(target: MenuRoute) {
        controlsVisibilityState.hideControls()
        menuRouteStack = listOf(target)
    }
    val showVideoFilters = {
        if (metadataState.isVideoEffectsAvailable) {
            videoFiltersInitialPreferences = playerPreferences
            openOverlayPanel(MenuRoute.VideoFilters)
        } else {
            Toast.makeText(context, videoFiltersUnavailableMessage, Toast.LENGTH_SHORT).show()
        }
    }
    fun addPlaybackMark() {
        viewModel.addPlaybackMark(
            mediaItem = player.currentMediaItem,
            positionMs = player.currentPosition,
            durationMs = player.duration,
        )
        controlsVisibilityState.showControls()
    }
    fun closeVideoFiltersOverlay() {
        restoreVideoFiltersPreview()
        menuRouteStack = emptyList()
    }
    fun confirmVideoFilters(preferences: PlayerPreferences) {
        videoFiltersInitialPreferences = null
        (player as? androidx.media3.session.MediaController)?.previewVideoFilters(preferences)
        viewModel.updateVideoFilters(preferences)
    }
    fun enterPictureInPicture() {
        when (playerPreferences.pictureInPictureMode) {
            PictureInPictureMode.NATIVE -> {
                if (!pictureInPictureState.hasPipPermission) {
                    Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                    pictureInPictureState.openPictureInPictureSettings()
                } else {
                    pictureInPictureState.enterPictureInPictureMode()
                }
            }

            PictureInPictureMode.CUSTOM -> {
                if (!pictureInPictureState.hasCustomPipPermission) {
                    Toast.makeText(context, coreUiR.string.enable_custom_pip_from_settings, Toast.LENGTH_SHORT).show()
                    pictureInPictureState.openCustomPictureInPictureSettings()
                    return
                }
                val controller = player as? MediaController ?: return
                scope.launch {
                    shouldAttachActivityVideoOutput = false
                    withFrameNanos { }
                    val result = controller.showCustomPictureInPicture().await()
                    if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                        onPlayInBackgroundClick()
                    } else {
                        shouldAttachActivityVideoOutput = true
                        Toast.makeText(context, coreUiR.string.enable_custom_pip_from_settings, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    fun dismissOverlay() {
        if (menuRouteStack.contains(MenuRoute.VideoFilters)) {
            restoreVideoFiltersPreview()
        }
        menuRouteStack = emptyList()
    }
    fun seekToPlaybackMark(mark: PlaybackMark) {
        player.seekToRequestedPosition(mark.positionMs)
        dismissOverlay()
        controlsVisibilityState.showControls()
    }

    fun seekToChapter(chapter: VideoChapter) {
        chaptersState.seekTo(chapter.index)
        dismissOverlay()
        controlsVisibilityState.showControls()
    }

    fun switchChapter(direction: ChapterSwipeDirection): Boolean {
        val chapter = when (direction) {
            ChapterSwipeDirection.PREVIOUS -> chaptersState.seekToPrevious(mediaPresentationState.position)
            ChapterSwipeDirection.NEXT -> chaptersState.seekToNext(mediaPresentationState.position)
        } ?: return false
        chapterSwitchFeedback = chapter
        return true
    }

    fun setControlsLocked(isLocked: Boolean) {
        controlsVisibilityState.showControls()
        if (isLocked) {
            controlsVisibilityState.lockControls()
        } else {
            controlsVisibilityState.unlockControls()
        }
    }

    fun setMuted(isMuted: Boolean) {
        if (volumeState.isMuted == isMuted) return

        volumeState.toggleMute()
    }

    fun setAmbienceModeEnabled(
        isEnabled: Boolean,
        shouldShowControls: Boolean = true,
    ) {
        Logger.info(TAG, "Ambience mode set enabled=$isEnabled showControls=$shouldShowControls")
        viewModel.updateAmbienceModeEnabled(isEnabled)
        if (shouldShowControls) {
            controlsVisibilityState.showControls()
        }
    }

    fun toggleAmbienceMode(shouldShowControls: Boolean = true) {
        setAmbienceModeEnabled(
            isEnabled = !isAmbienceModeEnabled,
            shouldShowControls = shouldShowControls,
        )
    }

    fun setVideoMirrored(isMirrored: Boolean) {
        isVideoMirrored = isMirrored
    }

    fun popMenuRoute() {
        if (menuRouteStack.lastOrNull() == MenuRoute.VideoFilters) {
            restoreVideoFiltersPreview()
        }
        if (menuRouteStack.size > 1) {
            menuRouteStack = menuRouteStack.dropLast(1)
        } else {
            menuRouteStack = emptyList()
        }
    }
    fun navigateToMenuRoute(target: MenuRoute) {
        if (target == MenuRoute.VideoFilters) {
            if (!metadataState.isVideoEffectsAvailable) {
                Toast.makeText(context, videoFiltersUnavailableMessage, Toast.LENGTH_SHORT).show()
                return
            }
            videoFiltersInitialPreferences = playerPreferences
        }
        menuRouteStack = menuRouteStack + target
    }

    val controlBindings = playerControlBindings(
        isPipSupported = pictureInPictureState.isPipSupported,
        isTakingScreenshot = isTakingScreenshot,
        hasChapters = chaptersState.chapters.isNotEmpty(),
        onRotate = { rotationState.rotate() },
        onPictureInPicture = ::enterPictureInPicture,
        onScreenshot = onScreenshotClick,
        onPlayInBackground = onPlayInBackgroundClick,
    )
    var longPressOverlayAnimationStep by remember { mutableIntStateOf(0) }
    val keyboardInteractionEnabledState = rememberUpdatedState(
        menuRouteStack.isEmpty() &&
            !controlsVisibilityState.isControlsLocked,
    )
    val seekIncrementState = rememberUpdatedState(playerPreferences.seekIncrement.seconds.inWholeMilliseconds)
    val currentPlayerState = rememberUpdatedState(player)
    val currentTapGestureState = rememberUpdatedState(tapGestureState)
    val currentControlsVisibilityState = rememberUpdatedState(controlsVisibilityState)
    val currentVolumeState = rememberUpdatedState(volumeState)
    val keyboardController = remember {
        PlayerKeyboardController(
            onSeekBackward = {
                Logger.debug(TAG, "Keyboard seek: offsetMs=${-seekIncrementState.value}")
                currentPlayerState.value.seekByRequestedOffset(-seekIncrementState.value)
                currentControlsVisibilityState.value.showControls()
            },
            onSeekForward = {
                Logger.debug(TAG, "Keyboard seek: offsetMs=${seekIncrementState.value}")
                currentPlayerState.value.seekByRequestedOffset(seekIncrementState.value)
                currentControlsVisibilityState.value.showControls()
            },
            onIncreaseVolume = {
                currentVolumeState.value.increaseVolume(shouldShowVolumePanel = true)
                currentControlsVisibilityState.value.showControls()
            },
            onDecreaseVolume = {
                currentVolumeState.value.decreaseVolume(shouldShowVolumePanel = true)
                currentControlsVisibilityState.value.showControls()
            },
            onTogglePlayPause = {
                if (currentPlayerState.value.isPlaying) {
                    currentPlayerState.value.pause()
                } else {
                    currentPlayerState.value.play()
                }
                currentControlsVisibilityState.value.showControls()
            },
            onStartTemporarySpeed = {
                val didStart = currentTapGestureState.value.handleKeyboardLongPress()
                if (didStart) {
                    currentControlsVisibilityState.value.hideControls()
                }
                didStart
            },
            onStopTemporarySpeed = {
                currentTapGestureState.value.handleOnLongPressRelease()
            },
        )
    }
    val keyboardEventHandler: (KeyEvent) -> Boolean = keyboardHandler@{ event ->
        if (!keyboardInteractionEnabledState.value) return@keyboardHandler false
        keyboardController.handleKeyEvent(event)
    }
    val longPressOverlayUiState = resolveLongPressOverlayUiState(
        isLongPressGestureInAction = tapGestureState.isLongPressGestureInAction,
        isDebugLongPressOverlayVisible = playerPreferences.isDebugLongPressOverlayVisible,
        longPressSpeed = tapGestureState.currentLongPressSpeed,
        shouldShowOverlay = shouldShowOverlay,
    )
    val shouldShowControlsScrim = controlsVisibilityState.isControlsVisible &&
        playerPreferences.shouldDimVideoWhenControlsVisible

    LaunchedEffect(playerPreferences) {
        if (subtitleStylePreviewPreferences?.hasSameSubtitleStyle(playerPreferences) == true) {
            subtitleStylePreviewPreferences = null
        }
    }

    LaunchedEffect(
        tapGestureState.isLongPressGestureInAction,
        tapGestureState.longPressSpeedChangeCount,
    ) {
        if (!tapGestureState.isLongPressGestureInAction) {
            shouldShowOverlay = false
            return@LaunchedEffect
        }

        shouldShowOverlay = true
        delay(3.seconds)
        shouldShowOverlay = false
    }

    LaunchedEffect(longPressOverlayUiState != null) {
        if (longPressOverlayUiState == null) {
            longPressOverlayAnimationStep = 0
            return@LaunchedEffect
        }
        while (true) {
            longPressOverlayAnimationStep = 0
            delay(120)
            longPressOverlayAnimationStep = 1
            delay(120)
            longPressOverlayAnimationStep = 2
            delay(120)
            longPressOverlayAnimationStep = 3
            delay(320)
        }
    }

    SideEffect {
        onKeyboardEventHandlerChanged(keyboardEventHandler)
    }

    DisposableEffect(Unit) {
        onDispose {
            onKeyboardEventHandlerChanged { false }
        }
    }

    fun stressPanZoom(extras: android.os.Bundle?) {
        val iterations = extras?.getString("value")?.toIntOrNull()
            ?: extras?.getInt("value", 0)?.takeIf { it > 0 }
            ?: 80
        val intervalMs = extras?.getString("interval_ms")?.toLongOrNull()
            ?: extras?.getLong("interval_ms", 0L)?.takeIf { it > 0L }
            ?: 8L
        scope.launch {
            repeat(iterations) { i ->
                val left = i * 2
                val top = i
                val right = left + 1600 + (i % 7)
                val bottom = top + 900 + (i % 5)
                pictureInPictureState.updateVideoViewRect(android.graphics.Rect(left, top, right, bottom))
                delay(intervalMs)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun android.os.Bundle?.longValue(key: String): Long? {
        if (this == null || !containsKey(key)) return null
        return when (val rawValue = get(key)) {
            is Long -> rawValue
            is Int -> rawValue.toLong()
            is Number -> rawValue.toLong()
            is String -> rawValue.toLongOrNull()
            else -> null
        }
    }

    fun markIdFrom(extras: android.os.Bundle?): Long? = extras.longValue("id") ?: extras.longValue("value")

    fun markPositionFrom(extras: android.os.Bundle?): Long? = extras.longValue("value")

    fun handleDebugPlayerAction(action: String, extras: android.os.Bundle?): Boolean {
        when (action) {
            PlayerDebugCommandBridge.ACTION_BACK -> onBackClick()

            PlayerDebugCommandBridge.ACTION_ROTATE -> rotationState.rotate()

            PlayerDebugCommandBridge.ACTION_TOGGLE_AMBIENCE -> toggleAmbienceMode()

            PlayerDebugCommandBridge.ACTION_TOGGLE_MIRROR -> isVideoMirrored = !isVideoMirrored

            PlayerDebugCommandBridge.ACTION_SHOW_CONTROLS -> controlsVisibilityState.showControls()

            PlayerDebugCommandBridge.ACTION_HIDE_CONTROLS -> controlsVisibilityState.hideControls()

            PlayerDebugCommandBridge.ACTION_SHOW_PLAYLIST -> openOverlayPanel(MenuRoute.Playlist)

            PlayerDebugCommandBridge.ACTION_SHOW_SPEED -> openOverlayPanel(MenuRoute.PlaybackSpeed)

            PlayerDebugCommandBridge.ACTION_SHOW_AUDIO -> openOverlayPanel(MenuRoute.Audio)

            PlayerDebugCommandBridge.ACTION_SHOW_SUBTITLE -> openOverlayPanel(MenuRoute.Subtitle)

            PlayerDebugCommandBridge.ACTION_LOCK -> {
                controlsVisibilityState.showControls()
                controlsVisibilityState.lockControls()
            }

            PlayerDebugCommandBridge.ACTION_UNLOCK -> {
                controlsVisibilityState.showControls()
                controlsVisibilityState.unlockControls()
            }

            PlayerDebugCommandBridge.ACTION_TOGGLE_LOCK -> {
                controlsVisibilityState.showControls()
                if (controlsVisibilityState.isControlsLocked) controlsVisibilityState.unlockControls() else controlsVisibilityState.lockControls()
            }

            PlayerDebugCommandBridge.ACTION_CYCLE_SCALE -> {
                videoZoomAndContentScaleState.switchToNextVideoContentScale()
                controlsVisibilityState.showControls()
            }

            PlayerDebugCommandBridge.ACTION_SHOW_SCALE -> openOverlayPanel(MenuRoute.VideoContentScale)

            PlayerDebugCommandBridge.ACTION_SHOW_DECODER -> openOverlayPanel(MenuRoute.Decoder)

            PlayerDebugCommandBridge.ACTION_SHOW_VIDEO_FILTERS -> showVideoFilters()

            PlayerDebugCommandBridge.ACTION_PIP -> {
                enterPictureInPicture()
            }

            PlayerDebugCommandBridge.ACTION_SCREENSHOT -> onScreenshotClick()

            PlayerDebugCommandBridge.ACTION_BACKGROUND -> onPlayInBackgroundClick()

            PlayerDebugCommandBridge.ACTION_SHOW_SLEEP_TIMER -> openOverlayPanel(MenuRoute.SleepTimer)

            PlayerDebugCommandBridge.ACTION_SHOW_MARKS -> openOverlayPanel(MenuRoute.PlaybackMarks)

            PlayerDebugCommandBridge.ACTION_SHOW_CHAPTERS -> openOverlayPanel(MenuRoute.Chapters)

            PlayerDebugCommandBridge.ACTION_CHAPTER_SWIPE_NEXT -> {
                if (!switchChapter(ChapterSwipeDirection.NEXT)) return false
            }

            PlayerDebugCommandBridge.ACTION_CHAPTER_SWIPE_PREVIOUS -> {
                if (!switchChapter(ChapterSwipeDirection.PREVIOUS)) return false
            }

            PlayerDebugCommandBridge.ACTION_MARK_ADD -> {
                val didAdd = runBlocking {
                    viewModel.addPlaybackMarkNow(
                        mediaItem = player.currentMediaItem,
                        positionMs = player.currentPosition,
                        durationMs = player.duration,
                    )
                }
                if (!didAdd) return false
                controlsVisibilityState.showControls()
            }

            PlayerDebugCommandBridge.ACTION_MARK_LIST -> {
                extras?.putString(
                    "value",
                    playbackMarks.joinToString(separator = "|") { mark -> "${mark.id}@${mark.positionMs}" },
                )
            }

            PlayerDebugCommandBridge.ACTION_MARK_SEEK -> {
                val markId = markIdFrom(extras)
                val positionMs = markPositionFrom(extras)
                val mark = markId?.let { id -> playbackMarks.firstOrNull { it.id == id } }
                    ?: positionMs?.let { PlaybackMark(mediaUri = "", positionMs = it, durationMs = player.duration) }
                    ?: playbackMarks.firstOrNull()
                    ?: return false
                seekToPlaybackMark(mark)
            }

            PlayerDebugCommandBridge.ACTION_MARK_DELETE -> {
                val markId = markIdFrom(extras) ?: playbackMarks.firstOrNull()?.id ?: return false
                runBlocking { viewModel.deletePlaybackMarkNow(markId) }
            }

            PlayerDebugCommandBridge.ACTION_SHOW_MENU -> {
                controlsVisibilityState.hideControls()
                menuRouteStack = listOf(MenuRoute.Root)
            }

            PlayerDebugCommandBridge.ACTION_MENU_BACK -> {
                if (menuRouteStack.size > 1) {
                    popMenuRoute()
                } else {
                    dismissOverlay()
                }
            }

            PlayerDebugCommandBridge.ACTION_STRESS_PAN_ZOOM -> {
                stressPanZoom(extras)
            }

            PlayerDebugCommandBridge.ACTION_PANEL_RESIZE -> {
                val command = extras?.getString("value").orEmpty()
                if (!floatingPanelState.applyDebugCommand(command)) return false
                extras?.putString("value", floatingPanelState.debugSnapshot())
            }

            PlayerDebugCommandBridge.ACTION_PANEL_MOVE -> {
                val command = extras?.getString("value").orEmpty()
                if (!floatingPanelState.applyDebugMove(command)) return false
                extras?.putString("value", floatingPanelState.debugSnapshot())
            }

            PlayerDebugCommandBridge.ACTION_PANEL_STATE -> {
                extras?.putString("value", floatingPanelState.debugSnapshot())
            }

            else -> return false
        }
        return true
    }

    val currentDebugActionHandler = rememberUpdatedState(::handleDebugPlayerAction)
    DisposableEffect(Unit) {
        val token = PlayerDebugCommandBridge.setHandler { action, extras -> currentDebugActionHandler.value(action, extras) }
        onDispose { PlayerDebugCommandBridge.clearHandler(token) }
    }

    CompositionLocalProvider(
        LocalControlsVisibilityState provides controlsVisibilityState,
    ) {
        Box {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { size ->
                        floatingPanelState.updateViewport(size.width, size.height)
                    },
            ) {
                val safeDrawingTopPadding = WindowInsets.safeDrawing
                    .asPaddingValues()
                    .calculateTopPadding()
                val longPressOverlayTopPadding = maxOf(
                    safeDrawingTopPadding,
                    pictureInPictureState.videoViewRect
                        ?.top
                        ?.let { with(LocalDensity.current) { it.toDp() } }
                        ?: 0.dp,
                ) + 16.dp
                val shouldRenderAmbienceBackground = isAmbienceModeEnabled && player.canUseTextureViewForAmbience()
                if (shouldRenderAmbienceBackground) {
                    val ambienceMediaKey = player.currentAmbienceMediaKey()
                    AmbienceBackground(
                        mediaKey = ambienceMediaKey,
                        videoViewRect = pictureInPictureState.videoViewRect,
                        hasRenderedFirstFrame = mediaPresentationState.hasRenderedFirstFrame,
                        isPlaying = mediaPresentationState.isPlaying,
                        isBuffering = mediaPresentationState.isBuffering,
                        isSeeking = seekGestureState.isSeeking,
                        positionMs = mediaPresentationState.position,
                        cachedFrameBitmap = viewModel.ambienceFrameFor(ambienceMediaKey)
                            ?: viewModel.latestAmbienceFrame(),
                        onFrameCaptured = viewModel::updateAmbienceFrame,
                    )
                }

                PlayerContentFrame(
                    player = player,
                    pictureInPictureState = pictureInPictureState,
                    controlsVisibilityState = controlsVisibilityState,
                    tapGestureState = tapGestureState,
                    seekGestureState = seekGestureState,
                    videoZoomAndContentScaleState = videoZoomAndContentScaleState,
                    volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
                    subtitleConfiguration = SubtitleConfiguration(
                        shouldUseSystemCaptionStyle = activePlayerPreferences.shouldUseSystemCaptionStyle,
                        shouldShowBackground = activePlayerPreferences.shouldShowSubtitleBackground,
                        font = activePlayerPreferences.subtitleFont,
                        textSize = activePlayerPreferences.subtitleTextSize,
                        shouldUseBoldText = activePlayerPreferences.shouldUseBoldSubtitleText,
                        color = activePlayerPreferences.subtitleColor,
                        edgeStyle = activePlayerPreferences.subtitleEdgeStyle,
                        outlineThickness = activePlayerPreferences.subtitleOutlineThickness,
                        shadowStrength = activePlayerPreferences.subtitleShadowStrength,
                        bottomPaddingFraction = activePlayerPreferences.subtitleBottomPaddingFraction,
                        shouldApplyEmbeddedStyles = activePlayerPreferences.shouldApplyEmbeddedStyles,
                        subtitleScale = activePlayerPreferences.subtitleScale,
                        externalSubtitleFontSource = externalSubtitleFontSource,
                    ),
                    decoderPriority = playerPreferences.decoderPriority,
                    shouldAttachVideoOutput = shouldAttachActivityVideoOutput,
                    shouldUseTextureView = isVideoMirrored,
                    isVideoMirrored = isVideoMirrored,
                    isChapterSwipeEnabled = chaptersState.chapters.size > 1,
                    onChapterSwipe = { direction -> switchChapter(direction) },
                )

                AnimatedVisibility(
                    visible = shouldShowControlsScrim,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.3f),
                            ),
                    )
                }

                if (mediaPresentationState.isBuffering && mediaPresentationState.hasRenderedFirstFrame) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                    )
                }
                DoubleTapIndicator(tapGestureState = tapGestureState)

                AnimatedVisibility(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = longPressOverlayTopPadding),
                    visible = chapterSwitchFeedback != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    chapterSwitchFeedback?.let { chapter ->
                        ChapterSwitchIndicator(chapter = chapter)
                    }
                }

                if (longPressOverlayUiState != null) {
                    LongPressSpeedOverlay(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = longPressOverlayTopPadding)
                            .testTag("long_press_speed_overlay"),
                        speedText = longPressOverlayUiState.speedText,
                        animationStep = longPressOverlayAnimationStep,
                    )
                }

                if (controlsVisibilityState.isControlsVisible && controlsVisibilityState.isControlsLocked) {
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
                } else {
                    PlayerControlsView(
                        topView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.isControlsVisible,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ControlsTopModernView(
                                    title = (metadataState.title ?: "").takeIf { shouldShowPlayerTitle }.orEmpty(),
                                    onBackClick = { onBackClick() },
                                    onMenuClick = {
                                        controlsVisibilityState.hideControls()
                                        menuRouteStack = listOf(MenuRoute.Root)
                                    },
                                    topRightControls = playerPreferences.playerControls(PlayerControlSlot.TOP_RIGHT),
                                    bindings = controlBindings,
                                    maxVisibleControls = cornerControlsCapacity.topRight,
                                    onOpenPanel = ::openOverlayPanel,
                                )
                            }
                        },
                        middleView = {
                            when {
                                seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")

                                videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")

                                videoZoomAndContentScaleState.shouldShowContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))

                                else -> Unit
                            }
                        },
                        bottomView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.isControlsVisible && !controlsVisibilityState.isControlsLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                ControlsBottomModernView(
                                    mediaPresentationState = mediaPresentationState,
                                    pendingSeekPosition = seekGestureState.pendingSeekPosition,
                                    isPlaying = mediaPresentationState.isPlaying,
                                    hasPrevious = player.hasPreviousMediaItem(),
                                    hasNext = player.hasNextMediaItem(),
                                    onPlayPauseClick = {
                                        if (player.isPlaying) player.pause() else player.play()
                                    },
                                    onPreviousClick = { player.seekToPrevious() },
                                    onNextClick = { player.seekToNext() },
                                    bottomRightControls = playerPreferences.playerControls(PlayerControlSlot.BOTTOM_RIGHT),
                                    bindings = controlBindings,
                                    maxVisibleControls = cornerControlsCapacity.bottomRight,
                                    onOpenPanel = ::openOverlayPanel,
                                    onSeek = seekGestureState::onSeek,
                                    onSeekEnd = seekGestureState::onSeekEnd,
                                )
                            }
                        },
                    )
                }

                val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .displayCutoutPadding()
                        .padding(systemBarsPadding.copy(top = 0.dp, bottom = 0.dp))
                        .padding(24.dp),
                ) {
                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterStart),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = volumeState.volumePercentage,
                            maxValue = volumeState.maxVolumePercentage,
                            icon = painterResource(coreUiR.drawable.ic_volume),
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = brightnessState.brightnessPercentage,
                            icon = painterResource(coreUiR.drawable.ic_brightness),
                        )
                    }
                }
            }

            val currentRoute = menuRouteStack.lastOrNull()
            val canGoBack = menuRouteStack.size > 1
            if (currentRoute != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .noRippleClickable { dismissOverlay() },
                )
            }
            MenuOverlayView(
                externalRoute = currentRoute,
                title = titleForMenuRoute(
                    route = currentRoute,
                    playlistItemCount = playlistState.playlist.size,
                ),
                canGoBack = canGoBack,
                panelState = floatingPanelState,
                onBack = {
                    if (canGoBack) popMenuRoute() else dismissOverlay()
                },
                onDismiss = ::dismissOverlay,
            ) { route ->
                when (route) {
                    MenuRoute.Root -> MenuRootContent(
                        menuControls = playerPreferences.playerControls(PlayerControlSlot.MENU),
                        bindings = controlBindings,
                        onNavigate = ::navigateToMenuRoute,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.ControlLock -> ToggleOptionSelectorContent(
                        panelTestTag = "panel_control_lock",
                        isEnabled = controlsVisibilityState.isControlsLocked,
                        offTestTag = "btn_control_lock_off",
                        onTestTag = "btn_control_lock_on",
                        onEnabledChanged = ::setControlsLocked,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.Mute -> ToggleOptionSelectorContent(
                        panelTestTag = "panel_mute_switch",
                        isEnabled = volumeState.isMuted,
                        offTestTag = "btn_mute_off",
                        onTestTag = "btn_mute_on",
                        onEnabledChanged = ::setMuted,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.AmbienceMode -> ToggleOptionSelectorContent(
                        panelTestTag = "panel_ambience_mode",
                        isEnabled = isAmbienceModeEnabled,
                        offTestTag = "btn_ambience_mode_off",
                        onTestTag = "btn_ambience_mode_on",
                        onEnabledChanged = { isEnabled ->
                            setAmbienceModeEnabled(
                                isEnabled = isEnabled,
                                shouldShowControls = false,
                            )
                        },
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.MirrorVideo -> ToggleOptionSelectorContent(
                        panelTestTag = "panel_mirror_video",
                        isEnabled = isVideoMirrored,
                        offTestTag = "btn_mirror_video_off",
                        onTestTag = "btn_mirror_video_on",
                        onEnabledChanged = ::setVideoMirrored,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.Audio -> AudioTrackSelectorContent(
                        player = player,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.Subtitle -> SubtitleSelectorContent(
                        player = player,
                        onSelectSubtitleClick = onSelectSubtitleClick,
                        onAddOnlineSubtitleClick = onAddOnlineSubtitleClick,
                        preferences = activePlayerPreferences,
                        onPreferencesChange = ::updateSubtitleStyle,
                        onEvent = viewModel::onSubtitleOptionEvent,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.PlaybackSpeed -> PlaybackSpeedSelectorContent(player = player)

                    MenuRoute.VideoContentScale -> VideoContentScaleSelectorContent(
                        videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                        isCustomZoomActive = !videoZoomAndContentScaleState.zoom.isDefaultVideoZoom(),
                        onVideoContentScaleChanged = {
                            videoZoomAndContentScaleState.onVideoContentScaleChanged(it)
                        },
                        onShowVideoFilters = null,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.VideoFilters -> VideoFiltersPanel(
                        modifier = Modifier.fillMaxSize(),
                        preferences = playerPreferences,
                        onDismissRequest = ::closeVideoFiltersOverlay,
                        onPreviewPreferences = { previewPreferences ->
                            (player as? androidx.media3.session.MediaController)?.previewVideoFilters(previewPreferences)
                        },
                        onConfirmPreferences = ::confirmVideoFilters,
                    )

                    MenuRoute.VideoInfo -> VideoInfoContent(
                        player = player,
                        video = currentVideoInfo,
                        durationMs = mediaPresentationState.duration,
                    )

                    MenuRoute.Playlist -> PlaylistContent(
                        isVisible = true,
                        player = player,
                    )

                    MenuRoute.SleepTimer -> SleepTimerSelectorContent(
                        sleepTimerState = sleepTimerState,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.Decoder -> DecoderPrioritySelectorContent(
                        currentDecoderPriority = playerPreferences.decoderPriority,
                        onDecoderPriorityClick = {
                            viewModel.updateDecoderPriority(it)
                            dismissOverlay()
                        },
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.PlaybackMarks -> PlaybackMarksContent(
                        modifier = Modifier.testTag("panel_playback_marks"),
                        marks = playbackMarks,
                        onAddMarkClick = ::addPlaybackMark,
                        onMarkClick = ::seekToPlaybackMark,
                        onDeleteMarkClick = { mark -> viewModel.deletePlaybackMark(mark.id) },
                    )

                    MenuRoute.Chapters -> ChaptersContent(
                        modifier = Modifier.testTag("panel_chapters"),
                        isVisible = true,
                        chapters = chaptersState.chapters,
                        positionMs = mediaPresentationState.position,
                        mediaUri = chaptersState.mediaUri,
                        onChapterClick = ::seekToChapter,
                    )

                    MenuRoute.LoopMode -> LoopModeSelectorContent(
                        player = player,
                        onDismiss = ::dismissOverlay,
                    )

                    MenuRoute.ShuffleMode -> ShuffleModeSelectorContent(
                        player = player,
                        onDismiss = ::dismissOverlay,
                    )
                }
            }
        }
    }

    errorState.error?.let { error ->
        AppDialog(
            onDismissRequest = { },
            title = stringResource(coreUiR.string.error_playing_video),
            content = {
                MiuixText(text = error.message ?: stringResource(coreUiR.string.unknown_error))
            },
            confirmButton = {
                if (player.hasNextMediaItem()) {
                    MiuixTextButton(
                        modifier = Modifier.testTag("btn_error_play_next"),
                        text = stringResource(coreUiR.string.play_next_video),
                        colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            errorState.dismiss()
                            player.seekToNext()
                            player.play()
                        },
                    )
                }
            },
            dismissButton = {
                MiuixTextButton(
                    modifier = Modifier.testTag("btn_error_exit"),
                    text = stringResource(coreUiR.string.exit),
                    onClick = {
                        errorState.dismiss()
                        onBackClick()
                    },
                )
            },
        )
    }

    BackHandler {
        when {
            menuRouteStack.size > 1 -> popMenuRoute()
            menuRouteStack.isNotEmpty() -> dismissOverlay()
            else -> onBackClick()
        }
    }
}

private fun PlayerPreferences.hasSameSubtitleStyle(other: PlayerPreferences): Boolean = shouldUseBoldSubtitleText == other.shouldUseBoldSubtitleText &&
    subtitleTextSize == other.subtitleTextSize &&
    shouldShowSubtitleBackground == other.shouldShowSubtitleBackground &&
    subtitleColor == other.subtitleColor &&
    subtitleEdgeStyle == other.subtitleEdgeStyle &&
    subtitleOutlineThickness == other.subtitleOutlineThickness &&
    subtitleShadowStrength == other.subtitleShadowStrength &&
    subtitleBottomPaddingFraction == other.subtitleBottomPaddingFraction &&
    subtitleScale == other.subtitleScale

private fun Float.isDefaultVideoZoom(): Boolean = kotlin.math.abs(this - 1f) < 0.0001f

@Composable
private fun titleForMenuRoute(
    route: MenuRoute?,
    playlistItemCount: Int = 0,
): String = when (route) {
    null, MenuRoute.Root -> stringResource(coreUiR.string.menu)
    MenuRoute.ControlLock -> stringResource(coreUiR.string.controls_lock_switch)
    MenuRoute.Mute -> stringResource(coreUiR.string.mute_switch)
    MenuRoute.AmbienceMode -> stringResource(coreUiR.string.ambience_mode)
    MenuRoute.MirrorVideo -> stringResource(coreUiR.string.mirror_video)
    MenuRoute.Audio -> stringResource(coreUiR.string.select_audio_track)
    MenuRoute.Subtitle -> stringResource(coreUiR.string.select_subtitle_track)
    MenuRoute.PlaybackSpeed -> stringResource(coreUiR.string.select_playback_speed)
    MenuRoute.VideoContentScale -> stringResource(coreUiR.string.video_zoom)
    MenuRoute.VideoInfo -> stringResource(coreUiR.string.video_info)
    MenuRoute.VideoFilters -> stringResource(coreUiR.string.video_filters)
    MenuRoute.Playlist -> stringResource(coreUiR.string.now_playing_with_count, playlistItemCount)
    MenuRoute.SleepTimer -> stringResource(coreUiR.string.sleep_timer)
    MenuRoute.Decoder -> stringResource(coreUiR.string.decoder_priority)
    MenuRoute.PlaybackMarks -> stringResource(coreUiR.string.playback_marks)
    MenuRoute.Chapters -> stringResource(coreUiR.string.chapters)
    MenuRoute.LoopMode -> stringResource(coreUiR.string.loop_mode)
    MenuRoute.ShuffleMode -> stringResource(coreUiR.string.shuffle)
}

@Composable
fun InfoView(
    modifier: Modifier = Modifier,
    info: String,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = info,
            style = textStyle.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.85f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                ),
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AmbienceBackground(
    mediaKey: String?,
    videoViewRect: AndroidRect?,
    hasRenderedFirstFrame: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isSeeking: Boolean,
    positionMs: Long,
    cachedFrameBitmap: Bitmap?,
    onFrameCaptured: (String?, Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val rootView = view.rootView
    val window = remember(context) { context.findActivity()?.window }
    val captureRect = remember(videoViewRect, rootView.width, rootView.height) {
        videoViewRect?.coerceToWindowBounds(rootView)
    }
    val currentCaptureRect = rememberUpdatedState(captureRect)
    val currentIsBuffering = rememberUpdatedState(isBuffering)
    val currentIsPlaying = rememberUpdatedState(isPlaying)
    val currentPositionMs = rememberUpdatedState(positionMs)
    var shouldPauseForSeek by remember { mutableStateOf(isSeeking) }
    val currentShouldPauseForSeek = rememberUpdatedState(shouldPauseForSeek)
    var frameBitmap by remember(mediaKey) { mutableStateOf(cachedFrameBitmap) }

    LaunchedEffect(isSeeking) {
        if (isSeeking) {
            shouldPauseForSeek = true
            return@LaunchedEffect
        }
        if (!shouldPauseForSeek) return@LaunchedEffect

        delay(AMBIENCE_FRAME_CAPTURE_SEEK_RESUME_DELAY_MS)
        shouldPauseForSeek = false
    }

    DisposableEffect(mediaKey) {
        Logger.info(TAG, "Ambience background mounted media=$mediaKey")
        onDispose {
            Logger.info(TAG, "Ambience background disposed media=$mediaKey hasImage=${frameBitmap != null}")
        }
    }

    LaunchedEffect(mediaKey, rootView, window, hasRenderedFirstFrame) {
        Logger.info(
            TAG,
            "Ambience capture effect start media=$mediaKey rect=${captureRect?.ambienceDebugString()} hasFrame=$hasRenderedFirstFrame playing=${currentIsPlaying.value} buffering=${currentIsBuffering.value} hasImage=${frameBitmap != null} root=${rootView.width}x${rootView.height}",
        )
        if (!hasRenderedFirstFrame) {
            Logger.info(
                TAG,
                "Ambience capture delayed for media transition media=$mediaKey window=${window != null} rect=${captureRect?.ambienceDebugString()} keepImage=${frameBitmap != null}",
            )
            delay(AMBIENCE_FRAME_CAPTURE_MEDIA_TRANSITION_DELAY_MS)
        }

        var captureCount = 0
        var consecutiveNearBlackFrameCount = 0
        var isDisplayedFrameNearBlack = false
        var didLogBuffering = false
        var didLogNearBlackFrame = false
        var didLogWaitingForRect = false
        var shouldLogNextSuccess = true
        while (true) {
            val sourceRect = currentCaptureRect.value
            if (sourceRect == null) {
                if (!didLogWaitingForRect) {
                    Logger.info(
                        TAG,
                        "Ambience capture waiting media=$mediaKey window=${window != null} rect=null hasFrame=$hasRenderedFirstFrame keepImage=${frameBitmap != null}",
                    )
                }
                didLogWaitingForRect = true
                delay(AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS)
                continue
            }

            didLogWaitingForRect = false
            if (currentIsBuffering.value || currentShouldPauseForSeek.value) {
                if (!didLogBuffering) {
                    val reason = if (currentShouldPauseForSeek.value) "seek" else "buffering"
                    Logger.info(
                        TAG,
                        "Ambience capture paused for $reason media=$mediaKey rect=${sourceRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameBitmap != null}",
                    )
                }
                didLogBuffering = true
                shouldLogNextSuccess = true
                delay(AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS)
                continue
            }

            didLogBuffering = false
            captureCount++
            when (
                val result = capturePlayerFrame(
                    rootView = rootView,
                    window = window,
                    sourceRect = sourceRect,
                )
            ) {
                is AmbienceFrameCaptureResult.Success -> {
                    val isNearBlackFrame = result.luma.isNearBlackAmbienceFrame()
                    val shouldUpdateFrame = when {
                        !isNearBlackFrame -> {
                            consecutiveNearBlackFrameCount = 0
                            isDisplayedFrameNearBlack = false
                            true
                        }

                        isDisplayedFrameNearBlack -> false
                        else -> {
                            consecutiveNearBlackFrameCount++
                            consecutiveNearBlackFrameCount >= AMBIENCE_FRAME_NEAR_BLACK_CONFIRM_COUNT
                        }
                    }

                    if (shouldUpdateFrame) {
                        didLogNearBlackFrame = false
                        frameBitmap = result.bitmap
                        onFrameCaptured(mediaKey, result.bitmap)
                        isDisplayedFrameNearBlack = isNearBlackFrame
                        if (isNearBlackFrame) {
                            Logger.info(
                                TAG,
                                "Ambience capture accepted stable near-black frame media=$mediaKey count=$captureCount nearBlackCount=$consecutiveNearBlackFrameCount rect=${sourceRect.ambienceDebugString()} positionMs=${currentPositionMs.value}",
                            )
                        } else if (shouldLogNextSuccess || captureCount % 10 == 0) {
                            Logger.info(
                                TAG,
                                "Ambience capture success media=$mediaKey count=$captureCount source=${result.sourceDebug} rect=${sourceRect.ambienceDebugString()} capture=${result.size.width}x${result.size.height} avgLuma=${result.luma.average} maxLuma=${result.luma.max} visible=${result.luma.visiblePixelCount} positionMs=${currentPositionMs.value}",
                            )
                        }
                    } else {
                        result.bitmap.recycle()
                        if (isNearBlackFrame && !isDisplayedFrameNearBlack && !didLogNearBlackFrame) {
                            Logger.info(
                                TAG,
                                "Ambience capture skipped media=$mediaKey count=$captureCount reason=unconfirmed_near_black_frame nearBlackCount=$consecutiveNearBlackFrameCount rect=${sourceRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameBitmap != null}",
                            )
                        }
                    }
                    if (isNearBlackFrame && !isDisplayedFrameNearBlack) didLogNearBlackFrame = true
                    shouldLogNextSuccess = !shouldUpdateFrame
                }

                is AmbienceFrameCaptureResult.Failure -> {
                    if (!isDisplayedFrameNearBlack) consecutiveNearBlackFrameCount = 0
                    didLogNearBlackFrame = false
                    shouldLogNextSuccess = true
                    Logger.info(
                        TAG,
                        "Ambience capture skipped media=$mediaKey count=$captureCount reason=${result.reason} source=${result.sourceDebug} pixelCopy=${result.pixelCopyResult} rect=${sourceRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameBitmap != null}",
                    )
                }
            }
            delay(
                if (currentIsPlaying.value) {
                    AMBIENCE_FRAME_CAPTURE_PLAYING_INTERVAL_MS
                } else {
                    AMBIENCE_FRAME_CAPTURE_PAUSED_INTERVAL_MS
                },
            )
        }
    }

    frameBitmap?.let { currentFrameBitmap ->
        Image(
            bitmap = currentFrameBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .blur(48.dp),
            alpha = 0.9f,
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
    )
}

private suspend fun capturePlayerFrame(
    rootView: View,
    window: Window?,
    sourceRect: AndroidRect,
): AmbienceFrameCaptureResult {
    val renderView = rootView.findPlayerVideoRenderView()
    return when (renderView) {
        is SurfaceView -> captureSurfaceViewFrame(renderView)
        is TextureView -> captureTextureViewFrame(renderView)
        null ->
            window
                ?.let { captureWindowFrame(window = it, sourceRect = sourceRect) }
                ?: AmbienceFrameCaptureResult.Failure(
                    reason = "render_view_missing",
                    sourceDebug = "window_missing",
                )
        else -> AmbienceFrameCaptureResult.Failure(
            reason = "unsupported_render_view",
            sourceDebug = renderView.ambienceDebugString(),
        )
    }
}

private suspend fun captureSurfaceViewFrame(
    surfaceView: SurfaceView,
): AmbienceFrameCaptureResult = suspendCancellableCoroutine { continuation ->
    val sourceDebug = surfaceView.ambienceDebugString()
    if (!surfaceView.canCaptureAmbienceFrame()) {
        continuation.resume(
            AmbienceFrameCaptureResult.Failure(
                reason = "render_view_not_ready",
                sourceDebug = sourceDebug,
            ),
        )
        return@suspendCancellableCoroutine
    }

    val captureSize = surfaceView.ambienceCaptureSize() ?: run {
        continuation.resume(
            AmbienceFrameCaptureResult.Failure(
                reason = "invalid_render_view_size",
                sourceDebug = sourceDebug,
            ),
        )
        return@suspendCancellableCoroutine
    }
    val bitmap = Bitmap.createBitmap(
        captureSize.width,
        captureSize.height,
        Bitmap.Config.ARGB_8888,
    )

    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                when (result) {
                    PixelCopy.SUCCESS -> {
                        val captureResult = bitmap.toAmbienceCaptureResult(
                            sourceDebug = sourceDebug,
                            captureSize = captureSize,
                        )
                        if (continuation.isActive) {
                            continuation.resume(captureResult)
                        } else {
                            bitmap.recycle()
                        }
                    }

                    else -> {
                        bitmap.recycle()
                        if (continuation.isActive) {
                            continuation.resume(
                                AmbienceFrameCaptureResult.Failure(
                                    reason = "pixel_copy_failed",
                                    pixelCopyResult = result,
                                    sourceDebug = sourceDebug,
                                ),
                            )
                        }
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (exception: IllegalArgumentException) {
        bitmap.recycle()
        if (continuation.isActive) {
            continuation.resume(
                AmbienceFrameCaptureResult.Failure(
                    reason = "pixel_copy_exception:${exception.javaClass.simpleName}",
                    sourceDebug = sourceDebug,
                ),
            )
        }
    }
}

private fun captureTextureViewFrame(
    textureView: TextureView,
): AmbienceFrameCaptureResult {
    val sourceDebug = textureView.ambienceDebugString()
    if (!textureView.canCaptureAmbienceFrame()) {
        return AmbienceFrameCaptureResult.Failure(
            reason = "render_view_not_ready",
            sourceDebug = sourceDebug,
        )
    }
    if (!textureView.isAvailable) {
        return AmbienceFrameCaptureResult.Failure(
            reason = "texture_not_available",
            sourceDebug = sourceDebug,
        )
    }

    val captureSize = textureView.ambienceCaptureSize()
        ?: return AmbienceFrameCaptureResult.Failure(
            reason = "invalid_render_view_size",
            sourceDebug = sourceDebug,
        )
    val bitmap = Bitmap.createBitmap(
        captureSize.width,
        captureSize.height,
        Bitmap.Config.ARGB_8888,
    )
    textureView.getBitmap(bitmap)

    return bitmap.toAmbienceCaptureResult(
        sourceDebug = sourceDebug,
        captureSize = captureSize,
    )
}

private suspend fun captureWindowFrame(
    window: Window,
    sourceRect: AndroidRect,
): AmbienceFrameCaptureResult = suspendCancellableCoroutine { continuation ->
    val sourceDebug = "Window:${sourceRect.ambienceDebugString()}"
    val captureSize = sourceRect.ambienceCaptureSize() ?: run {
        continuation.resume(
            AmbienceFrameCaptureResult.Failure(
                reason = "invalid_source_rect",
                sourceDebug = sourceDebug,
            ),
        )
        return@suspendCancellableCoroutine
    }
    val bitmap = Bitmap.createBitmap(
        captureSize.width,
        captureSize.height,
        Bitmap.Config.ARGB_8888,
    )

    try {
        PixelCopy.request(
            window,
            sourceRect,
            bitmap,
            { result ->
                when (result) {
                    PixelCopy.SUCCESS -> {
                        val captureResult = bitmap.toAmbienceCaptureResult(
                            sourceDebug = sourceDebug,
                            captureSize = captureSize,
                        )
                        if (continuation.isActive) {
                            continuation.resume(captureResult)
                        } else {
                            bitmap.recycle()
                        }
                    }

                    else -> {
                        bitmap.recycle()
                        if (continuation.isActive) {
                            continuation.resume(
                                AmbienceFrameCaptureResult.Failure(
                                    reason = "pixel_copy_failed",
                                    pixelCopyResult = result,
                                    sourceDebug = sourceDebug,
                                ),
                            )
                        }
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (exception: IllegalArgumentException) {
        bitmap.recycle()
        if (continuation.isActive) {
            continuation.resume(
                AmbienceFrameCaptureResult.Failure(
                    reason = "pixel_copy_exception:${exception.javaClass.simpleName}",
                    sourceDebug = sourceDebug,
                ),
            )
        }
    }
}

private sealed interface AmbienceFrameCaptureResult {
    data class Success(
        val bitmap: Bitmap,
        val size: AmbienceCaptureSize,
        val luma: AmbienceFrameLuma,
        val sourceDebug: String,
    ) : AmbienceFrameCaptureResult

    data class Failure(
        val reason: String,
        val sourceDebug: String,
        val pixelCopyResult: Int? = null,
    ) : AmbienceFrameCaptureResult
}

private data class AmbienceCaptureSize(
    val width: Int,
    val height: Int,
)

private data class AmbienceFrameLuma(
    val average: Float,
    val max: Float,
    val visiblePixelCount: Int,
)

private fun Bitmap.toAmbienceCaptureResult(
    sourceDebug: String,
    captureSize: AmbienceCaptureSize,
): AmbienceFrameCaptureResult {
    val luma = ambienceFrameLuma()
    return AmbienceFrameCaptureResult.Success(
        bitmap = this,
        size = captureSize,
        luma = luma,
        sourceDebug = sourceDebug,
    )
}

private fun View.findPlayerVideoRenderView(): View? {
    if (this is SurfaceView || this is TextureView) return this

    val viewGroup = this as? ViewGroup ?: return null
    for (index in 0 until viewGroup.childCount) {
        viewGroup.getChildAt(index).findPlayerVideoRenderView()?.let { return it }
    }
    return null
}

private fun View.canCaptureAmbienceFrame(): Boolean = isAttachedToWindow && isShown && width > 0 && height > 0

private fun View.ambienceCaptureSize(): AmbienceCaptureSize? = ambienceCaptureSize(
    sourceWidth = width,
    sourceHeight = height,
)

private fun AndroidRect.ambienceCaptureSize(): AmbienceCaptureSize? = ambienceCaptureSize(
    sourceWidth = width(),
    sourceHeight = height(),
)

private fun ambienceCaptureSize(
    sourceWidth: Int,
    sourceHeight: Int,
): AmbienceCaptureSize? {
    if (sourceWidth <= 0 || sourceHeight <= 0) return null

    val scale = (AMBIENCE_FRAME_CAPTURE_MAX_SIZE.toFloat() / maxOf(sourceWidth, sourceHeight)).coerceAtMost(1f)
    return AmbienceCaptureSize(
        width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
    )
}

private fun AndroidRect.ambienceDebugString(): String = "${width()}x${height()}@$left,$top"

private fun View.ambienceDebugString(): String = "${javaClass.simpleName}:${width}x$height@$left,$top shown=$isShown attached=$isAttachedToWindow"

private fun Bitmap.ambienceFrameLuma(): AmbienceFrameLuma {
    var visiblePixelCount = 0
    var totalLuma = 0f
    var maxLuma = 0f
    val pixels = IntArray(width)

    for (y in 0 until height) {
        getPixels(pixels, 0, width, 0, y, width, 1)
        for (pixel in pixels) {
            val alpha = pixel ushr 24
            if (alpha <= AMBIENCE_VISIBLE_ALPHA_THRESHOLD) continue

            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff
            val luma = red * 0.299f + green * 0.587f + blue * 0.114f

            visiblePixelCount++
            totalLuma += luma
            maxLuma = maxOf(maxLuma, luma)
        }
    }

    if (visiblePixelCount == 0) {
        return AmbienceFrameLuma(
            average = 0f,
            max = 0f,
            visiblePixelCount = 0,
        )
    }

    return AmbienceFrameLuma(
        average = totalLuma / visiblePixelCount,
        max = maxLuma,
        visiblePixelCount = visiblePixelCount,
    )
}

private fun AmbienceFrameLuma.isNearBlackAmbienceFrame(): Boolean = visiblePixelCount == 0 ||
    (average <= AMBIENCE_FRAME_NEAR_BLACK_AVERAGE_LUMA && max <= AMBIENCE_FRAME_NEAR_BLACK_MAX_LUMA)

private fun Player.canUseTextureViewForAmbience(): Boolean {
    if (currentMediaItem?.localConfiguration?.drmConfiguration != null) return false

    val videoFormat = currentTracks.groups
        .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        ?.getTrackFormat(0)
        ?: return false
    if (videoFormat.drmInitData != null) return false

    val colorTransfer = videoFormat.colorInfo?.colorTransfer
    return colorTransfer != C.COLOR_TRANSFER_ST2084 && colorTransfer != C.COLOR_TRANSFER_HLG
}

private fun Player.currentAmbienceMediaKey(): String {
    val mediaItem = currentMediaItem
    val uri = mediaItem?.localConfiguration?.uri ?: mediaItem?.requestMetadata?.mediaUri
    return "$currentMediaItemIndex:${mediaItem?.mediaId}:$uri"
}

private fun AndroidRect.coerceToWindowBounds(view: View): AndroidRect? {
    val windowWidth = view.width
    val windowHeight = view.height
    if (windowWidth <= 0 || windowHeight <= 0) return null

    val safeLeft = maxOf(left, 0)
    val safeTop = maxOf(top, 0)
    val safeRight = minOf(right, windowWidth)
    val safeBottom = minOf(bottom, windowHeight)
    if (safeRight <= safeLeft || safeBottom <= safeTop) return null

    return AndroidRect(safeLeft, safeTop, safeRight, safeBottom)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LongPressSpeedOverlay(
    speedText: String,
    animationStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LongPressSpeedIndicator(animationStep = animationStep)
        Text(
            text = speedText,
            modifier = Modifier.testTag("long_press_speed_text"),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.10f),
                    offset = Offset(0f, 1f),
                    blurRadius = 2f,
                ),
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun LongPressSpeedIndicator(
    animationStep: Int,
    modifier: Modifier = Modifier,
) {
    val alpha1 by animateFloatAsState(
        targetValue = if (animationStep >= 1) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "long_press_arrow_1",
    )
    val alpha2 by animateFloatAsState(
        targetValue = if (animationStep >= 2) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "long_press_arrow_2",
    )
    val alpha3 by animateFloatAsState(
        targetValue = if (animationStep >= 3) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "long_press_arrow_3",
    )

    Row(
        modifier = modifier.testTag("long_press_speed_indicator"),
        horizontalArrangement = Arrangement.spacedBy((-1).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LongPressSpeedArrow(alpha = alpha1)
        LongPressSpeedArrow(alpha = alpha2)
        LongPressSpeedArrow(alpha = alpha3)
    }
}

@Composable
private fun LongPressSpeedArrow(alpha: Float) {
    Icon(
        painter = painterResource(coreUiR.drawable.ic_play),
        contentDescription = null,
        modifier = Modifier.size(11.dp),
        tint = Color.White.copy(alpha = alpha),
    )
}

@Composable
fun PlayerControlsView(
    modifier: Modifier = Modifier,
    topView: @Composable () -> Unit,
    middleView: @Composable BoxScope.() -> Unit,
    bottomView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            topView()
            Spacer(modifier = Modifier.weight(1f))
            bottomView()
        }

        middleView()
    }
}
