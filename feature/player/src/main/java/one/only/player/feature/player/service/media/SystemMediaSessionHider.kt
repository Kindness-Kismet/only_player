package one.only.player.feature.player.service.media

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadata as PlatformMediaMetadata
import android.media.session.MediaSession as PlatformMediaSession
import android.media.session.PlaybackState as PlatformPlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.util.Printer
import androidx.media3.session.MediaSession
import androidx.media3.session.legacy.MediaMetadataCompat
import androidx.media3.session.legacy.MediaSessionCompat
import androidx.media3.session.legacy.PlaybackStateCompat
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.only.player.core.common.Logger

/**
 * Blocks Oplus QS media panel (`oplus_qs_media_panel_layout`) when suppression is enabled.
 *
 * Oplus: MediaSessionManager.getActiveSessions → only **active** platform sessions.
 * Media3 LegacyStub forces setActive(true); we proxy + release platform session.
 *
 * Power (logs193): after platform.release, stop burst/watchdog/16ms loops and only
 * re-suppress on resync() from player events (no busy spin).
 */
class SystemMediaSessionHider(
    private val context: Context,
    private val scope: CoroutineScope,
    private val mediaSessionProvider: () -> MediaSession?,
) {
    private var resyncJob: Job? = null
    private var hasLogged: Boolean = false
    private var hasLoggedProxySkip: Boolean = false
    private var hasProxiedCompat: Boolean = false
    private var hasLooperHook: Boolean = false
    private var hasIdleHandler: Boolean = false
    private var platformReleased: Boolean = false
    private var isSuppressionEnabled: Boolean = true
    private var applicationHandler: Handler? = null
    private var applicationLooper: Looper? = null
    private var cachedPlatformSession: PlatformMediaSession? = null
    private var cachedCompat: MediaSessionCompat? = null
    private var originalImpl: Any? = null

    private val emptyPlaybackStateCompat: PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
            .setActions(0L)
            .build()

    private val emptyMetadataCompat: MediaMetadataCompat =
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)
            .build()

    private val emptyPlatformState: PlatformPlaybackState =
        PlatformPlaybackState.Builder()
            .setState(PlatformPlaybackState.STATE_NONE, 0L, 0f)
            .setActions(0L)
            .build()

    private val emptyPlatformMetadata: PlatformMediaMetadata =
        PlatformMediaMetadata.Builder()
            .putString(PlatformMediaMetadata.METADATA_KEY_TITLE, "")
            .putString(PlatformMediaMetadata.METADATA_KEY_ARTIST, "")
            .putString(PlatformMediaMetadata.METADATA_KEY_ALBUM, "")
            .putString(PlatformMediaMetadata.METADATA_KEY_DISPLAY_TITLE, "")
            .putString(PlatformMediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "")
            .putLong(PlatformMediaMetadata.METADATA_KEY_DURATION, 0L)
            .build()

    private val continuousSuppressRunnable = object : Runnable {
        override fun run() {
            if (!isSuppressionEnabled || platformReleased) return
            suppressNow()
            applicationHandler?.postDelayed(this, CONTINUOUS_SUPPRESS_MS)
        }
    }

    private val idleHandler = MessageQueue.IdleHandler {
        if (!isSuppressionEnabled || platformReleased) return@IdleHandler false
        suppressNow()
        true
    }

    private val looperPrinter = Printer { x ->
        if (!isSuppressionEnabled || platformReleased) return@Printer
        if (x != null && x.startsWith("<<<<< Finished")) {
            suppressNow()
        }
    }

    /**
     * Enable/disable system-media suppression without recreating the service.
     * When disabled, stop all background work so platform session can stay active.
     */
    fun setSuppressionEnabled(enabled: Boolean) {
        if (isSuppressionEnabled == enabled) return
        isSuppressionEnabled = enabled
        if (!enabled) {
            stopBackgroundWork()
            // Do not re-activate a released platform session mid-life; next MediaSession
            // build / player recreate will publish normally under SHOW policy.
            Logger.info(TAG, "System media suppression disabled")
        } else {
            platformReleased = false
            hasProxiedCompat = false
            originalImpl = null
            cachedPlatformSession = null
            cachedCompat = null
            hasLogged = false
            mediaSessionProvider()?.let(::resync)
            Logger.info(TAG, "System media suppression enabled")
        }
    }

    fun resync(session: MediaSession? = mediaSessionProvider()) {
        if (!isSuppressionEnabled) return
        val active = session ?: return
        cacheAndDetach(active)
        suppressNow()
        // Only spin briefly until platform is released; then event-driven resync only.
        if (!platformReleased) {
            scheduleBurstUntilReleased(active)
        }
        cancelResidualMediaNotifications()
    }

    fun stop() {
        stopBackgroundWork()
        cancelResidualMediaNotifications()
        if (isSuppressionEnabled) {
            suppressNow()
            releasePlatformQuietly()
        }
        cachedPlatformSession = null
        cachedCompat = null
        originalImpl = null
        hasProxiedCompat = false
    }

    fun cancelMedia3DefaultNotification() {
        cancelResidualMediaNotifications()
    }

    private fun stopBackgroundWork() {
        applicationHandler?.removeCallbacks(continuousSuppressRunnable)
        runCatching { applicationLooper?.queue?.removeIdleHandler(idleHandler) }
        runCatching { applicationLooper?.setMessageLogging(null) }
        resyncJob?.cancel()
        resyncJob = null
        hasIdleHandler = false
        hasLooperHook = false
    }

    private fun cacheAndDetach(session: MediaSession) {
        runCatching {
            val mediaSessionImpl = resolveMediaSessionImpl(session) ?: return@runCatching
            val handler = resolveApplicationHandler(mediaSessionImpl)
            if (handler != null) {
                applicationHandler = handler
                applicationLooper = handler.looper
            }
            val compat = resolveLegacyMediaSessionCompat(session, mediaSessionImpl)
            if (compat != null) {
                cachedCompat = compat
                installCompatImplProxy(compat)
            }
            val platform = resolvePlatformSession(compat, mediaSessionImpl)
            if (platform != null) {
                cachedPlatformSession = platform
            }
            releasePlatformQuietly()
            if (!platformReleased) {
                installLooperHooks()
                scheduleContinuousSuppress()
            } else {
                // Released: drop continuous work (logs193 power fix)
                stopBackgroundWork()
            }
            applicationHandler?.post {
                if (!isSuppressionEnabled) return@post
                suppressNow()
                releasePlatformQuietly()
                if (platformReleased) stopBackgroundWork()
            }
        }.onFailure { error ->
            Logger.error(TAG, "cacheAndDetach failed", error)
        }
    }

    private fun installCompatImplProxy(compat: MediaSessionCompat) {
        if (hasProxiedCompat) return
        runCatching {
            val implField = findField(compat.javaClass, "impl") ?: return@runCatching
            implField.isAccessible = true
            val original = implField.get(compat) ?: return@runCatching
            if (Proxy.isProxyClass(original.javaClass)) {
                hasProxiedCompat = true
                return@runCatching
            }
            val iface = original.javaClass.interfaces.firstOrNull {
                it.name.contains("MediaSessionImpl") || it.simpleName == "MediaSessionImpl"
            } ?: original.javaClass.interfaces.firstOrNull()
            if (iface == null) {
                if (!hasLoggedProxySkip) {
                    hasLoggedProxySkip = true
                    Logger.info(TAG, "MediaSessionImpl interface missing; proxy skipped")
                }
                return@runCatching
            }
            originalImpl = original
            val proxy = Proxy.newProxyInstance(
                iface.classLoader ?: context.classLoader,
                arrayOf(iface),
                SuppressingImplHandler(original),
            )
            if (!forceSetField(implField, compat, proxy)) {
                if (!hasLoggedProxySkip) {
                    hasLoggedProxySkip = true
                    Logger.info(TAG, "Could not replace MediaSessionCompat.impl; using release+hooks")
                }
                return@runCatching
            }
            hasProxiedCompat = true
            invokeQuiet(original, "setActive", false)
            invokeQuiet(original, "setPlaybackState", emptyPlaybackStateCompat)
            invokeQuiet(original, "setMetadata", emptyMetadataCompat)
            Logger.info(TAG, "Proxied MediaSessionCompat.impl — system publish blocked")
        }.onFailure { error ->
            Logger.error(TAG, "installCompatImplProxy failed", error)
        }
    }

    private fun releasePlatformQuietly() {
        if (platformReleased) return
        val platform = cachedPlatformSession
            ?: (cachedCompat?.let {
                runCatching { it.getMediaSession() as? PlatformMediaSession }.getOrNull()
            })
            ?: return
        runCatching {
            platform.isActive = false
            platform.setPlaybackState(emptyPlatformState)
            platform.setMetadata(emptyPlatformMetadata)
            platform.setQueue(null)
            platform.setQueueTitle(null)
            runCatching { platform.setMediaButtonReceiver(null) }
            platform.release()
            platformReleased = true
            cachedPlatformSession = null
            stopBackgroundWork()
            Logger.info(TAG, "Released platform MediaSession for Oplus getActiveSessions drop")
        }.onFailure { error ->
            if (error is IllegalStateException) {
                platformReleased = true
                cachedPlatformSession = null
                stopBackgroundWork()
            } else {
                Logger.error(TAG, "platform.release failed", error)
            }
        }
    }

    private inner class SuppressingImplHandler(
        private val original: Any,
    ) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            if (!isSuppressionEnabled) {
                return try {
                    method.invoke(original, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.targetException ?: e
                }
            }
            when (method.name) {
                "setActive" -> return invokeQuiet(original, "setActive", false)
                "isActive" -> return false
                "setPlaybackState" ->
                    return invokeQuiet(original, "setPlaybackState", emptyPlaybackStateCompat)
                "setMetadata" ->
                    return invokeQuiet(original, "setMetadata", emptyMetadataCompat)
                "setQueue" -> return invokeQuiet(original, "setQueue", null)
                "setQueueTitle" -> return invokeQuiet(original, "setQueueTitle", "")
                "setFlags" -> return invokeQuiet(original, "setFlags", 0)
                "setMediaButtonReceiver" ->
                    return invokeQuiet(original, "setMediaButtonReceiver", null)
                "setSessionActivity" ->
                    return invokeQuiet(original, "setSessionActivity", null)
                "getMediaSession" -> {
                    return runCatching {
                        method.invoke(original, *(args ?: emptyArray()))
                    }.getOrNull()
                }
                "release" -> {
                    return runCatching {
                        method.invoke(original, *(args ?: emptyArray()))
                    }.getOrNull()
                }
            }
            return try {
                method.invoke(original, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                if (cause is IllegalStateException) {
                    return defaultValue(method.returnType)
                }
                throw cause ?: e
            } catch (_: IllegalStateException) {
                return defaultValue(method.returnType)
            }
        }

        private fun defaultValue(type: Class<*>): Any? = when {
            type == java.lang.Boolean.TYPE -> false
            type == java.lang.Integer.TYPE -> 0
            type == java.lang.Long.TYPE -> 0L
            type == java.lang.Float.TYPE -> 0f
            type == java.lang.Double.TYPE -> 0.0
            type == java.lang.Void.TYPE || type == Void.TYPE -> null
            else -> null
        }
    }

    private fun forceSetField(field: Field, target: Any, value: Any): Boolean {
        field.isAccessible = true
        runCatching {
            field.set(target, value)
            if (field.get(target) === value) return true
        }
        runCatching {
            val accessFlags = Field::class.java.getDeclaredField("accessFlags")
            accessFlags.isAccessible = true
            accessFlags.setInt(field, accessFlags.getInt(field) and Modifier.FINAL.inv())
            field.set(target, value)
            if (field.get(target) === value) return true
        }
        runCatching {
            val modifiers = Field::class.java.getDeclaredField("modifiers")
            modifiers.isAccessible = true
            modifiers.setInt(field, field.modifiers and Modifier.FINAL.inv())
            field.set(target, value)
            if (field.get(target) === value) return true
        }
        runCatching {
            val unsafeClass = runCatching { Class.forName("sun.misc.Unsafe") }.getOrNull()
                ?: Class.forName("jdk.internal.misc.Unsafe")
            val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply {
                isAccessible = true
            }.get(null)
            val offset = unsafeClass.getMethod("objectFieldOffset", Field::class.java)
                .invoke(theUnsafe, field) as Long
            unsafeClass.getMethod(
                "putObject",
                Any::class.java,
                Long::class.javaPrimitiveType,
                Any::class.java,
            ).invoke(theUnsafe, target, offset, value)
            if (field.get(target) === value) return true
        }
        return false
    }

    private fun invokeQuiet(target: Any, name: String, vararg args: Any?): Any? {
        return runCatching {
            val method = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterCount == args.size
            } ?: target.javaClass.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == args.size
            }
            method?.isAccessible = true
            method?.invoke(target, *args)
        }.getOrNull()
    }

    private fun installLooperHooks() {
        if (platformReleased || !isSuppressionEnabled) return
        val looper = applicationLooper ?: return
        if (!hasLooperHook) {
            runCatching {
                looper.setMessageLogging(looperPrinter)
                hasLooperHook = true
            }
        }
        if (!hasIdleHandler) {
            runCatching {
                looper.queue.addIdleHandler(idleHandler)
                hasIdleHandler = true
            }
        }
    }

    private fun scheduleContinuousSuppress() {
        if (platformReleased || !isSuppressionEnabled) return
        val handler = applicationHandler ?: return
        handler.removeCallbacks(continuousSuppressRunnable)
        handler.post(continuousSuppressRunnable)
    }

    /** Brief burst only until platform is released — no infinite 250ms loop (logs193). */
    private fun scheduleBurstUntilReleased(session: MediaSession) {
        resyncJob?.cancel()
        resyncJob = scope.launch {
            repeat(RESYNC_BURST_COUNT) {
                if (!isSuppressionEnabled || platformReleased) return@launch
                cacheAndDetach(session)
                suppressNow()
                cancelResidualMediaNotifications()
                if (platformReleased) return@launch
                delay(RESYNC_INTERVAL_MS)
            }
        }
    }

    private fun suppressNow() {
        if (!isSuppressionEnabled) return
        val compat = cachedCompat
        val platform = cachedPlatformSession
        runCatching {
            if (compat != null) {
                runCatching { compat.setPlaybackState(emptyPlaybackStateCompat) }
                runCatching { compat.setMetadata(emptyMetadataCompat) }
                runCatching { compat.setQueue(null) }
                runCatching { compat.setQueueTitle("") }
                runCatching { compat.setFlags(0) }
                runCatching { compat.setExtras(Bundle()) }
                runCatching { compat.setActive(false) }
            }
            if (platform != null && !platformReleased) {
                runCatching {
                    platform.setPlaybackState(emptyPlatformState)
                    platform.setMetadata(emptyPlatformMetadata)
                    platform.isActive = false
                }
            }
            if (!platformReleased) {
                releasePlatformQuietly()
            }
            if (!hasLogged) {
                hasLogged = true
                Logger.info(
                    TAG,
                    "System media detached proxy=$hasProxiedCompat released=$platformReleased " +
                        "looperHook=$hasLooperHook",
                )
            }
        }.onFailure { error ->
            Logger.error(TAG, "suppressNow failed", error)
        }
    }

    private fun cancelResidualMediaNotifications() {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return@runCatching
            nm.cancel(MEDIA3_MEDIA_NOTIFICATION_ID)
            nm.cancel(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.activeNotifications
                    ?.asSequence()
                    ?.filter { posted ->
                        if (posted.id == PlaybackForegroundNotifier.NOTIFICATION_ID) return@filter false
                        if (posted.packageName != context.packageName) return@filter false
                        val n = posted.notification
                        val extras = n.extras
                        val style = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
                        extras?.containsKey("android.mediaSession") == true ||
                            extras?.containsKey("android.mediaSessionCompat") == true ||
                            n.category == Notification.CATEGORY_TRANSPORT ||
                            style.contains("MediaStyle", ignoreCase = true)
                    }
                    ?.forEach { posted ->
                        nm.cancel(posted.tag, posted.id)
                    }
            }
        }
    }

    private fun resolveMediaSessionImpl(session: MediaSession): Any? {
        return runCatching {
            val method = session.javaClass.methods.firstOrNull {
                it.name == "getImpl" && it.parameterCount == 0
            } ?: session.javaClass.declaredMethods.firstOrNull {
                it.name == "getImpl" && it.parameterCount == 0
            }
            method?.isAccessible = true
            method?.invoke(session)
        }.getOrNull() ?: runCatching {
            val field = findField(session.javaClass, "impl")
            field?.isAccessible = true
            field?.get(session)
        }.getOrNull()
    }

    private fun resolveApplicationHandler(mediaSessionImpl: Any): Handler? {
        runCatching {
            val method = mediaSessionImpl.javaClass.methods.firstOrNull {
                it.name == "getApplicationHandler" && it.parameterCount == 0
            }
            method?.isAccessible = true
            val h = method?.invoke(mediaSessionImpl) as? Handler
            if (h != null) return h
        }
        return runCatching {
            val field = findField(mediaSessionImpl.javaClass, "applicationHandler")
            field?.isAccessible = true
            field?.get(mediaSessionImpl) as? Handler
        }.getOrNull()
    }

    private fun resolveLegacyMediaSessionCompat(
        session: MediaSession,
        mediaSessionImpl: Any?,
    ): MediaSessionCompat? {
        val impl = mediaSessionImpl ?: resolveMediaSessionImpl(session) ?: return null
        val legacyStub = runCatching {
            val field = findField(impl.javaClass, "sessionLegacyStub")
                ?: impl.javaClass.declaredFields.firstOrNull {
                    it.type.simpleName.contains("LegacyStub")
                }
            field?.isAccessible = true
            field?.get(impl)
        }.getOrNull() ?: return null

        return runCatching {
            legacyStub.javaClass.methods
                .firstOrNull { it.name == "getSessionCompat" && it.parameterCount == 0 }
                ?.apply { isAccessible = true }
                ?.invoke(legacyStub) as? MediaSessionCompat
        }.getOrNull() ?: runCatching {
            val field = findField(legacyStub.javaClass, "sessionCompat")
            field?.isAccessible = true
            field?.get(legacyStub) as? MediaSessionCompat
        }.getOrNull()
    }

    private fun resolvePlatformSession(
        compat: MediaSessionCompat?,
        mediaSessionImpl: Any?,
    ): PlatformMediaSession? {
        runCatching {
            val impl = originalImpl
            if (impl != null) {
                val fwk = findField(impl.javaClass, "sessionFwk")
                    ?: findField(impl.javaClass, "mSession")
                fwk?.isAccessible = true
                val platform = fwk?.get(impl) as? PlatformMediaSession
                if (platform != null) return platform
            }
        }
        runCatching {
            val fromCompat = compat?.getMediaSession() as? PlatformMediaSession
            if (fromCompat != null) return fromCompat
        }
        runCatching {
            if (compat != null) {
                val implField = findField(compat.javaClass, "impl")
                implField?.isAccessible = true
                val impl = implField?.get(compat)
                if (impl != null && !Proxy.isProxyClass(impl.javaClass)) {
                    val fwk = findField(impl.javaClass, "sessionFwk")
                        ?: findField(impl.javaClass, "mSession")
                    fwk?.isAccessible = true
                    val platform = fwk?.get(impl) as? PlatformMediaSession
                    if (platform != null) return platform
                }
            }
        }
        runCatching {
            if (mediaSessionImpl != null) {
                val legacyStubField = findField(mediaSessionImpl.javaClass, "sessionLegacyStub")
                legacyStubField?.isAccessible = true
                val legacyStub = legacyStubField?.get(mediaSessionImpl)
                if (legacyStub != null) {
                    val compatField = findField(legacyStub.javaClass, "sessionCompat")
                    compatField?.isAccessible = true
                    val c = compatField?.get(legacyStub) as? MediaSessionCompat
                    val p = c?.getMediaSession() as? PlatformMediaSession
                    if (p != null) return p
                }
            }
        }
        return null
    }

    private fun findField(start: Class<*>, name: String): Field? {
        var cls: Class<*>? = start
        while (cls != null) {
            try {
                return cls.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SystemMediaSessionHider"

        const val MEDIA3_MEDIA_NOTIFICATION_ID = 1001

        private const val RESYNC_INTERVAL_MS = 40L
        private const val RESYNC_BURST_COUNT = 40
        private const val CONTINUOUS_SUPPRESS_MS = 50L
    }
}
