package one.only.player

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okio.FileSystem
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
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(
                VideoThumbnailDecoder.Factory(
                    thumbnailStrategy = {
                        val preferences = preferencesRepository.applicationPreferences.value
                        when (preferences.thumbnailGenerationStrategy) {
                            ThumbnailGenerationStrategy.FIRST_FRAME -> ThumbnailStrategy.FirstFrame
                            ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE -> ThumbnailStrategy.FrameAtPercentage(preferences.thumbnailFramePosition)
                            ThumbnailGenerationStrategy.HYBRID -> ThumbnailStrategy.Hybrid(preferences.thumbnailFramePosition)
                        }
                    },
                ),
            )
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .memoryCache {
            MemoryCache.Builder()
                // 播放器页需要留给 ExoPlayer / Compose，缩略图内存缓存压到约 12%
                .maxSizePercent(context, percent = 0.12)
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .diskCache {
            DiskCache.Builder()
                .fileSystem(FileSystem.SYSTEM)
                .directory(context.filesDir.resolve("thumbnails"))
                // 原 100% 磁盘缓存会间接抬高峰值内存；压到 15%
                .maxSizePercent(0.15)
                .build()
        }
        .crossfade(true)
        .build()
}
