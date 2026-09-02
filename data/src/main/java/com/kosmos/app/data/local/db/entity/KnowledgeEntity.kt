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
 * [WHY] `embedding` 은 `ByteArray?` 다 — Room 이 BLOB 으로 네이티브 처리하므로 TypeConverter 가
 * 필요 없다(이 프로젝트에는 `@TypeConverters` 가 하나도 없다). 인코딩은 `FloatBytes` 를 쓴다.
 * nullable 이라 "임베딩 없음"을 빈 배열과 구분할 수 있고 도메인의 `FloatArray?` 와 형태가 맞는다.
 *
 * [WHY] `ByteArray` 가 `data class` 에 들어가면 `equals`/`hashCode` 가 **참조 비교**가 된다.
 * 그래서 이 엔티티를 `Set` 에 넣어 중복을 제거하면 안 된다 — 호출부는 `id` 기준으로 걸러야 한다
 * (`KnowledgeRepositoryImpl.searchByTags` 참조).
 */
data class KnowledgeEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val sourceSessionId: String?,
    val tags: String, // CSV format
    val embedding: ByteArray?, // little-endian float BLOB (FloatBytes)
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
