package com.kosmos.app.domain.model

/**
 * 활동 기록 한 건.
 *
 * @property chainSeq 해시 체인 번호(1부터, 0.41.4) — 0 이면 체인을 붙이기 전 기록.
 * @property prevHash 앞 행의 지문(첫 행 `GENESIS`, 체인 전 `LEGACY`). @property entryHash 이 행의 지문([com.kosmos.app.domain.audit.AuditHashChain]).
 *
 * [WHY] 체인 필드는 기본값을 둔다 — 기록하는 쪽은 내용만 채우고 번호·지문은 저장소가 저장하는 순간 머리를 보고 붙인다(한 트랜잭션).
 */
data class AuditEvent(
    val id: String,
    val type: AuditEventType,
    val sessionId: String,
    val details: String,
    val timestamp: Long,
    val chainSeq: Long = 0L,
    val prevHash: String = "",
    val entryHash: String = ""
)

enum class AuditEventType {
    MODEL_RUN,
    TOOL_CALL,
    APPROVAL_GRANTED,
    APPROVAL_REJECTED,
    ERROR,
    
    EXPORT,
    IMPORT,
    SEARCH_USED,
    THERMAL_WARNING,
    THERMAL_SHUTDOWN,

    /** 대화 초기화로 활동 기록을 지웠다 — 새 체인의 첫 기록(0.41.4 Q3). */
    RESET
}
