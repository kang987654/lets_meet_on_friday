package com.kosmos.app.data.local.repository

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.enumOrDefault
import com.kosmos.app.data.local.db.dao.AuditDao
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.domain.audit.AuditHashChain
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import javax.inject.Inject

/**
 * [AuditRepositoryImpl]
 * 활동 기록 Room 구현입니다 — 0.41.4 부터 저장은 해시 체인에 이어 붙이고([AuditDao.appendChained]), 검증은 쪽 단위([verifyChain]).
 */
class AuditRepositoryImpl @Inject constructor(
    private val auditDao: AuditDao
) : AuditRepository {

    override suspend fun save(event: AuditEvent): AppResult<Unit> = dbWrite(TABLE, "감사 기록 저장") {
        auditDao.appendChained(
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

    override suspend fun getEventsBetween(type: AuditEventType, from: Long, to: Long): AppResult<List<AuditEvent>> =
        dbRead(TABLE, "감사 기록 구간 조회") { auditDao.getEventsBetween(type.name, from, to).map { it.toDomain() } }

    /**
     * [WHY] 쪽 단위로 읽는다 — 모델 실행 기록에는 프롬프트 전문이 들어 있어 수천 건을 한 번에 올리면 메모리가 크다.
     * [WHY] 지문은 저장된 사건 종류 **글자 그대로** 다시 계산한다 — 도메인 enum 으로 바꾸면 모르는 종류가 ERROR 로 읽혀 멀쩡한 행이 "변조"로 보인다.
     */
    override suspend fun verifyChain(): AppResult<AuditHashChain.Result> = dbRead(TABLE, "감사 기록 체인 검증") {
        val verifier = AuditHashChain.Verifier()
        verifier.addLegacy(auditDao.legacyCount())
        var after = 0L
        var broken: AuditHashChain.Result.Tampered? = null
        while (broken == null) {
            val page = auditDao.chainPage(after, PAGE)
            if (page.isEmpty()) break
            broken = verifier.acceptRows(page.map { AuditHashChain.ChainRow(it.chainSeq, it.prevHash, it.entryHash, it.id, it.timestamp, it.eventType, it.sessionId, it.details) })
            after = page.last().chainSeq
        }
        broken ?: verifier.result()
    }

    private companion object {
        const val TABLE = "audit_log"
        const val PAGE = 500
    }
}

fun AuditEntity.toDomain(): AuditEvent {
    return AuditEvent(
        id = this.id,
        type = enumOrDefault(this.eventType, AuditEventType.ERROR),
        sessionId = this.sessionId,
        details = this.details,
        timestamp = this.timestamp,
        chainSeq = this.chainSeq,
        prevHash = this.prevHash,
        entryHash = this.entryHash
    )
}
