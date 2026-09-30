package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.enumOrDefault
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import javax.inject.Inject

class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao
) : AuditRepository {

    override suspend fun save(event: AuditEvent): AppResult<Unit> = dbWrite(TABLE, "감사 기록 저장") {
        auditDao.insert(
            AuditEntity(
                id = event.id,
                eventType = event.type.name,
                sessionId = event.sessionId,
                details = event.details,
                timestamp = event.timestamp
            )
        )
    }

    override suspend fun getEvents(offset: Int, limit: Int): AppResult<List<AuditEvent>> =
        dbRead(TABLE, "감사 기록 조회") { auditDao.getEvents(offset, limit).map { it.toDomain() } }

    private companion object {
        const val TABLE = "audit_log"
    }
}

fun AuditEntity.toDomain(): AuditEvent {
    return AuditEvent(
        id = this.id,
        type = enumOrDefault(this.eventType, AuditEventType.ERROR),
        sessionId = this.sessionId,
        details = this.details,
        timestamp = this.timestamp
    )
}
