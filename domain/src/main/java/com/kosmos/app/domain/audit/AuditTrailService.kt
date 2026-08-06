package com.kosmos.app.domain.audit

import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.core.security.Redaction
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import java.util.UUID
import javax.inject.Inject

class AuditTrailService @Inject constructor(
    private val auditRepository: AuditRepository
) {
    private companion object {
        const val TAG = "AuditTrailService"
    }

    suspend fun logModelRun(sessionId: String, prompt: String, output: String) {
        val details = "Prompt: ${redact(prompt)}\nOutput: ${redact(output)}"
        saveEvent(sessionId, AuditEventType.MODEL_RUN, details)
    }

    /**
     * 툴이 **실제로 실행된** 사실과 그 결과를 남깁니다.
     *
     * [WHY] `AuditEventType.TOOL_CALL` 은 enum 에 있고 감사 화면에 색상까지 지정돼 있었지만
     * **생산자가 한 곳도 없었다** — 툴 루프로 골격을 바꿀 때 기록 지점이 함께 옮겨오지 않았다.
     * 그래서 감사 로그에는 승인 여부만 남고 "무엇이 실행됐고 결과가 무엇인지"가 없었다.
     * 승인이 필요 없는 툴(일정 조회, 웹 검색)은 아무 흔적도 남지 않았다.
     *
     * @param note 부수 사실(예: 무시된 추가 호출). 없으면 null.
     */
    suspend fun logToolCall(
        sessionId: String,
        toolName: String,
        resultJson: String,
        note: String? = null
    ) {
        val details = buildString {
            append("Tool: $toolName")
            append("\nResult: ${redact(resultJson)}")
            if (note != null) append("\nNote: $note")
        }
        saveEvent(sessionId, AuditEventType.TOOL_CALL, details)
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

    suspend fun logSearchEvent(sessionId: String, query: String) {
        saveEvent(sessionId, AuditEventType.SEARCH_USED, "Query: ${redact(query)}")
    }

    suspend fun logBackupEvent(sessionId: String, action: String, status: String) {
        val type = if (action.equals("export", ignoreCase = true)) AuditEventType.EXPORT else AuditEventType.IMPORT
        saveEvent(sessionId, type, "Status: $status")
    }

    suspend fun logThermalEvent(sessionId: String, type: String, temperature: Float) {
        val eventType = if (type.equals("warning", ignoreCase = true)) AuditEventType.THERMAL_WARNING else AuditEventType.THERMAL_SHUTDOWN
        saveEvent(sessionId, eventType, "Temperature: $temperature°C")
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
            // [WHY] save는 실패를 예외가 아닌 AppResult로 돌려주므로 값 검사 없이는 유실이 무음으로 지나간다.
            val result = auditRepository.save(event)
            if (result is com.kosmos.app.core.common.AppResult.Failure) {
                AppLogger.w(TAG, "Failed to save audit event ($type): ${result.error}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 앱 크래시 방지를 위한 Fallback 로깅
            AppLogger.e(TAG, "Failed to save audit event ($type)", e)
        }
    }

    /**
     * 민감 텍스트 Redaction 적용 — [Redaction] 코어 정책(PII 마스킹 + 길이 절단)을 사용합니다.
     */
    private fun redact(text: String): String = Redaction.sanitize(text)
}
