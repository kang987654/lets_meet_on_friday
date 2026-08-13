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

    override fun buildApprovalRequest(args: ToolArguments, sessionId: String): ApprovalRequest {
        val draft = parse(args)
        return ApprovalRequest(
            sessionId = sessionId,
            title = "일정 추가 승인",
            description = "일정: '${draft.title}' (${draft.startTime})",
            // [WHY] 캘린더 승인은 일반 다이얼로그 대신 플로팅 초안 카드(CalendarDraftCard)로
            // 렌더링하기 위해 구조화된 초안을 함께 전달한다 (절충안, 2026-07-31).
            calendarDraft = com.kosmos.app.domain.model.CalendarDraft(
                title = draft.title,
                startIso = draft.startTime,
                endIso = draft.endTime.takeIf { it.isNotBlank() },
                note = draft.description,
                confidence = 1.0f
            )
        )
    }

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val draft = parse(args)

        val res = addScheduleUseCase(draft.title, draft.startTime, draft.endTime, draft.description)
        return if (res is AppResult.Success) {
            JSONObject()
                .put("status", "success")
                .put("message", "일정이 성공적으로 추가되었습니다.")
                .toString()
        } else {
            JSONObject().put("status", "error").put("message", "일정 추가 실패").toString()
        }
    }

    /**
     * 인자를 한 곳에서 검증합니다.
     *
     * [WHY] 이전에는 승인과 실행이 인자를 각자 읽었고 기본값도 달랐다("(제목 없음)" vs 거부).
     * 그래서 사용자가 애초에 실행될 수 없는 초안을 승인하는 경우가 있었다. 검증을 하나로 모아
     * 필수 인자가 없으면 승인 단계에서 이미 실패하게 만든다 — 누락은 ToolArgumentException 이
     * 되어 BaseAgent 가 모델에게 되돌린다.
     *
     * [WHY] 시각 인자는 형식까지 검증한다. 실기기에서 모델이 `202026-081717T010000` 같은
     * 깨진 타임스탬프를 생성했고(2026-08-13), 형식 검증이 없으면 그 값이 승인 카드에 그대로
     * 노출되고 승인 시 Room·기기 캘린더까지 오염된다. 여기서 걸리면 승인 카드가 뜨기 전에
     * BAD_FORMAT 으로 모델에게 되돌아간다.
     */
    private fun parse(args: ToolArguments) = ScheduleDraft(
        title = args.requireString("title"),
        startTime = args.requireIsoDateTime("startTime"),
        endTime = args.optIsoDateTime("endTime") ?: "",
        // [WHY] 네이티브 함수호출 선언에서는 파라미터 이름 `description` 이 스키마의 툴 설명
        // 키와 충돌해 `memo` 로 선언한다 (KosmosToolDeclarations). 기존 키도 함께 읽어
        // 테스트·과거 경로와의 호환을 유지한다.
        description = args.optString("memo") ?: args.optString("description")
    )

    private data class ScheduleDraft(
        val title: String,
        val startTime: String,
        val endTime: String,
        val description: String?
    )
}
