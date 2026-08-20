package one.only.player.core.datastore.serializer

import java.io.File
import one.only.player.core.model.ApplicationPreferences
import one.only.player.core.model.MediaLayoutMode
import one.only.player.core.model.MediaViewMode
import one.only.player.core.model.Sort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApplicationPreferencesSerializerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readFromFilePreservesSavedMediaSelections() {
        val preferencesFile = temporaryFolder.newFile().apply {
            writeText(
                """
                {
                    "mediaLayoutMode": "GRID",
                    "mediaViewMode": "VIDEOS",
                    "mediaLayoutScale": 1.35,
                    "sortBy": "DATE",
                    "sortOrder": "DESCENDING"
                }
                """.trimIndent(),
            )
        }

        val preferences = ApplicationPreferencesSerializer.readFromFile(preferencesFile)

        assertEquals(MediaLayoutMode.GRID, preferences.mediaLayoutMode)
        assertEquals(MediaViewMode.VIDEOS, preferences.mediaViewMode)
        assertEquals(1.35f, preferences.mediaLayoutScale)
        assertEquals(Sort.By.DATE, preferences.sortBy)
        assertEquals(Sort.Order.DESCENDING, preferences.sortOrder)
    }

    @Test
    fun readFromFileIgnoresUnknownFields() {
        val preferencesFile = temporaryFolder.newFile().apply {
            writeText(
                """
                {
                    "mediaLayoutMode": "GRID",
                    "mediaViewMode": "FOLDER_TREE",
                    "futurePreference": true
                }
                """.trimIndent(),
            )
        }

        val preferences = ApplicationPreferencesSerializer.readFromFile(preferencesFile)

        assertEquals(MediaLayoutMode.GRID, preferences.mediaLayoutMode)
        assertEquals(MediaViewMode.FOLDER_TREE, preferences.mediaViewMode)
    }

    @Test
    fun readFromFileReturnsDefaultForMissingFile() {
        val missingFile = File(temporaryFolder.root, "missing.json")

        assertEquals(ApplicationPreferences(), ApplicationPreferencesSerializer.readFromFile(missingFile))
    }

    @Test
    fun readFromFileReturnsDefaultForUnreadableFile() {
        val unreadableFile = temporaryFolder.newFolder("preferences.json")

        assertEquals(ApplicationPreferences(), ApplicationPreferencesSerializer.readFromFile(unreadableFile))
    }

    @Test
    fun readFromFileReturnsDefaultForUnreadablePayloads() {
        val payloads = listOf(
            "",
            "not-json",
            """{"ignoreNoMediaFiles":true,"mediaLayoutMode":"GRID"}""",
        )

        payloads.forEachIndexed { index, payload ->
            val preferencesFile = temporaryFolder.newFile("invalid-$index.json").apply {
                writeText(payload)
            }

            assertEquals(ApplicationPreferences(), ApplicationPreferencesSerializer.readFromFile(preferencesFile))
        }
    }
}
