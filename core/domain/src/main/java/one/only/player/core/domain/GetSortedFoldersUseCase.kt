package one.only.player.core.domain

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.data.repository.MediaRepository
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.model.Folder
import one.only.player.core.model.Sort
import one.only.player.core.model.StoragePath
import one.only.player.core.model.directorySortOverrides
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withSortedContent

class GetSortedFoldersUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<List<Folder>> = combine(
        mediaRepository.getFoldersFlow(),
        preferencesRepository.applicationPreferences.distinctUntilChangedBy {
            Triple((it.sortBy to it.sortOrder) to it.directorySortOverrides(), it.excludeFolders, it.isRecycleBinEnabled)
        },
    ) { folders, preferences ->
        val sort = Sort(by = preferences.sortBy, order = preferences.sortOrder)
        val visibleDirectories = folders.mapNotNull { folder ->
            if (preferences.isPathExcluded(StoragePath.of(folder.path))) {
                return@mapNotNull null
            }

            val visibleMedia = folder.mediaList.filterNot { video ->
                preferences.isRecycleBinEnabled && video.isInRecycleBin
            }
            if (visibleMedia.isEmpty()) {
                return@mapNotNull null
            }

            folder.copy(mediaList = visibleMedia).withSortedContent { directory ->
                preferences.resolveQuickSettings(StoragePath.of(directory.path)).sort.toSort()
            }
        }

        visibleDirectories.sortedWith(sort.folderComparator())
    }.flowOn(defaultDispatcher)
}
