package one.only.player.provider

import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.share.DiskShare
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import one.only.player.core.common.Logger
import one.only.player.core.data.remote.FtpClient
import one.only.player.core.data.remote.SmbClient
import one.only.player.core.data.remote.SmbClient.Companion.toSmbAuthContext
import one.only.player.core.data.remote.WebDavClient
import one.only.player.core.data.repository.RemoteServerRepository
import one.only.player.core.model.RemoteFile
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol

class CloudDocumentsProvider : DocumentsProvider() {

    private val remoteServerRepository: RemoteServerRepository by lazy {
        entryPoint.remoteServerRepository()
    }
    private val webDavClient: WebDavClient by lazy {
        entryPoint.webDavClient()
    }
    private val ftpClient: FtpClient by lazy {
        entryPoint.ftpClient()
    }
    private val smbClient: SmbClient by lazy {
        entryPoint.smbClient()
    }

    private val entryPoint: CloudDocumentsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            CloudDocumentsProviderEntryPoint::class.java,
        )
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): MatrixCursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        val servers = getServers()
        if (servers.isEmpty()) return result

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, ROOT_TITLE)
            add(DocumentsContract.Root.COLUMN_SUMMARY, buildRootSummary(servers.size))
            add(DocumentsContract.Root.COLUMN_FLAGS, ROOT_FLAGS)
            add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_upload)
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, ROOT_MIME_TYPES)
            add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, -1)
        }

        return result
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        appendDocumentRow(result, documentId)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))

        if (parentDocumentId == ROOT_DOCUMENT_ID) {
            getServers().forEach { server ->
                appendServerRow(result, server)
            }
            return result
        }

        val parsed = parseDocumentId(parentDocumentId)
        val server = getServer(parsed.serverId) ?: return result

        listFiles(server, resolveListPath(server, parsed.path)).forEach { file ->
            appendFileRow(
                result = result,
                server = server,
                file = file,
            )
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        if (!mode.startsWith("r")) {
            throw FileNotFoundException("Only read mode is supported")
        }
        if (documentId == ROOT_DOCUMENT_ID) {
            throw FileNotFoundException("Root document cannot be opened")
        }

        val parsed = parseDocumentId(documentId)
        val server = getServer(parsed.serverId) ?: throw FileNotFoundException("Server not found")
        if (parsed.path.isBlank() || isServerRootDocument(server, parsed.path)) {
            throw FileNotFoundException("Document path is empty")
        }

        return when (server.protocol) {
            ServerProtocol.WEBDAV -> openWebDavDocument(server, parsed.path, signal)
            ServerProtocol.SMB -> openSmbDocument(server, parsed.path, signal)
            ServerProtocol.FTP -> openFtpDocument(server, parsed.path, signal)
        }
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean {
        if (parentDocumentId == ROOT_DOCUMENT_ID) {
            return documentId != ROOT_DOCUMENT_ID
        }

        val parent = parseDocumentId(parentDocumentId)
        val child = parseDocumentId(documentId)
        if (parent.serverId != child.serverId) return false
        val server = getServer(parent.serverId) ?: return false
        return isChildPath(
            server = server,
            parentPath = parent.path,
            childPath = child.path,
        )
    }

    override fun getDocumentType(documentId: String): String {
        if (documentId == ROOT_DOCUMENT_ID) return DocumentsContract.Document.MIME_TYPE_DIR

        val parsed = parseDocumentId(documentId)
        val server = getServer(parsed.serverId) ?: return FALLBACK_VIDEO_MIME
        if (isServerRootDocument(server, parsed.path)) return DocumentsContract.Document.MIME_TYPE_DIR

        val fileName = parsed.path.substringAfterLast('/')
        val files = listFiles(server, parentPathOf(parsed.path))
        val file = files.firstOrNull { it.path.removeSuffix("/") == parsed.path.removeSuffix("/") }
        if (file?.isDirectory == true) return DocumentsContract.Document.MIME_TYPE_DIR
        return resolveMimeType(fileName, file?.contentType)
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path? {
        if (childDocumentId == ROOT_DOCUMENT_ID) {
            return DocumentsContract.Path(ROOT_ID, listOf(ROOT_DOCUMENT_ID))
        }

        val parsed = parseDocumentId(childDocumentId)
        val server = getServer(parsed.serverId) ?: return null
        val path = mutableListOf(ROOT_DOCUMENT_ID, buildServerDocumentId(server.id))

        if (!isServerRootDocument(server, parsed.path)) {
            path += buildDocumentPathSegments(server, parsed.path)
        }

        if (parentDocumentId != null && parentDocumentId !in path) {
            return null
        }

        return DocumentsContract.Path(ROOT_ID, path)
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): MatrixCursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))

        if (rootId != ROOT_ID) return result

        getServers().forEach { server ->
            listFiles(server, normalizeDirectoryPath(server, server.path))
                .filter { !it.isDirectory }
                .filter { it.name.contains(query, ignoreCase = true) }
                .forEach { file ->
                    appendFileRow(
                        result = result,
                        server = server,
                        file = file,
                    )
                }
        }
        return result
    }

    private fun appendDocumentRow(result: MatrixCursor, documentId: String) {
        if (documentId == ROOT_DOCUMENT_ID) {
            result.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, ROOT_TITLE)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                add(DocumentsContract.Document.COLUMN_FLAGS, DIRECTORY_FLAGS)
                add(DocumentsContract.Document.COLUMN_ICON, android.R.drawable.ic_menu_upload)
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
                add(DocumentsContract.Document.COLUMN_SIZE, null)
            }
            return
        }

        val parsed = parseDocumentId(documentId)
        val server = getServer(parsed.serverId) ?: return

        if (isServerRootDocument(server, parsed.path)) {
            appendServerRow(result, server)
            return
        }

        val files = listFiles(server, parentPathOf(parsed.path))
        val file = files.firstOrNull { it.path.removeSuffix("/") == parsed.path.removeSuffix("/") } ?: return
        appendFileRow(result, server, file)
    }

    private fun appendServerRow(
        result: MatrixCursor,
        server: RemoteServer,
    ) {
        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildServerDocumentId(server.id))
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, server.name.ifBlank { server.host })
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            add(DocumentsContract.Document.COLUMN_FLAGS, DIRECTORY_FLAGS)
            add(DocumentsContract.Document.COLUMN_ICON, android.R.drawable.ic_menu_upload)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            add(DocumentsContract.Document.COLUMN_SIZE, null)
        }
    }

    private fun appendFileRow(
        result: MatrixCursor,
        server: RemoteServer,
        file: RemoteFile,
    ) {
        val documentId = buildDocumentId(server.id, file.path)
        val isDirectory = file.isDirectory
        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    resolveMimeType(file.name, file.contentType)
                },
            )
            add(
                DocumentsContract.Document.COLUMN_FLAGS,
                if (isDirectory) DIRECTORY_FLAGS else FILE_FLAGS,
            )
            add(
                DocumentsContract.Document.COLUMN_ICON,
                if (isDirectory) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_media_play,
            )
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
            add(DocumentsContract.Document.COLUMN_SIZE, file.size.takeIf { !isDirectory })
        }
    }

    private fun listFiles(server: RemoteServer, path: String): List<RemoteFile> = when (server.protocol) {
        ServerProtocol.WEBDAV -> kotlinx.coroutines.runBlocking {
            webDavClient.listDirectory(server, path).getOrElse { exception ->
                Logger.error(TAG, "Failed to list WebDAV directory", exception)
                emptyList()
            }
        }
        ServerProtocol.SMB -> listSmbDirectory(server, path)
        ServerProtocol.FTP -> kotlinx.coroutines.runBlocking {
            ftpClient.listDirectory(server, path).getOrElse { exception ->
                Logger.error(TAG, "Failed to list FTP directory", exception)
                emptyList()
            }
        }
    }

    private fun listSmbDirectory(server: RemoteServer, path: String): List<RemoteFile> = runCatching {
        kotlinx.coroutines.runBlocking {
            smbClient.listDirectory(server, path).getOrElse { exception ->
                throw exception
            }
        }
    }.getOrElse { exception ->
        Logger.error(TAG, "Failed to list SMB directory", exception)
        emptyList()
    }

    private fun openWebDavDocument(
        server: RemoteServer,
        path: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val targetUrl = webDavClient.buildFileUrl(server, path)
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val authHeaders = webDavClient.buildAuthHeaders(server)
        val fileSize = findRemoteFile(server, path)?.size ?: throw FileNotFoundException("File size not found")
        return openProxyDocument(signal) { onReleased ->
            WebDavProxyFileCallback(
                url = targetUrl,
                authHeaders = authHeaders,
                fileSize = fileSize,
                httpClient = httpClient,
                onReleased = onReleased,
            )
        }
    }

    private fun openFtpDocument(
        server: RemoteServer,
        path: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val fileSize = findRemoteFile(server, path)?.size ?: throw FileNotFoundException("File size not found")
        return openProxyDocument(signal) { onReleased ->
            FtpProxyFileCallback(
                server = server,
                path = path,
                fileSize = fileSize,
                onReleased = onReleased,
            )
        }
    }

    private fun disconnectFtpClient(client: org.apache.commons.net.ftp.FTPClient) {
        runCatching { if (client.isConnected) client.logout() }
        runCatching { if (client.isConnected) client.disconnect() }
    }

    private fun openProxyDocument(
        signal: CancellationSignal?,
        createCallback: (() -> Unit) -> ProxyFileDescriptorCallback,
    ): ParcelFileDescriptor {
        val storageManager = context!!.getSystemService(StorageManager::class.java)
        val handlerThread = HandlerThread("CloudDocumentProxy").apply { start() }
        val callback = createCallback { handlerThread.quitSafely() }
        return try {
            val descriptor = storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                Handler(handlerThread.looper),
            )
            signal?.setOnCancelListener {
                runCatching { descriptor.close() }
            }
            descriptor
        } catch (exception: Exception) {
            runCatching { callback.onRelease() }
            handlerThread.quitSafely()
            throw exception
        }
    }

    private fun openSmbDocument(
        server: RemoteServer,
        path: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val sharePath = SmbClient.resolveSharePath(
            serverPath = server.path,
            path = path,
        )

        val config = SmbClient.buildFileConfig()
        val client = SMBClient(config)
        var connection: com.hierynomus.smbj.connection.Connection? = null
        var session: com.hierynomus.smbj.session.Session? = null
        var share: DiskShare? = null
        var file: com.hierynomus.smbj.share.File? = null

        try {
            connection = client.connect(server.host, server.port ?: SmbClient.DEFAULT_PORT)
            session = connection.authenticate(server.toSmbAuthContext())
            share = session.connectShare(sharePath.shareName) as DiskShare
            file = share.openFile(
                sharePath.relativePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            )
            val fileSize = file.getFileInformation().standardInformation.endOfFile
            return openProxyDocument(signal) { onReleased ->
                SmbProxyFileCallback(
                    client = client,
                    connection = connection,
                    session = session,
                    share = share,
                    file = file,
                    fileSize = fileSize,
                    onReleased = onReleased,
                )
            }
        } catch (exception: Exception) {
            closeSmbDocumentResources(
                client = client,
                connection = connection,
                session = session,
                share = share,
                file = file,
            )
            throw exception
        }
    }

    private fun closeSmbDocumentResources(
        client: SMBClient,
        connection: com.hierynomus.smbj.connection.Connection?,
        session: com.hierynomus.smbj.session.Session?,
        share: DiskShare?,
        file: com.hierynomus.smbj.share.File?,
    ) {
        runCatching { file?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client.close() }
    }

    private fun findRemoteFile(server: RemoteServer, path: String): RemoteFile? {
        val normalizedPath = path.removeSuffix("/")
        return listFiles(server, parentPathOf(path))
            .firstOrNull { file -> file.path.removeSuffix("/") == normalizedPath }
    }

    private fun getServers(): List<RemoteServer> = runCatching {
        kotlinx.coroutines.runBlocking {
            remoteServerRepository.getAll().first()
        }
    }.getOrDefault(emptyList())

    private fun getServer(serverId: Long): RemoteServer? = runCatching {
        kotlinx.coroutines.runBlocking {
            remoteServerRepository.getById(serverId)
        }
    }.getOrNull()

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        @Suppress("UNCHECKED_CAST")
        return projection as? Array<String> ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        @Suppress("UNCHECKED_CAST")
        return projection as? Array<String> ?: DEFAULT_DOCUMENT_PROJECTION
    }

    private fun buildRootSummary(serverCount: Int): String = "$serverCount 个云端位置"

    private fun buildServerDocumentId(serverId: Long): String = buildDocumentId(serverId, "/")

    private fun buildDocumentId(serverId: Long, path: String): String {
        val normalizedPath = if (path.isBlank()) "/" else path
        return "$serverId|${Uri.encode(normalizedPath)}"
    }

    private fun parseDocumentId(documentId: String): ParsedDocumentId {
        val separatorIndex = documentId.indexOf('|')
        if (separatorIndex <= 0) throw FileNotFoundException("Invalid documentId")
        val serverId = documentId.substring(0, separatorIndex).toLongOrNull()
            ?: throw FileNotFoundException("Invalid serverId")
        val encodedPath = documentId.substring(separatorIndex + 1)
        val decodedPath = Uri.decode(encodedPath).ifBlank { "/" }
        return ParsedDocumentId(serverId = serverId, path = decodedPath)
    }

    private fun parentPathOf(path: String): String {
        val normalized = path.removeSuffix("/")
        val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isBlank()) "/" else "$parent/"
    }

    private fun buildDocumentPathSegments(
        server: RemoteServer,
        path: String,
    ): List<String> {
        if (isServerRootDocument(server, path)) return emptyList()

        val normalizedPath = normalizeDirectoryPath(server, path)
        val normalized = normalizedPath.removePrefix("/").removeSuffix("/")
        if (normalized.isBlank()) return emptyList()

        val segments = normalized.split('/').filter { it.isNotBlank() }
        val documentIds = mutableListOf<String>()
        var currentPath = "/"

        if (server.protocol == ServerProtocol.SMB && SmbClient.isRootPath(server.path)) {
            for (segment in segments) {
                currentPath = if (currentPath == "/") "/$segment/" else "$currentPath$segment/"
                documentIds += buildDocumentId(server.id, currentPath)
            }
            return documentIds
        }

        val serverBasePath = normalizeDirectoryPath(server, server.path)
        val serverBaseSegments = serverBasePath.removePrefix("/").removeSuffix("/")
            .split('/')
            .filter { it.isNotBlank() }
        val currentSegments = serverBaseSegments.toMutableList()
        val relativeSegments = segments.drop(serverBaseSegments.size)

        for (segment in relativeSegments) {
            currentSegments += segment
            currentPath = "/${currentSegments.joinToString("/")}/"
            documentIds += buildDocumentId(server.id, currentPath)
        }

        return documentIds
    }

    private fun resolveListPath(server: RemoteServer, path: String): String {
        if (isServerRootDocument(server, path)) {
            return normalizeDirectoryPath(server, server.path)
        }
        return normalizeDirectoryPath(server, path)
    }

    private fun isServerRootDocument(server: RemoteServer, path: String): Boolean {
        val normalizedPath = normalizeDirectoryPath(server, path)
        if (normalizedPath == "/") return true

        val serverRoot = normalizeDirectoryPath(server, server.path)
        return when (server.protocol) {
            ServerProtocol.SMB -> normalizedPath.equals(serverRoot, ignoreCase = true)
            ServerProtocol.WEBDAV,
            ServerProtocol.FTP,
            -> normalizedPath == serverRoot
        }
    }

    private fun isChildPath(
        server: RemoteServer,
        parentPath: String,
        childPath: String,
    ): Boolean {
        val parent = normalizeDirectoryPath(server, parentPath).removeSuffix("/")
        val child = normalizeDirectoryPath(server, childPath).removeSuffix("/")
        if (parent.isBlank()) return child.isNotBlank()
        if (server.protocol == ServerProtocol.SMB) {
            if (child.equals(parent, ignoreCase = true)) return false
            return child.startsWith("$parent/", ignoreCase = true)
        }

        if (child == parent) return false
        return child.startsWith("$parent/")
    }

    private fun resolveMimeType(
        name: String,
        declaredMimeType: String?,
    ): String {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension == WEBVTT_EXTENSION) return FALLBACK_WEBVTT_MIME

        val cleanMimeType = declaredMimeType.orEmpty().takeIf {
            it.isNotBlank() && it != "application/octet-stream" && it != "audio/aac"
        }
        if (cleanMimeType != null) return cleanMimeType

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when {
                VIDEO_EXTENSIONS.contains(extension) -> FALLBACK_VIDEO_MIME
                SUBTITLE_EXTENSIONS.contains(extension) -> FALLBACK_SUBTITLE_MIME
                else -> FALLBACK_BINARY_MIME
            }
    }

    private abstract inner class CloudProxyFileCallback(
        private val fileSize: Long,
        private val onReleased: () -> Unit,
    ) : ProxyFileDescriptorCallback() {

        private var isReleased = false
        private val readCache = object : LinkedHashMap<Long, ByteArray>(REMOTE_READ_CACHE_MAX_BLOCKS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > REMOTE_READ_CACHE_MAX_BLOCKS
        }

        override fun onGetSize(): Long {
            if (fileSize >= 0L) return fileSize
            throw ErrnoException("onGetSize", OsConstants.EIO)
        }

        final override fun onRelease() {
            if (isReleased) return
            isReleased = true
            runCatching { closeResources() }
            onReleased()
        }

        protected fun readSizeAt(offset: Long, requestedSize: Int): Int {
            if (requestedSize <= 0) return 0
            if (offset >= fileSize) return 0
            return minOf(requestedSize.toLong(), fileSize - offset).toInt()
        }

        protected fun readCachedAt(
            offset: Long,
            requestedSize: Int,
            data: ByteArray,
            fetchBlock: (offset: Long, size: Int) -> ByteArray,
        ): Int {
            val bytesToRead = readSizeAt(offset, requestedSize)
            if (bytesToRead <= 0) return 0

            var copied = 0
            synchronized(readCache) {
                while (copied < bytesToRead) {
                    val currentOffset = offset + copied
                    val blockOffset = currentOffset % REMOTE_READ_CACHE_BLOCK_SIZE_BYTES
                    val blockStart = currentOffset - blockOffset
                    val block = readCache[blockStart] ?: run {
                        val blockSize = readSizeAt(blockStart, REMOTE_READ_CACHE_BLOCK_SIZE_BYTES)
                        fetchBlock(blockStart, blockSize).also { bytes ->
                            if (bytes.size == blockSize) readCache[blockStart] = bytes
                        }
                    }
                    val copyStart = blockOffset.toInt()
                    if (copyStart >= block.size) return copied

                    val copySize = minOf(bytesToRead - copied, block.size - copyStart)
                    System.arraycopy(block, copyStart, data, copied, copySize)
                    copied += copySize
                }
            }
            return copied
        }

        protected fun toErrnoException(
            functionName: String,
            exception: Exception,
        ): ErrnoException {
            Logger.error(TAG, "Failed to read cloud document", exception)
            return ErrnoException(functionName, OsConstants.EIO)
        }

        protected open fun closeResources() = Unit
    }

    private inner class WebDavProxyFileCallback(
        private val url: String,
        private val authHeaders: Map<String, String>,
        fileSize: Long,
        private val httpClient: OkHttpClient,
        onReleased: () -> Unit,
    ) : CloudProxyFileCallback(fileSize, onReleased) {

        override fun onRead(
            offset: Long,
            size: Int,
            data: ByteArray,
        ): Int = try {
            readCachedAt(offset, size, data) { blockOffset, blockSize ->
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$blockOffset-${blockOffset + blockSize - 1L}")
                authHeaders.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body
                    if (response.code != HTTP_PARTIAL_CONTENT_CODE && (blockOffset > 0L || !response.isSuccessful)) {
                        throw IOException("HTTP ${response.code}")
                    }
                    body.byteStream().use { input ->
                        input.readBytesUpTo(blockSize)
                    }
                }
            }
        } catch (exception: Exception) {
            throw toErrnoException("onRead", exception)
        }
    }

    private inner class FtpProxyFileCallback(
        private val server: RemoteServer,
        private val path: String,
        fileSize: Long,
        onReleased: () -> Unit,
    ) : CloudProxyFileCallback(fileSize, onReleased) {

        override fun onRead(
            offset: Long,
            size: Int,
            data: ByteArray,
        ): Int = try {
            readCachedAt(offset, size, data) { blockOffset, blockSize ->
                val client = org.apache.commons.net.ftp.FTPClient().apply {
                    connectTimeout = FtpClient.CONNECT_TIMEOUT_MS
                    dataTimeout = java.time.Duration.ofMillis(FtpClient.DATA_TIMEOUT_MS.toLong())
                    setControlEncoding(Charsets.UTF_8.name())
                    setAutodetectUTF8(true)
                }
                try {
                    client.connect(server.host, server.port ?: FtpClient.DEFAULT_PORT)
                    val loginOk = if (server.username.isBlank()) {
                        client.login("anonymous", "")
                    } else {
                        client.login(server.username, server.password)
                    }
                    if (!loginOk) throw IOException("FTP login failed")

                    client.enterLocalPassiveMode()
                    client.setFileType(org.apache.commons.net.ftp.FTPClient.BINARY_FILE_TYPE)
                    client.setRestartOffset(blockOffset)
                    val input = client.retrieveFileStream(path) ?: throw IOException("FTP file not found")
                    input.use { source ->
                        source.readBytesUpTo(blockSize)
                    }.also {
                        client.completePendingCommand()
                    }
                } finally {
                    runCatching { if (client.isConnected) client.logout() }
                    runCatching { if (client.isConnected) client.disconnect() }
                }
            }
        } catch (exception: Exception) {
            throw toErrnoException("onRead", exception)
        }
    }

    private inner class SmbProxyFileCallback(
        private val client: SMBClient,
        private val connection: com.hierynomus.smbj.connection.Connection?,
        private val session: com.hierynomus.smbj.session.Session?,
        private val share: DiskShare?,
        private val file: com.hierynomus.smbj.share.File?,
        fileSize: Long,
        onReleased: () -> Unit,
    ) : CloudProxyFileCallback(fileSize, onReleased) {

        override fun onRead(
            offset: Long,
            size: Int,
            data: ByteArray,
        ): Int {
            val targetFile = file ?: return 0
            val bytesToRead = readSizeAt(offset, size)
            if (bytesToRead <= 0) return 0

            return try {
                synchronized(targetFile) {
                    var total = 0
                    while (total < bytesToRead) {
                        val read = targetFile.read(
                            data,
                            offset + total,
                            total,
                            bytesToRead - total,
                        )
                        if (read <= 0) break
                        total += read
                    }
                    total
                }
            } catch (exception: Exception) {
                throw toErrnoException("onRead", exception)
            }
        }

        override fun closeResources() {
            runCatching { file?.close() }
            runCatching { share?.close() }
            runCatching { session?.close() }
            runCatching { connection?.close() }
            runCatching { client.close() }
        }
    }

    private fun normalizeDirectoryPath(server: RemoteServer, path: String): String = when (server.protocol) {
        ServerProtocol.SMB -> SmbClient.normalizeRemotePath(path, isDirectory = true)
        ServerProtocol.WEBDAV,
        ServerProtocol.FTP,
        -> path.ensureLeadingSlash().ensureDirectoryPath()
    }

    private fun String.ensureDirectoryPath(): String = if (endsWith('/')) this else "$this/"

    private fun String.ensureLeadingSlash(): String = if (startsWith('/')) this else "/$this"

    private data class ParsedDocumentId(
        val serverId: Long,
        val path: String,
    )

    companion object {
        private const val TAG = "CloudDocumentsProvider"
        private const val HTTP_PARTIAL_CONTENT_CODE = 206
        private const val ROOT_ID = "cloud"
        private const val ROOT_DOCUMENT_ID = "root"
        private const val ROOT_TITLE = "Only Player"
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val REMOTE_READ_CACHE_BLOCK_SIZE_BYTES = 512 * 1024
        private const val REMOTE_READ_CACHE_MAX_BLOCKS = 8
        private const val FALLBACK_VIDEO_MIME = "video/*"
        private const val FALLBACK_BINARY_MIME = "application/octet-stream"
        private const val FALLBACK_SUBTITLE_MIME = "application/x-subrip"
        private const val FALLBACK_WEBVTT_MIME = "text/vtt"
        private const val ROOT_MIME_TYPES = "video/*\napplication/x-matroska\nvideo/mp4\napplication/x-subrip\ntext/vtt\ntext/x-ssa\ntext/x-ass\napplication/ass\napplication/ssa\ntext/plain"

        private const val ROOT_FLAGS =
            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD or
                DocumentsContract.Root.FLAG_SUPPORTS_SEARCH

        private const val DIRECTORY_FLAGS =
            DocumentsContract.Document.FLAG_DIR_PREFERS_GRID or
                DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED

        private const val FILE_FLAGS = 0

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_ICON,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private val VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mts",
            "ts",
            "webm",
            "wmv",
        )

        private val SUBTITLE_EXTENSIONS = setOf(
            "ass",
            "sami",
            "smi",
            "srt",
            "ssa",
            "sub",
            "vtt",
            WEBVTT_EXTENSION,
        )

        private const val WEBVTT_EXTENSION = "webvtt"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudDocumentsProviderEntryPoint {
    fun remoteServerRepository(): RemoteServerRepository
    fun webDavClient(): WebDavClient
    fun ftpClient(): FtpClient
    fun smbClient(): SmbClient
}

private fun readInputUpTo(
    input: InputStream,
    target: ByteArray,
    targetSize: Int,
): Int {
    var total = 0
    while (total < targetSize) {
        val read = input.read(target, total, targetSize - total)
        if (read <= 0) break
        total += read
    }
    return total
}

private fun InputStream.readBytesUpTo(targetSize: Int): ByteArray {
    val target = ByteArray(targetSize)
    val read = readInputUpTo(
        input = this,
        target = target,
        targetSize = targetSize,
    )
    return if (read == target.size) target else target.copyOf(read)
}
