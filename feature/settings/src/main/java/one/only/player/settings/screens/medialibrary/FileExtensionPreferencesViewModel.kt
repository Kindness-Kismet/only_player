package one.only.player.settings.screens.medialibrary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.only.player.core.data.repository.PreferencesRepository
import one.only.player.core.media.sync.MediaSynchronizer
import one.only.player.core.model.DecoderPriority
import one.only.player.core.model.ExtensionDecoderPreference

@HiltViewModel
class FileExtensionPreferencesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: PreferencesRepository,
    private val mediaSynchronizer: MediaSynchronizer,
) : ViewModel() {


    private fun persistExtensionDecoderMirror(preferences: List<ExtensionDecoderPreference>) {
        runCatching {
            val dir = File(appContext.filesDir, "data")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "extension_decoder_preferences.json")
            // 可读镜像；权威配置仍在 files/datastore/app_preferences.json
            val lines = preferences.joinToString(separator = ",\n") { item ->
                """    {"extension": "${item.extension}", "decoderPriority": "${item.decoderPriority.name}", "isBuiltIn": ${item.isBuiltIn}}"""
            }
            val body = "{\n  \"extensions\": [\n$lines\n  ]\n}\n"
            file.writeText(body)
        }
    }

    private fun persistPerFilePlaybackMirror(
        preferences: List<one.only.player.core.model.PerFilePlaybackPreference>,
    ) {
        runCatching {
            val dir = File(appContext.filesDir, "data")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "per_file_playback_preferences.json")
            val lines = preferences.joinToString(separator = ",\n") { item ->
                val decoder = item.decoderPriority?.name ?: "null"
                val orientation = item.screenOrientation?.name ?: "null"
                """    {"fileName": "${item.fileName}", "decoderPriority": "$decoder", "screenOrientation": "$orientation"}"""
            }
            val body = "{\n  \"files\": [\n$lines\n  ]\n}\n"
            file.writeText(body)
        }
    }

    private val uiStateInternal = MutableStateFlow(FileExtensionPreferencesUiState())
    val uiState: StateFlow<FileExtensionPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                val normalized = prefs.normalizedExtensionDecoderPreferences()
                uiStateInternal.update {
                    it.copy(preferences = normalized)
                }
                // 同步导出可读配置到 data/files
                persistExtensionDecoderMirror(normalized)
                persistPerFilePlaybackMirror(prefs.normalizedPerFilePlaybackPreferences())
            }
        }
    }

    fun onEvent(event: FileExtensionPreferencesUiEvent) {
        when (event) {
            is FileExtensionPreferencesUiEvent.AddExtension -> addExtension(event.preference)
            is FileExtensionPreferencesUiEvent.RemoveExtension -> removeExtension(event.extension)
            is FileExtensionPreferencesUiEvent.UpdateDecoderPriority -> updateDecoderPriority(
                extension = event.extension,
                decoderPriority = event.decoderPriority,
            )
            is FileExtensionPreferencesUiEvent.BatchUpdateDecoderPriority -> batchUpdateDecoderPriority(
                extensions = event.extensions,
                decoderPriority = event.decoderPriority,
            )
            FileExtensionPreferencesUiEvent.RestoreDefaults -> restoreDefaults()
        }
    }

    private fun addExtension(preference: ExtensionDecoderPreference) {
        viewModelScope.launch {
            var didChange = false
            preferencesRepository.updateApplicationPreferences { current ->
                val existing = current.normalizedExtensionDecoderPreferences()
                if (existing.any { it.extension == preference.normalized().extension }) {
                    current
                } else {
                    didChange = true
                    current.withExtensionDecoderPreferences(existing + preference.normalized())
                }
            }
            if (didChange) {
                // 扩展名变更后全量扫描，文件系统发现自定义后缀文件
                mediaSynchronizer.refresh()
            }
        }
    }

    private fun removeExtension(extension: String) {
        val key = ExtensionDecoderPreference.normalizeExtension(extension)
        viewModelScope.launch {
            var didChange = false
            preferencesRepository.updateApplicationPreferences { current ->
                // 内置与自定义扩展名均可删除
                val next = current.normalizedExtensionDecoderPreferences().filterNot {
                    it.extension == key
                }
                if (next.size == current.normalizedExtensionDecoderPreferences().size) {
                    current
                } else {
                    didChange = true
                    current.withExtensionDecoderPreferences(next)
                }
            }
            if (didChange) {
                mediaSynchronizer.refresh()
            }
        }
    }

    private fun updateDecoderPriority(
        extension: String,
        decoderPriority: DecoderPriority,
    ) {
        val key = ExtensionDecoderPreference.normalizeExtension(extension)
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { current ->
                current.withExtensionDecoderPreferences(
                    current.normalizedExtensionDecoderPreferences().map { item ->
                        if (item.extension == key) {
                            item.copy(decoderPriority = decoderPriority)
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    private fun batchUpdateDecoderPriority(
        extensions: List<String>,
        decoderPriority: DecoderPriority,
    ) {
        val keys = extensions.map(ExtensionDecoderPreference::normalizeExtension).filter { it.isNotBlank() }.toSet()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { current ->
                current.withExtensionDecoderPreferences(
                    current.normalizedExtensionDecoderPreferences().map { item ->
                        if (item.extension in keys) {
                            item.copy(decoderPriority = decoderPriority)
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    private fun restoreDefaults() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { current ->
                current.withExtensionDecoderPreferences(ExtensionDecoderPreference.defaults())
            }
            // 恢复内置列表后全量扫描，误删的后缀重新进入媒体库
            mediaSynchronizer.refresh()
        }
    }
}

data class FileExtensionPreferencesUiState(
    val preferences: List<ExtensionDecoderPreference> = ExtensionDecoderPreference.defaults(),
)

sealed interface FileExtensionPreferencesUiEvent {
    data class AddExtension(val preference: ExtensionDecoderPreference) : FileExtensionPreferencesUiEvent
    data class RemoveExtension(val extension: String) : FileExtensionPreferencesUiEvent
    data class UpdateDecoderPriority(
        val extension: String,
        val decoderPriority: DecoderPriority,
    ) : FileExtensionPreferencesUiEvent
    data class BatchUpdateDecoderPriority(
        val extensions: List<String>,
        val decoderPriority: DecoderPriority,
    ) : FileExtensionPreferencesUiEvent
    data object RestoreDefaults : FileExtensionPreferencesUiEvent
}
