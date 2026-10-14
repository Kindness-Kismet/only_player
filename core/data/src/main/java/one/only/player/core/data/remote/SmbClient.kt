package one.only.player.core.data.remote

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import one.only.player.core.common.Dispatcher
import one.only.player.core.common.DispatcherType
import one.only.player.core.common.di.ApplicationScope
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol

@Singleton
class SmbClient @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val browseMutex = Mutex()
    private var browseSession: BrowseSession? = null
    private var closeBrowseSessionJob: Job? = null
    private val directoryCache = LinkedHashMap<DirectoryCacheKey, DirectoryCacheEntry>()

    suspend fun listDirectory(
        server: RemoteServer,
        directoryPath: String,
        forceRefresh: Boolean = false,
    ): Result<List<RemoteFile>> = withContext(ioDispatcher) {
        try {
            Result.success(
                listDirectoryInternal(
                    server = server,
                    directoryPath = directoryPath,
                    forceRefresh = forceRefresh,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private suspend fun listDirectoryInternal(
        server: RemoteServer,
        directoryPath: String,
        forceRefresh: Boolean,
    ): List<RemoteFile> {
        if (server.protocol != ServerProtocol.SMB) {
            error("SmbClient only supports SMB protocol")
        }

        val target = resolveListTarget(server, directoryPath)
        val authFingerprint = server.toAuthFingerprint()
        val cacheKey = DirectoryCacheKey(
            host = server.host,
            port = server.port ?: DEFAULT_PORT,
            serverPath = server.path.normalizeSmbPath(),
            authFingerprint = authFingerprint,
            directoryPath = target.directoryPath.normalizeSmbPath(),
        )

        return browseMutex.withLock {
            if (forceRefresh) {
                directoryCache.remove(cacheKey)
            }

            val cachedFiles = directoryCache[cacheKey]
                ?.takeIf { !forceRefresh && it.isFresh() }
                ?.files

            if (cachedFiles != null) {
                cachedFiles
            } else {
                val files = listShareDirectory(server, target)
                cacheDirectory(cacheKey, files)
                files
            }
        }
    }

    private fun listShareDirectory(
        server: RemoteServer,
        target: ListTarget,
    ): List<RemoteFile> = try {
        listShareDirectoryOnce(
            server = server,
            target = target,
        )
    } catch (exception: Exception) {
        closeBrowseSession()
        listShareDirectoryOnce(
            server = server,
            target = target,
        )
    }

    private fun listShareDirectoryOnce(
        server: RemoteServer,
        target: ListTarget,
    ): List<RemoteFile> {
        val sessionKey = BrowseSessionKey(
            host = server.host,
            port = server.port ?: DEFAULT_PORT,
            authFingerprint = server.toAuthFingerprint(),
        )
        val session = getBrowseSession(
            sessionKey = sessionKey,
            authContext = server.toSmbAuthContext(),
        ).session
        if (target.isShareEnumeration) return SmbShareEnumerator.listShares(session).sortedBy { it.name }

        val files = mutableListOf<RemoteFile>()
        val listing = (session.connectShare(target.shareName) as DiskShare).use { share ->
            share.list(target.relativePath)
        }

        for (info in listing) {
            val name = info.fileName
            if (name == "." || name == "..") continue

            val isDirectory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
            val size = info.endOfFile
            val fullPath = if (target.directoryPath.endsWith("/")) {
                "${target.directoryPath}$name"
            } else {
                "${target.directoryPath}/$name"
            }

            files.add(
                RemoteFile(
                    name = name,
                    path = if (isDirectory) "$fullPath/" else fullPath,
                    isDirectory = isDirectory,
                    size = size,
                ),
            )
        }

        return files.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
    }

    private fun getBrowseSession(
        sessionKey: BrowseSessionKey,
        authContext: AuthenticationContext,
    ): BrowseSession {
        browseSession?.takeIf { it.key == sessionKey && it.isFresh() }?.let { session ->
            session.touch()
            scheduleBrowseSessionClose(session)
            return session
        }
        closeBrowseSession()

        val client = SMBClient(buildBrowseConfig())
        var connection: com.hierynomus.smbj.connection.Connection? = null
        var session: com.hierynomus.smbj.session.Session? = null

        try {
            connection = client.connect(sessionKey.host, sessionKey.port)
            session = connection.authenticate(authContext)
            return BrowseSession(
                key = sessionKey,
                client = client,
                connection = connection,
                session = session,
                lastUsedAtMillis = System.currentTimeMillis(),
            ).also {
                browseSession = it
                scheduleBrowseSessionClose(it)
            }
        } catch (exception: Exception) {
            closeSmbResources(
                client = client,
                connection = connection,
                session = session,
            )
            throw exception
        }
    }

    private fun closeBrowseSession() {
        closeBrowseSessionJob?.cancel()
        closeBrowseSessionJob = null
        closeBrowseSessionNow()
    }

    private fun closeBrowseSessionNow() {
        val session = browseSession ?: return
        closeSmbResources(
            client = session.client,
            connection = session.connection,
            session = session.session,
        )
        browseSession = null
    }

    private fun scheduleBrowseSessionClose(session: BrowseSession) {
        closeBrowseSessionJob?.cancel()
        closeBrowseSessionJob = applicationScope.launch(ioDispatcher) {
            delay(BROWSE_SESSION_IDLE_TTL_MS)
            browseMutex.withLock {
                if (browseSession === session && !session.isFresh()) {
                    closeBrowseSessionJob = null
                    closeBrowseSessionNow()
                }
            }
        }
    }

    private fun closeSmbResources(
        client: SMBClient,
        connection: com.hierynomus.smbj.connection.Connection?,
        session: com.hierynomus.smbj.session.Session?,
    ) {
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client.close() }
    }

    private fun resolveListTarget(
        server: RemoteServer,
        directoryPath: String,
    ): ListTarget {
        val isServerPathRoot = isRootPath(server.path)
        val displayDirectoryPath = normalizeRemotePath(directoryPath, isDirectory = true)
        val isCurrentPathRoot = isRootPath(displayDirectoryPath)

        // 根路径先展示共享列表，再进入具体共享
        if (isServerPathRoot && isCurrentPathRoot) {
            return ListTarget(
                shareName = "",
                relativePath = "",
                directoryPath = displayDirectoryPath,
                isShareEnumeration = true,
            )
        }

        val sharePath = resolveSharePath(
            serverPath = server.path,
            path = displayDirectoryPath,
        )

        return ListTarget(
            shareName = sharePath.shareName,
            relativePath = sharePath.relativePath,
            directoryPath = displayDirectoryPath,
            isShareEnumeration = false,
        )
    }

    private fun cacheDirectory(
        key: DirectoryCacheKey,
        files: List<RemoteFile>,
    ) {
        directoryCache.remove(key)
        directoryCache[key] = DirectoryCacheEntry(
            files = files,
            createdAtMillis = System.currentTimeMillis(),
        )

        while (directoryCache.size > MAX_DIRECTORY_CACHE_SIZE) {
            val oldestKey = directoryCache.keys.firstOrNull() ?: return
            directoryCache.remove(oldestKey)
        }
    }

    private data class ListTarget(
        val shareName: String,
        val relativePath: String,
        val directoryPath: String,
        val isShareEnumeration: Boolean,
    )

    private data class BrowseSessionKey(
        val host: String,
        val port: Int,
        val authFingerprint: String,
    )

    private data class BrowseSession(
        val key: BrowseSessionKey,
        val client: SMBClient,
        val connection: com.hierynomus.smbj.connection.Connection,
        val session: com.hierynomus.smbj.session.Session,
        var lastUsedAtMillis: Long,
    ) {
        fun isFresh(): Boolean = connection.isConnected && System.currentTimeMillis() - lastUsedAtMillis <= BROWSE_SESSION_IDLE_TTL_MS

        fun touch() {
            lastUsedAtMillis = System.currentTimeMillis()
        }
    }

    private data class DirectoryCacheKey(
        val host: String,
        val port: Int,
        val serverPath: String,
        val authFingerprint: String,
        val directoryPath: String,
    )

    private data class DirectoryCacheEntry(
        val files: List<RemoteFile>,
        val createdAtMillis: Long,
    ) {
        fun isFresh(): Boolean = System.currentTimeMillis() - createdAtMillis <= DIRECTORY_CACHE_TTL_MS
    }

    companion object {
        const val DEFAULT_PORT = 445
        const val BROWSE_TIMEOUT_SECONDS = 30L
        const val FILE_TIMEOUT_SECONDS = 15L
        private const val DIRECTORY_CACHE_TTL_MS = 30_000L
        private const val BROWSE_SESSION_IDLE_TTL_MS = 60_000L
        private const val MAX_DIRECTORY_CACHE_SIZE = 32

        // Same connection configuration as Next Player SmbClient (896154b).
        fun buildBrowseConfig(): SmbConfig = SmbConfig.builder()
            .withTimeout(BROWSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(35, TimeUnit.SECONDS)
            .withDialects(
                SMB2Dialect.SMB_3_1_1,
                SMB2Dialect.SMB_3_0_2,
                SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_2_1,
                SMB2Dialect.SMB_2_0_2,
            )
            .withDfsEnabled(false)
            .withMultiProtocolNegotiate(true)
            .withSigningRequired(false)
            .withEncryptData(false)
            .withReadBufferSize(8 * 1024 * 1024)
            .withWriteBufferSize(8 * 1024 * 1024)
            .build()

        fun buildFileConfig(): SmbConfig = buildConfig(FILE_TIMEOUT_SECONDS)

        private fun buildConfig(timeoutSeconds: Long): SmbConfig = SmbConfig.builder()
            .withTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .withSoTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        fun extractShareName(serverPath: String): String {
            val normalized = serverPath.normalizeSmbPath()
            return normalized.substringBefore("/").ifBlank { normalized }
        }

        fun resolveSharePath(
            serverPath: String,
            path: String,
        ): SharePath {
            if (isRootPath(serverPath)) {
                val normalizedPath = path.normalizeSmbPath()
                return SharePath(
                    shareName = normalizedPath.substringBefore("/"),
                    relativePath = normalizedPath.substringAfter("/", missingDelimiterValue = "")
                        .replace("/", "\\"),
                )
            }

            return SharePath(
                shareName = extractShareName(serverPath),
                relativePath = extractRelativePath(serverPath, path),
            )
        }

        fun extractRelativePath(serverPath: String, directoryPath: String): String {
            val shareName = extractShareName(serverPath)
            val normalizedServerPath = serverPath.normalizeSmbPath()
            val serverRelative = normalizedServerPath.removeShareSegment(shareName)
            val relativeToShare = directoryPath.normalizeSmbPath().removeShareSegment(shareName)

            val combined = when {
                relativeToShare.isBlank() -> serverRelative
                serverRelative.isBlank() -> relativeToShare
                relativeToShare.equals(serverRelative, ignoreCase = true) -> serverRelative
                relativeToShare.startsWith("$serverRelative/", ignoreCase = true) -> relativeToShare
                else -> "$serverRelative/$relativeToShare"
            }

            return combined.replace("/", "\\")
        }

        fun isRootPath(path: String): Boolean = path.normalizeSmbPath().isBlank()

        fun normalizeRemotePath(
            path: String,
            isDirectory: Boolean,
        ): String {
            val normalized = path.normalizeSmbPath()
            val remotePath = if (normalized.isBlank()) "/" else "/$normalized"
            if (!isDirectory || remotePath.endsWith("/")) return remotePath
            return "$remotePath/"
        }

        private fun String.normalizeSmbPath(): String = trim()
            .replace('\\', '/')
            .trim('/')

        private fun String.removeShareSegment(shareName: String): String {
            if (equals(shareName, ignoreCase = true)) return ""

            val sharePrefix = "$shareName/"
            if (startsWith(sharePrefix, ignoreCase = true)) {
                return drop(sharePrefix.length)
            }

            return this
        }

        fun RemoteServer.toSmbAuthContext(): AuthenticationContext {
            if (username.isBlank()) return AuthenticationContext.anonymous()

            val domain = username.substringBefore('\\', missingDelimiterValue = "")
                .substringBefore('/', missingDelimiterValue = "")
            val account = username.substringAfterLast('\\').substringAfterLast('/')

            return AuthenticationContext(account, password.toCharArray(), domain)
        }

        data class SharePath(
            val shareName: String,
            val relativePath: String,
        )
    }
}
