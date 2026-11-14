package one.only.player.feature.player.service.pip

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.hypot
import kotlin.math.roundToInt
import one.only.player.core.common.Logger
import one.only.player.core.ui.R as coreUiR
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.player.R

@OptIn(UnstableApi::class)
internal class CustomPictureInPictureOverlay(
    private val context: Context,
    private val onStopPlayback: () -> Unit,
    private val isDarkTheme: () -> Boolean,
) {
    companion object {
        private const val TAG = "CustomPipOverlay"
        private const val WINDOW_ASPECT = 16f / 9f
        private const val WINDOW_WIDTH_DP = 280
        private const val WINDOW_MIN_WIDTH_DP = 176
        private const val WINDOW_MARGIN_DP = 12
        private const val WINDOW_CORNER_RADIUS_DP = 16
        private const val CONTROLS_HIDE_DELAY_MS = 2800L
        private const val BUTTON_SIZE_DP = 32
        private const val PLAY_BUTTON_SIZE_DP = 36
        private const val DARK_WINDOW_COLOR = 0xFF1E1E22.toInt()
        private const val LIGHT_WINDOW_COLOR = 0xFFF2F2F7.toInt()
        private const val DARK_ICON_COLOR = 0xFFF2F2F7.toInt()
        private const val LIGHT_ICON_COLOR = 0xFF1C1C1E.toInt()
        private const val DARK_BUTTON_FILL = 0xB31A1A1A.toInt()
        private const val LIGHT_BUTTON_FILL = 0xCCFFFFFF.toInt()
        private const val DARK_BORDER_COLOR = 0x1AFFFFFF
        private const val LIGHT_BORDER_COLOR = 0x1A000000
    }

    private data class OverlayTheme(
        val windowColor: Int,
        val borderColor: Int,
        val iconColor: Int,
        val buttonFill: Int,
    )

    private data class OverlaySize(
        val width: Int,
        val height: Int,
    )

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rootView: PipWindowLayout? = null
    private var playerView: PlayerView? = null
    private var backgroundView: View? = null
    private var controlsView: View? = null
    private var attachedPlayer: Player? = null
    private var playerListener: Player.Listener? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var areControlsVisible = true
    private var isConfigurationCallbackRegistered = false

    private val hideControlsRunnable = Runnable { setControlsVisible(isVisible = false, animated = true) }

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            mainHandler.post {
                applyTheme()
                val params = windowLayoutParams ?: return@post
                updateWindowLayout(
                    width = params.width,
                    height = params.height,
                    x = params.x,
                    y = params.y,
                )
            }
        }

        override fun onLowMemory() = Unit
    }

    fun show(player: Player): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (rootView != null) {
            applyTheme()
            attachPlayer(player)
            showControlsTemporarily()
            return true
        }

        val overlayView = createOverlayView()
        val params = createLayoutParams()
        return try {
            windowManager.addView(overlayView, params)
            rootView = overlayView
            windowLayoutParams = params
            registerConfigurationCallbacks()
            attachPlayer(player)
            showControlsTemporarily()
            true
        } catch (exception: RuntimeException) {
            Logger.error(TAG, "Failed to show custom picture-in-picture overlay", exception)
            playerView = null
            backgroundView = null
            controlsView = null
            false
        }
    }

    fun replacePlayer(player: Player) {
        if (rootView == null) return
        attachPlayer(player)
    }

    fun dismiss() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        unregisterConfigurationCallbacks()
        attachedPlayer?.let { player ->
            playerListener?.let(player::removeListener)
        }
        playerListener = null
        attachedPlayer = null
        playerView?.player = null
        playerView = null
        backgroundView = null
        controlsView = null

        rootView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        rootView = null
        windowLayoutParams = null
        areControlsVisible = true
    }

    private fun attachPlayer(player: Player) {
        if (attachedPlayer !== player) {
            attachedPlayer?.let { previousPlayer ->
                playerListener?.let(previousPlayer::removeListener)
            }
            attachedPlayer = player
            val listener = object : Player.Listener {
                override fun onEvents(
                    currentPlayer: Player,
                    events: Player.Events,
                ) {
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                        updatePlayPauseButton(currentPlayer.isPlaying)
                    }
                }
            }
            playerListener = listener
            player.addListener(listener)
        }
        playerView?.player = player
        updatePlayPauseButton(player.isPlaying)
        playerView?.let { view ->
            view.post { forceOpaqueVideoSurface(view, resolveTheme().windowColor) }
        }
    }

    private fun createOverlayView(): PipWindowLayout {
        val theme = resolveTheme()
        val root = PipWindowLayout(context).apply {
            background = windowBackground(theme)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        dp(WINDOW_CORNER_RADIUS_DP).toFloat(),
                    )
                }
            }
            clipToOutline = true
            elevation = 0f
            translationZ = 0f
            outlineAmbientShadowColor = Color.TRANSPARENT
            outlineSpotShadowColor = Color.TRANSPARENT
            contentDescription = context.getString(coreUiR.string.custom_pip_drag)
        }

        val underlay = View(context).apply {
            setBackgroundColor(theme.windowColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        backgroundView = underlay
        root.addView(
            underlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val videoView = LayoutInflater.from(context).inflate(
            R.layout.view_custom_pip_player,
            root,
            false,
        ) as PlayerView
        videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        videoView.setKeepContentOnPlayerReset(true)
        forceOpaqueVideoSurface(videoView, theme.windowColor)
        playerView = videoView
        root.addView(
            videoView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val controls = createControls(theme)
        controlsView = controls
        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return root
    }

    private fun createControls(theme: OverlayTheme): View {
        val container = FrameLayout(context).apply {
            id = R.id.custom_pip_controls
        }
        val openButton = circleButton(
            icon = R.drawable.ic_fullscreen,
            contentDescription = context.getString(coreUiR.string.custom_pip_open_player),
            theme = theme,
            onClick = ::openPlayer,
        ).apply { id = R.id.custom_pip_open }
        val closeButton = circleButton(
            icon = coreUiR.drawable.ic_close,
            contentDescription = context.getString(coreUiR.string.player_panel_close),
            theme = theme,
            onClick = {
                dismiss()
                onStopPlayback()
            },
        ).apply { id = R.id.custom_pip_close }
        container.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            },
        )
        container.addView(
            openButton,
            FrameLayout.LayoutParams(dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(BUTTON_SIZE_DP + 12)
            },
        )

        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(10))
        }
        val previousButton = circleButton(
            icon = coreUiR.drawable.ic_skip_prev,
            contentDescription = context.getString(coreUiR.string.player_controls_previous),
            theme = theme,
            onClick = { attachedPlayer?.seekToPrevious() },
        ).apply { id = R.id.custom_pip_previous }
        val playPauseButton = circleButton(
            icon = coreUiR.drawable.ic_play,
            contentDescription = context.getString(coreUiR.string.player_controls_play_pause),
            theme = theme,
            sizeDp = PLAY_BUTTON_SIZE_DP,
            onClick = {
                attachedPlayer?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            },
        ).apply { id = R.id.custom_pip_play_pause }
        val nextButton = circleButton(
            icon = coreUiR.drawable.ic_skip_next,
            contentDescription = context.getString(coreUiR.string.player_controls_next),
            theme = theme,
            onClick = { attachedPlayer?.seekToNext() },
        ).apply { id = R.id.custom_pip_next }
        bottomBar.addView(previousButton, LinearLayout.LayoutParams(dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP)))
        bottomBar.addView(
            playPauseButton,
            LinearLayout.LayoutParams(dp(PLAY_BUTTON_SIZE_DP), dp(PLAY_BUTTON_SIZE_DP)).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            },
        )
        bottomBar.addView(nextButton, LinearLayout.LayoutParams(dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP)))
        container.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        return container
    }

    private fun circleButton(
        @DrawableRes icon: Int,
        contentDescription: String,
        theme: OverlayTheme,
        sizeDp: Int = BUTTON_SIZE_DP,
        onClick: () -> Unit,
    ): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(theme.iconColor)
        background = circleBackground(theme)
        this.contentDescription = contentDescription
        val padding = dp((sizeDp * 0.22f).roundToInt())
        setPadding(padding, padding, padding, padding)
        setOnClickListener {
            showControlsTemporarily()
            onClick()
        }
    }

    private fun applyTheme() {
        val theme = resolveTheme()
        val root = rootView ?: return
        root.background = windowBackground(theme)
        backgroundView?.setBackgroundColor(theme.windowColor)
        playerView?.let { view -> forceOpaqueVideoSurface(view, theme.windowColor) }
        val iconTint = ColorStateList.valueOf(theme.iconColor)
        val buttonIds = intArrayOf(
            R.id.custom_pip_open,
            R.id.custom_pip_close,
            R.id.custom_pip_previous,
            R.id.custom_pip_play_pause,
            R.id.custom_pip_next,
        )
        buttonIds.forEach { id ->
            root.findViewById<ImageButton>(id)?.let { button ->
                button.imageTintList = iconTint
                button.background = circleBackground(theme)
            }
        }
    }

    private fun resolveTheme(): OverlayTheme = if (isDarkTheme()) {
        OverlayTheme(
            windowColor = DARK_WINDOW_COLOR,
            borderColor = DARK_BORDER_COLOR,
            iconColor = DARK_ICON_COLOR,
            buttonFill = DARK_BUTTON_FILL,
        )
    } else {
        OverlayTheme(
            windowColor = LIGHT_WINDOW_COLOR,
            borderColor = LIGHT_BORDER_COLOR,
            iconColor = LIGHT_ICON_COLOR,
            buttonFill = LIGHT_BUTTON_FILL,
        )
    }

    private fun windowBackground(theme: OverlayTheme): GradientDrawable = GradientDrawable().apply {
        setColor(theme.windowColor)
        cornerRadius = dp(WINDOW_CORNER_RADIUS_DP).toFloat()
        setStroke(dp(1).coerceAtLeast(1), theme.borderColor)
    }

    private fun forceOpaqueVideoSurface(
        videoView: PlayerView,
        backgroundColor: Int,
    ) {
        videoView.setBackgroundColor(backgroundColor)
        videoView.setShutterBackgroundColor(backgroundColor)
        videoView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        when (val surface = videoView.videoSurfaceView) {
            is TextureView -> surface.isOpaque = true
            is SurfaceView -> {
                surface.setZOrderOnTop(false)
                surface.holder.setFormat(PixelFormat.OPAQUE)
            }
        }
        Logger.debug(
            TAG,
            "Custom pip surface=${videoView.videoSurfaceView?.javaClass?.simpleName}",
        )
    }

    private fun circleBackground(theme: OverlayTheme): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(theme.buttonFill)
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val button = rootView?.findViewById<ImageButton>(R.id.custom_pip_play_pause) ?: return
        button.setImageResource(if (isPlaying) coreUiR.drawable.ic_pause else coreUiR.drawable.ic_play)
    }

    private fun openPlayer() {
        val playbackUri = attachedPlayer?.currentMediaItem?.localConfiguration?.uri
        dismiss()
        context.startActivity(
            Intent(context, PlayerActivity::class.java).apply {
                if (playbackUri != null) {
                    action = Intent.ACTION_VIEW
                    data = playbackUri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun toggleControls() {
        if (areControlsVisible) {
            setControlsVisible(isVisible = false, animated = true)
        } else {
            showControlsTemporarily()
        }
    }

    private fun showControlsTemporarily() {
        setControlsVisible(isVisible = true, animated = true)
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun setControlsVisible(
        isVisible: Boolean,
        animated: Boolean,
    ) {
        areControlsVisible = isVisible
        val view = controlsView ?: return
        val alpha = if (isVisible) 1f else 0f
        val duration = if (animated) 180L else 0L
        view.animate().cancel()
        if (duration == 0L) {
            view.alpha = alpha
            view.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        } else {
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(alpha)
                .setDuration(duration)
                .withEndAction {
                    if (!isVisible) view.visibility = View.INVISIBLE
                }
                .start()
        }
        if (!isVisible) {
            mainHandler.removeCallbacks(hideControlsRunnable)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val bounds = screenBounds()
        val size = defaultWindowSize(bounds)
        val margin = dp(WINDOW_MARGIN_DP)
        return WindowManager.LayoutParams(
            size.width,
            size.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bounds.width() - size.width - margin).coerceAtLeast(margin)
            y = (bounds.height() - size.height - margin * 4).coerceAtLeast(margin)
            title = context.getString(coreUiR.string.pip_mode_custom)
            alpha = 1f
        }
    }

    private fun updateWindowLayout(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ) {
        val params = windowLayoutParams ?: return
        val view = rootView ?: return
        val bounds = screenBounds()
        val size = fitSize(desiredWidth = width, bounds = bounds)

        val maxX = (bounds.width() - size.width).coerceAtLeast(0)
        val maxY = (bounds.height() - size.height).coerceAtLeast(0)
        params.width = size.width
        params.height = size.height
        params.x = x.coerceIn(0, maxX)
        params.y = y.coerceIn(0, maxY)
        windowManager.updateViewLayout(view, params)
        view.invalidateOutline()
    }

    private fun defaultWindowSize(bounds: Rect): OverlaySize {
        val preferredWidth = dp(WINDOW_WIDTH_DP)
        return fitSize(desiredWidth = preferredWidth, bounds = bounds)
    }

    private fun fitSize(
        desiredWidth: Int,
        bounds: Rect,
    ): OverlaySize {
        val minWidth = dp(WINDOW_MIN_WIDTH_DP)
        val maxWidth = bounds.width().coerceAtLeast(minWidth)
        val maxHeight = bounds.height().coerceAtLeast(1)
        var resolvedWidth = desiredWidth.coerceIn(minWidth, maxWidth)
        var resolvedHeight = (resolvedWidth / WINDOW_ASPECT).roundToInt()
        if (resolvedHeight > maxHeight) {
            resolvedHeight = maxHeight
            resolvedWidth = (resolvedHeight * WINDOW_ASPECT).roundToInt().coerceIn(minWidth, maxWidth)
            resolvedHeight = (resolvedWidth / WINDOW_ASPECT).roundToInt().coerceAtMost(maxHeight)
        }
        return OverlaySize(width = resolvedWidth, height = resolvedHeight)
    }

    private fun screenBounds(): Rect = Rect(windowManager.maximumWindowMetrics.bounds)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun registerConfigurationCallbacks() {
        if (isConfigurationCallbackRegistered) return
        context.applicationContext.registerComponentCallbacks(configurationCallbacks)
        isConfigurationCallbackRegistered = true
    }

    private fun unregisterConfigurationCallbacks() {
        if (!isConfigurationCallbackRegistered) return
        context.applicationContext.unregisterComponentCallbacks(configurationCallbacks)
        isConfigurationCallbackRegistered = false
    }

    private inner class PipWindowLayout(
        context: Context,
    ) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val scaleDetector = ScaleGestureDetector(context, ScaleListener()).apply {
            isQuickScaleEnabled = false
        }
        private var isScaling = false
        private var isDragging = false
        private var didMove = false
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var startWidth = 0
        private var startHeight = 0
        private var startFocusX = 0f
        private var startFocusY = 0f
        private var startSpan = 1f

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                captureStart(event)
            }

            scaleDetector.onTouchEvent(event)
            if (isScaling || event.pointerCount >= 2) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    isScaling = false
                }
                return true
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val distance = hypot(
                        (event.rawX - downRawX).toDouble(),
                        (event.rawY - downRawY).toDouble(),
                    )
                    if (!isDragging && distance > touchSlop) {
                        isDragging = true
                        didMove = true
                    }
                    if (isDragging) {
                        val params = windowLayoutParams ?: return true
                        updateWindowLayout(
                            width = params.width,
                            height = params.height,
                            x = startX + (event.rawX - downRawX).roundToInt(),
                            y = startY + (event.rawY - downRawY).roundToInt(),
                        )
                        return true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!didMove) {
                        val handledByChild = super.dispatchTouchEvent(event)
                        if (!handledByChild || !isEventOnControl(event)) {
                            toggleControls()
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_CANCEL -> isDragging = false
            }

            if (!isDragging) {
                return super.dispatchTouchEvent(event)
            }
            return true
        }

        private fun captureStart(event: MotionEvent) {
            val params = windowLayoutParams
            isScaling = false
            isDragging = false
            didMove = false
            downRawX = event.rawX
            downRawY = event.rawY
            startX = params?.x ?: 0
            startY = params?.y ?: 0
            startWidth = params?.width ?: 0
            startHeight = params?.height ?: 0
        }

        private fun isEventOnControl(event: MotionEvent): Boolean {
            val controlIds = intArrayOf(
                R.id.custom_pip_open,
                R.id.custom_pip_close,
                R.id.custom_pip_previous,
                R.id.custom_pip_play_pause,
                R.id.custom_pip_next,
            )
            return controlIds.any { id -> isEventOnView(findViewById(id), event) }
        }

        private fun isEventOnView(
            view: View?,
            event: MotionEvent,
        ): Boolean {
            if (view == null || view.visibility != VISIBLE || view.alpha < 0.5f) return false
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val x = event.rawX
            val y = event.rawY
            return x >= location[0] &&
                x < location[0] + view.width &&
                y >= location[1] &&
                y < location[1] + view.height
        }

        private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                val params = windowLayoutParams ?: return false
                isScaling = true
                didMove = true
                startWidth = params.width
                startHeight = params.height
                startX = params.x
                startY = params.y
                startFocusX = detector.focusX
                startFocusY = detector.focusY
                startSpan = detector.currentSpan.coerceAtLeast(1f)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.currentSpan / startSpan
                val newWidth = (startWidth * scale).roundToInt()
                val widthScale = if (startWidth == 0) 1f else newWidth.toFloat() / startWidth
                updateWindowLayout(
                    width = newWidth,
                    height = (startHeight * scale).roundToInt(),
                    x = (startX + startFocusX - startFocusX * widthScale).roundToInt(),
                    y = (startY + startFocusY - startFocusY * widthScale).roundToInt(),
                )
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        }
    }
}
