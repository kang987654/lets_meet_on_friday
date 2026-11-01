package com.kosmos.app.core.common

import java.util.Locale

/**
 * [AppLanguage]
 * 앱의 표시 언어 및 비서(Gemma) 프롬프트·툴 결과·음성 읽기 언어입니다 (0.40.0).
 *
 * - 사용자가 설정 화면에서 고르는 선택지는 [Option] (`SYSTEM` / `KO` / `EN`)이고,
 * - 실제 실행 시점(프롬프트 조립·일시 표기·툴 결과·TTS)에는 [AppLanguage] (`KO` / `EN`) 두 값 중 하나로 풀립니다.
 */
enum class AppLanguage(val tag: String) {
    KO("ko"),
    EN("en");

    /** 설정 화면의 3단 언어 토글 선택지 (`시스템 / 한국어 / English`). */
    enum class Option(val key: String, val tag: String) {
        SYSTEM("SYSTEM", ""),
        KO("KO", "ko"),
        EN("EN", "en");

        fun resolve(systemLocale: Locale = Locale.getDefault()): AppLanguage = when (this) {
            KO -> AppLanguage.KO
            EN -> AppLanguage.EN
            SYSTEM -> fromLocale(systemLocale)
        }

        companion object {
            fun fromKey(key: String?): Option = entries.firstOrNull { it.key == key } ?: SYSTEM

            /** OS `LocaleManager.applicationLocales` 태그로부터 현재 선택된 옵션을 역산합니다. */
            fun fromLocaleTag(languageTag: String?): Option {
                val tag = languageTag?.trim()?.lowercase(Locale.ROOT).orEmpty()
                return when {
                    tag.isEmpty() -> SYSTEM
                    tag.startsWith("en") -> EN
                    tag.startsWith("ko") -> KO
                    else -> SYSTEM
                }
            }
        }
    }

    companion object {
        /** 기기/리소스 [Locale] 이 영어(`en`)이면 [EN], 그 밖에는 기본 [KO] 로 해석합니다. */
        fun fromLocale(locale: Locale?): AppLanguage =
            if (locale?.language?.lowercase(Locale.ROOT) == "en") EN else KO

        fun fromTag(tag: String?): AppLanguage =
            if (tag?.trim()?.lowercase(Locale.ROOT)?.startsWith("en") == true) EN else KO
    }
}
