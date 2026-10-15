package one.only.player

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.ThumbnailGenerationStrategy

@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository,
        @Dispatcher(DispatcherType.IO) ioDispatcher: CoroutineDispatcher,
    ): ImageLoader {
        val thumbnailStrategy = {
            val preferences = preferencesRepository.applicationPreferences.value
            when (preferences.thumbnailGenerationStrategy) {
                ThumbnailGenerationStrategy.FIRST_FRAME -> ThumbnailStrategy.FirstFrame
                ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> ThumbnailStrategy.FrameAtPercentage(preferences.thumbnailFramePosition)
                ThumbnailGenerationStrategy.HYBRID -> ThumbnailStrategy.Hybrid(preferences.thumbnailFramePosition)
            }
        }
        val diskCache = DiskCache.Builder()
            .fileSystem(FileSystem.SYSTEM)
            .directory(context.filesDir.resolve("thumbnails"))
            .maxSizePercent(1.0)
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(CloudThumbnailInterceptor(diskCache, thumbnailStrategy, ioDispatcher, CloudThumbnailScheduler(context)))
                add(VideoThumbnailDecoder.Factory(thumbnailStrategy))
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache(diskCache)
            .crossfade(true)
            .build()
    }
}
