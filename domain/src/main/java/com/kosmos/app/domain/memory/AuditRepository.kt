package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.AuditEvent

/**
 * [v0] 감사 로그 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface AuditRepository {
    suspend fun save(event: AuditEvent): AppResult<Unit>
    /**
     * [WHY] 이전에는 `Flow<PagingData<AuditEvent>>` 를 반환했다. Pure Kotlin JVM 모듈이
     * `androidx` 타입을 공개 계약에 노출하는 문제였고, `Pager` 생성은 UI 관심사이므로
     * ViewModel 로 올렸다. `TaskRepository.getPendingTasksData` 와 같은 형태다.
     */
    suspend fun getEvents(offset: Int, limit: Int): AppResult<List<AuditEvent>>
}
