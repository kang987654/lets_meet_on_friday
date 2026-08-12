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

    /**
     * 프리필 예산. 저장된 값이 엔진 KV 천장을 넘으면 잘라서 내보냅니다.
     *
     * [WHY] 슬라이더 상한이 8000 이던 시절에 저장된 값이 기존 설치에 남아 있다. 그 값을 그대로
     * 되살리면 이번에 고친 초과 문제가 **업그레이드한 사용자에게만** 되살아난다 — 새 설치는
     * 정상인데 기존 사용자만 겪는, 재현이 가장 어려운 형태의 결함이 된다.
     *
     * [WHY] 저장값을 덮어쓰지 않고 읽을 때만 자른다. 사용자가 슬라이더를 만지지도 않았는데
     * 저장값이 바뀌는 것은 설정을 임의로 조작하는 것이고, 나중에 천장이 올라가면 원래 의도한
     * 값으로 자연히 복구된다.
     */
    val maxTokensFlow: Flow<Int> = dataStore.data.map {
        (it[MAX_TOKENS_KEY] ?: Constants.MAX_CONTEXT_TOKENS)
            .coerceAtMost(Constants.PREFILL_CEILING_TOKENS)
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
