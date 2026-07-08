package com.localfriday.app.assistant.approval

import com.localfriday.app.domain.model.ModelOutput

data class ApprovalRequest(
    val sessionId: String,
    val action: ModelOutput
)
