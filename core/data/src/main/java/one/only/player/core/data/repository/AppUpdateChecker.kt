package one.only.player.core.data.repository

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.common.Logger
import one.only.player.core.model.AppUpdateInfo
import one.only.player.core.model.UpdateChannel
import org.json.JSONArray
import org.json.JSONObject

// 拿不到结果和确认已是最新是两回事，前者不能当成最新版本展示
sealed interface AppUpdateResult {
    data class Available(val info: AppUpdateInfo) : AppUpdateResult
    data object UpToDate : AppUpdateResult
    data object Failed : AppUpdateResult
}

private data class RemoteRelease(
    val version: ParsedVersion,
    val info: AppUpdateInfo,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val RELEASES_URL = "https://api.github.com/repos/Kindness-Kismet/only_player/releases"
    }

    suspend fun checkForUpdate(
        currentVersion: String,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): AppUpdateResult = withContext(ioDispatcher) {
        runCatching {
            val installedVersion = requireNotNull(parseVersion(currentVersion))
            // 正式版单独查询，避免被大量测试版挤出发布列表首页。
            val stable = requireNotNull(JSONObject(fetchReleaseJson("$RELEASES_URL/latest")).toRemoteRelease())
            val candidate = when (channel) {
                UpdateChannel.STABLE -> stable
                UpdateChannel.TEST -> (fetchReleases() + stable).maxBy { it.version }
            }

            if (candidate.version > installedVersion) {
                AppUpdateResult.Available(candidate.info)
            } else {
                AppUpdateResult.UpToDate
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Logger.error(TAG, "Failed to check for updates", throwable)
            AppUpdateResult.Failed
        }
    }

    private fun fetchReleases(): List<RemoteRelease> {
        val array = JSONArray(fetchReleaseJson("$RELEASES_URL?per_page=100"))
        return (0 until array.length()).mapNotNull { array.getJSONObject(it).toRemoteRelease() }
    }

    private fun fetchReleaseJson(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "OnlyPlayer")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("Unexpected response code: ${connection.responseCode}")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.toRemoteRelease(): RemoteRelease? {
    if (getBoolean("draft")) return null
    val tagName = getString("tag_name").removePrefix("v")
    val version = parseVersion(tagName) ?: return null
    return RemoteRelease(
        version = version,
        info = AppUpdateInfo(
            latestVersion = tagName,
            releaseUrl = getString("html_url"),
            releaseNotes = if (isNull("body")) "" else getString("body"),
        ),
    )
}

private data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val betaNumber: Int?,
) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int {
        val coreComparison = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (coreComparison != 0) return coreComparison

        // 同版本正式版高于测试版，测试序号按数值比较。
        return when {
            betaNumber == null && other.betaNumber == null -> 0
            betaNumber == null -> 1
            other.betaNumber == null -> -1
            else -> betaNumber.compareTo(other.betaNumber)
        }
    }
}

private fun parseVersion(raw: String): ParsedVersion? {
    val match = VERSION_PATTERN.matchEntire(raw.removePrefix("v")) ?: return null
    return ParsedVersion(
        major = match.groupValues[1].toInt(),
        minor = match.groupValues[2].toInt(),
        patch = match.groupValues[3].toInt(),
        betaNumber = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt(),
    )
}

private val VERSION_PATTERN = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-beta(\d+))?$""")
