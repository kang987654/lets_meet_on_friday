package com.kosmos.app.assistant.approval

import com.kosmos.app.domain.model.ModelOutput

data class ApprovalRequest(
    val sessionId: String,
    val action: ModelOutput
)
