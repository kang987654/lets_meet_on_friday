package com.kosmos.app.assistant.context

import com.kosmos.app.domain.model.ProfileEntry
import com.kosmos.app.domain.tool.Tokenizer

/**
 * 프로필 항목을 시스템 지시의 `[User Profile]` 블록으로 렌더합니다 (C′1).
 *
 * [WHY] **바이트-안정이 계약이다**: GemmaModelRunner 는 시스템 지시의 문자열 동등성으로
 * Conversation 재사용을 판정한다 — 프로필이 안 바뀌었는데 렌더가 흔들리면(순서·공백·시각)
 * 매 턴 전체 재프리필이 된다(ADR-010 실측 0.5s→3.8s). 그래서 키 사전순 고정, updatedAt
 * 미포함, trim 고정. 빈 목록은 빈 문자열 — 블록 자체가 생략돼 비용 0.
 *
 * [WHY] 형식(`- 키: 값`)은 exp35 실측 원문 그대로다 — 바꾸려면 재실측이 선행된다 (AGENTS §2-⑤).
 */
internal fun renderProfileBlock(entries: List<ProfileEntry>): String {
    val meaningful = entries
        .map { it.key.trim() to it.value.trim() }
        .filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }
        .sortedBy { (key, _) -> key }
    if (meaningful.isEmpty()) return ""
    return buildString {
        append("[User Profile]")
        meaningful.forEach { (key, value) -> append("\n- $key: $value") }
    }
}

/**
 * [key] 를 [value] 로 넣거나 고쳤을 때 프로필 블록 전체가 차지할 토큰 수.
 *
 * [WHY] 상한 집행은 "편집이 반영된 전체"를 렌더해 재야 한다 — 항목 단위 검사는 합계 초과를
 * 못 막는다. 드로어 시트(수동)와 제안 승인(자동, C′2) 두 경로가 같은 함수를 써야 한쪽이
 * 우회하는 일이 없다. Tokenizer 추정은 과대 방향이라 상한 집행에 안전하다(exp26).
 */
internal fun projectedProfileTokens(
    current: List<ProfileEntry>,
    key: String,
    value: String,
    tokenizer: Tokenizer
): Int {
    val projected = current.filterNot { it.key == key } + ProfileEntry(key = key, value = value)
    return tokenizer.sizeInTokens(renderProfileBlock(projected))
}
