package one.only.player.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import one.only.player.core.common.Logger
import one.only.player.core.data.repository.ExternalSubtitleFontSource
import one.only.player.core.model.PlaybackMark
import one.only.player.core.model.PlayerControl
import one.only.player.core.model.PlayerControlZone
import one.only.player.core.model.PlayerControlsLayout
import one.only.player.core.model.PlayerControlsStyle
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.controllerAutoHideTimeoutSecondsOrNull
import one.only.player.core.ui.R as coreUiR
import one.only.player.core.ui.components.NextDialog
import one.only.player.core.ui.components.VideoFiltersPanel
import one.only.player.core.ui.extensions.copy
import one.only.player.feature.player.buttons.NextButton
import one.only.player.feature.player.buttons.PlayPauseButton
import one.only.player.feature.player.buttons.PlayerButton
import one.only.player.feature.player.buttons.PreviousButton
import one.only.player.feature.player.extensions.nameRes
import one.only.player.feature.player.extensions.noRippleClickable
import one.only.player.feature.player.extensions.seekByRequestedOffset
import one.only.player.feature.player.extensions.seekToRequestedPosition
import one.only.player.feature.player.input.PlayerKeyboardController
import one.only.player.feature.player.service.previewVideoFilters
import one.only.player.feature.player.state.ControlsVisibilityState
import one.only.player.feature.player.state.VerticalGesture
import one.only.player.feature.player.state.rememberBrightnessState
import one.only.player.feature.player.state.rememberControlsVisibilityState
import one.only.player.feature.player.state.rememberErrorState
import one.only.player.feature.player.state.rememberMediaPresentationState
import one.only.player.feature.player.state.rememberMetadataState
import one.only.player.feature.player.state.rememberPictureInPictureState
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
import one.only.player.feature.player.ui.DecoderPrioritySelectorContent
import one.only.player.feature.player.ui.DoubleTapIndicator
import one.only.player.feature.player.ui.LoopModeSelectorContent
import one.only.player.feature.player.ui.MenuOverlayView
import one.only.player.feature.player.ui.MenuRootContent
import one.only.player.feature.player.ui.MenuRoute
import one.only.player.feature.player.ui.OverlayShowView
import one.only.player.feature.player.ui.OverlayView
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
import one.only.player.feature.player.ui.controls.ControlsBottomModernView
import one.only.player.feature.player.ui.controls.ControlsBottomView
import one.only.player.feature.player.ui.controls.ControlsTopModernView
import one.only.player.feature.player.ui.controls.ControlsTopView
import one.only.player.feature.player.ui.controls.PlayerCustomizableControlButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "MediaPlayerScreen"
private const val AMBIENCE_ARTWORK_SAMPLE_SIZE = 32
private const val AMBIENCE_FRAME_CAPTURE_MAX_SIZE = 360
private const val AMBIENCE_FRAME_CAPTURE_PLAYING_INTERVAL_MS = 700L
private const val AMBIENCE_FRAME_CAPTURE_PAUSED_INTERVAL_MS = 1_500L
private const val AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS = 250L
private const val AMBIENCE_FRAME_CAPTURE_SEEK_HOLD_MS = 900L
private const val AMBIENCE_FRAME_CAPTURE_SEEK_DELTA_MS = 2_000L
private const val AMBIENCE_FRAME_CROSSFADE_MS = 600
private const val AMBIENCE_FRAME_NEAR_BLACK_AVERAGE_LUMA = 6f
private const val AMBIENCE_FRAME_NEAR_BLACK_MAX_LUMA = 18f
private const val AMBIENCE_VISIBLE_ALPHA_THRESHOLD = 16
private const val AMBIENCE_NEAR_BLACK_AVERAGE_LUMA = 10f
private const val AMBIENCE_NEAR_BLACK_MAX_LUMA = 28f
private const val AMBIENCE_NEAR_BLACK_AVERAGE_CHROMA = 8f
private const val AMBIENCE_NEAR_BLACK_BRIGHT_PIXEL_RATIO = 0.02f
private const val AMBIENCE_BRIGHT_LUMA = 42f

val LocalControlsVisibilityState = compositionLocalOf<ControlsVisibilityState?> { null }
val LocalPlayerIconStyle = compositionLocalOf { PlayerIconStyle.TONAL }

internal data class LongPressOverlayUiState(
    val speedText: String,
)

internal data class DraggingPlayerControlUiState(
    val control: PlayerControl,
    val sourceBounds: Rect,
    val dragOffset: Offset,
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
    )
    val pictureInPictureState = rememberPictureInPictureState(
        player = player,
        shouldAutoEnter = playerPreferences.shouldAutoEnterPip,
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
    val errorState = rememberErrorState(player = player)

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

    var overlayView by remember { mutableStateOf<OverlayView?>(null) }
    val isModern = playerPreferences.controlsStyle == PlayerControlsStyle.MODERN
    var menuRouteStack by remember { mutableStateOf<List<MenuRoute>>(emptyList()) }
    var isCustomizingControls by remember { mutableStateOf(false) }
    var customizingHiddenPlayerControls by remember { mutableStateOf(playerPreferences.hiddenPlayerControls) }
    var customizingPlayerControlsLayout by remember { mutableStateOf(playerPreferences.playerControlsLayout) }
    var draggingPlayerControlUiState by remember { mutableStateOf<DraggingPlayerControlUiState?>(null) }
    var previewPlayerControlsLayout by remember { mutableStateOf<PlayerControlsLayout?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val shouldShowPlayerTitle = configuration.orientation != Configuration.ORIENTATION_PORTRAIT
    val sleepTimerState = rememberSleepTimerState(player = player)
    val permanentlyVisibleControls = remember {
        setOf(
            PlayerControl.BACK,
            PlayerControl.PREVIOUS,
            PlayerControl.PLAY_PAUSE,
            PlayerControl.NEXT,
            PlayerControl.ROTATE,
        )
    }
    val hiddenPlayerControls = when (isCustomizingControls) {
        true -> customizingHiddenPlayerControls
        false -> playerPreferences.hiddenPlayerControls
    }
    val playerControlsLayout = when {
        isCustomizingControls -> previewPlayerControlsLayout ?: customizingPlayerControlsLayout
        else -> playerPreferences.playerControlsLayout
    }
    val controlsByZone = remember(playerControlsLayout) {
        PlayerControlZone.entries.associateWith(playerControlsLayout::controlsIn)
    }
    val topRightControls = controlsByZone.getValue(PlayerControlZone.TOP_RIGHT)
    val bottomLeftControls = controlsByZone.getValue(PlayerControlZone.BOTTOM_LEFT)
    val visiblePlayerControls = remember(hiddenPlayerControls) {
        PlayerControl.entries.toSet() - hiddenPlayerControls
    }
    var shouldShowOverlay by remember { mutableStateOf(false) }
    var videoFiltersInitialPreferences by remember { mutableStateOf<PlayerPreferences?>(null) }
    var subtitleStylePreviewPreferences by remember { mutableStateOf<PlayerPreferences?>(null) }
    var isAmbienceModeEnabled by remember { mutableStateOf(false) }
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
    fun overlayViewToMenuRoute(view: OverlayView): MenuRoute = when (view) {
        OverlayView.AUDIO_SELECTOR -> MenuRoute.Audio
        OverlayView.SUBTITLE_SELECTOR -> MenuRoute.Subtitle
        OverlayView.PLAYBACK_SPEED -> MenuRoute.PlaybackSpeed
        OverlayView.VIDEO_CONTENT_SCALE -> MenuRoute.VideoContentScale
        OverlayView.VIDEO_FILTERS -> MenuRoute.VideoFilters
        OverlayView.PLAYLIST -> MenuRoute.Playlist
        OverlayView.SLEEP_TIMER -> MenuRoute.SleepTimer
        OverlayView.DECODER_PRIORITY -> MenuRoute.Decoder
        OverlayView.PLAYBACK_MARKS -> MenuRoute.PlaybackMarks
        OverlayView.LOOP_MODE -> MenuRoute.LoopMode
        OverlayView.SHUFFLE_MODE -> MenuRoute.ShuffleMode
        OverlayView.CONTROL_LOCK -> MenuRoute.ControlLock
        OverlayView.MUTE -> MenuRoute.Mute
        OverlayView.AMBIENCE_MODE -> MenuRoute.AmbienceMode
        OverlayView.MIRROR_VIDEO -> MenuRoute.MirrorVideo
    }
    fun openOverlayPanel(target: OverlayView) {
        controlsVisibilityState.hideControls()
        if (isModern) {
            menuRouteStack = listOf(overlayViewToMenuRoute(target))
        } else {
            overlayView = target
        }
    }
    val showVideoFilters = {
        if (metadataState.isVideoEffectsAvailable) {
            videoFiltersInitialPreferences = playerPreferences
            openOverlayPanel(OverlayView.VIDEO_FILTERS)
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
        overlayView = null
        menuRouteStack = emptyList()
    }
    fun dismissOverlay() {
        if (overlayView == OverlayView.VIDEO_FILTERS || menuRouteStack.contains(MenuRoute.VideoFilters)) {
            restoreVideoFiltersPreview()
        }
        overlayView = null
        menuRouteStack = emptyList()
    }
    fun seekToPlaybackMark(mark: PlaybackMark) {
        player.seekToRequestedPosition(mark.positionMs)
        dismissOverlay()
        controlsVisibilityState.showControls()
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
        isAmbienceModeEnabled = isEnabled
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
    var longPressOverlayAnimationStep by remember { mutableIntStateOf(0) }
    val keyboardInteractionEnabledState = rememberUpdatedState(
        overlayView == null &&
            menuRouteStack.isEmpty() &&
            !isCustomizingControls &&
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
    val playerControlItemBounds = remember { mutableMapOf<PlayerControl, Rect>() }
    val playerControlZoneBounds = remember { mutableMapOf<PlayerControlZone, Rect>() }
    val longPressOverlayUiState = resolveLongPressOverlayUiState(
        isLongPressGestureInAction = tapGestureState.isLongPressGestureInAction,
        isDebugLongPressOverlayVisible = playerPreferences.isDebugLongPressOverlayVisible,
        longPressSpeed = tapGestureState.currentLongPressSpeed,
        shouldShowOverlay = shouldShowOverlay,
    )
    val shouldShowControlsScrim = controlsVisibilityState.isControlsVisible &&
        (isCustomizingControls || playerPreferences.shouldDimVideoWhenControlsVisible)

    LaunchedEffect(playerPreferences) {
        if (subtitleStylePreviewPreferences?.hasSameSubtitleStyle(playerPreferences) == true) {
            subtitleStylePreviewPreferences = null
        }
    }

    LaunchedEffect(
        playerPreferences.hiddenPlayerControls,
        playerPreferences.playerControlsLayout,
        isCustomizingControls,
    ) {
        if (!isCustomizingControls) {
            customizingHiddenPlayerControls = playerPreferences.hiddenPlayerControls - permanentlyVisibleControls
            customizingPlayerControlsLayout = playerPreferences.playerControlsLayout
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

    fun isControlVisible(control: PlayerControl): Boolean = control in permanentlyVisibleControls || isCustomizingControls || control !in hiddenPlayerControls

    fun isControlSelected(control: PlayerControl): Boolean = isCustomizingControls && control !in permanentlyVisibleControls && control !in hiddenPlayerControls

    fun toggleControlVisibility(control: PlayerControl) {
        val updatedControls = hiddenPlayerControls.toMutableSet().apply {
            if (!add(control)) remove(control)
        }
        if (isCustomizingControls) {
            customizingHiddenPlayerControls = updatedControls
            controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
        } else {
            controlsVisibilityState.showControls()
            viewModel.updatePlayerControlsCustomization(
                hiddenControls = updatedControls,
                layout = playerPreferences.playerControlsLayout,
            )
        }
    }

    fun startDraggingControl(control: PlayerControl) {
        if (!isCustomizingControls) return

        val sourceBounds = playerControlItemBounds[control] ?: return
        draggingPlayerControlUiState = DraggingPlayerControlUiState(
            control = control,
            sourceBounds = sourceBounds,
            dragOffset = Offset.Zero,
        )
        previewPlayerControlsLayout = customizingPlayerControlsLayout
        controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
    }

    fun moveDraggingControl(
        control: PlayerControl,
        dragOffset: Offset,
    ) {
        if (!isCustomizingControls) return
        val draggingState = draggingPlayerControlUiState ?: return
        if (draggingState.control != control) return

        draggingPlayerControlUiState = draggingState.copy(dragOffset = dragOffset)
        previewPlayerControlsLayout = customizingPlayerControlsLayout.previewReorder(
            control = control,
            dropPosition = draggingState.sourceBounds.center + dragOffset,
            itemBounds = playerControlItemBounds,
        )
        controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
    }

    fun clearDraggingControl() {
        draggingPlayerControlUiState = null
        previewPlayerControlsLayout = null
        controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
    }

    fun dropDraggedControl(
        control: PlayerControl,
        dragOffset: Offset,
    ) {
        if (!isCustomizingControls) return

        val dropPosition = draggingPlayerControlUiState
            ?.takeIf { it.control == control }
            ?.sourceBounds
            ?.center
            ?.plus(dragOffset)
        val updatedLayout = when (dropPosition) {
            null -> customizingPlayerControlsLayout.dropDraggedControl(
                control = control,
                dragOffset = dragOffset,
                itemBounds = playerControlItemBounds,
                zoneBounds = playerControlZoneBounds,
            )

            else -> customizingPlayerControlsLayout.dropControl(
                control = control,
                dropPosition = dropPosition,
                itemBounds = playerControlItemBounds,
                zoneBounds = playerControlZoneBounds,
            )
        }
        // 清除被拖控件的旧位置记录，避免 recomposition 时触发多余的 reflow 动画
        playerControlItemBounds.remove(control)
        customizingPlayerControlsLayout = updatedLayout
        clearDraggingControl()
        controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
    }

    fun enterControlCustomization() {
        player.pause()
        customizingHiddenPlayerControls = playerPreferences.hiddenPlayerControls - permanentlyVisibleControls
        customizingPlayerControlsLayout = playerPreferences.playerControlsLayout
        clearDraggingControl()
        isCustomizingControls = true
        controlsVisibilityState.showControls(duration = kotlin.time.Duration.INFINITE)
    }

    fun exitControlCustomization() {
        clearDraggingControl()
        isCustomizingControls = false
        controlsVisibilityState.showControls()
        viewModel.updatePlayerControlsCustomization(
            hiddenControls = customizingHiddenPlayerControls,
            layout = customizingPlayerControlsLayout,
        )
    }

    fun cancelControlCustomization() {
        clearDraggingControl()
        customizingHiddenPlayerControls = playerPreferences.hiddenPlayerControls - permanentlyVisibleControls
        customizingPlayerControlsLayout = playerPreferences.playerControlsLayout
        isCustomizingControls = false
        controlsVisibilityState.showControls()
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

    fun markPositionFrom(extras: android.os.Bundle?): Long? = extras.longValue("position_ms") ?: extras.longValue("value")

    fun handleDebugPlayerAction(action: String, extras: android.os.Bundle?): Boolean {
        if (isCustomizingControls && action != PlayerDebugCommandBridge.ACTION_TOGGLE_CUSTOMIZE_CONTROLS) return false
        when (action) {
            PlayerDebugCommandBridge.ACTION_BACK -> onBackClick()

            PlayerDebugCommandBridge.ACTION_ROTATE -> rotationState.rotate()

            PlayerDebugCommandBridge.ACTION_TOGGLE_AMBIENCE -> toggleAmbienceMode()

            PlayerDebugCommandBridge.ACTION_TOGGLE_MIRROR -> isVideoMirrored = !isVideoMirrored

            PlayerDebugCommandBridge.ACTION_SHOW_CONTROLS -> controlsVisibilityState.showControls()

            PlayerDebugCommandBridge.ACTION_HIDE_CONTROLS -> controlsVisibilityState.hideControls()

            PlayerDebugCommandBridge.ACTION_SHOW_PLAYLIST -> openOverlayPanel(OverlayView.PLAYLIST)

            PlayerDebugCommandBridge.ACTION_SHOW_SPEED -> openOverlayPanel(OverlayView.PLAYBACK_SPEED)

            PlayerDebugCommandBridge.ACTION_SHOW_AUDIO -> openOverlayPanel(OverlayView.AUDIO_SELECTOR)

            PlayerDebugCommandBridge.ACTION_SHOW_SUBTITLE -> openOverlayPanel(OverlayView.SUBTITLE_SELECTOR)

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

            PlayerDebugCommandBridge.ACTION_SHOW_SCALE -> openOverlayPanel(OverlayView.VIDEO_CONTENT_SCALE)

            PlayerDebugCommandBridge.ACTION_SHOW_DECODER -> openOverlayPanel(OverlayView.DECODER_PRIORITY)

            PlayerDebugCommandBridge.ACTION_SHOW_VIDEO_FILTERS -> showVideoFilters()

            PlayerDebugCommandBridge.ACTION_PIP -> {
                if (!pictureInPictureState.hasPipPermission) {
                    pictureInPictureState.openPictureInPictureSettings()
                } else {
                    pictureInPictureState.enterPictureInPictureMode()
                }
            }

            PlayerDebugCommandBridge.ACTION_SCREENSHOT -> onScreenshotClick()

            PlayerDebugCommandBridge.ACTION_BACKGROUND -> onPlayInBackgroundClick()

            PlayerDebugCommandBridge.ACTION_SHOW_SLEEP_TIMER -> openOverlayPanel(OverlayView.SLEEP_TIMER)

            PlayerDebugCommandBridge.ACTION_SHOW_MARKS -> openOverlayPanel(OverlayView.PLAYBACK_MARKS)

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
                if (isModern) {
                    controlsVisibilityState.hideControls()
                    menuRouteStack = listOf(MenuRoute.Root)
                }
            }

            PlayerDebugCommandBridge.ACTION_MENU_BACK -> {
                if (menuRouteStack.size > 1) {
                    popMenuRoute()
                } else {
                    dismissOverlay()
                }
            }

            PlayerDebugCommandBridge.ACTION_TOGGLE_CUSTOMIZE_CONTROLS -> {
                if (isModern) return false
                if (isCustomizingControls) exitControlCustomization() else enterControlCustomization()
            }

            PlayerDebugCommandBridge.ACTION_STRESS_PAN_ZOOM -> {
                stressPanZoom(extras)
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
        LocalPlayerIconStyle provides playerPreferences.playerIconStyle,
    ) {
        Box {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
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
                if (isAmbienceModeEnabled) {
                    AmbienceBackground(
                        artworkData = metadataState.artworkData,
                        artworkUri = metadataState.artworkUri,
                        mediaKey = player.currentMediaItem?.mediaId,
                        videoViewRect = pictureInPictureState.videoViewRect,
                        hasRenderedFirstFrame = mediaPresentationState.hasRenderedFirstFrame,
                        isPlaying = mediaPresentationState.isPlaying,
                        isBuffering = mediaPresentationState.isBuffering,
                        positionMs = mediaPresentationState.position,
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
                    isGesturesEnabled = !isCustomizingControls,
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
                        externalSubtitleFontSource = externalSubtitleFontSource,
                    ),
                    decoderPriority = playerPreferences.decoderPriority,
                    shouldUseTextureView = isVideoMirrored,
                    isVideoMirrored = isVideoMirrored,
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
                                Color.Black.copy(
                                    alpha = when (isCustomizingControls) {
                                        true -> 0.75f
                                        false -> 0.3f
                                    },
                                ),
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
                                if (isModern) {
                                    ControlsTopModernView(
                                        title = (metadataState.title ?: "").takeIf { shouldShowPlayerTitle }.orEmpty(),
                                        onBackClick = { onBackClick() },
                                        onMenuClick = {
                                            controlsVisibilityState.hideControls()
                                            menuRouteStack = listOf(MenuRoute.Root)
                                        },
                                    )
                                } else {
                                    ControlsTopView(
                                        title = (metadataState.title ?: "").takeIf { shouldShowPlayerTitle }.orEmpty(),
                                        player = player,
                                        topRightControls = topRightControls,
                                        controlButtonsPosition = playerPreferences.controlButtonsPosition,
                                        visiblePlayerControls = visiblePlayerControls,
                                        videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                        isPipSupported = pictureInPictureState.isPipSupported,
                                        isTakingScreenshot = isTakingScreenshot,
                                        itemBounds = playerControlItemBounds,
                                        zoneBounds = playerControlZoneBounds,
                                        isCustomizingControls = isCustomizingControls,
                                        shouldHideLabels = playerPreferences.shouldHidePlayerControlLabels,
                                        draggingControl = draggingPlayerControlUiState?.control,
                                        onControlDropDragged = ::dropDraggedControl,
                                        onControlDragStarted = ::startDraggingControl,
                                        onControlDragMoved = ::moveDraggingControl,
                                        onControlDragCancelled = { clearDraggingControl() },
                                        isBackVisible = isControlVisible(PlayerControl.BACK),
                                        isBackSelected = isControlSelected(PlayerControl.BACK),
                                        isBackInteractive = !isCustomizingControls,
                                        onAudioClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.AUDIO)
                                            } else {
                                                openOverlayPanel(OverlayView.AUDIO_SELECTOR)
                                            }
                                        },
                                        onSubtitleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SUBTITLE)
                                            } else {
                                                openOverlayPanel(OverlayView.SUBTITLE_SELECTOR)
                                            }
                                        },
                                        onPlaybackSpeedClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PLAYBACK_SPEED)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYBACK_SPEED)
                                            }
                                        },
                                        onPlaylistClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PLAYLIST)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYLIST)
                                            }
                                        },
                                        onBackClick = {
                                            if (!isCustomizingControls) {
                                                onBackClick()
                                            }
                                        },
                                        onSleepTimerClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SLEEP_TIMER)
                                            } else {
                                                openOverlayPanel(OverlayView.SLEEP_TIMER)
                                            }
                                        },
                                        onLockControlsClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.LOCK)
                                            } else {
                                                controlsVisibilityState.showControls()
                                                controlsVisibilityState.lockControls()
                                            }
                                        },
                                        isMuted = volumeState.isMuted,
                                        onMuteClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.MUTE)
                                            } else {
                                                volumeState.toggleMute()
                                            }
                                        },
                                        onPlaybackMarksClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.MARK)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYBACK_MARKS)
                                            }
                                        },
                                        onVideoContentScaleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SCALE)
                                            } else {
                                                openOverlayPanel(OverlayView.VIDEO_CONTENT_SCALE)
                                            }
                                        },
                                        onVideoContentScaleLongClick = {
                                            if (!isCustomizingControls) {
                                                openOverlayPanel(OverlayView.VIDEO_CONTENT_SCALE)
                                            }
                                        },
                                        onDecoderClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.DECODER)
                                            } else {
                                                openOverlayPanel(OverlayView.DECODER_PRIORITY)
                                            }
                                        },
                                        onAmbienceModeClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.AMBIENCE_MODE)
                                            } else {
                                                toggleAmbienceMode()
                                            }
                                        },
                                        isAmbienceModeEnabled = isAmbienceModeEnabled,
                                        onVideoFiltersClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.VIDEO_FILTERS)
                                            } else {
                                                showVideoFilters()
                                            }
                                        },
                                        onPictureInPictureClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PIP)
                                            } else if (!pictureInPictureState.hasPipPermission) {
                                                Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                                pictureInPictureState.openPictureInPictureSettings()
                                            } else {
                                                pictureInPictureState.enterPictureInPictureMode()
                                            }
                                        },
                                        onRotateClick = {
                                            rotationState.rotate()
                                        },
                                        onScreenshotClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SCREENSHOT)
                                            } else {
                                                onScreenshotClick()
                                            }
                                        },
                                        onPlayInBackgroundClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.BACKGROUND_PLAY)
                                            } else {
                                                onPlayInBackgroundClick()
                                            }
                                        },
                                        onLoopClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.LOOP)
                                            } else {
                                                openOverlayPanel(OverlayView.LOOP_MODE)
                                            }
                                        },
                                        onShuffleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SHUFFLE)
                                            } else {
                                                openOverlayPanel(OverlayView.SHUFFLE_MODE)
                                            }
                                        },
                                        sleepTimerState = sleepTimerState,
                                    )
                                }
                            }
                        },
                        middleView = {
                            when {
                                seekGestureState.seekAmount != null -> InfoView(info = "${seekGestureState.seekAmountFormatted}\n[${seekGestureState.seekToPositionFormated}]")

                                videoZoomAndContentScaleState.isZooming -> InfoView(info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%")

                                videoZoomAndContentScaleState.shouldShowContentScaleIndicator -> InfoView(info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()))

                                !isModern && controlsVisibilityState.isControlsVisible -> ControlsMiddleView(
                                    player = player,
                                    isCustomizingControls = isCustomizingControls,
                                    isPreviousVisible = isControlVisible(PlayerControl.PREVIOUS),
                                    isPreviousSelected = isControlSelected(PlayerControl.PREVIOUS),
                                    isPlayPauseVisible = isControlVisible(PlayerControl.PLAY_PAUSE),
                                    isPlayPauseSelected = isControlSelected(PlayerControl.PLAY_PAUSE),
                                    isNextVisible = isControlVisible(PlayerControl.NEXT),
                                    isNextSelected = isControlSelected(PlayerControl.NEXT),
                                    onPreviousClick = { },
                                    onPlayPauseClick = { },
                                    onNextClick = { },
                                )

                                else -> Unit
                            }
                        },
                        bottomView = {
                            AnimatedVisibility(
                                visible = controlsVisibilityState.isControlsVisible && !controlsVisibilityState.isControlsLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                if (isModern) {
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
                                        onRotateClick = { rotationState.rotate() },
                                        onPlaylistClick = { openOverlayPanel(OverlayView.PLAYLIST) },
                                        onPlaybackSpeedClick = { openOverlayPanel(OverlayView.PLAYBACK_SPEED) },
                                        onSeek = seekGestureState::onSeek,
                                        onSeekEnd = seekGestureState::onSeekEnd,
                                    )
                                } else {
                                    ControlsBottomView(
                                        player = player,
                                        mediaPresentationState = mediaPresentationState,
                                        bottomLeftControls = bottomLeftControls,
                                        controlButtonsPosition = playerPreferences.controlButtonsPosition,
                                        videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                        isPipSupported = pictureInPictureState.isPipSupported,
                                        pendingSeekPosition = seekGestureState.pendingSeekPosition,
                                        itemBounds = playerControlItemBounds,
                                        zoneBounds = playerControlZoneBounds,
                                        isCustomizingControls = isCustomizingControls,
                                        shouldHideLabels = playerPreferences.shouldHidePlayerControlLabels,
                                        draggingControl = draggingPlayerControlUiState?.control,
                                        onControlDropDragged = ::dropDraggedControl,
                                        onControlDragStarted = ::startDraggingControl,
                                        onControlDragMoved = ::moveDraggingControl,
                                        onControlDragCancelled = { clearDraggingControl() },
                                        visiblePlayerControls = visiblePlayerControls,
                                        onSeek = seekGestureState::onSeek,
                                        onSeekEnd = seekGestureState::onSeekEnd,
                                        onPlaylistClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PLAYLIST)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYLIST)
                                            }
                                        },
                                        onPlaybackSpeedClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PLAYBACK_SPEED)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYBACK_SPEED)
                                            }
                                        },
                                        onAudioClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.AUDIO)
                                            } else {
                                                openOverlayPanel(OverlayView.AUDIO_SELECTOR)
                                            }
                                        },
                                        onSubtitleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SUBTITLE)
                                            } else {
                                                openOverlayPanel(OverlayView.SUBTITLE_SELECTOR)
                                            }
                                        },
                                        onRotateClick = {
                                            rotationState.rotate()
                                        },
                                        onPlayInBackgroundClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.BACKGROUND_PLAY)
                                            } else {
                                                onPlayInBackgroundClick()
                                            }
                                        },
                                        isTakingScreenshot = isTakingScreenshot,
                                        onScreenshotClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SCREENSHOT)
                                            } else {
                                                onScreenshotClick()
                                            }
                                        },
                                        onCustomizeControlsClick = {
                                            if (isCustomizingControls) {
                                                exitControlCustomization()
                                            } else {
                                                enterControlCustomization()
                                            }
                                        },
                                        onLoopClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.LOOP)
                                            } else {
                                                openOverlayPanel(OverlayView.LOOP_MODE)
                                            }
                                        },
                                        onShuffleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SHUFFLE)
                                            } else {
                                                openOverlayPanel(OverlayView.SHUFFLE_MODE)
                                            }
                                        },
                                        onSleepTimerClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SLEEP_TIMER)
                                            } else {
                                                openOverlayPanel(OverlayView.SLEEP_TIMER)
                                            }
                                        },
                                        sleepTimerState = sleepTimerState,
                                        onLockControlsClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.LOCK)
                                            } else {
                                                controlsVisibilityState.showControls()
                                                controlsVisibilityState.lockControls()
                                            }
                                        },
                                        isMuted = volumeState.isMuted,
                                        onMuteClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.MUTE)
                                            } else {
                                                volumeState.toggleMute()
                                            }
                                        },
                                        onPlaybackMarksClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.MARK)
                                            } else {
                                                openOverlayPanel(OverlayView.PLAYBACK_MARKS)
                                            }
                                        },
                                        onVideoContentScaleClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.SCALE)
                                            } else {
                                                openOverlayPanel(OverlayView.VIDEO_CONTENT_SCALE)
                                            }
                                        },
                                        onVideoContentScaleLongClick = {
                                            if (!isCustomizingControls) {
                                                openOverlayPanel(OverlayView.VIDEO_CONTENT_SCALE)
                                            }
                                        },
                                        onDecoderClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.DECODER)
                                            } else {
                                                openOverlayPanel(OverlayView.DECODER_PRIORITY)
                                            }
                                        },
                                        onAmbienceModeClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.AMBIENCE_MODE)
                                            } else {
                                                toggleAmbienceMode()
                                            }
                                        },
                                        isAmbienceModeEnabled = isAmbienceModeEnabled,
                                        onVideoFiltersClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.VIDEO_FILTERS)
                                            } else {
                                                showVideoFilters()
                                            }
                                        },
                                        onPictureInPictureClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.PIP)
                                            } else if (!pictureInPictureState.hasPipPermission) {
                                                Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                                pictureInPictureState.openPictureInPictureSettings()
                                            } else {
                                                pictureInPictureState.enterPictureInPictureMode()
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )

                    draggingPlayerControlUiState?.let { draggingState ->
                        Box(
                            modifier = Modifier
                                .offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        x = (draggingState.sourceBounds.left + draggingState.dragOffset.x).toInt(),
                                        y = (draggingState.sourceBounds.top + draggingState.dragOffset.y).toInt(),
                                    )
                                }
                                .shadow(16.dp, RoundedCornerShape(16.dp)),
                        ) {
                            PlayerCustomizableControlButton(
                                control = draggingState.control,
                                player = player,
                                videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                                isPipSupported = pictureInPictureState.isPipSupported,
                                isCustomizingControls = true,
                                visiblePlayerControls = visiblePlayerControls,
                                onPlaylistClick = { },
                                onPlaybackSpeedClick = { },
                                onAudioClick = { },
                                onSubtitleClick = { },
                                onLockControlsClick = { },
                                isMuted = volumeState.isMuted,
                                onMuteClick = { },
                                onPlaybackMarksClick = { },
                                onVideoContentScaleClick = { },
                                onVideoContentScaleLongClick = { },
                                onDecoderClick = { },
                                onAmbienceModeClick = { },
                                isAmbienceModeEnabled = isAmbienceModeEnabled,
                                onVideoFiltersClick = { },
                                onPictureInPictureClick = { },
                                onRotateClick = { },
                                isTakingScreenshot = isTakingScreenshot,
                                onScreenshotClick = { },
                                onPlayInBackgroundClick = { },
                                onLoopClick = { },
                                onShuffleClick = { },
                                onSleepTimerClick = { },
                            )
                        }
                    }
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

                    AnimatedVisibility(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 132.dp),
                        visible = isCustomizingControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            FilledTonalButton(
                                modifier = Modifier.testTag("btn_customize_controls_confirm"),
                                onClick = ::exitControlCustomization,
                            ) {
                                Text(text = stringResource(coreUiR.string.done))
                            }
                            TextButton(
                                modifier = Modifier.testTag("btn_customize_controls_cancel"),
                                onClick = ::cancelControlCustomization,
                            ) {
                                Text(text = stringResource(coreUiR.string.cancel))
                            }
                        }
                    }
                }
            }

            if (isModern) {
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
                    title = titleForMenuRoute(currentRoute),
                    canGoBack = canGoBack,
                    onBack = {
                        if (canGoBack) popMenuRoute() else dismissOverlay()
                    },
                ) { route ->
                    when (route) {
                        MenuRoute.Root -> MenuRootContent(
                            isPipSupported = pictureInPictureState.isPipSupported,
                            isTakingScreenshot = isTakingScreenshot,
                            onNavigate = ::navigateToMenuRoute,
                            onPictureInPictureClick = {
                                if (!pictureInPictureState.hasPipPermission) {
                                    Toast.makeText(context, coreUiR.string.enable_pip_from_settings, Toast.LENGTH_SHORT).show()
                                    pictureInPictureState.openPictureInPictureSettings()
                                } else {
                                    pictureInPictureState.enterPictureInPictureMode()
                                }
                                dismissOverlay()
                            },
                            onScreenshotClick = {
                                onScreenshotClick()
                                dismissOverlay()
                            },
                            onPlayInBackgroundClick = {
                                onPlayInBackgroundClick()
                                dismissOverlay()
                            },
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
                            onConfirmPreferences = viewModel::updateVideoFilters,
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
            } else {
                OverlayShowView(
                    player = player,
                    overlayView = overlayView,
                    videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                    isCustomVideoZoomActive = !videoZoomAndContentScaleState.zoom.isDefaultVideoZoom(),
                    playerPreferences = activePlayerPreferences,
                    sleepTimerState = sleepTimerState,
                    isControlLockEnabled = controlsVisibilityState.isControlsLocked,
                    isMuted = volumeState.isMuted,
                    isAmbienceModeEnabled = isAmbienceModeEnabled,
                    isVideoMirrored = isVideoMirrored,
                    onDismiss = ::dismissOverlay,
                    onSelectSubtitleClick = onSelectSubtitleClick,
                    onAddOnlineSubtitleClick = onAddOnlineSubtitleClick,
                    onSubtitleOptionEvent = viewModel::onSubtitleOptionEvent,
                    onSubtitleStyleChanged = ::updateSubtitleStyle,
                    onVideoContentScaleChanged = { videoZoomAndContentScaleState.onVideoContentScaleChanged(it) },
                    onPreviewVideoFilters = { previewPreferences ->
                        (player as? androidx.media3.session.MediaController)?.previewVideoFilters(previewPreferences)
                    },
                    onConfirmVideoFilters = viewModel::updateVideoFilters,
                    onCloseVideoFilters = ::closeVideoFiltersOverlay,
                    onShowVideoFilters = {
                        overlayView = null
                        showVideoFilters()
                    },
                    onDecoderPriorityChanged = {
                        viewModel.updateDecoderPriority(it)
                        dismissOverlay()
                    },
                    playbackMarks = playbackMarks,
                    onAddPlaybackMarkClick = ::addPlaybackMark,
                    onPlaybackMarkClick = ::seekToPlaybackMark,
                    onDeletePlaybackMarkClick = { mark -> viewModel.deletePlaybackMark(mark.id) },
                    onControlLockChanged = ::setControlsLocked,
                    onMuteChanged = ::setMuted,
                    onAmbienceModeChanged = { isEnabled -> setAmbienceModeEnabled(isEnabled) },
                    onVideoMirroredChanged = ::setVideoMirrored,
                )
            }
        }
    }

    errorState.error?.let { error ->
        NextDialog(
            onDismissRequest = { },
            title = {
                MiuixText(
                    text = stringResource(coreUiR.string.error_playing_video),
                    style = MiuixTheme.textStyles.title4,
                )
            },
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
            overlayView != null -> dismissOverlay()
            isCustomizingControls -> cancelControlCustomization()
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
    subtitleBottomPaddingFraction == other.subtitleBottomPaddingFraction

private fun Float.isDefaultVideoZoom(): Boolean = kotlin.math.abs(this - 1f) < 0.0001f

@Composable
private fun titleForMenuRoute(route: MenuRoute?): String = when (route) {
    null, MenuRoute.Root -> stringResource(coreUiR.string.menu)
    MenuRoute.ControlLock -> stringResource(coreUiR.string.controls_lock_switch)
    MenuRoute.Mute -> stringResource(coreUiR.string.mute_switch)
    MenuRoute.AmbienceMode -> stringResource(coreUiR.string.ambience_mode)
    MenuRoute.MirrorVideo -> stringResource(coreUiR.string.mirror_video)
    MenuRoute.Audio -> stringResource(coreUiR.string.select_audio_track)
    MenuRoute.Subtitle -> stringResource(coreUiR.string.select_subtitle_track)
    MenuRoute.PlaybackSpeed -> stringResource(coreUiR.string.select_playback_speed)
    MenuRoute.VideoContentScale -> stringResource(coreUiR.string.video_zoom)
    MenuRoute.VideoFilters -> stringResource(coreUiR.string.video_filters)
    MenuRoute.Playlist -> stringResource(coreUiR.string.now_playing)
    MenuRoute.SleepTimer -> stringResource(coreUiR.string.sleep_timer)
    MenuRoute.Decoder -> stringResource(coreUiR.string.decoder_priority)
    MenuRoute.PlaybackMarks -> stringResource(coreUiR.string.playback_marks)
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
            style = textStyle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AmbienceBackground(
    artworkData: ByteArray?,
    artworkUri: Uri?,
    mediaKey: String?,
    videoViewRect: AndroidRect?,
    hasRenderedFirstFrame: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val rootView = view.rootView
    val window = remember(context) { context.findActivity()?.window }
    val captureRect = remember(videoViewRect, rootView.width, rootView.height) {
        videoViewRect?.coerceToWindowBounds(rootView)
    }
    val currentIsBuffering = rememberUpdatedState(isBuffering)
    val currentPositionMs = rememberUpdatedState(positionMs)
    var frameImage by remember(mediaKey) { mutableStateOf<ImageBitmap?>(null) }
    var previousPositionMs by remember(mediaKey) { mutableLongStateOf(positionMs) }
    var captureHoldUntilMs by remember(mediaKey) { mutableLongStateOf(0L) }
    val currentCaptureHoldUntilMs = rememberUpdatedState(captureHoldUntilMs)
    val shouldUseFallbackArtwork = remember(artworkData) {
        artworkData?.isNearBlackAmbienceArtwork() == true
    }
    val model = when {
        artworkData != null && !shouldUseFallbackArtwork -> artworkData
        shouldUseFallbackArtwork -> R.drawable.artwork_default
        artworkUri != null -> artworkUri
        else -> R.drawable.artwork_default
    }

    DisposableEffect(mediaKey) {
        Logger.info(TAG, "Ambience background mounted media=$mediaKey")
        onDispose {
            Logger.info(TAG, "Ambience background disposed media=$mediaKey hasImage=${frameImage != null}")
        }
    }

    LaunchedEffect(positionMs) {
        val previous = previousPositionMs
        previousPositionMs = positionMs

        val deltaMs = abs(positionMs - previous)
        if (previous <= 0L || deltaMs < AMBIENCE_FRAME_CAPTURE_SEEK_DELTA_MS) return@LaunchedEffect

        captureHoldUntilMs = SystemClock.elapsedRealtime() + AMBIENCE_FRAME_CAPTURE_SEEK_HOLD_MS
        Logger.info(
            TAG,
            "Ambience capture hold after seek media=$mediaKey fromMs=$previous toMs=$positionMs deltaMs=$deltaMs holdMs=$AMBIENCE_FRAME_CAPTURE_SEEK_HOLD_MS keepImage=${frameImage != null}",
        )
    }

    LaunchedEffect(rootView, window, captureRect, hasRenderedFirstFrame, isPlaying) {
        Logger.info(
            TAG,
            "Ambience capture effect start media=$mediaKey rect=${captureRect?.ambienceDebugString()} hasFrame=$hasRenderedFirstFrame playing=$isPlaying buffering=${currentIsBuffering.value} hasImage=${frameImage != null} root=${rootView.width}x${rootView.height}",
        )
        if (captureRect == null || !hasRenderedFirstFrame) {
            Logger.info(
                TAG,
                "Ambience capture waiting media=$mediaKey window=${window != null} rect=${captureRect?.ambienceDebugString()} hasFrame=$hasRenderedFirstFrame keepImage=${frameImage != null}",
            )
            return@LaunchedEffect
        }

        var captureCount = 0
        var didLogBuffering = false
        var didLogHolding = false
        var shouldLogNextSuccess = true
        while (true) {
            if (currentIsBuffering.value) {
                if (!didLogBuffering) {
                    Logger.info(
                        TAG,
                        "Ambience capture paused for buffering media=$mediaKey rect=${captureRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameImage != null}",
                    )
                }
                didLogBuffering = true
                shouldLogNextSuccess = true
                delay(AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS)
                continue
            }

            didLogBuffering = false
            val holdRemainingMs = currentCaptureHoldUntilMs.value - SystemClock.elapsedRealtime()
            if (holdRemainingMs > 0L) {
                if (!didLogHolding) {
                    Logger.info(
                        TAG,
                        "Ambience capture paused after seek media=$mediaKey remainMs=$holdRemainingMs rect=${captureRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameImage != null}",
                    )
                }
                didLogHolding = true
                shouldLogNextSuccess = true
                delay(minOf(holdRemainingMs, AMBIENCE_FRAME_CAPTURE_BUFFERING_RETRY_MS))
                continue
            }

            didLogHolding = false
            captureCount++
            when (
                val result = capturePlayerFrame(
                    rootView = rootView,
                    window = window,
                    sourceRect = captureRect,
                )
            ) {
                is AmbienceFrameCaptureResult.Success -> {
                    frameImage = result.image
                    if (shouldLogNextSuccess || captureCount % 10 == 0) {
                        Logger.info(
                            TAG,
                            "Ambience capture success media=$mediaKey count=$captureCount source=${result.sourceDebug} rect=${captureRect.ambienceDebugString()} capture=${result.size.width}x${result.size.height} avgLuma=${result.luma.average} maxLuma=${result.luma.max} visible=${result.luma.visiblePixelCount} positionMs=${currentPositionMs.value}",
                        )
                    }
                    shouldLogNextSuccess = false
                }

                is AmbienceFrameCaptureResult.Failure -> {
                    shouldLogNextSuccess = true
                    Logger.info(
                        TAG,
                        "Ambience capture skipped media=$mediaKey count=$captureCount reason=${result.reason} source=${result.sourceDebug} pixelCopy=${result.pixelCopyResult} avgLuma=${result.luma?.average} maxLuma=${result.luma?.max} visible=${result.luma?.visiblePixelCount} rect=${captureRect.ambienceDebugString()} positionMs=${currentPositionMs.value} keepImage=${frameImage != null}",
                    )
                }
            }
            delay(
                if (isPlaying) {
                    AMBIENCE_FRAME_CAPTURE_PLAYING_INTERVAL_MS
                } else {
                    AMBIENCE_FRAME_CAPTURE_PAUSED_INTERVAL_MS
                },
            )
        }
    }

    Crossfade(
        targetState = frameImage,
        animationSpec = tween(durationMillis = AMBIENCE_FRAME_CROSSFADE_MS),
        label = "AmbienceFrameCrossfade",
    ) { currentFrameImage ->
        if (currentFrameImage != null) {
            Image(
                bitmap = currentFrameImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .fillMaxSize()
                    .blur(48.dp),
                alpha = 0.9f,
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.artwork_default),
                error = painterResource(R.drawable.artwork_default),
                modifier = modifier
                    .fillMaxSize()
                    .blur(48.dp),
                alpha = 0.9f,
            )
        }
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
                        } else if (captureResult is AmbienceFrameCaptureResult.Success) {
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
                        } else if (captureResult is AmbienceFrameCaptureResult.Success) {
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
        val image: ImageBitmap,
        val size: AmbienceCaptureSize,
        val luma: AmbienceFrameLuma,
        val sourceDebug: String,
    ) : AmbienceFrameCaptureResult

    data class Failure(
        val reason: String,
        val sourceDebug: String,
        val pixelCopyResult: Int? = null,
        val luma: AmbienceFrameLuma? = null,
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
    if (luma.isNearBlackAmbienceFrame()) {
        recycle()
        return AmbienceFrameCaptureResult.Failure(
            reason = "near_black_frame",
            sourceDebug = sourceDebug,
            luma = luma,
        )
    }

    return AmbienceFrameCaptureResult.Success(
        image = asImageBitmap(),
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

private fun ByteArray.isNearBlackAmbienceArtwork(): Boolean {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(this, 0, size, boundsOptions)
    val width = boundsOptions.outWidth
    val height = boundsOptions.outHeight
    if (width <= 0 || height <= 0) return false

    val bitmap = BitmapFactory.decodeByteArray(
        this,
        0,
        size,
        BitmapFactory.Options().apply {
            inSampleSize = ambienceArtworkSampleSize(width, height)
        },
    ) ?: return false

    return try {
        var visiblePixelCount = 0
        var brightPixelCount = 0
        var totalLuma = 0f
        var totalChroma = 0f
        var maxLuma = 0f
        val pixels = IntArray(bitmap.width)

        for (y in 0 until bitmap.height) {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in pixels) {
                val alpha = pixel ushr 24
                if (alpha <= AMBIENCE_VISIBLE_ALPHA_THRESHOLD) continue

                val red = pixel shr 16 and 0xff
                val green = pixel shr 8 and 0xff
                val blue = pixel and 0xff
                val luma = red * 0.299f + green * 0.587f + blue * 0.114f
                val chroma = maxOf(red, green, blue) - minOf(red, green, blue)

                visiblePixelCount++
                totalLuma += luma
                totalChroma += chroma
                maxLuma = maxOf(maxLuma, luma)
                if (luma >= AMBIENCE_BRIGHT_LUMA) brightPixelCount++
            }
        }

        if (visiblePixelCount == 0) return true

        val averageLuma = totalLuma / visiblePixelCount
        val averageChroma = totalChroma / visiblePixelCount
        val brightPixelRatio = brightPixelCount.toFloat() / visiblePixelCount
        averageLuma <= AMBIENCE_NEAR_BLACK_AVERAGE_LUMA &&
            averageChroma <= AMBIENCE_NEAR_BLACK_AVERAGE_CHROMA &&
            (maxLuma <= AMBIENCE_NEAR_BLACK_MAX_LUMA || brightPixelRatio <= AMBIENCE_NEAR_BLACK_BRIGHT_PIXEL_RATIO)
    } finally {
        bitmap.recycle()
    }
}

private fun ambienceArtworkSampleSize(
    width: Int,
    height: Int,
): Int {
    var sampleSize = 1
    while (width / sampleSize > AMBIENCE_ARTWORK_SAMPLE_SIZE || height / sampleSize > AMBIENCE_ARTWORK_SAMPLE_SIZE) {
        sampleSize *= 2
    }
    return sampleSize
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPreviousVisible) {
            if (isCustomizingControls) {
                PreviousButton(
                    player = player,
                    onClick = onPreviousClick,
                    isInteractive = false,
                )
            } else {
                PreviousButton(player = player)
            }
        }
        if (isPlayPauseVisible) {
            if (isCustomizingControls) {
                PlayPauseButton(
                    player = player,
                    onClick = onPlayPauseClick,
                    isInteractive = false,
                )
            } else {
                PlayPauseButton(player = player)
            }
        }
        if (isNextVisible) {
            if (isCustomizingControls) {
                NextButton(
                    player = player,
                    onClick = onNextClick,
                    isInteractive = false,
                )
            } else {
                NextButton(player = player)
            }
        }
    }
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
