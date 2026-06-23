package one.only.player.core.media.sync

import android.net.Uri

interface MediaInfoSynchronizer {

    fun sync(uri: Uri)

    fun syncAll(uris: List<Uri>)

    suspend fun clearThumbnailsCache()

    suspend fun clearVideoCache()
}
