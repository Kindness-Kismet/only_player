package one.only.player

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.get
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfo
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.MediaThumbnailRetriever
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import one.only.player.core.common.Logger
import one.only.player.core.common.media.MpegTsProgramMapPidFix
import one.only.player.core.common.media.detectMpegTsProgramMapPidFix
import one.only.player.core.common.media.patchMpegTsProgramMapPid
import one.only.player.core.common.media.toMpegTsPidHex

private const val MPEG_TS_PACKET_SIZE_BYTES = 188

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
    private val diskCache: Lazy<DiskCache?>,
    private val mimeType: String?,
) : Decoder {

    companion object {
        // 缩略图最大尺寸，避免 4K 全分辨率 Bitmap 占用过多内存
        private const val MAX_THUMBNAIL_SIZE = 512
        private const val THUMBNAIL_CACHE_VERSION = 5
        private const val LARGE_CONTAINER_ARTWORK_LIMIT_BYTES = 256L * 1024L * 1024L
        private const val MIN_MPEG_TS_THUMBNAIL_SCORE = 80f
        private const val GOOD_MPEG_TS_THUMBNAIL_SCORE = 120f
        private val VIDEO_CONTAINER_MIME_TYPES = setOf(
            "application/matroska",
            "application/x-matroska",
            "application/webm",
        )
        private val mediaInfoSemaphore = Semaphore(1)
    }

    // 内嵌封面表达作者意图，优先级高于抽帧。
    private fun tryLoadEmbeddedArtwork(shouldSkipArtwork: Boolean): Bitmap? {
        if (shouldSkipArtwork) return null

        val retriever = MediaThumbnailRetriever()
        return try {
            val metadata = source.metadata
            val mediaInfoSource = when {
                metadata is ContentMetadata -> {
                    val uri = metadata.uri.toAndroidUri()
                    retriever.setDataSource(options.context, uri)
                    "contentUri=$uri"
                }
                source.fileSystem === FileSystem.SYSTEM -> {
                    val path = source.file().toFile().path
                    retriever.setDataSource(path)
                    "filePath=$path"
                }
                else -> return null
            }

            val artworkData = retriever.getEmbeddedPicture() ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, bounds)
            BitmapFactory.decodeByteArray(
                artworkData,
                0,
                artworkData.size,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                },
            ).also { bitmap ->
                logThumbnail { "embeddedArtwork result=${bitmap != null} source=$mediaInfoSource key=$diskCacheKey" }
            }
        } catch (e: Exception) {
            logThumbnail { "embeddedArtwork fail key=$diskCacheKey err=${e.message}" }
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    // 大型 Matroska 取 embeddedPicture 可能长时间阻塞，优先保证抽帧可完成。
    private fun shouldSkipEmbeddedArtwork(): Boolean {
        if (!mimeType.isLargeContainerMimeType()) return false

        val sourceSize = getSourceSize() ?: return false
        val shouldSkip = sourceSize > LARGE_CONTAINER_ARTWORK_LIMIT_BYTES
        if (shouldSkip) {
            logThumbnail { "embeddedArtwork skip mimeType=$mimeType size=$sourceSize key=$diskCacheKey" }
        }
        return shouldSkip
    }

    private fun getSourceSize(): Long? {
        val metadata = source.metadata
        return when {
            metadata is ContentMetadata -> getContentUriSize(metadata)
            source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().length().takeIf { it > 0L }
            else -> null
        }
    }

    private fun getContentUriSize(metadata: ContentMetadata): Long? {
        val uri = metadata.uri.toAndroidUri()
        return try {
            options.context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 }
                    ?.let(cursor::getLong)
                    ?.takeIf { it > 0L }
            }
        } catch (_: Exception) {
            null
        }
    }

    // 优先使用系统缩略图服务，质量优于 FFmpeg 提取帧
    private fun tryLoadSystemThumbnail(): Bitmap? {
        val uri = when (val metadata = source.metadata) {
            is ContentMetadata -> metadata.uri.toAndroidUri()
            else -> {
                if (source.fileSystem !== FileSystem.SYSTEM) return null
                findContentUriForPath(source.file().toFile().path) ?: return null
            }
        }
        val start = System.currentTimeMillis()
        return try {
            options.context.contentResolver.loadThumbnail(
                uri,
                Size(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE),
                null,
            ).also {
                logThumbnail { "systemThumbnail ok ${System.currentTimeMillis() - start}ms uri=$uri" }
            }
        } catch (e: Exception) {
            logThumbnail { "systemThumbnail fail ${System.currentTimeMillis() - start}ms uri=$uri err=${e.message}" }
            null
        }
    }

    // 通过文件路径查询 MediaStore 获取 content:// URI
    private fun findContentUriForPath(path: String): android.net.Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return try {
            options.context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private val sourceCacheKey: String
        get() = options.diskCacheKey ?: run {
            val metadata = source.metadata
            when {
                metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
                source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
                else -> error("Not supported")
            }
        }

    private val diskCacheKey: String
        get() = "$sourceCacheKey#thumbnail=v$THUMBNAIL_CACHE_VERSION:${strategy.cacheKey}"

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        val key = diskCacheKey
        logThumbnail { "decode start strategy=${strategy.logName} key=$key" }
        readFromDiskCache()?.use { snapshot ->
            val file = snapshot.data.toFile()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)

            val cachedBitmap = BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
                },
            )

            if (cachedBitmap != null) {
                logThumbnail { "diskCache hit strategy=${strategy.logName} key=$key" }
                return DecodeResult(
                    image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }
        }
        logThumbnail { "diskCache miss strategy=${strategy.logName} key=$key" }

        val shouldSkipArtwork = shouldSkipEmbeddedArtwork()
        var mpegTsProgramMapPidFix = if (mimeType.isMpegTsMimeType()) {
            detectMpegTsProgramMapPidFix()
        } else {
            null
        }
        val shouldPreferMediaInfo = shouldSkipArtwork || mpegTsProgramMapPidFix != null
        var hasTriedPatchedMpegTsThumbnail = false
        tryLoadEmbeddedArtwork(shouldSkipArtwork)?.scaleToFit()?.let { artworkBitmap ->
            logThumbnail { "embeddedArtwork ok strategy=${strategy.logName} key=$key" }
            val bitmap = writeToDiskCache(artworkBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        if (shouldPreferMediaInfo) {
            hasTriedPatchedMpegTsThumbnail = mpegTsProgramMapPidFix != null
            tryDecodeMediaInfoThumbnail(
                shouldRejectNearBlack = mpegTsProgramMapPidFix != null,
                mpegTsProgramMapPidFix = mpegTsProgramMapPidFix,
            )?.let { return it }
        }

        tryLoadSystemThumbnail()?.takeUnless { systemBitmap ->
            val isNearBlackThumbnail = isNearBlackFrame(systemBitmap)
            if (mpegTsProgramMapPidFix == null && isNearBlackThumbnail && mimeType.shouldSniffMpegTsAfterBlackThumbnail()) {
                mpegTsProgramMapPidFix = detectMpegTsProgramMapPidFix()
            }
            val isBlackMpegTsThumbnail = mpegTsProgramMapPidFix != null && isNearBlackThumbnail
            if (isBlackMpegTsThumbnail) {
                logThumbnail { "systemThumbnail skip nearBlack patchedTs key=$key" }
                systemBitmap.recycle()
            }
            isBlackMpegTsThumbnail
        }?.let { systemBitmap ->
            logThumbnail { "systemThumbnail ok strategy=${strategy.logName} key=$key" }
            val bitmap = writeToDiskCache(systemBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        if (mpegTsProgramMapPidFix != null && !hasTriedPatchedMpegTsThumbnail) {
            tryDecodeMediaInfoThumbnail(
                shouldRejectNearBlack = true,
                mpegTsProgramMapPidFix = mpegTsProgramMapPidFix,
            )?.let { return it }
        }

        if (!shouldPreferMediaInfo) {
            tryDecodeMediaInfoThumbnail(shouldRejectNearBlack = false)?.let { return it }
        }

        logThumbnail { "decode fail strategy=${strategy.logName} key=$key" }
        throw IllegalStateException("Failed to get video thumbnail for key=$key")
    }

    private suspend fun tryDecodeMediaInfoThumbnail(
        shouldRejectNearBlack: Boolean,
        mpegTsProgramMapPidFix: MpegTsProgramMapPidFix? = null,
    ): DecodeResult? {
        val key = diskCacheKey
        val mediaInfoStart = System.currentTimeMillis()
        mediaInfoSemaphore.withPermit {
            if (mpegTsProgramMapPidFix != null) {
                getThumbnailFromPatchedMpegTsRetriever(mpegTsProgramMapPidFix)
                    ?: getThumbnailFromMediaInfo(shouldRejectNearBlack = true)
                    ?: getThumbnailFromRetriever(shouldRejectNearBlack = true)
            } else if (shouldRejectNearBlack) {
                getThumbnailFromMediaInfo(shouldRejectNearBlack = true)
                    ?: getThumbnailFromRetriever(shouldRejectNearBlack = true)
            } else {
                getThumbnailFromMediaInfo(shouldRejectNearBlack = false)
            }
        }?.scaleToFit()?.let { rawBitmap ->
            logThumbnail { "mediaInfo ok ${System.currentTimeMillis() - mediaInfoStart}ms strategy=${strategy.logName} key=$key" }
            val bitmap = writeToDiskCache(rawBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }
        return null
    }

    private fun detectMpegTsProgramMapPidFix(): MpegTsProgramMapPidFix? {
        val metadata = source.metadata
        return when {
            metadata is ContentMetadata -> metadata.uri.toAndroidUri().detectMpegTsProgramMapPidFix(options.context)
            source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().detectMpegTsProgramMapPidFix()
            else -> null
        }
    }

    private fun getThumbnailFromPatchedMpegTsRetriever(fix: MpegTsProgramMapPidFix): Bitmap? {
        val key = diskCacheKey
        val sourceSize = getSourceSize() ?: return null
        val sourceFileDescriptor = openSourceFileDescriptor() ?: return null
        val dataSource = PatchedMpegTsMediaDataSource(
            sourceFileDescriptor = sourceFileDescriptor,
            sourceSize = sourceSize,
            fix = fix,
        )
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(dataSource)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            logThumbnail {
                "platformRetriever patchedTs built duration=$duration pmt=${fix.declaredPmtPid.toMpegTsPidHex()}->${fix.actualPmtPid.toMpegTsPidHex()} key=$key"
            }
            getMpegTsFrameFromPlatformRetriever(retriever, duration, key)
        } catch (e: Exception) {
            logThumbnail { "platformRetriever patchedTs fail key=$key err=${e.message}" }
            null
        } finally {
            runCatching { retriever.release() }
            runCatching { dataSource.close() }
        }
    }

    private fun getMpegTsFrameFromPlatformRetriever(
        retriever: MediaMetadataRetriever,
        duration: Long,
        key: String,
    ): Bitmap? {
        var bestFrame: Bitmap? = null
        var bestScore = 0f
        mpegTsCandidateTimesMs(duration).forEach { timeMs ->
            val frame = runCatching {
                retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
            }.getOrNull()
            val isNearBlack = frame != null && isNearBlackFrame(frame)
            val visibilityScore = frame?.visibilityScore() ?: 0f
            logThumbnail { "platformRetriever patchedTs timeMs=$timeMs result=${frame != null} nearBlack=$isNearBlack score=$visibilityScore key=$key" }
            if (frame != null && !isNearBlack && visibilityScore > bestScore) {
                if (visibilityScore >= GOOD_MPEG_TS_THUMBNAIL_SCORE) {
                    bestFrame?.recycle()
                    return frame
                }
                bestFrame?.recycle()
                bestFrame = frame
                bestScore = visibilityScore
            } else {
                frame?.recycle()
            }
        }
        if (bestScore >= MIN_MPEG_TS_THUMBNAIL_SCORE) return bestFrame

        bestFrame?.recycle()
        logThumbnail { "platformRetriever patchedTs noVisibleFrame bestScore=$bestScore key=$key" }
        return null
    }

    private fun openSourceFileDescriptor(): ParcelFileDescriptor? {
        val metadata = source.metadata
        return try {
            when {
                metadata is ContentMetadata -> options.context.contentResolver.openFileDescriptor(
                    metadata.uri.toAndroidUri(),
                    "r",
                )
                source.fileSystem === FileSystem.SYSTEM -> ParcelFileDescriptor.open(
                    source.file().toFile(),
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getThumbnailFromMediaInfo(shouldRejectNearBlack: Boolean): Bitmap? {
        val key = diskCacheKey
        val metadata = source.metadata
        val mediaInfoSource = when {
            metadata is ContentMetadata -> "contentUri=${metadata.uri}"
            source.fileSystem === FileSystem.SYSTEM -> "filePath=${source.file().toFile().path}"
            else -> "unsupported"
        }
        val mediaInfo = try {
            when {
                metadata is ContentMetadata -> {
                    MediaInfoBuilder().from(
                        context = options.context,
                        uri = metadata.uri.toAndroidUri(),
                    ).build()
                }
                source.fileSystem === FileSystem.SYSTEM -> {
                    MediaInfoBuilder().from(
                        filePath = source.file().toFile().path,
                    ).build()
                }
                else -> null
            }
        } catch (e: Exception) {
            logThumbnail { "mediaInfo build fail strategy=${strategy.logName} source=$mediaInfoSource err=${e.message}" }
            null
        } ?: return null

        return try {
            val duration = mediaInfo.duration
            logThumbnail { "mediaInfo built strategy=${strategy.logName} duration=$duration source=$mediaInfoSource key=$key" }
            if (shouldRejectNearBlack) {
                getMpegTsFrameFromMediaInfo(
                    mediaInfo = mediaInfo,
                    duration = duration,
                    key = key,
                )
            } else {
                when (strategy) {
                    is ThumbnailStrategy.FirstFrame -> {
                        mediaInfo.getFrameAtMillis(0L).also { frame ->
                            logThumbnail { "mediaInfo firstFrame result=${frame != null} key=$key" }
                        }
                    }
                    is ThumbnailStrategy.FrameAtPercentage -> {
                        val timeMs = (duration * strategy.percentage).toLong()
                        mediaInfo.getFrameAtMillis(timeMs).also { frame ->
                            logThumbnail { "mediaInfo frameAt timeMs=$timeMs result=${frame != null} key=$key" }
                        }
                    }
                    is ThumbnailStrategy.Hybrid -> {
                        val firstFrame = mediaInfo.getFrameAtMillis(0L)
                        val isFirstFrameSolid = firstFrame != null && isSolidColor(firstFrame)
                        logThumbnail { "mediaInfo hybrid firstFrame=${firstFrame != null} solid=$isFirstFrameSolid key=$key" }
                        if (firstFrame != null && isFirstFrameSolid) {
                            val timeMs = (duration * strategy.percentage).toLong()
                            val fallbackFrame = mediaInfo.getFrameAtMillis(timeMs).also { frame ->
                                logThumbnail { "mediaInfo hybrid fallback timeMs=$timeMs result=${frame != null} key=$key" }
                            }
                            if (fallbackFrame != null) {
                                firstFrame.recycle()
                                fallbackFrame
                            } else {
                                firstFrame
                            }
                        } else {
                            firstFrame
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logThumbnail { "mediaInfo frame fail strategy=${strategy.logName} key=$key err=${e.message}" }
            null
        } finally {
            mediaInfo.release()
        }
    }

    private fun getMpegTsFrameFromMediaInfo(
        mediaInfo: MediaInfo,
        duration: Long,
        key: String,
    ): Bitmap? {
        mpegTsCandidateTimesMs(duration).forEach { timeMs ->
            val frameAt = mediaInfo.getFrameAtMillis(timeMs)
            val isFrameAtNearBlack = frameAt != null && isNearBlackFrame(frameAt)
            logThumbnail { "mediaInfo mpegTs frameAt timeMs=$timeMs result=${frameAt != null} nearBlack=$isFrameAtNearBlack key=$key" }
            if (frameAt != null && !isFrameAtNearBlack) return frameAt
            frameAt?.recycle()

            val frame = mediaInfo.getDecodedFrameAtMillis(timeMs)
            val isFrameNearBlack = frame != null && isNearBlackFrame(frame)
            logThumbnail { "mediaInfo mpegTs frame timeMs=$timeMs result=${frame != null} nearBlack=$isFrameNearBlack key=$key" }
            if (frame != null && !isFrameNearBlack) return frame
            frame?.recycle()
        }
        return null
    }

    private fun getThumbnailFromRetriever(shouldRejectNearBlack: Boolean): Bitmap? {
        val key = diskCacheKey
        val retriever = MediaThumbnailRetriever()
        val mediaInfoSource = try {
            val metadata = source.metadata
            when {
                metadata is ContentMetadata -> {
                    val uri = metadata.uri.toAndroidUri()
                    retriever.setDataSource(options.context, uri)
                    "contentUri=$uri"
                }
                source.fileSystem === FileSystem.SYSTEM -> {
                    val path = source.file().toFile().path
                    retriever.setDataSource(path)
                    "filePath=$path"
                }
                else -> null
            }
        } catch (e: Exception) {
            logThumbnail { "retriever build fail strategy=${strategy.logName} key=$key err=${e.message}" }
            null
        }
        if (mediaInfoSource == null) {
            runCatching { retriever.release() }
            return null
        }

        return try {
            if (shouldRejectNearBlack) {
                getMpegTsFrameFromRetriever(retriever, key)
            } else {
                val timeUs = strategy.primaryTimeMs(duration = 0L) * 1_000L
                retriever.getFrameAtTime(timeUs).also { frame ->
                    logThumbnail { "retriever frameAt source=$mediaInfoSource result=${frame != null} key=$key" }
                }
            }
        } catch (e: Exception) {
            logThumbnail { "retriever frame fail source=$mediaInfoSource strategy=${strategy.logName} key=$key err=${e.message}" }
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun getMpegTsFrameFromRetriever(
        retriever: MediaThumbnailRetriever,
        key: String,
    ): Bitmap? {
        var bestFrame: Bitmap? = null
        var bestScore = 0f

        fun acceptCandidate(
            frame: Bitmap?,
            label: String,
        ): Bitmap? {
            if (frame == null) {
                logThumbnail { "retriever mpegTs $label result=false key=$key" }
                return null
            }

            val isNearBlack = isNearBlackFrame(frame)
            val visibilityScore = frame.visibilityScore()
            logThumbnail { "retriever mpegTs $label result=true nearBlack=$isNearBlack score=$visibilityScore key=$key" }
            if (!isNearBlack && visibilityScore > bestScore) {
                if (visibilityScore >= GOOD_MPEG_TS_THUMBNAIL_SCORE) {
                    bestFrame?.recycle()
                    return frame
                }
                bestFrame?.recycle()
                bestFrame = frame
                bestScore = visibilityScore
            } else {
                frame.recycle()
            }
            return null
        }

        mpegTsCandidateFrameIndexes().forEach { frameIndex ->
            acceptCandidate(
                frame = runCatching { retriever.getFrameAtIndex(frameIndex) }.getOrNull(),
                label = "frameIndex=$frameIndex",
            )?.let { return it }
        }

        mpegTsCandidateTimesMs(duration = 0L).forEach { timeMs ->
            acceptCandidate(
                frame = runCatching { retriever.getFrameAtTime(timeMs * 1_000L) }.getOrNull(),
                label = "timeMs=$timeMs",
            )?.let { return it }
        }
        if (bestScore >= MIN_MPEG_TS_THUMBNAIL_SCORE) return bestFrame

        bestFrame?.recycle()
        logThumbnail { "retriever mpegTs noVisibleFrame bestScore=$bestScore key=$key" }
        return null
    }

    private fun mpegTsCandidateTimesMs(duration: Long): List<Long> {
        val preferredTimeMs = strategy.primaryTimeMs(duration)
            .takeIf { duration <= 0L || it in 0..duration }
        return listOfNotNull(
            preferredTimeMs,
            20_000L,
            30_000L,
            45_000L,
            60_000L,
            120_000L,
            300_000L,
            600_000L,
            1_000L,
            3_000L,
            5_000L,
            10_000L,
            0L,
        ).filter { duration <= 0L || it <= duration }
            .distinct()
    }

    private fun mpegTsCandidateFrameIndexes(): List<Int> = listOf(
        0,
        1,
        5,
        15,
        30,
        60,
        120,
        240,
        480,
        900,
    )

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return 1
        var inSampleSize = 1
        val maxDimension = maxOf(width, height)
        while (maxDimension / (inSampleSize * 2) >= MAX_THUMBNAIL_SIZE) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun Bitmap.scaleToFit(): Bitmap {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) return this
        val scale = MAX_THUMBNAIL_SIZE.toFloat() / maxOf(width, height)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        if (scaled !== this) recycle()
        return scaled
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? = if (options.diskCachePolicy.readEnabled) {
        diskCache.value?.openSnapshot(diskCacheKey)
    } else {
        null
    }

    private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
        if (!options.diskCachePolicy.writeEnabled) return inBitmap
        val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
        try {
            editor.data.toFile().outputStream().use { output ->
                inBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }
            editor.commitAndOpenSnapshot()?.use { snapshot ->
                val outBitmap = snapshot.data.toFile().inputStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
                inBitmap.recycle()
                return outBitmap
            }
        } catch (_: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
        }
        return inBitmap
    }

    class Factory(
        private val thumbnailStrategy: () -> ThumbnailStrategy,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            logThumbnail { "factory create mimeType=${result.mimeType}" }
            if (!isApplicable(result.mimeType)) return null
            val strategy = thumbnailStrategy()
            logThumbnail { "factory strategy=${strategy.logName}" }
            return VideoThumbnailDecoder(
                source = result.source,
                options = options,
                strategy = strategy,
                diskCache = lazy { imageLoader.diskCache },
                mimeType = result.mimeType,
            )
        }

        private fun isApplicable(mimeType: String?): Boolean {
            val normalizedMimeType = mimeType?.lowercase() ?: return false
            return normalizedMimeType.startsWith("video/") || normalizedMimeType in VIDEO_CONTAINER_MIME_TYPES
        }
    }
}

private class PatchedMpegTsMediaDataSource(
    private val sourceFileDescriptor: ParcelFileDescriptor,
    private val sourceSize: Long,
    private val fix: MpegTsProgramMapPidFix,
) : MediaDataSource() {

    private val inputStream = FileInputStream(sourceFileDescriptor.fileDescriptor)
    private val channel = inputStream.channel
    private var isClosed = false

    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        if (size == 0) return 0
        if (position >= sourceSize) return -1

        val readSize = minOf(size.toLong(), sourceSize - position).toInt()
        val bytesRead = channel.read(ByteBuffer.wrap(buffer, offset, readSize), position)
        if (bytesRead <= 0) return -1

        patchOverlappedPackets(
            buffer = buffer,
            bufferOffset = offset,
            readStart = position,
            bytesRead = bytesRead,
        )
        return bytesRead
    }

    override fun getSize(): Long = sourceSize

    override fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { channel.close() }
        runCatching { inputStream.close() }
        runCatching { sourceFileDescriptor.close() }
    }

    private fun patchOverlappedPackets(
        buffer: ByteArray,
        bufferOffset: Int,
        readStart: Long,
        bytesRead: Int,
    ) {
        val readEnd = readStart + bytesRead
        var packetStart = readStart - (readStart % MPEG_TS_PACKET_SIZE_BYTES)
        while (packetStart < readEnd) {
            val packetEnd = packetStart + MPEG_TS_PACKET_SIZE_BYTES
            if (packetEnd > 0L && packetStart < sourceSize) {
                patchOverlappedPacket(
                    buffer = buffer,
                    bufferOffset = bufferOffset,
                    readStart = readStart,
                    readEnd = readEnd,
                    packetStart = packetStart,
                    packetEnd = packetEnd,
                )
            }
            packetStart += MPEG_TS_PACKET_SIZE_BYTES
        }
    }

    private fun patchOverlappedPacket(
        buffer: ByteArray,
        bufferOffset: Int,
        readStart: Long,
        readEnd: Long,
        packetStart: Long,
        packetEnd: Long,
    ) {
        val packet = ByteArray(MPEG_TS_PACKET_SIZE_BYTES)
        val packetBytesRead = channel.read(ByteBuffer.wrap(packet), packetStart)
        if (packetBytesRead != MPEG_TS_PACKET_SIZE_BYTES) return

        packet.patchMpegTsProgramMapPid(
            bufferOffset = 0,
            readStart = packetStart,
            readEnd = packetEnd,
            fix = fix,
        )

        val copyStart = maxOf(readStart, packetStart)
        val copyEnd = minOf(readEnd, packetEnd)
        if (copyStart >= copyEnd) return

        System.arraycopy(
            packet,
            (copyStart - packetStart).toInt(),
            buffer,
            bufferOffset + (copyStart - readStart).toInt(),
            (copyEnd - copyStart).toInt(),
        )
    }
}

sealed class ThumbnailStrategy {
    data object FirstFrame : ThumbnailStrategy()
    data class FrameAtPercentage(val percentage: Float = 0.5f) : ThumbnailStrategy()
    data class Hybrid(val percentage: Float = 0.5f) : ThumbnailStrategy()
}

private val ThumbnailStrategy.logName: String
    get() = when (this) {
        ThumbnailStrategy.FirstFrame -> "firstFrame"
        is ThumbnailStrategy.FrameAtPercentage -> "frameAt:$percentage"
        is ThumbnailStrategy.Hybrid -> "hybrid:$percentage"
    }

private val ThumbnailStrategy.cacheKey: String
    get() = when (this) {
        ThumbnailStrategy.FirstFrame -> "first"
        is ThumbnailStrategy.FrameAtPercentage -> "frameAt:$percentage"
        is ThumbnailStrategy.Hybrid -> "hybrid:$percentage"
    }

private fun ThumbnailStrategy.primaryTimeMs(duration: Long): Long = when (this) {
    ThumbnailStrategy.FirstFrame -> 0L
    is ThumbnailStrategy.FrameAtPercentage -> (duration * percentage).toLong()
    is ThumbnailStrategy.Hybrid -> (duration * percentage).toLong()
}

private fun MediaInfo.getFrameAtMillis(timeMs: Long): Bitmap? = getFrameAt(timeMs.coerceAtLeast(0L) * 1_000L)

private fun MediaInfo.getDecodedFrameAtMillis(timeMs: Long): Bitmap? = getFrame(timeMs.coerceAtLeast(0L) * 1_000L)

private fun String?.isLargeContainerMimeType(): Boolean = when (this?.lowercase()) {
    "application/matroska" -> true
    "application/x-matroska" -> true
    "application/webm" -> true
    "video/x-matroska" -> true
    "video/webm" -> true
    else -> false
}

private fun String?.isMpegTsMimeType(): Boolean = when (this?.lowercase()) {
    "video/mp2t" -> true
    "video/mp2ts" -> true
    "video/mpeg2ts" -> true
    "video/vnd.dlna.mpeg-tts" -> true
    else -> false
}

private fun String?.shouldSniffMpegTsAfterBlackThumbnail(): Boolean = when (this?.lowercase()) {
    null -> true
    "application/mp4" -> true
    "application/octet-stream" -> true
    "video/mp4" -> true
    else -> isMpegTsMimeType()
}

private inline fun logThumbnail(message: () -> String) {
    if (BuildConfig.DEBUG) {
        // 保持完整日志 tag 不超过旧系统 23 字符限制。
        Logger.info("VideoThumb", message())
    }
}

private fun isSolidColor(bitmap: Bitmap, threshold: Float = 0.7f): Boolean {
    val width = bitmap.width
    val height = bitmap.height

    // 采样中心区域网格，避开黑边干扰
    val marginX = width / 10
    val marginY = height / 10
    val sampleAreaRight = width - marginX
    val sampleAreaBottom = height - marginY

    // 构建采样点网格
    val gridSize = 10
    val stepX = (sampleAreaRight - marginX) / gridSize
    val stepY = (sampleAreaBottom - marginY) / gridSize

    if (stepX <= 0 || stepY <= 0) return false

    val sampledColors = mutableListOf<Int>()

    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            val pixelX = marginX + x * stepX
            val pixelY = marginY + y * stepY
            if (pixelX < width && pixelY < height) {
                sampledColors.add(bitmap[pixelX, pixelY])
            }
        }
    }

    if (sampledColors.isEmpty()) return false

    // 以首个颜色作为参考值
    val referenceColor = sampledColors[0]
    val referenceR = (referenceColor shr 16) and 0xFF
    val referenceG = (referenceColor shr 8) and 0xFF
    val referenceB = referenceColor and 0xFF

    // 统计容差内的相似颜色数量
    val tolerance = 30 // RGB 容差
    val similarCount = sampledColors.count { color ->
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        abs(r - referenceR) <= tolerance &&
            abs(g - referenceG) <= tolerance &&
            abs(b - referenceB) <= tolerance
    }

    val similarityRatio = similarCount.toFloat() / sampledColors.size
    return similarityRatio >= threshold
}

private fun isNearBlackFrame(
    bitmap: Bitmap,
    threshold: Float = 0.96f,
): Boolean {
    val width = bitmap.width
    val height = bitmap.height
    val gridSize = 40
    val stepX = width / gridSize
    val stepY = height / gridSize

    if (stepX <= 0 || stepY <= 0) return false

    var sampleCount = 0
    var nearBlackCount = 0
    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            val pixelX = x * stepX + stepX / 2
            val pixelY = y * stepY + stepY / 2
            if (pixelX >= width || pixelY >= height) continue

            val color = bitmap[pixelX, pixelY]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luminance = (0.299f * r) + (0.587f * g) + (0.114f * b)
            sampleCount++
            if (luminance <= 18f) {
                nearBlackCount++
            }
        }
    }

    if (sampleCount == 0) return false
    return nearBlackCount.toFloat() / sampleCount >= threshold
}

private fun Bitmap.visibilityScore(): Float {
    val gridSize = 40
    val stepX = width / gridSize
    val stepY = height / gridSize
    if (stepX <= 0 || stepY <= 0) return 0f

    var sampleCount = 0
    var luminanceSum = 0f
    var visibleSampleCount = 0
    for (x in 0 until gridSize) {
        for (y in 0 until gridSize) {
            val pixelX = x * stepX + stepX / 2
            val pixelY = y * stepY + stepY / 2
            if (pixelX >= width || pixelY >= height) continue

            val color = this[pixelX, pixelY]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val luminance = (0.299f * r) + (0.587f * g) + (0.114f * b)
            sampleCount++
            luminanceSum += luminance
            if (luminance >= 42f || maxOf(r, g, b) >= 56) {
                visibleSampleCount++
            }
        }
    }

    if (sampleCount == 0) return 0f
    val averageLuminance = luminanceSum / sampleCount
    val visibleRatio = visibleSampleCount.toFloat() / sampleCount
    return averageLuminance + visibleRatio * 100f
}
