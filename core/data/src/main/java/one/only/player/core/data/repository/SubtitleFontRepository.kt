package one.only.player.core.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface SubtitleFontRepository {
    val state: StateFlow<ExternalSubtitleFontState>

    val source: StateFlow<ExternalSubtitleFontSource?>

    suspend fun importFonts(uris: List<Uri>)

    suspend fun clearFont()
}

data class ExternalSubtitleFontState(
    val isAvailable: Boolean = false,
    val displayName: String = "",
)

data class ExternalSubtitleFontSource(
    val fonts: List<ExternalSubtitleFontFile>,
) {
    val absolutePath: String?
        get() = fonts.firstOrNull()?.absolutePath
}

data class ExternalSubtitleFontFile(
    val displayName: String,
    val absolutePath: String,
)
