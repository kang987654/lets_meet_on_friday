package com.localfriday.app.data.local.repository.fake


import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.memory.AuditRepository
import com.localfriday.app.domain.model.AuditEvent
import com.localfriday.app.domain.model.AuditEventType

class FakeAuditRepository : AuditRepository {
    private val events = mutableListOf<AuditEvent>()

    override suspend fun save(event: AuditEvent): AppResult<Unit> {
        events.add(event)
        return AppResult.Success(Unit)
    }

    override suspend fun getPaged(offset: Int, limit: Int): AppResult<List<AuditEvent>> {
        val data = events.sortedByDescending { it.timestamp }.drop(offset).take(limit)
        return AppResult.Success(data)
    }

    override suspend fun getPagedByType(type: AuditEventType, offset: Int, limit: Int): AppResult<List<AuditEvent>> {
        val data = events.filter { it.type == type }.sortedByDescending { it.timestamp }.drop(offset).take(limit)
        return AppResult.Success(data)
    }
}
