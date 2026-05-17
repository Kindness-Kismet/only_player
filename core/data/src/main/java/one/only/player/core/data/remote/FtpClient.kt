package one.only.player.core.data.remote

import android.net.Uri
import javax.inject.Inject
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

class FtpClient @Inject constructor() {

    suspend fun listDirectory(
        server: RemoteServer,
        directoryPath: String,
    ): Result<List<RemoteFile>> = runCatching {
        if (server.protocol != ServerProtocol.FTP) {
            error("FtpClient only supports FTP protocol")
        }

        val client = server.connect()
        try {
            val files = client.listFiles(directoryPath)
            files
                .filter { file -> file.name != "." && file.name != ".." }
                .map { file -> file.toRemoteFile(directoryPath) }
                .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name })
        } finally {
            client.disconnectQuietly()
        }
    }

    fun buildFileUrl(server: RemoteServer, filePath: String): String {
        val authority = server.port?.let { "${server.host}:$it" } ?: server.host
        return Uri.Builder()
            .scheme("ftp")
            .encodedAuthority(authority)
            .path(filePath.ensureLeadingSlash())
            .build()
            .toString()
    }

    companion object {
        const val DEFAULT_PORT = 21
        const val CONNECT_TIMEOUT_MS = 15_000
        const val DATA_TIMEOUT_MS = 30_000

        private fun RemoteServer.connect(): FTPClient {
            val client = FTPClient().apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                dataTimeout = java.time.Duration.ofMillis(DATA_TIMEOUT_MS.toLong())
                setControlEncoding(Charsets.UTF_8.name())
                setAutodetectUTF8(true)
            }
            try {
                client.connect(host, port ?: DEFAULT_PORT)
                val loginOk = when {
                    username.isBlank() -> client.login("anonymous", "")
                    else -> client.login(username, password)
                }
                if (!loginOk) {
                    error("FTP login failed")
                }
                client.enterLocalPassiveMode()
                client.setFileType(FTPClient.BINARY_FILE_TYPE)
                return client
            } catch (exception: Exception) {
                client.disconnectQuietly()
                throw exception
            }
        }

        private fun FTPClient.disconnectQuietly() {
            runCatching {
                if (isConnected) logout()
            }
            runCatching {
                if (isConnected) disconnect()
            }
        }

        fun String.toFtpPath(): String = ensureLeadingSlash()
    }
}

private fun FTPFile.toRemoteFile(directoryPath: String): RemoteFile {
    val fullPath = directoryPath.ensureTrailingSlash() + name
    return RemoteFile(
        name = name,
        path = if (isDirectory) fullPath.ensureTrailingSlash() else fullPath,
        isDirectory = isDirectory,
        size = size,
    )
}

private fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
