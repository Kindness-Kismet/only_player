package one.only.player.core.model

data class FavoriteItem(
    val id: Long = 0,
    val parentId: Long? = null,
    val targetType: FavoriteTargetType,
    val targetKey: String,
    val title: String,
    val subtitle: String = "",
    val localUri: String? = null,
    val localPath: String? = null,
    val remoteServerId: Long? = null,
    val remoteProtocol: ServerProtocol? = null,
    val remotePath: String? = null,
    val remoteServerName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val sortOrder: Long = createdAt,
)

enum class FavoriteTargetType {
    FAVORITE_FOLDER,
    LOCAL_VIDEO,
    LOCAL_FOLDER,
    REMOTE_FILE,
    REMOTE_DIRECTORY,
    REMOTE_SERVER_ROOT,
}
