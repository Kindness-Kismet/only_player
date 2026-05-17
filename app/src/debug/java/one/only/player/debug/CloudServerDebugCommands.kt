package one.only.player.debug

import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol

internal fun Context.runCloudServerCommand(
    action: String,
    target: String?,
    extras: Bundle?,
): Bundle {
    val command = "cloud.server.$action"
    val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        DebugCommandEntryPoint::class.java,
    )
    val value = extras.withTarget(target)

    return runCatching {
        runBlocking { entryPoint.runCloudServerAction(action, value) }
    }.getOrElse {
        debugResult(
            isOk = false,
            message = it.message ?: "Failed to handle cloud server action: $action",
            command = command,
            target = action,
        )
    }
}

private suspend fun DebugCommandEntryPoint.runCloudServerAction(
    action: String,
    extras: Bundle,
): Bundle {
    val command = "cloud.server.$action"
    val repository = remoteServerRepository()
    return when (action) {
        "add" -> {
            val server = extras.toRemoteServer()
            val id = repository.insert(server)
            debugResult(
                isOk = true,
                message = "Added cloud server: $id",
                command = command,
                target = action,
                value = id.toString(),
            )
        }
        "update" -> {
            val id = extras.requiredTargetLong(EXTRA_ID)
            repository.update(extras.toRemoteServer(id))
            debugResult(
                isOk = true,
                message = "Updated cloud server: $id",
                command = command,
                target = action,
                value = id.toString(),
            )
        }
        "delete" -> {
            val id = extras.requiredTargetLong(EXTRA_ID)
            repository.deleteById(id)
            debugResult(
                isOk = true,
                message = "Deleted cloud server: $id",
                command = command,
                target = action,
                value = id.toString(),
            )
        }
        "clear" -> {
            val servers = repository.getAll().first()
            servers.forEach { server -> repository.deleteById(server.id) }
            debugResult(
                isOk = true,
                message = "Cleared cloud servers: ${servers.size}",
                command = command,
                target = action,
                value = servers.size.toString(),
            )
        }
        "list" -> {
            val servers = repository.getAll().first()
            debugResult(
                isOk = true,
                message = servers.joinToString(separator = "; ") { server ->
                    "${server.id}:${server.protocol.name}:${server.host}:${server.port ?: ""}${server.path}"
                },
                command = command,
                target = action,
                value = servers.size.toString(),
            )
        }
        else -> error("Unknown cloud server action: $action")
    }
}

private fun Bundle.toRemoteServer(id: Long = 0): RemoteServer {
    val protocol = enumValue<ServerProtocol>(requiredString(EXTRA_PROTOCOL))
    val host = requiredString(EXTRA_HOST)
    return RemoteServer(
        id = id,
        name = getString(EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: host,
        protocol = protocol,
        host = host,
        port = optionalInt(EXTRA_PORT),
        path = getString(EXTRA_PATH)?.ifBlank { "/" } ?: "/",
        username = getString(EXTRA_USERNAME).orEmpty(),
        password = getString(EXTRA_PASSWORD).orEmpty(),
        isProxyEnabled = getBoolean(EXTRA_PROXY_ENABLED, false),
        proxyHost = getString(EXTRA_PROXY_HOST).orEmpty(),
        proxyPort = optionalInt(EXTRA_PROXY_PORT),
    )
}
