package one.next.player

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
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
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import kotlin.math.abs
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.FileSystem
import one.next.player.core.common.Logger

class VideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
    private val diskCache: Lazy<DiskCache?>,
) : Decoder {

    companion object {
        // 缩略图最大尺寸，避免 4K 全分辨率 Bitmap 占用过多内存
        private const val MAX_THUMBNAIL_SIZE = 512
        private val mediaInfoSemaphore = Semaphore(1)
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
        get() = "$sourceCacheKey#thumbnail=${strategy.cacheKey}"

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

        if (strategy is ThumbnailStrategy.FirstFrame) {
            tryLoadSystemThumbnail()?.let { systemBitmap ->
                val bitmap = writeToDiskCache(systemBitmap)
                return DecodeResult(
                    image = bitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }
        } else {
            logThumbnail { "systemThumbnail skip strategy=${strategy.logName} key=$key" }
        }

        val mediaInfoStart = System.currentTimeMillis()
        mediaInfoSemaphore.withPermit {
            getThumbnailFromMediaInfo()
        }?.scaleToFit()?.let { rawBitmap ->
            logThumbnail { "mediaInfo ok ${System.currentTimeMillis() - mediaInfoStart}ms strategy=${strategy.logName} key=$key" }
            val bitmap = writeToDiskCache(rawBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        tryLoadSystemThumbnail()?.let { systemBitmap ->
            logThumbnail { "systemThumbnail fallback strategy=${strategy.logName} key=$key" }
            val bitmap = writeToDiskCache(systemBitmap)
            return DecodeResult(
                image = bitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }

        logThumbnail { "decode fail strategy=${strategy.logName} key=$key" }
        throw IllegalStateException("Failed to get video thumbnail for key=$key")
    }

    private fun getThumbnailFromMediaInfo(): Bitmap? {
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
            when (strategy) {
                is ThumbnailStrategy.FirstFrame -> {
                    mediaInfo.getFrame().also { frame ->
                        logThumbnail { "mediaInfo firstFrame result=${frame != null} key=$key" }
                    }
                }
                is ThumbnailStrategy.FrameAtPercentage -> {
                    val timeMs = (duration * strategy.percentage).toLong()
                    mediaInfo.getFrameAt(timeMs).also { frame ->
                        logThumbnail { "mediaInfo frameAt timeMs=$timeMs result=${frame != null} key=$key" }
                    }
                }
                is ThumbnailStrategy.Hybrid -> {
                    val firstFrame = mediaInfo.getFrame()
                    val isFirstFrameSolid = firstFrame != null && isSolidColor(firstFrame)
                    logThumbnail { "mediaInfo hybrid firstFrame=${firstFrame != null} solid=$isFirstFrameSolid key=$key" }
                    if (firstFrame != null && isFirstFrameSolid) {
                        val timeMs = (duration * strategy.percentage).toLong()
                        val fallbackFrame = mediaInfo.getFrameAt(timeMs).also { frame ->
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
        } catch (e: Exception) {
            logThumbnail { "mediaInfo frame fail strategy=${strategy.logName} key=$key err=${e.message}" }
            null
        } finally {
            mediaInfo.release()
        }
    }

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
            )
        }

        private fun isApplicable(mimeType: String?): Boolean = mimeType != null && mimeType.startsWith("video/")
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
