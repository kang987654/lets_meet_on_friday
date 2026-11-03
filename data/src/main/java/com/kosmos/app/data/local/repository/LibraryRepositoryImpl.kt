package com.kosmos.app.data.local.repository

import androidx.room.withTransaction
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.entity.LibraryChunkEntity
import com.kosmos.app.data.local.db.entity.LibraryDocumentEntity
import com.kosmos.app.data.local.db.entity.LibraryFtsEntity
import com.kosmos.app.domain.library.LibraryCandidate
import com.kosmos.app.domain.library.LibraryChunk
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibrarySearch
import com.kosmos.app.domain.library.LibraryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [LibraryRepositoryImpl]
 * 문서 보관함 저장소 — Room 테이블 셋(문서·조각·FTS4 색인)과 검색 (0.41.0 M2).
 *
 * ### Architecture Context
 * - **Layer**: Data — [KosmosDatabase]. 검색 계산은 도메인 [LibrarySearch] 가 맡고 여기는 질의·적재만 한다.
 *
 * ### Key Flow (검색)
 * 1. 질의 토큰마다 색인 문서빈도를 센다 → 흔한 토큰을 뺀 `OR` 질의([LibrarySearch.queryTerms]).
 * 2. 적중 행의 `matchinfo` 로 idf 합 상위 [LibrarySearch.CANDIDATES] 개.
 * 3. 질의에 조문 번호가 있으면 그 번호의 조각을 구조 열로 더한다 — 바이그램 후보에서 빠지는 조문 번호 질문을 메운다(exp74 9/10 → 10/10).
 * 4. 후보를 [LibrarySearch.rank] 로 다시 매겨 상위 [limit] 개.
 */
class LibraryRepositoryImpl @Inject constructor(
    private val db: KosmosDatabase
) : LibraryRepository {

    private val dao = db.libraryDao()

    override fun observeDocuments(): Flow<List<LibraryDocument>> =
        dao.observeDocuments().map { list -> list.map { it.toDomain() } }

    override suspend fun document(id: String): AppResult<LibraryDocument?> =
        dbRead(TABLE, "document") { dao.document(id)?.toDomain() }

    override suspend fun upsert(document: LibraryDocument): AppResult<Unit> =
        dbWrite(TABLE, "upsert") { dao.upsertDocument(document.toEntity()) }

    override suspend fun replaceChunks(documentId: String, chunks: List<LibraryChunk>, charCount: Int): AppResult<Unit> =
        dbWrite(TABLE, "replaceChunks") {
            db.withTransaction {
                dao.deleteFtsOf(documentId)
                dao.deleteChunksOf(documentId)
                val rowIds = dao.insertChunks(
                    chunks.map { LibraryChunkEntity(documentId = documentId, idx = it.index, sectionPath = it.sectionPath, article = it.article, page = it.page, text = it.text) }
                )
                dao.insertFts(rowIds.zip(chunks) { rowId, chunk -> LibraryFtsEntity(rowId, LibrarySearch.indexText(chunk)) })
                dao.markReady(documentId, LibraryStatus.READY.name, chunks.size, charCount)
            }
        }

    override suspend fun markFailed(documentId: String, error: String): AppResult<Unit> =
        dbWrite(TABLE, "markFailed") { dao.setStatus(documentId, LibraryStatus.FAILED.name, error) }

    override suspend fun markInterrupted(error: String): AppResult<Int> =
        dbRead(TABLE, "markInterrupted") { dao.replaceStatus(LibraryStatus.INDEXING.name, LibraryStatus.FAILED.name, error) }

    override suspend fun remove(documentId: String): AppResult<Unit> =
        dbWrite(TABLE, "remove") {
            db.withTransaction {
                dao.deleteFtsOf(documentId)
                dao.deleteChunksOf(documentId)
                dao.deleteDocument(documentId)
            }
        }

    override suspend fun totalChars(excludeId: String?): AppResult<Int> =
        dbRead(TABLE, "totalChars") { dao.totalChars(excludeId.orEmpty()) }

    override suspend fun search(query: String, limit: Int): AppResult<List<LibraryHit>> = dbRead(TABLE, "search") {
        val total = dao.chunkCount()
        if (total == 0 || query.isBlank()) return@dbRead emptyList()
        val df = LibrarySearch.tokens(query).associateWith { dao.documentFrequency(it) }
        val terms = LibrarySearch.queryTerms(query, df, total)
        val ftsRowIds = if (terms.isEmpty()) emptyList() else {
            val idf = terms.map { LibrarySearch.idf(df[it] ?: 0, total) }
            dao.match(LibrarySearch.matchQuery(terms))
                .map { it.rowId to LibrarySearch.candidateScore(LibrarySearch.decodeMatchinfo(it.matchinfo), idf) }
                .sortedByDescending { it.second }
                .take(LibrarySearch.CANDIDATES)
                .map { it.first }
        }
        val byArticle = LibrarySearch.articleOf(query)?.let { dao.chunksByArticle(it) }.orEmpty()
        val chunks = (dao.chunksByRowId(ftsRowIds) + byArticle).distinctBy { it.rowId }
        if (chunks.isEmpty()) return@dbRead emptyList()
        val names = dao.documents().associate { it.id to it.name }
        val candidates = chunks.mapNotNull { e -> names[e.documentId]?.let { LibraryCandidate(it, e.toDomain(), e.documentId) } }
        LibrarySearch.rank(query, candidates)
            .take(limit)
            .map { LibraryHit(it.documentId, it.documentName, it.chunk) }
    }

    private fun LibraryDocumentEntity.toDomain() = LibraryDocument(
        id = id, uri = uri, name = name, mimeType = mimeType, addedAt = addedAt,
        status = runCatching { LibraryStatus.valueOf(status) }.getOrDefault(LibraryStatus.FAILED),
        chunkCount = chunkCount, charCount = charCount, error = error
    )

    private fun LibraryDocument.toEntity() = LibraryDocumentEntity(
        id = id, uri = uri, name = name, mimeType = mimeType, addedAt = addedAt, status = status.name,
        chunkCount = chunkCount, charCount = charCount, error = error
    )

    private fun LibraryChunkEntity.toDomain() = LibraryChunk(idx, sectionPath, article, page, text)

    internal companion object {
        const val TABLE = "library"
    }
}
