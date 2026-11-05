package com.kosmos.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 활동 기록 한 행.
 *
 * [WHY] 체인 열(0.41.4, v14)은 DB 기본값을 둔다 — 마이그레이션 전부터 있던 행은 번호 0·앞 지문 `LEGACY`·지문 빈칸("체인 시작 전", 검증 대상 아님 — Q2).
 * 새 행은 `AuditDao.appendChained` 가 번호·지문을 채운다. `defaultValue` 는 마이그레이션 DDL 과 같아야 Room 스키마 검증이 통과한다.
 */
@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["timestamp"]),
        Index(value = ["chainSeq"])
    ]
)
data class AuditEntity(
    @PrimaryKey
    val id: String,
    val eventType: String, // e.g. "MODEL_RUN", "TOOL_CALL"
    val details: String,
    val sessionId: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val chainSeq: Long = 0L,
    @ColumnInfo(defaultValue = "LEGACY")
    val prevHash: String = "LEGACY",
    @ColumnInfo(defaultValue = "")
    val entryHash: String = ""
)
