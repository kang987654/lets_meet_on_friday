package com.kosmos.app.core.security

/**
 * ApprovalRules:
 * - 고정 액션 타입과 기본 승인 필요 여부를 정의하는 core 규칙
 * - domain/policy/ApprovalPolicy 는 이 규칙을 사용하는 도메인 정책 인터페이스
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
