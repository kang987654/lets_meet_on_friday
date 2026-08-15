package com.kosmos.app.domain.modelrunner

/**
 * [ConversationResetEvent]
 * 런타임이 살아있는 Conversation 을 버리고 새로 만든 사건입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (ModelRunner 계약)
 *
 * [WHY] 리셋은 전적으로 런타임 내부 사건이었고 앱 계층 통로가 없었다. 에피소드 기억(ADR-022)은
 * "예산 리셋 = 에피소드 경계 후보"이므로 이 이벤트를 밖으로 낸다. `loadState: StateFlow` 가
 * 계약상 선례다.
 *
 * [WHY] reason 을 싣는 이유: **경계로 취급하는 것은 [Reason.TOKEN_BUDGET] 뿐**이다 — 웹 검색
 * 토글(TOOLS)이나 응답 스타일 변경(SYSTEM_INSTRUCTION)으로 주제가 갈라지면 안 된다. 필터링
 * 책임은 수신자(EpisodeBoundaryManager)에 있다.
 */
data class ConversationResetEvent(
    val sessionId: String,
    val reason: Reason
) {
    enum class Reason {
        TOKEN_BUDGET,
        SYSTEM_INSTRUCTION,
        TOOLS
    }
}
