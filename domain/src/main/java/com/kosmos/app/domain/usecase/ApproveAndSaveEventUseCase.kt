package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.tool.CalendarTool
import javax.inject.Inject

class ApproveAndSaveEventUseCase @Inject constructor(
    private val calendarTool: CalendarTool,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(
        sessionId: String,
        draft: CalendarDraft,
        isApproved: Boolean
    ): AppResult<Unit> {
        val eventDetails = "Event: ${draft.title} (${draft.startIso} ~ ${draft.endIso})"

        if (!isApproved) {
            auditTrailService.logApprovalRejected(
                sessionId = sessionId,
                actionDetails = "User rejected saving calendar event. $eventDetails"
            )
            // 거절은 정상적인 워크플로우의 일부이므로 성공(Unit)으로 반환
            return AppResult.Success(Unit)
        }

        // 사용자가 승인한 경우 DB에 저장 시도
        return when (val insertResult = calendarTool.insert(draft)) {
            is AppResult.Success -> {
                auditTrailService.logApprovalGranted(
                    sessionId = sessionId,
                    actionDetails = "Successfully saved event [ID: ${insertResult.data}]. $eventDetails"
                )
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                auditTrailService.logError(
                    sessionId = sessionId,
                    errorMsg = "Failed to save approved event. Error: ${insertResult.error}, $eventDetails"
                )
                AppResult.Failure(insertResult.error)
            }
        }
    }
}
