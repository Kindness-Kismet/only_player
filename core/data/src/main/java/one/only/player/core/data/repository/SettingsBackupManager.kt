package one.only.player.core.data.repository

import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import one.only.player.core.model.PlayerIconStyle
import one.only.player.core.model.SettingsBackup

class SettingsBackupManager @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun write(outputStream: OutputStream, settingsBackup: SettingsBackup) {
        outputStream.write(
            json.encodeToString(
                serializer = SettingsBackup.serializer(),
                value = settingsBackup,
            ).encodeToByteArray(),
        )
    }

    fun read(inputStream: InputStream): SettingsBackup {
        val rawBackup = inputStream.readBytes().decodeToString()
        val backup = json.decodeFromString(
            deserializer = SettingsBackup.serializer(),
            string = rawBackup,
        )
        return backup.upgradeLegacyDefaults(rawBackup)
    }

    private fun SettingsBackup.upgradeLegacyDefaults(rawBackup: String): SettingsBackup {
        val root = runCatching { json.parseToJsonElement(rawBackup).jsonObject }.getOrNull() ?: return this
        val playerRoot = root["playerPreferences"]?.jsonObject ?: return this
        if ("playerIconStyle" in playerRoot) return this
        if (playerRoot["shouldUseClassicPlayerIcons"]?.jsonPrimitive?.content != "true") return this
        return copy(playerPreferences = playerPreferences.copy(playerIconStyle = PlayerIconStyle.CLASSIC))
    }
}
