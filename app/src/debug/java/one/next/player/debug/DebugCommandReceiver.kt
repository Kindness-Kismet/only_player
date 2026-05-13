package one.next.player.debug

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol
import one.next.player.feature.player.service.CustomCommands
import one.next.player.feature.player.service.PlayerService
import one.next.player.feature.player.subtitle.OnlineSubtitleRepository

class DebugCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    DebugCommandReceiverEntryPoint::class.java,
                )
                runBlocking { dispatch(context, entryPoint, intent) }
            } catch (throwable: Throwable) {
                Logger.error(TAG, "Failed to handle debug command: ${intent.action}", throwable)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private suspend fun dispatch(
        context: Context,
        entryPoint: DebugCommandReceiverEntryPoint,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_SET_IGNORE_NOMEDIA -> setIgnoreNoMedia(entryPoint.preferencesRepository(), intent)
            ACTION_SET_LONG_PRESS_CONTROLS -> setLongPressControls(entryPoint.preferencesRepository(), intent)
            ACTION_SET_LONG_PRESS_OVERLAY -> setLongPressOverlay(entryPoint.preferencesRepository(), intent)
            ACTION_SET_VIDEO_FILTERS -> setVideoFilters(entryPoint.preferencesRepository(), intent)
            ACTION_ADD_ONLINE_SUBTITLE -> addOnlineSubtitle(
                context = context.applicationContext,
                repository = entryPoint.onlineSubtitleRepository(),
                intent = intent,
            )
            ACTION_SET_CONTROLLER_TIMEOUT -> setControllerTimeout(entryPoint.preferencesRepository(), intent)
            ACTION_REFRESH_LIBRARY -> refreshLibrary(entryPoint.mediaSynchronizer())
            ACTION_ADD_REMOTE_SERVER -> addRemoteServer(entryPoint.remoteServerRepository(), intent)
            ACTION_DELETE_REMOTE_SERVER -> deleteRemoteServer(entryPoint.remoteServerRepository(), intent)
            else -> Logger.info(TAG, "Ignored unknown debug action: ${intent.action}")
        }
    }

    private suspend fun setIgnoreNoMedia(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        if (!intent.hasExtra(EXTRA_ENABLED)) return

        val shouldIgnoreNoMediaFiles = intent.getBooleanExtra(EXTRA_ENABLED, false)
        preferencesRepository.updateApplicationPreferences {
            if (it.shouldIgnoreNoMediaFiles == shouldIgnoreNoMediaFiles) {
                it
            } else {
                it.copy(shouldIgnoreNoMediaFiles = shouldIgnoreNoMediaFiles)
            }
        }
        Logger.info(TAG, "shouldIgnoreNoMediaFiles set to $shouldIgnoreNoMediaFiles")
    }

    private suspend fun setLongPressControls(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        if (!intent.hasExtra(EXTRA_ENABLED)) return

        val isEnabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        preferencesRepository.updatePlayerPreferences {
            it.copy(shouldUseLongPressControls = isEnabled)
        }
        Logger.info(TAG, "shouldUseLongPressControls set to $isEnabled")
    }

    private suspend fun setLongPressOverlay(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        if (!intent.hasExtra(EXTRA_ENABLED)) return

        val isEnabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        preferencesRepository.updatePlayerPreferences {
            it.copy(isDebugLongPressOverlayVisible = isEnabled)
        }
        Logger.info(TAG, "isDebugLongPressOverlayVisible set to $isEnabled")
    }

    private suspend fun setVideoFilters(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        preferencesRepository.updatePlayerPreferences { preferences ->
            preferences.copy(
                shouldApplyVideoFilters = intent.getBooleanExtra(EXTRA_ENABLED, preferences.shouldApplyVideoFilters),
                videoBrightness = intent.getFloatExtra(EXTRA_BRIGHTNESS, preferences.videoBrightness)
                    .coerceIn(PlayerPreferences.MIN_VIDEO_BRIGHTNESS, PlayerPreferences.MAX_VIDEO_BRIGHTNESS),
                videoContrast = intent.getFloatExtra(EXTRA_CONTRAST, preferences.videoContrast)
                    .coerceIn(PlayerPreferences.MIN_VIDEO_CONTRAST, PlayerPreferences.MAX_VIDEO_CONTRAST),
                videoSaturation = intent.getFloatExtra(EXTRA_SATURATION, preferences.videoSaturation)
                    .coerceIn(PlayerPreferences.MIN_VIDEO_SATURATION, PlayerPreferences.MAX_VIDEO_SATURATION),
                videoHue = intent.getFloatExtra(EXTRA_HUE, preferences.videoHue)
                    .coerceIn(PlayerPreferences.MIN_VIDEO_HUE, PlayerPreferences.MAX_VIDEO_HUE),
                videoGamma = intent.getFloatExtra(EXTRA_GAMMA, preferences.videoGamma)
                    .coerceIn(PlayerPreferences.MIN_VIDEO_GAMMA, PlayerPreferences.MAX_VIDEO_GAMMA),
                videoSharpening = intent.getFloatExtra(EXTRA_SHARPENING, preferences.videoSharpening)
                    .coerceIn(PlayerPreferences.DEFAULT_VIDEO_SHARPENING, PlayerPreferences.MAX_VIDEO_SHARPENING),
            )
        }
        Logger.info(TAG, "Video filters updated from debug command")
    }

    private suspend fun addOnlineSubtitle(
        context: Context,
        repository: OnlineSubtitleRepository,
        intent: Intent,
    ) {
        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isBlank()) return

        val subtitle = repository.downloadSubtitle(url)
        withContext(Dispatchers.Main) {
            val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val controller = MediaController.Builder(context, sessionToken).buildAsync().await()
            try {
                var remainingAttempts = MAX_WAIT_MEDIA_ITEM_ATTEMPTS
                while (controller.currentMediaItem == null && remainingAttempts > 0) {
                    delay(WAIT_MEDIA_ITEM_INTERVAL_MS)
                    remainingAttempts--
                }
                val args = Bundle().apply {
                    putString(CustomCommands.SUBTITLE_TRACK_URI_KEY, subtitle.uriString)
                }
                val result = controller.sendCustomCommand(CustomCommands.ADD_SUBTITLE_TRACK.sessionCommand, args).await()
                Logger.info(TAG, "Online subtitle add result=${result.resultCode}: ${subtitle.uriString}")
            } finally {
                controller.release()
            }
        }
    }

    private suspend fun setControllerTimeout(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, PlayerPreferences.DEFAULT_CONTROLLER_AUTO_HIDE_TIMEOUT)
            .coerceIn(1, 60)
        preferencesRepository.updatePlayerPreferences {
            it.copy(controllerAutoHideTimeout = seconds)
        }
        Logger.info(TAG, "controllerAutoHideTimeout set to $seconds")
    }

    private suspend fun addRemoteServer(
        remoteServerRepository: RemoteServerRepository,
        intent: Intent,
    ) {
        val protocolName = intent.getStringExtra(EXTRA_PROTOCOL)?.uppercase() ?: return
        val protocol = ServerProtocol.entries.firstOrNull { it.name == protocolName } ?: return
        val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        if (host.isBlank()) return

        val server = RemoteServer(
            id = intent.getLongExtra(EXTRA_ID, 0L),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            protocol = protocol,
            host = host,
            port = intent.getIntExtra(EXTRA_PORT, 0).takeIf { it > 0 },
            path = intent.getStringExtra(EXTRA_PATH)?.ifBlank { "/" } ?: "/",
            username = intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
            password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
            isProxyEnabled = intent.getBooleanExtra(EXTRA_PROXY_ENABLED, false),
            proxyHost = intent.getStringExtra(EXTRA_PROXY_HOST).orEmpty(),
            proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, 0).takeIf { it > 0 },
        )

        val id = if (server.id > 0) {
            remoteServerRepository.update(server)
            server.id
        } else {
            remoteServerRepository.insert(server)
        }
        Logger.info(TAG, "Upserted remote server id=$id protocol=${server.protocol} host=${server.host} path=${server.path}")
    }

    private suspend fun deleteRemoteServer(
        remoteServerRepository: RemoteServerRepository,
        intent: Intent,
    ) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return
        remoteServerRepository.deleteById(id)
        Logger.info(TAG, "Deleted remote server id=$id")
    }

    private suspend fun refreshLibrary(
        mediaSynchronizer: MediaSynchronizer,
    ) {
        Logger.info(TAG, "refreshLibrary start")
        mediaSynchronizer.refresh(null)
        mediaSynchronizer.startSync()
        Logger.info(TAG, "Triggered media library refresh")
    }

    companion object {
        private const val TAG = "DebugCommandReceiver"
        private const val MAX_WAIT_MEDIA_ITEM_ATTEMPTS = 20
        private const val WAIT_MEDIA_ITEM_INTERVAL_MS = 250L
        const val ACTION_SET_IGNORE_NOMEDIA = "one.next.player.debug.SET_IGNORE_NOMEDIA"
        const val ACTION_SET_LONG_PRESS_CONTROLS = "one.next.player.debug.SET_LONG_PRESS_CONTROLS"
        const val ACTION_SET_LONG_PRESS_OVERLAY = "one.next.player.debug.SET_LONG_PRESS_OVERLAY"
        const val ACTION_SET_VIDEO_FILTERS = "one.next.player.debug.SET_VIDEO_FILTERS"
        const val ACTION_ADD_ONLINE_SUBTITLE = "one.next.player.debug.ADD_ONLINE_SUBTITLE"
        const val ACTION_SET_CONTROLLER_TIMEOUT = "one.next.player.debug.SET_CONTROLLER_TIMEOUT"
        const val ACTION_REFRESH_LIBRARY = "one.next.player.debug.REFRESH_LIBRARY"
        const val ACTION_ADD_REMOTE_SERVER = "one.next.player.debug.ADD_REMOTE_SERVER"
        const val ACTION_DELETE_REMOTE_SERVER = "one.next.player.debug.DELETE_REMOTE_SERVER"

        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PATH = "path"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_PROXY_ENABLED = "proxy_enabled"
        const val EXTRA_PROXY_HOST = "proxy_host"
        const val EXTRA_PROXY_PORT = "proxy_port"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_URL = "url"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_BRIGHTNESS = "brightness"
        const val EXTRA_CONTRAST = "contrast"
        const val EXTRA_SATURATION = "saturation"
        const val EXTRA_HUE = "hue"
        const val EXTRA_GAMMA = "gamma"
        const val EXTRA_SHARPENING = "sharpening"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugCommandReceiverEntryPoint {
    fun preferencesRepository(): PreferencesRepository
    fun mediaSynchronizer(): MediaSynchronizer
    fun remoteServerRepository(): RemoteServerRepository
    fun onlineSubtitleRepository(): OnlineSubtitleRepository
}
