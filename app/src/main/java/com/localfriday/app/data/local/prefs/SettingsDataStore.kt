package com.localfriday.app.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val RESPONSE_STYLE_KEY = stringPreferencesKey("response_style")
    }

    val responseStyleFlow: Flow<String> = dataStore.data.map {
        it[RESPONSE_STYLE_KEY] ?: "DEFAULT"
    }

    suspend fun saveResponseStyle(style: String) {
        dataStore.edit { prefs ->
            prefs[RESPONSE_STYLE_KEY] = style
        }
    }
}
