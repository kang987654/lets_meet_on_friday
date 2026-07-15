package com.kosmos.app.assistant.approval

data class ApprovalRequest(
    val sessionId: String,
    val title: String,
    val description: String
)
