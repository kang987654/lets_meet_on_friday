package com.kosmos.app.data.local.prefs

import com.kosmos.app.core.common.ResponseStyle
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
        private val BRIEFING_ENABLED_KEY = booleanPreferencesKey("briefing_enabled")
        private val BRIEFING_TIME_MINUTES_KEY = intPreferencesKey("briefing_time_minutes")
        private val AUTO_EXTRACT_ENABLED_KEY = booleanPreferencesKey("auto_extract_enabled")
        private val TTS_AUTO_READ_KEY = booleanPreferencesKey("tts_auto_read")
        private val TTS_ENGINE_KEY = stringPreferencesKey("tts_engine")
        private val DOC_TEXT_SCALE_KEY = intPreferencesKey("doc_text_scale_step")

        // [WHY] 557 = 09:17 (사용자 지정 기본, 2026-08-21). 자정 기준 분 단위 int 하나가
        // 단일 출처다 — 시/분을 따로 저장하면 갱신이 반쪽만 될 수 있다.
        const val DEFAULT_BRIEFING_TIME_MINUTES = 9 * 60 + 17
    }

    // [WHY] 브리핑은 기본 켜짐 — 1인 앱이라 옵트인 부담이 없고, 선제형 비서가 이 앱의
    // 정체성이다 (expand.md 순서 원칙 ④). 알림·본문 생성 둘 다 이 플래그 하나에 걸린다.
    val briefingEnabledFlow: Flow<Boolean> = dataStore.data.map {
        it[BRIEFING_ENABLED_KEY] ?: true
    }

    suspend fun saveBriefingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BRIEFING_ENABLED_KEY] = enabled
        }
    }

    // [WHY] 자동 추출(C′2)은 기본 켜짐 — 브리핑과 같은 이유(1인 앱, 선제형 정체성). 지식은
    // 자동 저장·프로필은 승인 카드라는 계약 자체가 이 플래그 하나에 걸린다. OFF 면 추출 oneShot
    // 자체가 돌지 않는다(대기 중 제안은 남아 카드로 종결 가능).
    val autoExtractEnabledFlow: Flow<Boolean> = dataStore.data.map {
        it[AUTO_EXTRACT_ENABLED_KEY] ?: true
    }

    suspend fun saveAutoExtractEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_EXTRACT_ENABLED_KEY] = enabled
        }
    }

    // [WHY] 자동 낭독은 기본 꺼짐(사용자 결정, 0.29.0) — 켜 두면 첫 실행부터 소리가 나 당황스럽다.
    val ttsAutoReadFlow: Flow<Boolean> = dataStore.data.map {
        it[TTS_AUTO_READ_KEY] ?: false
    }

    suspend fun saveTtsAutoRead(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[TTS_AUTO_READ_KEY] = enabled
        }
    }

    /** TTS 엔진 패키지. 빈 문자열 = 시스템 기본 엔진. */
    val ttsEngineFlow: Flow<String> = dataStore.data.map {
        it[TTS_ENGINE_KEY] ?: ""
    }

    suspend fun saveTtsEngine(enginePackage: String) {
        dataStore.edit { prefs ->
            prefs[TTS_ENGINE_KEY] = enginePackage
        }
    }

    val briefingTimeMinutesFlow: Flow<Int> = dataStore.data.map {
        (it[BRIEFING_TIME_MINUTES_KEY] ?: DEFAULT_BRIEFING_TIME_MINUTES)
            .coerceIn(0, 24 * 60 - 1)
    }

    suspend fun saveBriefingTimeMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[BRIEFING_TIME_MINUTES_KEY] = minutes
        }
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
        it[RESPONSE_STYLE_KEY] ?: ResponseStyle.DEFAULT
    }

    /**
     * 프리필 예산. 저장된 값이 현재 상한을 넘으면 잘라서 내보냅니다.
     *
     * [WHY] 슬라이더 상한이 8000·3000 이던 시절에 저장된 값이 기존 설치에 남아 있다. 그 값을
     * 그대로 되살리면 이번에 고친 문제(예전엔 KV 초과, 지금은 GPU 숫자 깨짐 구간 진입)가
     * **업그레이드한 사용자에게만** 되살아난다 — 새 설치는 정상인데 기존 사용자만 겪는,
     * 재현이 가장 어려운 형태의 결함이 된다.
     *
     * [WHY] 상한이 [Constants.PREFILL_CEILING_TOKENS](엔진 용량)에서
     * [Constants.MAX_CONTEXT_TOKENS](GPU 발병점 아래, ADR-021)로 내려왔다 — 지금의 구속
     * 조건은 용량이 아니라 GPU 숫자 정밀도다.
     *
     * [WHY] 저장값을 덮어쓰지 않고 읽을 때만 자른다. 사용자가 슬라이더를 만지지도 않았는데
     * 저장값이 바뀌는 것은 설정을 임의로 조작하는 것이고, 나중에 상한이 올라가면 원래 의도한
     * 값으로 자연히 복구된다.
     */
    val maxTokensFlow: Flow<Int> = dataStore.data.map {
        (it[MAX_TOKENS_KEY] ?: Constants.MAX_CONTEXT_TOKENS)
            .coerceAtMost(Constants.MAX_CONTEXT_TOKENS)
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

    // [WHY] 문서 읽기 모드 글자 크기(0.35.0) — 단계 번호 하나만 저장한다(0=작게, 1=보통, 2=크게 — 계획서 3단계). 배율 값을 저장하면
    // 단계 표를 바꿀 때 옛 값이 표에 없는 배율로 남는다.
    val docTextScaleStepFlow: Flow<Int> = dataStore.data.map { (it[DOC_TEXT_SCALE_KEY] ?: 1).coerceIn(0, 2) }

    suspend fun saveDocTextScaleStep(step: Int) {
        dataStore.edit { prefs -> prefs[DOC_TEXT_SCALE_KEY] = step.coerceIn(0, 2) }
    }
}
