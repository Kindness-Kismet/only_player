package one.only.player.core.media.sync

interface MediaSynchronizer {
    suspend fun refresh(path: String? = null): Boolean
    suspend fun removeDeleted(uris: List<String>)
    suspend fun registerManualVideoPath(path: String)
    fun startSync()
    fun stopSync()
}
