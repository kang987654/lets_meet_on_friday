package com.localfriday.app.domain.agent

import com.localfriday.app.core.common.AppError
import com.localfriday.app.domain.model.ModelOutput

sealed class AgentResult {
    data class Text(val content: String) : AgentResult()
    data class ActionRequired(val output: ModelOutput) : AgentResult()
    data class Error(val error: AppError) : AgentResult()
}
