package com.localfriday.app.domain.policy

import com.localfriday.app.core.security.ApprovalRules

/**
 * [v0] 승인 규칙에 기반하여 실행 가능 여부와 승인 필요 여부를 판별하는 정책
 */
interface ExecutionPolicy {
    fun canExecute(actionType: ApprovalRules.ActionType): Boolean
    fun requiresApproval(actionType: ApprovalRules.ActionType): Boolean
}
