package one.only.player.core.model

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String,
)
