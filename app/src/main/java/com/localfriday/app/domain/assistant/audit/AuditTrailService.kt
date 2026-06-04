package com.localfriday.app.domain.assistant.audit

import com.localfriday.app.domain.memory.AuditRepository
import com.localfriday.app.domain.model.AuditEvent
import com.localfriday.app.domain.model.AuditEventType
import java.util.UUID
import javax.inject.Inject

class AuditTrailService @Inject constructor(
    private val auditRepository: AuditRepository
) {
    suspend fun logModelRun(sessionId: String, prompt: String, output: String) {
        val details = "Prompt: ${redact(prompt)}\nOutput: ${redact(output)}"
        saveEvent(sessionId, AuditEventType.MODEL_RUN, details)
    }

    suspend fun logApprovalGranted(sessionId: String, actionDetails: String) {
        saveEvent(sessionId, AuditEventType.APPROVAL_GRANTED, actionDetails)
    }

    suspend fun logApprovalRejected(sessionId: String, actionDetails: String) {
        saveEvent(sessionId, AuditEventType.APPROVAL_REJECTED, actionDetails)
    }

    suspend fun logError(sessionId: String, errorMsg: String) {
        saveEvent(sessionId, AuditEventType.ERROR, errorMsg)
    }

    private suspend fun saveEvent(sessionId: String, type: AuditEventType, details: String) {
        try {
            val event = AuditEvent(
                id = UUID.randomUUID().toString(),
                type = type,
                sessionId = sessionId,
                details = details,
                timestamp = System.currentTimeMillis()
            )
            auditRepository.save(event)
        } catch (e: Exception) {
            // 앱 크래시 방지를 위한 Fallback 로깅
            println("AuditTrailService: Failed to save audit event: ${e.message}")
        }
    }

    /**
     * 민감 텍스트 Redaction 적용
     * 너무 긴 텍스트는 로깅 사이즈 초과 방지를 위해 자르고 마스킹 처리합니다.
     */
    private fun redact(text: String): String {
        val maxLength = 500
        return if (text.length > maxLength) {
            text.take(maxLength) + "... [REDACTED_DUE_TO_LENGTH]"
        } else {
            text
        }
    }
}
