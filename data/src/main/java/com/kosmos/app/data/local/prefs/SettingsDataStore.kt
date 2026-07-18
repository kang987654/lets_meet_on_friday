package com.kosmos.app.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.kosmos.app.core.common.Constants

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val RESPONSE_STYLE_KEY = stringPreferencesKey("response_style")
        private val MAX_TOKENS_KEY = intPreferencesKey("max_tokens")
    }

    val responseStyleFlow: Flow<String> = dataStore.data.map {
        it[RESPONSE_STYLE_KEY] ?: "DEFAULT"
    }

    val maxTokensFlow: Flow<Int> = dataStore.data.map {
        it[MAX_TOKENS_KEY] ?: Constants.MAX_CONTEXT_TOKENS
    }

    suspend fun saveResponseStyle(style: String) {
        dataStore.edit { prefs ->
            prefs[RESPONSE_STYLE_KEY] = style
        }
    }

    suspend fun saveMaxTokens(tokens: Int) {
        dataStore.edit { prefs ->
            prefs[MAX_TOKENS_KEY] = tokens
        }
    }
}
