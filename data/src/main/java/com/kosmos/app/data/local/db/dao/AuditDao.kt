package com.kosmos.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kosmos.app.data.local.db.entity.AuditEntity
import com.kosmos.app.domain.audit.AuditHashChain

/**
 * [AuditDao]
 * 활동 기록(`audit_log`) — 0.41.4 부터 행마다 해시 체인 번호·앞 지문·지문을 단다([AuditHashChain]).
 *
 * [WHY] 인터페이스가 아니라 추상 클래스다 — 저장(머리 조회 → 번호·지문 계산 → 삽입)을 [appendChained] 한 트랜잭션으로 묶으려면 본문 있는
 * `@Transaction` 메서드가 필요하다. 코루틴 여럿이 동시에 저장해도 Room 이 쓰기 트랜잭션을 한 줄로 세워 번호가 겹치지 않는다.
 */
@Dao
abstract class AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(audit: AuditEntity)

    /**
     * 최신부터 — 체인 번호순, 체인 전 행(번호 0)은 맨 뒤에 시각순.
     *
     * [WHY] 시각만으로 정렬하면 같은 밀리초의 두 기록(승인 직후 툴 실행)의 순서가 정해지지 않았다(0.41.4) — 체인 번호는 저장 순서 그대로다.
     */
    @Query("SELECT * FROM audit_log ORDER BY chainSeq DESC, timestamp DESC LIMIT :limit OFFSET :offset")
    abstract suspend fun getEvents(offset: Int, limit: Int): List<AuditEntity>

    @Query("SELECT * FROM audit_log WHERE eventType = :eventType AND timestamp BETWEEN :from AND :to ORDER BY timestamp")
    abstract suspend fun getEventsBetween(eventType: String, from: Long, to: Long): List<AuditEntity>

    /** 체인 머리(마지막 체인 행) — 없으면 null. */
    @Query("SELECT * FROM audit_log WHERE chainSeq > 0 ORDER BY chainSeq DESC LIMIT 1")
    abstract suspend fun chainHead(): AuditEntity?

    @Query("SELECT COUNT(*) FROM audit_log WHERE chainSeq = 0")
    abstract suspend fun legacyCount(): Int

    /** 검증용 — [afterSeq] 다음 번호부터 오름차순 한 쪽. */
    @Query("SELECT * FROM audit_log WHERE chainSeq > :afterSeq ORDER BY chainSeq ASC LIMIT :limit")
    abstract suspend fun chainPage(afterSeq: Long, limit: Int): List<AuditEntity>

    /** 체인에 이어 저장한다 — 머리의 다음 번호, 머리의 지문을 앞 지문으로(머리가 없으면 [AuditHashChain.GENESIS]). 저장한 행을 돌려준다. */
    @Transaction
    open suspend fun appendChained(entry: AuditEntity): AuditEntity {
        val head = chainHead()
        val seq = (head?.chainSeq ?: 0L) + 1
        val prev = head?.entryHash ?: AuditHashChain.GENESIS
        val hash = AuditHashChain.entryHash(seq, prev, entry.id, entry.timestamp, entry.eventType, entry.sessionId, entry.details)
        val chained = entry.copy(chainSeq = seq, prevHash = prev, entryHash = hash)
        insert(chained)
        return chained
    }
}
