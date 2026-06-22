package one.only.player.feature.player.utils

import android.content.Intent
import android.net.Uri
import androidx.media3.common.C
import one.only.player.feature.player.PlayerActivity
import one.only.player.feature.player.extensions.getParcelableUriArray
import one.only.player.feature.player.model.Subtitle

class PlayerApi(val activity: PlayerActivity) {

    private val intentExtras get() = activity.intent.extras
    val isApiAccess: Boolean get() = intentExtras != null
    val hasPosition: Boolean get() = intentExtras?.containsKey(API_POSITION) == true
    val hasTitle: Boolean get() = intentExtras?.containsKey(API_TITLE) == true
    val shouldReturnResult: Boolean get() = intentExtras?.containsKey(API_RETURN_RESULT) == true
    val position: Int?
        get() {
            val extras = intentExtras ?: return null
            return if (extras.containsKey(API_POSITION)) extras.getInt(API_POSITION) else null
        }
    val title: String?
        get() {
            val extras = intentExtras ?: return null
            return if (extras.containsKey(API_TITLE)) extras.getString(API_TITLE) else null
        }

    fun getSubs(): List<Subtitle> {
        val extras = intentExtras ?: return emptyList()
        if (!extras.containsKey(API_SUBS)) return emptyList()

        val subs = extras.getParcelableUriArray(API_SUBS) ?: return emptyList()
        val subsName = extras.getStringArray(API_SUBS_NAME)

        val subsEnable = extras.getParcelableUriArray(API_SUBS_ENABLE)
        val defaultSub = if (!subsEnable.isNullOrEmpty()) subsEnable[0] else null

        return subs.mapIndexed { index, subtitleUri ->
            val subtitleName = subsName?.let { if (it.size > index) it[index] else null }
            Subtitle(
                name = subtitleName,
                uri = subtitleUri,
                isSelected = subtitleUri == defaultSub,
            )
        }
    }

    fun getPlaylist(): List<String> {
        val extras = intentExtras ?: return emptyList()
        if (!extras.containsKey(API_PLAYLIST)) return emptyList()
        val playlist = extras.getParcelableUriArray(API_PLAYLIST) ?: return emptyList()
        return playlist.map(Uri::toString)
    }

    fun getPlaylistRemotePaths(): List<String> {
        val extras = intentExtras ?: return emptyList()
        if (!extras.containsKey(API_PLAYLIST_REMOTE_PATHS)) return emptyList()
        return extras.getStringArrayList(API_PLAYLIST_REMOTE_PATHS).orEmpty()
    }

    fun getResult(isPlaybackFinished: Boolean, duration: Long, position: Long): Intent = Intent(API_RESULT_INTENT).apply {
        if (isPlaybackFinished) {
            putExtra(API_END_BY, API_END_BY_COMPLETION)
        } else {
            putExtra(API_END_BY, API_END_BY_USER)
            if (duration != C.TIME_UNSET) putExtra(API_DURATION, duration.toInt())
            if (position != C.TIME_UNSET) putExtra(API_POSITION, position.toInt())
        }
    }

    companion object {
        const val API_TITLE = "title"
        const val API_POSITION = "position"
        const val API_DURATION = "duration"
        const val API_RETURN_RESULT = "return_result"
        const val API_END_BY = "end_by"
        const val API_SUBS = "subs"
        const val API_SUBS_ENABLE = "subs.enable"
        const val API_SUBS_NAME = "subs.name"
        const val API_PLAYLIST = "video_list"
        const val API_PLAYLIST_REMOTE_PATHS = "video_remote_paths"

        const val API_RESULT_INTENT = "com.mxtech.intent.result.VIEW"

        private const val API_END_BY_USER = "user"
        private const val API_END_BY_COMPLETION = "playback_completion"
    }
}
