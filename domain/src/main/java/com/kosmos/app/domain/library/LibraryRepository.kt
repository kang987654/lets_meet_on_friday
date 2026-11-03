package com.kosmos.app.domain.library

import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** 보관함 문서의 색인 상태. */
enum class LibraryStatus { INDEXING, READY, FAILED }

/**
 * 보관함에 등록한 문서.
 *
 * @property uri 원본의 콘텐츠 URI(지속 열람 권한) — 원본은 제자리에 두고 글자·조각·색인만 앱 DB 에 둔다(결정 D2).
 * @property charCount 색인한 본문 글자 수 — 상한([LibraryLimits]) 판정용.
 * @property error 실패 사유(사람이 읽는 문구) — [LibraryStatus.FAILED] 일 때만.
 */
data class LibraryDocument(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val addedAt: Long,
    val status: LibraryStatus,
    val chunkCount: Int = 0,
    val charCount: Int = 0,
    val error: String? = null
)

/** 검색 결과 한 건 — 조각과 그 문서. */
data class LibraryHit(val documentId: String, val documentName: String, val chunk: LibraryChunk)

/**
 * [LibraryRepository]
 * 문서 보관함 저장소 계약입니다 (0.41.0 M2) — 문서 목록·조각·색인과 검색.
 *
 * ### Architecture Context
 * - **Layer**: Domain 계약 — 구현은 data 모듈(Room + FTS4). 기억 저장소(`KnowledgeRepository`)와 테이블·엔진을 나눈다(로드맵 원안).
 *
 * [WHY] 조각은 [replaceChunks] 한 번에 통째로 바꾼다 — 색인 중인 문서의 조각 일부가 검색에 섞이지 않게(쓰기는 한 트랜잭션).
 */
interface LibraryRepository {
    fun observeDocuments(): Flow<List<LibraryDocument>>

    suspend fun document(id: String): AppResult<LibraryDocument?>

    /** 새 문서를 넣거나 같은 id 를 덮어쓴다(다시 색인할 때 [LibraryStatus.INDEXING] 으로). */
    suspend fun upsert(document: LibraryDocument): AppResult<Unit>

    /** 문서의 조각·색인을 [chunks] 로 바꾸고 문서를 [LibraryStatus.READY] 로 — 한 트랜잭션. */
    suspend fun replaceChunks(documentId: String, chunks: List<LibraryChunk>, charCount: Int): AppResult<Unit>

    suspend fun markFailed(documentId: String, error: String): AppResult<Unit>

    /** 색인 중에 프로세스가 죽어 [LibraryStatus.INDEXING] 으로 남은 문서를 실패로 — 앱 시작 때. 바꾼 수를 돌려준다. */
    suspend fun markInterrupted(error: String): AppResult<Int>

    /** 문서와 그 조각·색인을 지운다. */
    suspend fun remove(documentId: String): AppResult<Unit>

    /** [excludeId] 를 뺀 보관함 전체 본문 글자 수 — 전체 상한 판정용. */
    suspend fun totalChars(excludeId: String? = null): AppResult<Int>

    /** 질의와 맞는 조각 상위 [limit] 개 — 색인 후보 → 재순위([LibrarySearch]) + 조문 번호 구조 조회. */
    suspend fun search(query: String, limit: Int): AppResult<List<LibraryHit>>
}
