package com.kosmos.app.core.security

/**
 * [ApprovalRules]
 * 에이전트 액션 수행 시 사용자 승인이 필요한 툴 액션 규칙을 정의하는 코어 세큐리티 싱글톤입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Security)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 각 툴 실행 공통 경로(BaseAgent)에서 실행 직전에 승인 필요 여부를 조회합니다.
 * 2. 승인이 필요한 액션은 ApprovalCoordinator를 통해 사용자 결정을 대기한 후 실행됩니다.
 */
object ApprovalRules {

    enum class ActionType {
        CALENDAR_WRITE,
        MEMORY_WRITE,
        WEB_SEARCH
    }

    fun requiresApproval(actionType: ActionType): Boolean =
        when (actionType) {
            ActionType.CALENDAR_WRITE -> true
            ActionType.MEMORY_WRITE -> true
            // [WHY] 2026-07-31 기획 변경: 웹 검색은 건별 승인 대신 채팅 헤더의
            // 전역 토글(webSearchEnabled)로 허용 여부를 제어한다. 토글 OFF 시
            // 에이전트의 allowlist에서 제외되어 실행 자체가 차단된다.
            ActionType.WEB_SEARCH -> false
        }
}
