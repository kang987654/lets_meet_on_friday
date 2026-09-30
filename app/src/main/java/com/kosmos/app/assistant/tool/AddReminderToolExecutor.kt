package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.security.ApprovalRules
import com.kosmos.app.domain.usecase.AddReminderUseCase
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import javax.inject.Inject

/**
 * [AddReminderToolExecutor]
 * 모델의 `AddReminder` 툴 콜을 받아 리마인더를 저장하고 알람을 예약하는 실행기입니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [AddReminderUseCase], [ReminderAlarmScheduler]
 *
 * ### Key Flow
 * 1. 인자 검증([parse] — 승인·실행 공유, AddSchedule 규약)
 * 2. 사용자 승인(BaseAgent 공통 경로, REMINDER_WRITE → 기본 ApprovalSheet)
 * 3. 저장 → 알람 예약 → 한국어 표기로 결과 반환
 */
class AddReminderToolExecutor @Inject constructor(
    private val addReminderUseCase: AddReminderUseCase,
    private val reminderAlarmScheduler: ReminderAlarmScheduler,
    private val widgetRefresher: com.kosmos.app.widget.WidgetRefresher
) : ToolExecutor {
    override val name: String = ToolNames.ADD_REMINDER

    override val actionType: ApprovalRules.ActionType = ApprovalRules.ActionType.REMINDER_WRITE

    override fun buildApprovalRequest(args: ToolArguments, sessionId: String): ApprovalRequest {
        val draft = parse(args)
        return ApprovalRequest(
            sessionId = sessionId,
            title = "리마인더 등록",
            // [WHY] ISO 원문을 사용자에게 노출하지 않는다 — toDisplayKorean 단일 출처 (0.19.2).
            // calendarDraft 는 null — 기본 ApprovalSheet 가 뜬다 (전용 카드 없음, 사용자 결정).
            description = "${draft.displayTime}에 알림: '${draft.content}'"
        )
    }

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val draft = parse(args)

        return when (val res = addReminderUseCase(draft.content, draft.time)) {
            is AppResult.Success -> {
                // [WHY] requireIsoDateTime 을 통과한 값이라 파싱은 항상 성공하지만, 만에 하나를
                // 알람 없는 저장(다음 부팅 복원이 줍는다)으로 강등할 뿐 실패로 만들지 않는다.
                IsoDateTimeParser.toEpochMillis(draft.time)?.let { triggerAtMs ->
                    reminderAlarmScheduler.schedule(res.data.id, triggerAtMs)
                }
                widgetRefresher.refresh()
                ToolResultJson.success()
                    .put("message", "리마인더 등록: ${draft.displayTime}에 '${draft.content}' 알림")
                    .toString()
            }
            is AppResult.Failure -> {
                // [WHY] 과거 시각은 형식 오류가 아니라 의미 오류다 — 모델이 자가수정할 수 있게
                // 구체적 사유를 관측값으로 돌려준다 (BaseAgent 인자 오류 프로토콜과 같은 취지).
                val message = if (res.error is AppError.ValidationError) {
                    "이미 지난 시각입니다. 미래의 시각으로 다시 호출하세요."
                } else {
                    "리마인더 등록 실패"
                }
                ToolResultJson.error(message)
            }
        }
    }

    /**
     * 인자를 한 곳에서 검증합니다 — 승인과 실행이 같은 검증을 공유해야 "실행 불가능한 초안을
     * 승인"하는 결함이 안 생긴다 (AddScheduleToolExecutor 규약).
     */
    private fun parse(args: ToolArguments): ReminderDraft {
        val time = args.requireIsoDateTime("time")
        return ReminderDraft(
            time = time,
            content = args.requireString("content"),
            displayTime = IsoDateTimeParser.toDisplayKorean(time) ?: time
        )
    }

    private data class ReminderDraft(
        val time: String,
        val content: String,
        val displayTime: String
    )
}
