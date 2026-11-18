package one.only.player.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class ScreenOrientation {
    AUTOMATIC,
    LANDSCAPE_AUTO,
    LANDSCAPE,
    LANDSCAPE_REVERSE,

    @JsonNames("PORTRAIT")
    VIDEO_ORIENTATION,
}

enum class LastPlayerScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class VideoLandscapeOrientation {
    SENSOR,
    LANDSCAPE,
    REVERSE_LANDSCAPE,
}
