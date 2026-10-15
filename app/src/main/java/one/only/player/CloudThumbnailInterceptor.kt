package one.only.player

import android.net.Uri
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.toAndroidUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import one.only.player.feature.player.ui.ChapterThumbnailPositionMsExtra

internal class CloudThumbnailInterceptor(
    private val diskCache: DiskCache,
    private val thumbnailStrategy: () -> ThumbnailStrategy,
    private val ioDispatcher: CoroutineDispatcher,
) : Interceptor {

    private val semaphore = Semaphore(2)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val uri = when (val data = request.data) {
            is Uri -> data
            is coil3.Uri -> data.toAndroidUri()
            is String -> Uri.parse(data)
            else -> null
        }
        if (uri?.scheme != "content" || uri.authority != "${request.context.packageName}.documents") {
            return chain.proceed()
        }

        return withContext(ioDispatcher) {
            val strategy = request.extras[ChapterThumbnailPositionMsExtra]
                ?.let(ThumbnailStrategy::FrameAtTime)
                ?: thumbnailStrategy()
            val cacheKey = videoThumbnailDiskCacheKey(
                sourceCacheKey = request.diskCacheKey ?: uri.toString(),
                strategy = strategy,
                isCloudDocument = true,
            )
            // 缓存命中时直接加载图片，避免先连接服务器，也不占远程取图名额。
            if (request.diskCachePolicy.readEnabled) {
                diskCache.openSnapshot(cacheKey)?.use { snapshot ->
                    return@withContext chain.withRequest(
                        request.newBuilder()
                            .data(snapshot.data)
                            .memoryCacheKey(request.memoryCacheKey ?: uri.toString())
                            .build(),
                    ).proceed()
                }
            }

            // 限制整个取图流程；离开屏幕后取消的排队请求不会再打开远程文件。
            semaphore.withPermit { chain.proceed() }
        }
    }
}
