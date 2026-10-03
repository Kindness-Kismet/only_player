package one.only.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.StoragePath
import one.only.player.core.model.Video
import one.only.player.core.model.directorySortOverrides
import one.only.player.core.model.resolveQuickSettings

class GetSortedVideosUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    operator fun invoke(
        folderPath: String? = null,
        isRecycleBinOnly: Boolean = false,
    ): Flow<List<Video>> {
        val videosFlow = if (isRecycleBinOnly) {
            mediaRepository.getRecycleBinVideosFlow()
        } else if (folderPath != null) {
            mediaRepository.getVideosFlowFromFolderPath(folderPath)
        } else {
            mediaRepository.getVideosFlow()
        }

        return combine(
            videosFlow,
            preferencesRepository.applicationPreferences.distinctUntilChangedBy {
                Triple((it.sortBy to it.sortOrder) to it.directorySortOverrides(), it.excludeFolders, it.isRecycleBinEnabled)
            },
        ) { videoItems, preferences ->
            val visibleVideos = videoItems.filterNot { video ->
                (!isRecycleBinOnly && preferences.isPathExcluded(StoragePath.of(video.parentPath))) ||
                    (!isRecycleBinOnly && preferences.isRecycleBinEnabled && video.isInRecycleBin)
            }

            val sort = preferences.resolveQuickSettings(folderPath?.let(StoragePath::of)).sort.toSort()
            visibleVideos.sortedWith(sort.videoComparator())
        }.flowOn(defaultDispatcher)
    }
}
