package one.only.player.core.data.repository

import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.serialization.json.Json
import one.only.player.core.datastore.serializer.withCanonicalLayoutPaths
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

    fun read(inputStream: InputStream): SettingsBackup = json.decodeFromString(
        deserializer = SettingsBackup.serializer(),
        string = inputStream.readBytes().decodeToString(),
    ).let { it.copy(applicationPreferences = it.applicationPreferences.withCanonicalLayoutPaths()) }
}
