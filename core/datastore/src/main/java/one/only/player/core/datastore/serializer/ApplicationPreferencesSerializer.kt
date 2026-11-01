package one.only.player.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.NavigationBarStyle

object ApplicationPreferencesSerializer : Serializer<ApplicationPreferences> {

    private const val TAG = "ApplicationPreferencesSerializer"
    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private val legacyKeys = setOf(
        "ignoreNoMediaFiles",
        "markLastPlayedMedia",
        "showDurationField",
        "showExtensionField",
        "showPathField",
        "showPlayedProgress",
        "showResolutionField",
        "showSizeField",
        "showThumbnailField",
        "useDynamicColors",
    )

    override val defaultValue: ApplicationPreferences
        get() = ApplicationPreferences()

    override suspend fun readFrom(input: InputStream): ApplicationPreferences = decode(input.readBytes().decodeToString())

    internal fun readFromFile(file: File): ApplicationPreferences = readPersistedDataStoreValue(
        file = file,
        defaultValue = defaultValue,
        tag = TAG,
        valueName = "application preferences",
        decode = ::decode,
    )

    private fun decode(serializedPreferences: String): ApplicationPreferences = try {
        val root = jsonFormat.parseToJsonElement(serializedPreferences) as? JsonObject
            ?: throw SerializationException("Expected application preferences object")
        if (root.keys.any(legacyKeys::contains)) {
            throw CorruptionException(
                message = "Cannot read datastore",
                cause = IllegalStateException("Legacy application preferences format is unsupported"),
            )
        }
        // 旧版悬浮开关迁移为样式枚举，已有的新样式优先。
        val migratedRoot = if ("navigationBarStyle" !in root && root["shouldUseFloatingNavigationBar"] == JsonPrimitive(true)) {
            JsonObject(root + ("navigationBarStyle" to JsonPrimitive(NavigationBarStyle.FLOATING.name)))
        } else {
            root
        }
        jsonFormat.decodeFromJsonElement(
            deserializer = ApplicationPreferences.serializer(),
            element = migratedRoot,
        ).withCanonicalLayoutPaths()
    } catch (exception: SerializationException) {
        throw CorruptionException("Cannot read datastore", exception)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: ApplicationPreferences, output: OutputStream) {
        output.write(
            jsonFormat.encodeToString(
                serializer = ApplicationPreferences.serializer(),
                value = t,
            ).encodeToByteArray(),
        )
    }
}
