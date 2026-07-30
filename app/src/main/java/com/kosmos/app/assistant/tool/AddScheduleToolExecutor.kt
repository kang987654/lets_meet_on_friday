package com.kosmos.app.assistant.tool

import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.security.ApprovalRules
import com.kosmos.app.domain.usecase.AddScheduleUseCase
import org.json.JSONObject
import javax.inject.Inject

/**
 * [AddScheduleToolExecutor]
 * 모델의 `AddSchedule` 툴 콜을 받아 일정 등록 유즈케이스를 실행하는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [AddScheduleUseCase]
 *
 * ### Key Flow
 * 1. 필수 인자(title, startTime)를 검증하고, 누락 시 실행 없이 오류 응답을 반환해 모델이 되묻게 합니다.
 * 2. 사용자 승인(BaseAgent 공통 경로, CALENDAR_WRITE)은 실행 전에 이미 완료된 상태입니다.
 * 3. [AddScheduleUseCase] 실행 결과를 JSON 문자열로 반환합니다.
 */
class AddScheduleToolExecutor @Inject constructor(
    private val addScheduleUseCase: AddScheduleUseCase
) : ToolExecutor {
    override val name: String = "AddSchedule"

    override val actionType: ApprovalRules.ActionType = ApprovalRules.ActionType.CALENDAR_WRITE

    override fun buildApprovalRequest(args: Map<String, Any>, sessionId: String): ApprovalRequest {
        val title = args["title"] as? String ?: "(제목 없음)"
        val startTime = args["startTime"] as? String ?: "(시간 미정)"
        return ApprovalRequest(
            sessionId = sessionId,
            title = "일정 추가 승인",
            description = "일정: '$title' ($startTime)",
            // [WHY] 캘린더 승인은 일반 다이얼로그 대신 플로팅 초안 카드(CalendarDraftCard)로
            // 렌더링하기 위해 구조화된 초안을 함께 전달한다 (절충안, 2026-07-31).
            calendarDraft = com.kosmos.app.domain.model.CalendarDraft(
                title = title,
                startIso = startTime,
                endIso = (args["endTime"] as? String)?.takeIf { it.isNotBlank() },
                note = args["description"] as? String,
                confidence = 1.0f
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        // [WHY] 인자 누락 시 "Event"/"" 기본값으로 무성 진행하면 쓰레기 일정이 저장되므로,
        // 실행하지 않고 오류를 돌려 모델이 사용자에게 되묻도록 한다 (프롬프트 수칙과 일치).
        val title = (args["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: return errorJson("title 인자가 필요합니다. 사용자에게 일정 제목을 확인하세요.")
        val startTime = (args["startTime"] as? String)?.takeIf { it.isNotBlank() }
            ?: return errorJson("startTime 인자가 필요합니다. 사용자에게 일정 시작 시간을 확인하세요.")
        val endTime = args["endTime"] as? String ?: ""
        val desc = args["description"] as? String

        val res = addScheduleUseCase(title, startTime, endTime, desc)
        return if (res is AppResult.Success) {
            JSONObject()
                .put("status", "success")
                .put("message", "일정이 성공적으로 추가되었습니다.")
                .toString()
        } else {
            errorJson("일정 추가 실패")
        }
    }

    private fun errorJson(message: String): String =
        JSONObject().put("status", "error").put("message", message).toString()
}
