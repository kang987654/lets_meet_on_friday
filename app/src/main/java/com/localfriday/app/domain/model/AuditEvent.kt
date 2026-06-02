package com.localfriday.app.domain.model

data class AuditEvent(
    val id: String,
    val type: AuditEventType,
    val sessionId: String,
    val details: String,
    val timestamp: Long
)

enum class AuditEventType {
    MODEL_RUN,
    TOOL_CALL,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    ERROR,
    
    // v1 Extensions
    EXPORT,
    IMPORT,
    SEARCH_USED
}
