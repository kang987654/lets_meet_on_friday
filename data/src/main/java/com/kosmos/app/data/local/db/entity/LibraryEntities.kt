package com.kosmos.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** 보관함 문서 한 건 (0.41.0) — 원본은 제자리(URI), 상태·통계만 여기. */
@Entity(tableName = "library_document")
data class LibraryDocumentEntity(
    @PrimaryKey
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val addedAt: Long,
    val status: String,
    val chunkCount: Int,
    val charCount: Int,
    val error: String?
)

/**
 * 보관함 조각 (0.41.0). [rowId] 가 [LibraryFtsEntity] 의 `rowid` 와 같다 — 색인 적중 행을 조각으로 되돌린다.
 *
 * [WHY] 외래 키(삭제 연쇄)를 걸지 않는다 — Room 은 외래 키 강제를 켜지 않고, 색인(FTS) 행도 함께 지워야 해서 저장소가 한 트랜잭션에서 지운다.
 */
@Entity(
    tableName = "library_chunk",
    indices = [Index(value = ["documentId"]), Index(value = ["article"])]
)
data class LibraryChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId: Long = 0,
    val documentId: String,
    val idx: Int,
    val sectionPath: String,
    val article: String?,
    val page: Int?,
    val text: String
)

/**
 * 보관함 색인 — 조각의 바이그램을 공백으로 이은 글자(`LibrarySearch.indexText`)를 FTS4 `simple` 토크나이저로 넣는다(exp74).
 *
 * [WHY] FTS5 가 아니라 FTS4 — 안드로이드 프레임워크 SQLite 에 늘 있고 Room 이 `@Fts4` 를 지원한다. 한국어 토크나이저 문제는 바이그램을 미리
 * 잘라 넣어 피한다.
 */
@Fts4
@Entity(tableName = "library_fts")
data class LibraryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val tokens: String
)
