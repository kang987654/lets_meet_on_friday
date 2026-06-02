package com.localfriday.app.domain.policy

import com.localfriday.app.core.security.ApprovalRules
import com.localfriday.app.domain.model.ApprovalRequest

/**
 * [v0] 앱 내 승인 흐름 관리 정책 인터페이스.
 * 실제 비즈니스 컨텍스트에서 core/security/ApprovalRules 를 사용하여
 * 승인 필요 여부를 결정하는 확장 지점입니다.
 */
interface ApprovalPolicy {
    suspend fun requestApproval(actionType: ApprovalRules.ActionType, request: ApprovalRequest): Boolean
}
