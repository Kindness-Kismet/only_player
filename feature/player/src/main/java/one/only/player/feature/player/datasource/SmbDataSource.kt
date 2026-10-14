package one.only.player.feature.player.datasource

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet

// 通过 smbj 读取 SMB 共享文件的 Media3 DataSource 实现
@OptIn(UnstableApi::class)
class SmbDataSource private constructor(
    private val sessions: SmbPlaybackSessions,
    private val username: String,
    private val password: String,
) : BaseDataSource(true) {

    private var lease: SmbPlaybackSessions.Lease? = null
    private var smbFile: com.hierynomus.smbj.share.File? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var hasStartedTransfer: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val host = dataSpec.uri.host ?: throw IOException("SMB URI missing host")
        val port = dataSpec.uri.port.takeIf { it > 0 } ?: DEFAULT_PORT
        val pathSegments = dataSpec.uri.pathSegments
        if (pathSegments.size < 2) throw IOException("SMB URI path too short: ${dataSpec.uri}")

        val shareName = pathSegments.first()
        val filePath = pathSegments.drop(1).joinToString("\\")

        try {
            val acquired = sessions.acquire(host, port, shareName, username, password)
            lease = acquired

            smbFile = acquired.share.openFile(
                filePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )

            val fileSize = smbFile!!.fileInformation.standardInformation.endOfFile
            if (dataSpec.position < 0L || dataSpec.position > fileSize) {
                throw IOException("SMB position ${dataSpec.position} is outside file size $fileSize")
            }

            inputStream = smbFile!!.bufferedPlaybackStream(dataSpec.position)

            val availableBytes = fileSize - dataSpec.position
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                availableBytes
            } else {
                dataSpec.length.coerceAtMost(availableBytes)
            }

            transferStarted(dataSpec)
            hasStartedTransfer = true
            return bytesRemaining
        } catch (exception: Exception) {
            lease?.invalidate()
            closeResources()
            throw if (exception is IOException) exception else IOException("Failed to open SMB file", exception)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        val stream = inputStream ?: throw IOException("SMB file is not open")
        // smbj 在连接断开或被关闭时抛 RuntimeException，Media3 只会重试 IOException
        val bytesRead = try {
            stream.read(buffer, offset, bytesToRead)
        } catch (exception: IOException) {
            lease?.invalidate()
            throw exception
        } catch (exception: RuntimeException) {
            lease?.invalidate()
            throw IOException("Failed to read SMB file", exception)
        }
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT

        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        closeResources()
        if (hasStartedTransfer) {
            hasStartedTransfer = false
            transferEnded()
        }
    }

    private fun closeResources() {
        try {
            // 流接管文件句柄；open 失败、尚未创建流时仍需单独关闭文件。
            val stream = inputStream
            if (stream != null) stream.close() else smbFile?.close()
        } catch (_: Exception) {
            lease?.invalidate()
        }
        lease?.close()
        smbFile = null
        inputStream = null
        lease = null
        uri = null
        bytesRemaining = 0
    }

    class Factory(
        private val sessions: SmbPlaybackSessions,
        private val username: String,
        private val password: String,
    ) : DataSource.Factory {
        override fun createDataSource(): SmbDataSource = SmbDataSource(sessions, username, password)
    }

    companion object {
        private const val DEFAULT_PORT = 445
    }
}
