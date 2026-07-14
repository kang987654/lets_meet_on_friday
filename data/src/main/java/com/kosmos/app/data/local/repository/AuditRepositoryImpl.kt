package com.kosmos.app.data.local.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override fun getPaged(): Flow<PagingData<AuditEvent>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            auditDao.getPaged()
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override fun getPagedByType(type: AuditEventType): Flow<PagingData<AuditEvent>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            auditDao.getPagedByType(type.name)
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
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
