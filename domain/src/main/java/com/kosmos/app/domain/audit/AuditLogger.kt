package com.kosmos.app.domain.audit

import com.kosmos.app.domain.model.AuditEvent

interface AuditLogger {
    suspend fun log(event: AuditEvent)
}
