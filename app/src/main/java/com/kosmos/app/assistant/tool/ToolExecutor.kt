package com.kosmos.app.assistant.tool

import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.core.security.ApprovalRules

/**
 * [ToolExecutor]
 * 각 툴 콜(Tool Call)을 위임받아 수행하는 인터페이스입니다.
 * 단일 책임 원칙(SRP)에 따라 각 툴의 동작을 개별 클래스로 캡슐화합니다.
 *
 * 승인 정책: [actionType]이 null이 아니고 [ApprovalRules.requiresApproval]이 true인 툴은
 * 실행 공통 경로(BaseAgent)에서 사용자 승인을 거친 후에만 [execute]가 호출됩니다.
 */
interface ToolExecutor {
    /** 툴의 고유 이름 (예: "AddSchedule") */
    val name: String

    /** 승인 정책 분류. null이면 승인 절차 없이 실행 가능합니다. */
    val actionType: ApprovalRules.ActionType?
        get() = null

    /** 승인 다이얼로그에 표시할 요청 정보를 구성합니다. */
    fun buildApprovalRequest(args: Map<String, Any>, sessionId: String): ApprovalRequest =
        ApprovalRequest(
            sessionId = sessionId,
            title = "$name 실행 승인",
            description = "도구 '$name' 실행을 승인하시겠습니까?"
        )

    /**
     * 파싱된 매개변수와 세션 ID를 받아 툴을 실행하고,
     * 그 결과를 LLM 컨텍스트에 추가할 JSON/텍스트 문자열 형태로 반환합니다.
     */
    suspend fun execute(args: Map<String, Any>, sessionId: String): String
}
