package com.kosmos.app.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val MODEL_ID_KEY = stringPreferencesKey("model_id")
        private val MODEL_PATH_KEY = stringPreferencesKey("model_path")
        private val QUANTIZATION_KEY = stringPreferencesKey("quantization")
    }

    val modelIdFlow: Flow<String?> = dataStore.data.map { it[MODEL_ID_KEY] }
    val modelPathFlow: Flow<String?> = dataStore.data.map { it[MODEL_PATH_KEY] }
    val quantizationFlow: Flow<String?> = dataStore.data.map { it[QUANTIZATION_KEY] }

    suspend fun saveModelInfo(modelId: String, path: String, quantization: String) {
        dataStore.edit { prefs ->
            prefs[MODEL_ID_KEY] = modelId
            prefs[MODEL_PATH_KEY] = path
            prefs[QUANTIZATION_KEY] = quantization
        }
    }
}
