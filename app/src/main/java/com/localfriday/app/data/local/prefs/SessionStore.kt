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
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val ACTIVE_SESSION_ID_KEY = stringPreferencesKey("active_session_id")
    }

    val activeSessionIdFlow: Flow<String?> = dataStore.data.map {
        it[ACTIVE_SESSION_ID_KEY]
    }

    suspend fun saveActiveSessionId(sessionId: String) {
        dataStore.edit { prefs ->
            prefs[ACTIVE_SESSION_ID_KEY] = sessionId
        }
    }

    suspend fun clearActiveSessionId() {
        dataStore.edit { prefs ->
            prefs.remove(ACTIVE_SESSION_ID_KEY)
        }
    }
}
