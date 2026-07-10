package com.kosmos.app.data.local.repository.fake


import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.paging.PagingData

class FakeAuditRepository : AuditRepository {
    private val events = mutableListOf<AuditEvent>()

    override suspend fun save(event: AuditEvent): AppResult<Unit> {
        events.add(event)
        return AppResult.Success(Unit)
    }

    override fun getPaged(): Flow<PagingData<AuditEvent>> {
        return flowOf(PagingData.empty())
    }

    override fun getPagedByType(type: AuditEventType): Flow<PagingData<AuditEvent>> {
        return flowOf(PagingData.empty())
    }
}
