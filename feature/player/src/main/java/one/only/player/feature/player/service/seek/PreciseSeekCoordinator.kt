package one.only.player.feature.player.service.seek

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toFile
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.SeekMap
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.core.common.Logger
import one.only.player.core.common.extensions.getPath
import one.only.player.feature.player.engine.media3.MkvCuePoint
import one.only.player.feature.player.engine.media3.MkvCuesParser
import one.only.player.feature.player.engine.media3.buildSeekMapFromCues
import one.only.player.feature.player.extensions.copy
import one.only.player.feature.player.extensions.isApproximateSeekEnabled
import one.only.player.feature.player.service.CustomCommands

internal class PreciseSeekCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentPlayerProvider: () -> ExoPlayer?,
    private val createMediaSource: (MediaItem) -> androidx.media3.exoplayer.source.MediaSource,
    private val resolvePlaybackStateUri: suspend (MediaItem) -> String,
    private val updatePlaybackPosition: suspend (String, Long) -> Unit,
    private val mediaLogSummary: (String) -> String,
    private val shouldUseFastSeek: (MediaItem) -> Boolean,
) {

    private var pendingPromotionJob: Job? = null
    private var requestId = 0L
    private var pendingStartupResumeToken: String? = null
    private var pendingStartupResumePositionMs: Long? = null

    private val seekMapCache = ConcurrentHashMap<String, SeekMap>()
    private val cueParseJobs = ConcurrentHashMap<String, Deferred<SeekMap?>>()
    private val preciseSeekMediaIds = ConcurrentHashMap.newKeySet<String>()

    operator fun contains(mediaId: String): Boolean = mediaId in preciseSeekMediaIds

    fun cachedSeekMap(mediaId: String): SeekMap? = seekMapCache[mediaId]

    fun clearPreciseMediaIds() {
        preciseSeekMediaIds.clear()
    }

    fun resetForMediaItem(mediaId: String?) {
        cancelPendingPromotion()
        requestId++
        preciseSeekMediaIds.retainAll(setOfNotNull(mediaId))
    }

    fun release() {
        cancelPendingPromotion()
        requestId++
        cueParseJobs.clear()
    }

    fun prepareCachedMediaItems(mediaItems: List<MediaItem>) {
        mediaItems.forEach { mediaItem ->
            if (!mediaItem.mediaMetadata.isApproximateSeekEnabled) return@forEach
            restoreCachedSeekMap(mediaItem)?.let { seekMap ->
                seekMapCache[mediaItem.mediaId] = seekMap
                preciseSeekMediaIds.add(mediaItem.mediaId)
            }
        }
    }

    fun prepareStartup(
        mediaItem: MediaItem,
        resumePositionMs: Long?,
    ) {
        val mediaId = mediaItem.mediaId
        val resumePosition = resumePositionMs?.takeIf { it >= STARTUP_PRECISE_RESUME_THRESHOLD_MS }
        // 加入播放列表时已用缓存索引创建了可跳转的媒体源，无需重建
        if (mediaId in preciseSeekMediaIds) {
            resumePosition?.let { currentPlayerProvider()?.seekTo(it) }
            return
        }
        // 时长未知时无法构建索引，保持快速起播，等首帧补齐时长后再准备
        if (mediaItem.mediaMetadata.durationMs == null) {
            resumePosition?.let { deferStartupResume(mediaId, it) }
            return
        }

        pendingPromotionJob?.cancel()
        pendingPromotionJob = scope.launch {
            val seekMap = withContext(Dispatchers.IO) { scheduleCueCache(mediaItem).await() }
            if (seekMap == null && resumePosition != null) {
                deferStartupResume(mediaId, resumePosition)
                return@launch
            }

            val player = currentPlayerProvider() ?: return@launch
            val currentItem = player.currentMediaItem ?: return@launch
            if (currentItem.mediaId != mediaId) return@launch
            val position = resumePosition ?: player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
            replaceWithPreciseSource(player, currentItem, position, seekMap)
            resumePosition?.let {
                Logger.info(TAG, "Resume cached precise-seek media=${mediaLogSummary(mediaId)} position=$it")
            }
        }
    }

    private fun scheduleCueCache(mediaItem: MediaItem): Deferred<SeekMap?> {
        val mediaId = mediaItem.mediaId
        seekMapCache[mediaId]?.let { return CompletableDeferred(it) }
        cueParseJobs[mediaId]?.let { return it }

        val restoredSeekMap = restoreCachedSeekMap(mediaItem)
        if (restoredSeekMap != null) {
            seekMapCache[mediaId] = restoredSeekMap
            return CompletableDeferred(restoredSeekMap)
        }

        // 必须用具名参数：CompletableDeferred(null) 会匹配 parent 重载，得到永不完成的 Deferred
        val durationMs = mediaItem.mediaMetadata.durationMs ?: return CompletableDeferred(value = null)
        val uri = Uri.parse(mediaId)

        val parseJob = scope.async(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cuePoints = MkvCuesParser.parse(context, uri)
            val elapsed = System.currentTimeMillis() - startTime

            if (cuePoints == null) {
                Logger.debug(TAG, "MKV Cues pre-parse returned null for ${mediaLogSummary(mediaId)} (${elapsed}ms)")
                return@async null
            }

            val durationUs = durationMs * 1_000L
            val seekMap = buildSeekMapFromCues(cuePoints, durationUs)
            seekMapCache[mediaId] = seekMap
            persistSeekMap(uri, cuePoints, durationUs)
            Logger.info(
                TAG,
                "MKV Cues pre-parsed: ${cuePoints.size} cue points in ${elapsed}ms for ${mediaLogSummary(mediaId)}",
            )
            seekMap
        }
        parseJob.invokeOnCompletion {
            cueParseJobs.remove(mediaId, parseJob)
        }
        cueParseJobs[mediaId] = parseJob
        return parseJob
    }

    fun onFirstFrameRendered(currentMediaItem: MediaItem) {
        val mediaId = currentMediaItem.mediaId
        if (!currentMediaItem.mediaMetadata.isApproximateSeekEnabled || mediaId in preciseSeekMediaIds) {
            clearPendingStartupResume()
            return
        }

        val resumePosition = pendingStartupResumePositionMs?.takeIf { pendingStartupResumeToken == mediaId }
        clearPendingStartupResume()
        if (resumePosition == null) {
            // 首帧补齐时长后提前解析索引，首次跳转无需等待
            scheduleCueCache(currentMediaItem)
            return
        }

        val player = currentPlayerProvider() ?: return
        if (player.currentPosition >= resumePosition - 1_000L) return
        pendingPromotionJob?.cancel()
        pendingPromotionJob = scope.launch {
            Logger.info(TAG, "Continue deferred resume media=${mediaLogSummary(mediaId)} position=$resumePosition")
            promoteCurrentItemToPreciseSeek(resumePosition)
        }
    }

    suspend fun requestSeekForCurrentItem(targetPositionMs: Long): SessionResult {
        val player = currentPlayerProvider() ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val currentItem = player.currentMediaItem ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        // 用户跳转优先于启动阶段尚未完成的升级和恢复进度
        cancelPendingPromotion()
        val maxPosition = currentItem.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = maxPosition?.let { targetPositionMs.coerceIn(0L, it) } ?: targetPositionMs.coerceAtLeast(0L)

        if (!currentItem.mediaMetadata.isApproximateSeekEnabled) {
            Logger.info(TAG, "Precise seek direct media=${mediaLogSummary(currentItem.mediaId)} target=$targetPosition")
            player.seekTo(targetPosition)
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        val startPosition = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
        val startDelta = kotlin.math.abs(targetPosition - startPosition)
        if (startDelta < FAST_SEEK_MIN_DELTA_MS) {
            Logger.info(
                TAG,
                "Skip precise promotion media=${mediaLogSummary(currentItem.mediaId)} target=$targetPosition start=$startPosition",
            )
            return SessionResult(
                SessionResult.RESULT_SUCCESS,
                Bundle().apply { putBoolean(CustomCommands.SEEK_WAS_APPLIED_KEY, false) },
            )
        }

        if (shouldUseFastSeek(currentItem) && player.isCurrentMediaItemSeekable) {
            Logger.info(TAG, "Fast seek media=${mediaLogSummary(currentItem.mediaId)} start=$startPosition target=$targetPosition")
            scheduleCueCache(currentItem)
            player.seekTo(targetPosition)
            scope.launch {
                val playbackStateUri = resolvePlaybackStateUri(currentItem)
                updatePlaybackPosition(playbackStateUri, targetPosition)
            }
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        Logger.info(
            TAG,
            "Promote to precise seek media=${mediaLogSummary(currentItem.mediaId)} start=$startPosition target=$targetPosition",
        )
        return promoteCurrentItemToPreciseSeek(targetPosition)
    }

    private suspend fun promoteCurrentItemToPreciseSeek(targetPositionMs: Long): SessionResult {
        val currentRequestId = ++requestId
        val initialPlayer = currentPlayerProvider() ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val initialItem = initialPlayer.currentMediaItem ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val maxPosition = initialItem.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: initialPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = maxPosition?.let { targetPositionMs.coerceIn(0L, it) } ?: targetPositionMs.coerceAtLeast(0L)

        if (!initialItem.mediaMetadata.isApproximateSeekEnabled || initialItem.mediaId in preciseSeekMediaIds) {
            Logger.info(TAG, "Precise seek direct media=${mediaLogSummary(initialItem.mediaId)} target=$targetPosition")
            initialPlayer.seekTo(targetPosition)
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        val cachedSeekMap = seekMapCache[initialItem.mediaId] ?: restoreCachedSeekMap(initialItem)
        if (cachedSeekMap == null && shouldUseFastSeek(initialItem) && initialPlayer.isCurrentMediaItemSeekable) {
            Logger.info(TAG, "Fast seek without cached cues media=${mediaLogSummary(initialItem.mediaId)} target=$targetPosition")
            scheduleCueCache(initialItem)
            initialPlayer.seekTo(targetPosition)
            scope.launch {
                val playbackStateUri = resolvePlaybackStateUri(initialItem)
                updatePlaybackPosition(playbackStateUri, targetPosition)
            }
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        // 索引未就绪时等待解析完成；期间有更新的请求或切换了媒体，本次请求作废
        val seekMap = cachedSeekMap ?: run {
            Logger.info(TAG, "Precise seek awaiting cues media=${mediaLogSummary(initialItem.mediaId)} target=$targetPosition")
            scheduleCueCache(initialItem).await()
        }
        if (currentRequestId != requestId) {
            return SessionResult(SessionError.ERROR_BAD_VALUE)
        }

        val player = currentPlayerProvider() ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val currentItem = player.currentMediaItem ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        if (currentItem.mediaId != initialItem.mediaId) {
            return SessionResult(SessionError.ERROR_BAD_VALUE)
        }
        if (!currentItem.mediaMetadata.isApproximateSeekEnabled || currentItem.mediaId in preciseSeekMediaIds) {
            Logger.info(TAG, "Precise seek direct media=${mediaLogSummary(currentItem.mediaId)} target=$targetPosition")
            player.seekTo(targetPosition)
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        Logger.info(
            TAG,
            "Promote current item to precise seek media=${mediaLogSummary(currentItem.mediaId)} target=$targetPosition hasCachedSeekMap=${seekMap != null}",
        )
        replaceWithPreciseSource(player, currentItem, targetPosition, seekMap)
        scope.launch {
            val playbackStateUri = resolvePlaybackStateUri(currentItem)
            updatePlaybackPosition(playbackStateUri, targetPosition)
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    // seekMap 为空时改用常规媒体源，由播放器自行加载 Cues
    private fun replaceWithPreciseSource(
        player: ExoPlayer,
        currentItem: MediaItem,
        positionMs: Long,
        seekMap: SeekMap?,
    ) {
        val mediaId = currentItem.mediaId
        val currentIndex = player.currentMediaItemIndex
        seekMap?.let { seekMapCache[mediaId] = it }
        preciseSeekMediaIds.add(mediaId)
        val updatedMediaItem = currentItem.copy(
            positionMs = positionMs,
            isApproximateSeekEnabled = false,
        )
        val shouldPlayWhenReady = player.playWhenReady
        player.addMediaSource(currentIndex + 1, createMediaSource(updatedMediaItem))
        player.seekTo(currentIndex + 1, positionMs)
        player.removeMediaItem(currentIndex)
        player.prepare()
        player.playWhenReady = shouldPlayWhenReady
    }

    private fun deferStartupResume(
        mediaId: String,
        positionMs: Long,
    ) {
        Logger.info(TAG, "Resume deferred precise-seek media=${mediaLogSummary(mediaId)} position=$positionMs")
        pendingStartupResumeToken = mediaId
        pendingStartupResumePositionMs = positionMs
    }

    private fun cancelPendingPromotion() {
        pendingPromotionJob?.cancel()
        pendingPromotionJob = null
        clearPendingStartupResume()
    }

    private fun clearPendingStartupResume() {
        pendingStartupResumeToken = null
        pendingStartupResumePositionMs = null
    }

    private fun cueCacheFile(uri: Uri): File? {
        val path = runCatching {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> uri.toFile().absolutePath
                ContentResolver.SCHEME_CONTENT -> context.getPath(uri)
                else -> null
            }
        }.getOrNull() ?: return null
        val sourceFile = File(path)
        if (!sourceFile.exists()) return null
        val cacheKey = sourceFile.absolutePath.hashCode().toUInt().toString(16)
        val cacheDir = File(context.cacheDir, "mkv-cues")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return File(cacheDir, "mkv-cues-$cacheKey.bin")
    }

    private fun persistSeekMap(
        uri: Uri,
        cuePoints: List<MkvCuePoint>,
        durationUs: Long,
    ) {
        val sourceFile = resolveLocalFile(uri) ?: return
        val cacheFile = cueCacheFile(uri) ?: return
        runCatching {
            DataOutputStream(cacheFile.outputStream().buffered()).use { output ->
                output.writeInt(MKV_CUES_CACHE_MAGIC)
                output.writeLong(sourceFile.length())
                output.writeLong(sourceFile.lastModified())
                output.writeLong(durationUs)
                output.writeInt(cuePoints.size)
                cuePoints.forEach { cuePoint ->
                    output.writeLong(cuePoint.timeUs)
                    output.writeLong(cuePoint.clusterPosition)
                }
            }
        }.onFailure {
            cacheFile.delete()
            Logger.debug(TAG, "Failed to persist MKV cue cache for ${mediaLogSummary(uri.toString())}")
        }
    }

    private fun restoreCachedSeekMap(mediaItem: MediaItem): SeekMap? {
        val uri = Uri.parse(mediaItem.mediaId)
        val sourceFile = resolveLocalFile(uri) ?: return null
        val cacheFile = cueCacheFile(uri) ?: return null
        if (!cacheFile.exists()) return null

        return runCatching {
            DataInputStream(cacheFile.inputStream().buffered()).use { input ->
                val magic = input.readInt()
                if (magic != MKV_CUES_CACHE_MAGIC) return@runCatching null
                val fileSize = input.readLong()
                val lastModified = input.readLong()
                if (fileSize != sourceFile.length() || lastModified != sourceFile.lastModified()) {
                    return@runCatching null
                }
                val durationUs = input.readLong()
                val count = input.readInt()
                val cuePoints = List(count) {
                    MkvCuePoint(
                        timeUs = input.readLong(),
                        clusterPosition = input.readLong(),
                    )
                }
                buildSeekMapFromCues(cuePoints, durationUs)
            }
        }.getOrNull()
    }

    private fun resolveLocalFile(uri: Uri): File? = when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> runCatching { uri.toFile() }.getOrNull()
        ContentResolver.SCHEME_CONTENT -> context.getPath(uri)?.let(::File)
        else -> null
    }?.takeIf(File::exists)

    private companion object {
        private const val TAG = "PreciseSeekCoordinator"
        private const val FAST_SEEK_MIN_DELTA_MS = 2_000L
        private const val STARTUP_PRECISE_RESUME_THRESHOLD_MS = 10_000L
        private const val MKV_CUES_CACHE_MAGIC = 0x4E505145
    }
}
