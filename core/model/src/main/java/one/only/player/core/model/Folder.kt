package one.only.player.core.model

import java.io.Serializable

data class Folder(
    val name: String,
    val path: String,
    val dateModified: Long,
    val parentPath: String? = null,
    val mediaList: List<Video> = emptyList(),
    val folderList: List<Folder> = emptyList(),
) : Serializable {

    val mediaSize: Long = mediaList.sumOf { it.size } + folderList.sumOf { it.mediaSize }
    val allMediaList: List<Video> = mediaList + folderList.flatMap { it.allMediaList }
    val recentlyPlayedVideo: Video? = allMediaList.recentPlayed()
    val firstVideo: Video? = allMediaList.firstOrNull()

    fun isRecentlyPlayedVideo(video: Video?): Boolean {
        if (recentlyPlayedVideo == null) return false
        if (video == null) return false
        return video.path == recentlyPlayedVideo.path
    }

    companion object {
        val rootFolder = Folder(
            name = "Root",
            path = "/",
            dateModified = System.currentTimeMillis(),
        )

        val sample = Folder(
            name = "Sample Folder",
            path = "/storage/emulated/0/Movies/Sample",
            dateModified = 2000,
        )
    }
}

// 媒体与子目录都按给定排序落位，快照缓存会拿它当文件夹页首帧，顺序必须与目录流一致
fun Folder.withSortedContent(sort: Sort): Folder = withSortedContent { sort }

fun Folder.withSortedContent(sortForDirectory: (Folder) -> Sort): Folder {
    val sort = sortForDirectory(this)
    return copy(
        mediaList = mediaList.sortedWith(sort.videoComparator()),
        folderList = folderList.map { it.withSortedContent(sortForDirectory) }.sortedWith(sort.folderComparator()),
    )
}
