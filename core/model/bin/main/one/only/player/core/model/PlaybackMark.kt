package one.only.player.core.model

data class PlaybackMark(
    val id: Long = 0,
    val mediaUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
