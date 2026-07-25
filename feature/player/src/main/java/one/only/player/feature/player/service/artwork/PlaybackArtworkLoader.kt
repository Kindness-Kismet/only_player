package one.only.player.feature.player.service.artwork

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.only.player.feature.player.R

internal class PlaybackArtworkLoader(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val scope: CoroutineScope,
    private val findMediaItem: suspend (String) -> Triple<Player, Int, MediaItem>?,
) {

    fun defaultArtworkUri(): Uri = Uri.Builder().apply {
        val defaultArtwork = R.drawable.artwork_default
        scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        authority(context.resources.getResourcePackageName(defaultArtwork))
        appendPath(context.resources.getResourceTypeName(defaultArtwork))
        appendPath(context.resources.getResourceEntryName(defaultArtwork))
    }.build()

    fun loadInBackground(mediaItems: List<MediaItem>) {
        scope.launch(Dispatchers.Default) {
            mediaItems.forEach { mediaItem ->
                launch {
                    val artworkData = loadArtworkForMediaItem(mediaItem) ?: return@launch

                    withContext(Dispatchers.Main) {
                        val (player, index, currentMediaItem) = findMediaItem(mediaItem.mediaId) ?: return@withContext
                        val updatedMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(
                                currentMediaItem.mediaMetadata.buildUpon()
                                    // 系统媒体控件优先使用 artworkData 显示封面
                                    .setArtworkUri(null)
                                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build(),
                            )
                            .build()
                        player.replaceMediaItem(index, updatedMediaItem)
                    }
                }
            }
        }
    }

    private suspend fun loadArtworkForMediaItem(mediaItem: MediaItem): ByteArray? {
        val candidates = buildList {
            add(mediaItem.mediaId.toUri())
            mediaItem.localConfiguration?.uri?.let(::add)
            mediaItem.requestMetadata.mediaUri?.let(::add)
            mediaItem.mediaMetadata.artworkUri?.let(::add)
        }.distinct()
        for (uri in candidates) {
            loadArtworkForUri(uri)?.let { return it }
        }
        return null
    }

    private suspend fun loadArtworkForUri(uri: Uri): ByteArray? = try {
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(uri)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()?.toByteArray()
    } catch (_: Exception) {
        null
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
