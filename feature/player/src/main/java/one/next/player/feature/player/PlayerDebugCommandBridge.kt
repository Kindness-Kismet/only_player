package one.next.player.feature.player

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object PlayerDebugCommandBridge {
    const val ACTION_BACK = "back"
    const val ACTION_ROTATE = "rotate"
    const val ACTION_TOGGLE_AMBIENCE = "toggle_ambience"
    const val ACTION_SHOW_CONTROLS = "show_controls"
    const val ACTION_HIDE_CONTROLS = "hide_controls"
    const val ACTION_SHOW_PLAYLIST = "show_playlist"
    const val ACTION_SHOW_SPEED = "show_speed"
    const val ACTION_SHOW_AUDIO = "show_audio"
    const val ACTION_SHOW_SUBTITLE = "show_subtitle"
    const val ACTION_LOCK = "lock"
    const val ACTION_UNLOCK = "unlock"
    const val ACTION_TOGGLE_LOCK = "toggle_lock"
    const val ACTION_CYCLE_SCALE = "cycle_scale"
    const val ACTION_SHOW_SCALE = "show_scale"
    const val ACTION_SHOW_DECODER = "show_decoder"
    const val ACTION_SHOW_VIDEO_FILTERS = "show_video_filters"
    const val ACTION_PIP = "pip"
    const val ACTION_SCREENSHOT = "screenshot"
    const val ACTION_BACKGROUND = "background"
    const val ACTION_SHOW_SLEEP_TIMER = "show_sleep_timer"
    const val ACTION_TOGGLE_CUSTOMIZE_CONTROLS = "toggle_customize_controls"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var registeredHandler: RegisteredHandler? = null

    fun setHandler(handler: (String) -> Boolean): String {
        val token = UUID.randomUUID().toString()
        registeredHandler = RegisteredHandler(token, handler)
        return token
    }

    fun clearHandler(token: String) {
        if (registeredHandler?.token == token) {
            registeredHandler = null
        }
    }

    fun dispatch(action: String): Boolean {
        val currentHandler = registeredHandler?.handler ?: return false
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return currentHandler(action)
        }

        val latch = CountDownLatch(1)
        val isExpired = AtomicBoolean(false)
        var result = false
        mainHandler.post {
            if (!isExpired.get()) {
                result = runCatching { currentHandler(action) }.getOrDefault(false)
            }
            latch.countDown()
        }
        if (!latch.await(2, TimeUnit.SECONDS)) {
            isExpired.set(true)
        }
        return result
    }

    private data class RegisteredHandler(
        val token: String,
        val handler: (String) -> Boolean,
    )
}
