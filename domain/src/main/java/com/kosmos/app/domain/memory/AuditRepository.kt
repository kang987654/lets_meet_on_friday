package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditHashChain
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType

/**
 * [v0] 감사 로그 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface AuditRepository {
    /** 저장 — 구현은 체인 머리에 이어 번호·지문을 붙인다(0.41.4, 한 트랜잭션). [event] 의 체인 필드는 무시된다. */
    suspend fun save(event: AuditEvent): AppResult<Unit>

    /**
     * 해시 체인 검증(0.41.4) — 체인 전 행은 세기만, 체인 행은 번호 오름차순 쪽 단위로.
     *
     * [WHY] 기본 구현은 "빈 체인 통과" — 테스트 가짜 저장소가 그대로 컴파일되게. Room 구현체가 덮어쓴다.
     */
    suspend fun verifyChain(): AppResult<AuditHashChain.Result> = AppResult.Success(AuditHashChain.Result.Valid(0, 0, null, null))

    /**
     * [WHY] 이전에는 `Flow<PagingData<AuditEvent>>` 를 반환했다. Pure Kotlin JVM 모듈이
     * `androidx` 타입을 공개 계약에 노출하는 문제였고, `Pager` 생성은 UI 관심사이므로
     * ViewModel 로 올렸다. `TaskRepository.getPendingTasksData` 와 같은 형태다.
     */
    suspend fun getEvents(offset: Int, limit: Int): AppResult<List<AuditEvent>>

    /** [type] 사건 중 `from..to`(양끝 포함) 시각의 것, 오래된 순. */
    suspend fun getEventsBetween(type: AuditEventType, from: Long, to: Long): AppResult<List<AuditEvent>>
}
