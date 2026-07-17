package com.kosmos.app.assistant.guard

import com.kosmos.app.domain.model.ModelOutput
import javax.inject.Inject

sealed class ExecutionPolicy {
    object Allowed : ExecutionPolicy()
    data class Blocked(val reason: String) : ExecutionPolicy()
}

class PreExecutionGuard @Inject constructor() {
    
    fun checkPolicy(output: ModelOutput): ExecutionPolicy {
        return when (output) {
            is ModelOutput.TextOutput -> ExecutionPolicy.Allowed
            is ModelOutput.CalendarDraftOutput -> ExecutionPolicy.Allowed
        }
    }
}
