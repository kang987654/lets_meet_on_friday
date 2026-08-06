package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import javax.inject.Inject

class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao
) : AuditRepository {

    override suspend fun save(event: AuditEvent): AppResult<Unit> {
        return try {
            val entity = AuditEntity(
                id = event.id,
                eventType = event.type.name,
                sessionId = event.sessionId,
                details = event.details,
                timestamp = event.timestamp
            )
            auditDao.insert(entity)
            AppResult.Success(Unit)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbWriteError("audit_log"))
        }
    }

    override suspend fun getEvents(offset: Int, limit: Int): AppResult<List<AuditEvent>> {
        return try {
            AppResult.Success(auditDao.getEvents(offset, limit).map { it.toDomain() })
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbReadError("audit_log"))
        }
    }
}

fun AuditEntity.toDomain(): AuditEvent {
    return AuditEvent(
        id = this.id,
        type = try { AuditEventType.valueOf(this.eventType) } catch (e: Exception) { AuditEventType.ERROR },
        sessionId = this.sessionId,
        details = this.details,
        timestamp = this.timestamp
    )
}
