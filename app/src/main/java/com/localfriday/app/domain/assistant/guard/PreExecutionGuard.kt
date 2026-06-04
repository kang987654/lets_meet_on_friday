package com.localfriday.app.domain.assistant.guard

import com.localfriday.app.domain.model.ModelOutput
import javax.inject.Inject

sealed class ExecutionPolicy {
    object Allowed : ExecutionPolicy()
    object RequiresApproval : ExecutionPolicy()
    data class Blocked(val reason: String) : ExecutionPolicy()
}

class PreExecutionGuard @Inject constructor() {
    
    fun checkPolicy(output: ModelOutput): ExecutionPolicy {
        return when (output) {
            is ModelOutput.TextOutput -> ExecutionPolicy.Allowed
            is ModelOutput.CalendarDraftOutput -> ExecutionPolicy.RequiresApproval
            is ModelOutput.SearchOutput -> ExecutionPolicy.RequiresApproval
            is ModelOutput.KnowledgeSaveOutput -> ExecutionPolicy.Blocked("지식 저장 기능은 v1 업데이트에서 지원될 예정입니다.")
        }
    }
}
