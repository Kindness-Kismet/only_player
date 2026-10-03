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
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.Folder
import one.only.player.core.model.StoragePath
import one.only.player.core.model.directorySortOverrides
import one.only.player.core.model.resolveQuickSettings
import one.only.player.core.model.withSortedContent

class GetSortedFolderTreeUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(folderPath: String? = null): Flow<Folder?> = combine(
        mediaRepository.getFoldersFlow(),
        preferencesRepository.applicationPreferences.distinctUntilChangedBy {
            Triple((it.sortBy to it.sortOrder) to it.directorySortOverrides(), it.excludeFolders, it.isRecycleBinEnabled)
        },
    ) { folders, preferences ->
        val currentFolder = folderPath?.let {
            folders.find { it.path == folderPath } ?: return@combine null
        } ?: Folder.rootFolder

        val visibleMedia = currentFolder.mediaList.filterNot { video ->
            preferences.isRecycleBinEnabled && video.isInRecycleBin
        }

        currentFolder.copy(
            mediaList = visibleMedia,
            folderList = folders.getFoldersFor(path = currentFolder.path, preferences = preferences),
        ).withSortedContent { directory ->
            preferences.resolveQuickSettings(StoragePath.of(directory.path).takeUnless(StoragePath::isRoot)).sort.toSort()
        }.let { folder ->
            if (folderPath == null) folder.getInitialFolderWithContent() else folder
        }
    }.flowOn(defaultDispatcher)

    private fun Folder.getInitialFolderWithContent(): Folder = when {
        mediaList.isEmpty() && folderList.size == 1 -> folderList.first().getInitialFolderWithContent()
        else -> this
    }

    private fun List<Folder>.getFoldersFor(
        path: String,
        preferences: ApplicationPreferences,
    ): List<Folder> = mapNotNull { directory ->
        if (directory.parentPath != path || preferences.isPathExcluded(StoragePath.of(directory.path))) {
            return@mapNotNull null
        }

        val childFolders = getFoldersFor(path = directory.path, preferences = preferences)
        val visibleMedia = directory.mediaList.filterNot { video ->
            preferences.isRecycleBinEnabled && video.isInRecycleBin
        }
        if (visibleMedia.isEmpty() && childFolders.isEmpty()) {
            return@mapNotNull null
        }

        Folder(
            name = directory.name,
            path = directory.path,
            dateModified = directory.dateModified,
            mediaList = visibleMedia,
            folderList = childFolders,
        )
    }
}
