package com.localfriday.app.domain.assistant.approval

import com.localfriday.app.domain.model.ModelOutput

data class ApprovalRequest(
    val sessionId: String,
    val action: ModelOutput
)
