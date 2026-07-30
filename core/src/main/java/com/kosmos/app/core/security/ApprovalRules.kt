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
 * 1. 캘린더 등록, 웹 검색 등 사용자 승인이 필요한 툴 액션별 승인 기본 규칙을 리턴합니다.
 */
object ApprovalRules {

    enum class ActionType {
        CALENDAR_WRITE,
        WEB_SEARCH
    }

    fun requiresApproval(actionType: ActionType): Boolean =
        when (actionType) {
            ActionType.CALENDAR_WRITE -> true
            ActionType.WEB_SEARCH -> true
        }
}
