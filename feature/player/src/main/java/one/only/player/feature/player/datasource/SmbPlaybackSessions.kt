package one.only.player.feature.player.datasource

import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import one.only.player.core.common.Logger

// 播放服务持有连接，数据源只借用共享并独立打开文件；seek 不重复连接和认证。
class SmbPlaybackSessions : Closeable {
    private var current: ConnectedShare? = null
    private var isClosed = false

    @Synchronized
    fun acquire(
        host: String,
        port: Int,
        shareName: String,
        username: String,
        password: String,
    ): Lease {
        if (isClosed) throw IOException("SMB playback sessions are closed")
        val key = Key(host, port, shareName, username, password)
        val existing = current
        val connected = if (existing != null && existing.key == key && existing.isConnected()) {
            existing
        } else {
            retireCurrent()
            connect(key).also { current = it }
        }
        connected.references++
        return Lease(connected.share) { hasFailed -> release(connected, hasFailed) }
    }

    @Synchronized
    override fun close() {
        isClosed = true
        retireCurrent()
    }

    private fun retireCurrent() {
        val connected = current ?: return
        current = null
        connected.isRetired = true
        if (connected.references == 0) connected.close()
    }

    @Synchronized
    private fun release(connected: ConnectedShare, hasFailed: Boolean) {
        if (hasFailed) {
            connected.isRetired = true
            if (current === connected) current = null
        }
        connected.references--
        if (connected.isRetired && connected.references == 0) connected.close()
    }

    private fun connect(key: Key): ConnectedShare {
        val config = SmbConfig.builder()
            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(SOCKET_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
            // 与 Next Player 的 SmbClient 配置一致。
            .withReadBufferSize(SMB_BUFFER_BYTES)
            .withWriteBufferSize(SMB_BUFFER_BYTES)
            .build()
        val client = SMBClient(config)
        var connection: Connection? = null
        var session: Session? = null
        try {
            connection = client.connect(key.host, key.port)
            session = connection.authenticate(authenticationContext(key.username, key.password))
            val share = session.connectShare(key.shareName) as? DiskShare
                ?: throw IOException("SMB share is not a disk share")
            return ConnectedShare(key, client, connection, session, share)
        } catch (exception: Exception) {
            runCatching { session?.close() }
            runCatching { connection?.close() }
            runCatching { client.close() }
            Logger.error(TAG, "Failed to connect SMB playback session", exception)
            throw IOException("Failed to connect SMB playback session", exception)
        }
    }

    class Lease internal constructor(
        val share: DiskShare,
        private val onRelease: (Boolean) -> Unit,
    ) : Closeable {
        private var hasFailed = false
        private var isClosed = false

        @Synchronized
        fun invalidate() {
            hasFailed = true
        }

        @Synchronized
        override fun close() {
            if (isClosed) return
            isClosed = true
            onRelease(hasFailed)
        }
    }

    private data class Key(
        val host: String,
        val port: Int,
        val shareName: String,
        val username: String,
        val password: String,
    )

    private class ConnectedShare(
        val key: Key,
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
    ) : Closeable {
        var references = 0
        var isRetired = false

        fun isConnected(): Boolean = connection.isConnected && share.isConnected

        override fun close() {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    companion object {
        private const val TAG = "SmbPlaybackSessions"
        private const val TIMEOUT_SECONDS = 30L
        private const val SOCKET_TIMEOUT_SECONDS = 35L
        private const val SMB_BUFFER_BYTES = 8 * 1024 * 1024

        private fun authenticationContext(username: String, password: String): AuthenticationContext {
            if (username.isBlank()) return AuthenticationContext.anonymous()
            val domain = username.substringBefore('\\', missingDelimiterValue = "")
                .substringBefore('/', missingDelimiterValue = "")
            val account = username.substringAfterLast('\\').substringAfterLast('/')
            return AuthenticationContext(account, password.toCharArray(), domain)
        }
    }
}
