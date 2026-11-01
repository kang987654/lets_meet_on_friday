package com.kosmos.app.domain.util

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage

/**
 * [ContentLanguage]
 * 대화 원문이나 메모 본문의 주된 언어([AppLanguage.KO] / [AppLanguage.EN])를 한글·라틴 알파벳 비율로 판정하는 순수 함수 모음입니다 (0.40.0 M2b).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Util)
 * - **Dependencies**: [AppLanguage], [ChatMessage]
 *
 * ### Key Flow
 * 1. [detect] — 텍스트 안의 한글 음절(`가..힣`)과 라틴 알파벳(`a..zA..Z`) 글자 수를 세어 주된 언어를 반환한다.
 * 2. [detectMessages] — 사용자([ChatMessage.Role.USER]) 발화를 우선 모아(없으면 전체 메시지) [detect] 로 판정한다.
 *
 * [WHY] 기억으로 남는 산출물(에피소드 요약·자동 추출·중복 합치기)은 앱 설정 언어가 아니라 **그 재료 대화의 언어**를 따른다 —
 * 한↔영 교차 언어 기억 검색을 지원하지 않으므로(ADR-033), 한국어로 나눈 대화가 영어 설정에서 영어 요약으로 저장되면
 * 나중에 한국어 질문으로 찾지 못하기 때문이다. 한글·라틴 비율이 애매하거나 글자가 없으면 [fallback](설정 언어)으로 넘어간다.
 */
object ContentLanguage {

    /**
     * [text] 의 주된 언어를 판정합니다.
     * - 한글 음절과 라틴 알파벳이 모두 0개면 [fallback] 반환.
     * - 한글은 1음절이 영어 2~3글자 분량의 형태소를 담으므로, `latin > hangul * 2` 일 때 [AppLanguage.EN],
     *   `hangul >= latin` 일 때 [AppLanguage.KO], 그 사이(혼합)는 [fallback] 을 반환합니다.
     */
    fun detect(text: String, fallback: AppLanguage = AppLanguage.KO): AppLanguage {
        var hangul = 0
        var latin = 0
        for (c in text) {
            when (c) {
                in '\uAC00'..'\uD7A3' -> hangul++
                in 'a'..'z', in 'A'..'Z' -> latin++
            }
        }
        if (hangul == 0 && latin == 0) return fallback
        return when {
            latin > hangul * 2 -> AppLanguage.EN
            hangul >= latin -> AppLanguage.KO
            else -> fallback
        }
    }

    /**
     * 대화 메시지 목록에서 사용자 발화를 우선 기준으로 주된 언어를 판정합니다.
     */
    fun detectMessages(
        messages: List<ChatMessage>,
        fallback: AppLanguage = AppLanguage.KO
    ): AppLanguage {
        val userMessages = messages.filter { it.role == ChatMessage.Role.USER }
        val target = userMessages.ifEmpty { messages }
        val combined = target.joinToString("\n") { it.content }
        return detect(combined, fallback)
    }
}
