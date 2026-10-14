package one.only.player.feature.player.datasource

import com.hierynomus.smbj.share.File
import java.io.InputStream

// 移植自 Next Player SmbClient.openStream（896154b）：按偏移读取，外包 2 MiB 缓冲。
// https://github.com/anilbeesetti/nextplayer/blob/896154b5a23ec32eb124dfebc367a050a87bb2f9/core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/network/clients/SmbClient.kt
// 共享由 SmbPlaybackSessions 的租约管理，关闭文件流时不直接关闭其他读取者仍在使用的共享。
internal fun File.bufferedPlaybackStream(offset: Long): InputStream {
    val file = this
    val rawStream = object : InputStream() {
        private var position = offset

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = file.read(b, position, off, len)
            if (read > 0) position += read
            return read
        }

        override fun close() {
            file.close()
        }
    }
    return rawStream.buffered(2 * 1024 * 1024)
}
