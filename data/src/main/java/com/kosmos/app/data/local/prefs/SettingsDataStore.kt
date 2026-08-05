package com.kosmos.app.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val WEB_SEARCH_ENABLED_KEY = booleanPreferencesKey("web_search_enabled")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    // [WHY] 테마 모드는 UI 계층의 enum(ThemeMode)이므로 data 계층에서는 키 문자열로만 다룬다
    // (SYSTEM/LIGHT/DARK). 기본값 SYSTEM — 기기 설정을 따른다. (ADR-005)
    val themeModeFlow: Flow<String> = dataStore.data.map {
        it[THEME_MODE_KEY] ?: "SYSTEM"
    }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    // [WHY] 프라이버시 우선 원칙에 따라 웹 검색(네트워크 egress)은 기본 비활성화(false)이며,
    // 사용자가 채팅 헤더 토글로 명시적으로 허용해야 활성화된다. (2026-07-31 기획 변경)
    val webSearchEnabledFlow: Flow<Boolean> = dataStore.data.map {
        it[WEB_SEARCH_ENABLED_KEY] ?: false
    }

    suspend fun saveWebSearchEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WEB_SEARCH_ENABLED_KEY] = enabled
        }
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
