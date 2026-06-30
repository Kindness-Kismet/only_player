package one.only.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationPreferencesTest {

    @Test
    fun isPathExcluded_matchesOnlyConfiguredFolderAndDescendants() {
        val preferences = ApplicationPreferences(
            excludeFolders = listOf("/storage/emulated/0/Movies/Private"),
        )

        assertTrue(preferences.isPathExcluded("/storage/emulated/0/Movies/Private"))
        assertTrue(preferences.isPathExcluded("/storage/emulated/0/Movies/Private/Camera"))
        assertFalse(preferences.isPathExcluded("/storage/emulated/0/Movies/Privateer"))
        assertFalse(preferences.isPathExcluded(""))
    }

    @Test
    fun cloudQuickSettings_areIsolatedByServerAndRejectUnsupportedSort() {
        val preferences = ApplicationPreferences()
            .withCloudQuickSettings(
                serverId = 1L,
                settings = CloudQuickSettings(
                    sortBy = Sort.By.DATE,
                    sortOrder = Sort.Order.DESCENDING,
                    mediaLayoutMode = MediaLayoutMode.GRID,
                    shouldShowPathField = false,
                ),
            )
            .withCloudQuickSettings(
                serverId = 2L,
                settings = CloudQuickSettings(
                    sortBy = Sort.By.SIZE,
                    sortOrder = Sort.Order.ASCENDING,
                ),
            )

        assertEquals(Sort.By.TITLE, preferences.cloudQuickSettings(1L).sortBy)
        assertEquals(Sort.Order.DESCENDING, preferences.cloudQuickSettings(1L).sortOrder)
        assertEquals(MediaLayoutMode.GRID, preferences.cloudQuickSettings(1L).mediaLayoutMode)
        assertFalse(preferences.cloudQuickSettings(1L).shouldShowPathField)
        assertEquals(Sort.By.SIZE, preferences.cloudQuickSettings(2L).sortBy)
        assertEquals(CloudQuickSettings(), preferences.cloudQuickSettings(3L))

        val withoutFirstServer = preferences.withoutCloudQuickSettings(1L)

        assertEquals(CloudQuickSettings(), withoutFirstServer.cloudQuickSettings(1L))
        assertEquals(Sort.By.SIZE, withoutFirstServer.cloudQuickSettings(2L).sortBy)
    }
}
