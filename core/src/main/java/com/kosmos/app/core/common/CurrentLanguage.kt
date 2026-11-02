package com.kosmos.app.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 현재 앱 언어의 읽기 전용 창구 (0.40.3 M1).
 * [WHY] 앱 모듈의 AppLanguageHolder 를 :domain·테스트가 직접 알지 않게 한다 — 기본값 [KO] 로 테스트는 주입 없이 한국어 동작.
 */
interface CurrentLanguage {
    val languageFlow: StateFlow<AppLanguage>
    val current: AppLanguage get() = languageFlow.value

    companion object {
        fun fixed(language: AppLanguage): CurrentLanguage = object : CurrentLanguage {
            override val languageFlow: StateFlow<AppLanguage> = MutableStateFlow(language).asStateFlow()
        }
        val KO: CurrentLanguage = fixed(AppLanguage.KO)
    }
}
