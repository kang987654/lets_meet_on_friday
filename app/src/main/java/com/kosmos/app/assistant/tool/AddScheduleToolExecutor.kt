package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.assistant.approval.ApprovalCoordinator
import com.kosmos.app.assistant.approval.ApprovalRequest
import com.kosmos.app.domain.usecase.AddScheduleUseCase
import javax.inject.Inject

class AddScheduleToolExecutor @Inject constructor(
    private val addScheduleUseCase: AddScheduleUseCase,
    private val approvalCoordinator: ApprovalCoordinator
) : ToolExecutor {
    override val name: String = "AddSchedule"

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        val title = args["title"] as? String ?: "Event"
        val startTime = args["startTime"] as? String ?: ""
        val endTime = args["endTime"] as? String ?: ""
        val desc = args["description"] as? String
        
        val request = ApprovalRequest(
            sessionId = sessionId,
            title = "일정 추가 승인",
            description = "일정: '$title' (${startTime})"
        )
        
        val approved = approvalCoordinator.requireApproval(request)
        if (approved) {
            val res = addScheduleUseCase(title, startTime, endTime, desc)
            return if (res is AppResult.Success) {
                "{\"status\": \"success\", \"message\": \"일정이 성공적으로 추가되었습니다.\"}"
            } else {
                "{\"status\": \"error\", \"message\": \"일정 추가 실패\"}"
            }
        } else {
            return "{\"status\": \"error\", \"message\": \"사용자가 취소했습니다\"}"
        }
    }
}
