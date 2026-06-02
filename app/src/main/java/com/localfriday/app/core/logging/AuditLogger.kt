package com.localfriday.app.core.logging

import com.localfriday.app.domain.model.AuditEvent

interface AuditLogger {
    suspend fun log(event: AuditEvent)
}
