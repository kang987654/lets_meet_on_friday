package com.kosmos.app.domain.util

import com.kosmos.app.domain.tool.Tokenizer

/**
 * [SentenceTruncator]
 * 토큰 예산에 맞춰 텍스트를 **문장 경계**에서 자르는 공용 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Util) — Pure Kotlin
 * - **Dependencies**: [Tokenizer]
 *
 * ### Key Flow
 * 1. 예산 이내면 원문을 그대로 반환합니다.
 * 2. 넘으면 예산(표식 비용 차감) 이내에 들어오는 가장 긴 문장 경계 접두사를 찾습니다.
 * 3. 한 문장도 예산을 넘으면 문자 단위 하드컷으로 폴백합니다 — 빈 결과는 내지 않습니다.
 *
 * [WHY] 예전 위키 캡은 `substring(0, 5000)` 단어 중간 절단이었다. 모델에게 가는 마지막
 * 문장이 중간에서 끊기면 절단 표식과 섞여 "잘린 산문 흉내"가 되고, 잘린 조각이 사실처럼
 * 읽히는 것 자체가 환각의 재료가 된다. 문장 경계에서 자르면 남는 것이 전부 온전한 문장이다.
 */
object SentenceTruncator {

    private val BOUNDARIES = charArrayOf('.', '!', '?', '\n')

    /**
     * @param marker 잘렸음을 모델에게 알리는 표식. 반환값 끝에 붙으며 비용도 예산에서 차감된다.
     */
    fun truncate(text: String, maxTokens: Int, tokenizer: Tokenizer, marker: String = ""): String {
        if (tokenizer.sizeInTokens(text) <= maxTokens) return text

        val markerCost = if (marker.isEmpty()) 0 else tokenizer.sizeInTokens(marker)
        val budget = (maxTokens - markerCost).coerceAtLeast(1)

        // 뒤에서부터 문장 경계를 좁혀 가며 예산에 들어오는 가장 긴 접두사를 찾는다.
        var searchEnd = text.length
        while (searchEnd > 1) {
            val boundary = text.lastIndexOfAny(BOUNDARIES, searchEnd - 1)
            if (boundary <= 0) break
            val candidate = text.substring(0, boundary + 1)
            if (tokenizer.sizeInTokens(candidate) <= budget) return candidate + marker
            searchEnd = boundary
        }

        // [WHY] 한 문장이 예산을 넘는 경우다. 문장을 포기하고 하드컷하되 빈 결과는 금지한다 —
        // 툴이 빈 data 를 돌려주면 모델은 "결과 없음"으로 오해한다.
        var low = 1
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (tokenizer.sizeInTokens(text.substring(0, mid)) <= budget) low = mid else high = mid - 1
        }
        return text.substring(0, low) + marker
    }
}
