package com.kosmos.app.core.logging

import com.kosmos.app.domain.model.AuditEvent

interface AuditLogger {
    suspend fun log(event: AuditEvent)
}
