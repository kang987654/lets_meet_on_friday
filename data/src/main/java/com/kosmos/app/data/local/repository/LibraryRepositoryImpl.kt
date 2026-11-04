package com.kosmos.app.data.local.repository

import androidx.room.withTransaction
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.entity.LibraryChunkEntity
import com.kosmos.app.data.local.db.entity.LibraryDocumentEntity
import com.kosmos.app.data.local.db.entity.LibraryFtsEntity
import com.kosmos.app.data.local.db.entity.LibraryVectorEntity
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.library.LibraryCandidate
import com.kosmos.app.domain.library.LibraryChunk
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryEmbedding
import com.kosmos.app.domain.library.LibraryFusion
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibrarySearch
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.LibraryVectorInput
import com.kosmos.app.domain.library.SemanticStatus
import com.kosmos.app.domain.library.TextEmbedder
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
                dao.deleteVectorsOf(documentId)
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
                dao.deleteVectorsOf(documentId)
                dao.deleteFtsOf(documentId)
                dao.deleteChunksOf(documentId)
                dao.deleteDocument(documentId)
            }
        }

    override suspend fun totalChars(excludeId: String?): AppResult<Int> =
        dbRead(TABLE, "totalChars") { dao.totalChars(excludeId.orEmpty()) }

    override suspend fun search(query: String, limit: Int, embedder: TextEmbedder?): AppResult<List<LibraryHit>> = dbRead(TABLE, "search") {
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
        val names = dao.documents().associate { it.id to it.name }
        // 후보와 행 id 를 짝으로 — 결합 점수의 키는 행 id 다.
        val pairs = chunks.mapNotNull { e -> names[e.documentId]?.let { e.rowId to LibraryCandidate(it, e.toDomain(), e.documentId) } }
        val queryVector = embedder?.takeIf { semanticReadyFor(it.modelId) }?.embedQuery(LibraryEmbedding.queryInput(query))
        if (embedder == null || queryVector == null) {
            return@dbRead LibrarySearch.rank(query, pairs.map { it.second })
                .take(limit)
                .map { LibraryHit(it.documentId, it.documentName, it.chunk) }
        }
        // [WHY] 뜻 점수는 전 조각, 바이그램 점수는 색인 후보만(나머지 0) — exp78 의 결합과 같은 모양(sparse 는 질의의 최고점으로 나눈다).
        val sparse = LibrarySearch.scored(query, pairs.map { it.second }).mapIndexed { i, (_, score) -> pairs[i].first to score }.toMap()
        // [WHY] 조문 번호를 직접 물었으면 그 조(법령 이름이 맞는 것부터)를 결합 순위보다 앞에 고정한다 — 뜻 점수가 다른 조를 강하게 밀면 0.05 배 sparse
        // (조문 번호 가산 포함)로는 뒤집지 못한다(0.41.2 M2 테스트에서 발견). 0.41.0 의 "조문 번호는 구조 열로" 원칙과 같다.
        val wantArticle = LibrarySearch.articleOf(query)
        val pinned = if (wantArticle == null) emptyList() else
            pairs.filter { it.second.chunk.article == wantArticle }.map { it.first }.sortedByDescending { sparse[it] ?: 0.0 }
        val fused = LibraryFusion.fuse(denseScores(embedder.modelId, queryVector), sparse).map { it.first }
        val top = (pinned + fused).distinct().take(limit)
        val known = chunks.associateBy { it.rowId }
        val byRow = known + dao.chunksByRowId(top.filterNot { it in known }).associateBy { it.rowId }
        top.mapNotNull { id -> byRow[id]?.let { e -> names[e.documentId]?.let { LibraryHit(e.documentId, it, e.toDomain()) } } }
    }

    override suspend fun semanticStatus(modelId: String?): AppResult<SemanticStatus> = dbRead(TABLE, "semanticStatus") {
        val ready = LibraryStatus.READY.name
        val readyChunks = dao.readyChunkCount(ready)
        if (modelId == null) return@dbRead SemanticStatus(null, readyChunks, 0, 0)
        SemanticStatus(modelId, readyChunks, dao.embeddedChunkCount(modelId, ready), dao.pendingDocumentCount(modelId, ready))
    }

    override suspend fun chunksNeedingVectors(modelId: String, documentId: String?, limit: Int): AppResult<List<LibraryVectorInput>> =
        dbRead(TABLE, "chunksNeedingVectors") {
            val names = dao.documents().associate { it.id to it.name }
            dao.chunksWithoutVector(modelId, documentId, LibraryStatus.READY.name, limit).mapNotNull { e ->
                names[e.documentId]?.let { LibraryVectorInput(e.rowId, e.documentId, LibraryEmbedding.documentInput(it, e.toDomain())) }
            }
        }

    override suspend fun saveVectors(modelId: String, vectors: List<Pair<LibraryVectorInput, Int8Vector>>): AppResult<Unit> =
        dbWrite(TABLE, "saveVectors") {
            db.withTransaction {
                // [WHY] 모델이 바뀌었으면 옛 좌표계 벡터를 먼저 버린다 — 섞이면 순위가 무의미하다.
                dao.deleteVectorsNotOf(modelId)
                dao.insertVectors(vectors.map { (input, v) -> LibraryVectorEntity(input.chunkRowId, input.documentId, modelId, v.toBytes()) })
            }
        }

    override suspend fun clearVectors(): AppResult<Unit> = dbWrite(TABLE, "clearVectors") { dao.deleteAllVectors() }

    private suspend fun semanticReadyFor(modelId: String): Boolean {
        val ready = LibraryStatus.READY.name
        return SemanticStatus(modelId, dao.readyChunkCount(ready), dao.embeddedChunkCount(modelId, ready), 0).semanticReady
    }

    /** 전 조각 뜻 점수 — 쪽 단위로 읽어 메모리에 벡터 전체를 들지 않는다. */
    private suspend fun denseScores(modelId: String, query: FloatArray): Map<Long, Double> {
        val out = HashMap<Long, Double>()
        var after = Long.MIN_VALUE
        while (true) {
            val page = dao.vectorsAfter(modelId, after, VECTOR_PAGE)
            if (page.isEmpty()) break
            for (row in page) Int8Vector.fromBytes(row.vector)?.let { out[row.chunkRowId] = it.dot(query).toDouble() }
            after = page.last().chunkRowId
        }
        return out
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
        const val VECTOR_PAGE = 2_000
    }
}
