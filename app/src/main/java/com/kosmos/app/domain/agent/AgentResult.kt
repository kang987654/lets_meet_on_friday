package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppError
import com.kosmos.app.domain.model.ModelOutput

sealed class AgentResult {
    data class Text(val content: String) : AgentResult()
    data class ActionRequired(val output: ModelOutput) : AgentResult()
    data class Error(val error: AppError) : AgentResult()
}
