package com.kosmos.app.domain.library

import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** 보관함 문서의 색인 상태. */
enum class LibraryStatus { INDEXING, READY, FAILED }

/**
 * 색인 실패 사유 — [LibraryDocument.error] 에 이름으로 저장하고 화면이 언어별 문구로 바꾼다.
 *
 * [WHY] 문구를 저장하면 언어를 바꿔도 저장한 언어로 남는다(최근 문서 이름 0.40.2 M2-7 과 같은 결함) — 사유 코드만 둔다.
 */
enum class LibraryFailure {
    /** 파일을 열 수 없다(권한 만료·삭제·손상·암호) — 문서 뷰어의 오류 분류([com.kosmos.app.domain.document.DocumentError])를 뒤에 붙인다. */
    OPEN_FAILED,

    /** 글자를 뽑지 못했다 — 스캔 PDF, 또는 PDF 글자 추출을 지원하지 않는 기기(안드로이드 15 + 확장 13 미만). */
    NO_TEXT,

    /** 문서 하나가 [LibraryLimits.MAX_DOCUMENT_CHARS] 를 넘는다. */
    TOO_LARGE,

    /** 보관함 전체가 [LibraryLimits.MAX_TOTAL_CHARS] 를 넘는다. */
    LIBRARY_FULL,

    /** 색인 중에 앱이 종료됐다. */
    INTERRUPTED
}

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

/**
 * 모델·화면에 보일 문서 이름 — 확장자를 뗀다(`민법.docx` → `민법`). 떼고 비면 원래 이름.
 *
 * [WHY] 트리거 줄·결과 묶음·출처 카드가 같은 이름을 써야 모델이 "민법"과 "민법.docx"를 다른 문서로 보지 않는다(0.41.2 실기기).
 */
fun libraryDisplayName(name: String): String = name.substringBeforeLast('.', name).trim().ifEmpty { name }

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

    /**
     * 질의와 맞는 조각 상위 [limit] 개 — 색인 후보 → 재순위([LibrarySearch]) + 조문 번호 구조 조회.
     * [embedder] 가 있고 보관함 전부에 그 모델의 벡터가 있으면([SemanticStatus.semanticReady]) 뜻 점수와 결합한다([LibraryFusion], 0.41.2).
     */
    suspend fun search(query: String, limit: Int, embedder: TextEmbedder? = null): AppResult<List<LibraryHit>>

    /** 뜻 검색 준비 상태 — [modelId] 가 null 이면 미설치(준비 0). */
    suspend fun semanticStatus(modelId: String?): AppResult<SemanticStatus>

    /** [modelId] 벡터가 없는 READY 조각 최대 [limit] 개 — [documentId] 가 있으면 그 문서만. 조각 id 순(이어서 하기). */
    suspend fun chunksNeedingVectors(modelId: String, documentId: String? = null, limit: Int): AppResult<List<LibraryVectorInput>>

    /** 벡터를 저장한다(같은 조각이면 덮어쓴다). */
    suspend fun saveVectors(modelId: String, vectors: List<Pair<LibraryVectorInput, Int8Vector>>): AppResult<Unit>

    /** 모든 벡터를 지운다 — 임베딩 모델을 지울 때. */
    suspend fun clearVectors(): AppResult<Unit>
}

/** 벡터를 만들 조각 — [input] 은 [LibraryEmbedding.documentInput] 으로 만든 임베딩 글자. */
data class LibraryVectorInput(val chunkRowId: Long, val documentId: String, val input: String)
