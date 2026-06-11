package com.localfriday.app.domain.memory

import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.AuditEvent
import com.localfriday.app.domain.model.AuditEventType
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingData

/**
 * [v0] 감사 로그 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface AuditRepository {
    suspend fun save(event: AuditEvent): AppResult<Unit>
    fun getPaged(): Flow<PagingData<AuditEvent>>
    fun getPagedByType(type: AuditEventType): Flow<PagingData<AuditEvent>>
}
