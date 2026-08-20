package one.only.player.core.datastore.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import one.only.player.core.common.Logger
import one.only.player.core.datastore.di.APP_PREFERENCES_DATASTORE_FILE
import one.only.player.core.datastore.serializer.ApplicationPreferencesSerializer
import one.only.player.core.model.ApplicationPreferences

class AppPreferencesDataSource @Inject constructor(
    @ApplicationContext context: Context,
    private val appPreferences: DataStore<ApplicationPreferences>,
) : PreferencesDataSource<ApplicationPreferences> {

    companion object {
        private const val TAG = "AppPreferencesDataSource"
    }

    override val preferences = appPreferences.data
    val bootstrapPreferences = ApplicationPreferencesSerializer.readFromFile(
        context.dataStoreFile(APP_PREFERENCES_DATASTORE_FILE),
    )

    override suspend fun update(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        try {
            appPreferences.updateData(transform)
        } catch (ioException: Exception) {
            Logger.error(TAG, "Failed to update app preferences: $ioException")
        }
    }
}
