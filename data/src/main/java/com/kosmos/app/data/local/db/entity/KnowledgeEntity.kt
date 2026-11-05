package com.kosmos.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kosmos.app.domain.model.KnowledgeNote

@Entity(
    tableName = "knowledge_note",
    indices = [
        Index(value = ["createdAt"])
    ]
)
/**
 * [WHY] 옛 `embedding` BLOB 열(영어 전용 임베더 자리, 늘 null)은 v13 에서 뺐다 — 뜻 벡터는 `memory_vector` 에 둔다(0.41.3, ADR-038).
 */
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val sourceSessionId: String?,
    val tags: String, // CSV format
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * [WHY] v9 에서 `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT 'manual'` 로 추가했다 —
     * NOT NULL 컬럼 추가는 SQLite 가 DEFAULT 를 요구하고, Room 은 그 DDL 을 여기 defaultValue 로
     * 선언해야 다음 실행의 스키마 검증이 통과한다(5→6 의 `DEFAULT NULL` 결함과 정확히 역방향).
     */
    @ColumnInfo(defaultValue = "manual")
    val source: String = KnowledgeNote.SOURCE_MANUAL
)
