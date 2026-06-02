package com.localfriday.app.data.local.repository

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.data.local.db.dao.AuditDao
import com.localfriday.app.data.local.db.entity.AuditEntity
import com.localfriday.app.domain.memory.AuditRepository
import com.localfriday.app.domain.model.AuditEvent
import com.localfriday.app.domain.model.AuditEventType
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
        } catch (e: Exception) {
            AppResult.Failure(AppError.DbWriteError("audit_log"))
        }
    }

    override fun getPaged(): PagingSource<Int, AuditEvent> {
        val originalSource = auditDao.getPaged()
        return MappedPagingSource(originalSource) { it.toDomain() }
    }

    override fun getPagedByType(type: AuditEventType): PagingSource<Int, AuditEvent> {
        val originalSource = auditDao.getPagedByType(type.name)
        return MappedPagingSource(originalSource) { it.toDomain() }
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
