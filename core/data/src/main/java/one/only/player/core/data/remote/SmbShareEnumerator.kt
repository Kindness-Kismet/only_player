package one.only.player.core.data.remote

import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.PipeShare
import com.rapid7.client.dcerpc.Interface
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransport
import com.rapid7.helper.smbj.share.NamedPipe
import one.only.player.core.model.RemoteFile

// Ported from Next Player SmbClient.listSmbShares (896154b).
internal object SmbShareEnumerator {
    fun listShares(session: Session): List<RemoteFile> = (session.connectShare("IPC$") as PipeShare).use { share ->
        NamedPipe(session, share, "srvsvc").use { pipe ->
            val transport = SMBTransport(pipe)
            transport.bind(Interface.SRVSVC_V3_0, Interface.NDR_32BIT_V2)
            ServerService(transport).shares1
                .filter { it.type and 0xFFFF == 0 && !it.netName.endsWith("$") }
                .map { RemoteFile(name = it.netName, path = "/${it.netName}/", isDirectory = true, size = 0) }
        }
    }
}
