package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppError

sealed class AgentResult {
    data class Text(val content: String, val thinkingProcess: String? = null) : AgentResult()
    data class Error(val error: AppError) : AgentResult()
}
