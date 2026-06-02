package com.localfriday.app.data.local.repository.fake

import androidx.paging.PagingSource
import androidx.paging.PagingState
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

    override fun getPaged(): PagingSource<Int, AuditEvent> {
        return object : PagingSource<Int, AuditEvent>() {
            override fun getRefreshKey(state: PagingState<Int, AuditEvent>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AuditEvent> {
                val data = events.sortedByDescending { it.timestamp }
                return LoadResult.Page(
                    data = data,
                    prevKey = null,
                    nextKey = null
                )
            }
        }
    }

    override fun getPagedByType(type: AuditEventType): PagingSource<Int, AuditEvent> {
        return object : PagingSource<Int, AuditEvent>() {
            override fun getRefreshKey(state: PagingState<Int, AuditEvent>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AuditEvent> {
                val data = events.filter { it.type == type }.sortedByDescending { it.timestamp }
                return LoadResult.Page(
                    data = data,
                    prevKey = null,
                    nextKey = null
                )
            }
        }
    }
}
