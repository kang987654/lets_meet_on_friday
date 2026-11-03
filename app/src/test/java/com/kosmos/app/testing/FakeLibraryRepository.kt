package com.kosmos.app.testing

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.LibraryChunk
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 문서 보관함 저장소 가짜 — 문서·조각을 메모리에 든다(검색은 하지 않는다 — 검색은 LibraryRepositoryTest 가 실제 FTS4 로 본다).
 *
 * [WHY] 화면·ViewModel·색인기 테스트는 Room 없이 목록 흐름과 상태 전이만 보면 된다.
 */
class FakeLibraryRepository(initial: List<LibraryDocument> = emptyList()) : LibraryRepository {

    val documents = MutableStateFlow(initial)
    val chunks = mutableMapOf<String, List<LibraryChunk>>()

    override fun observeDocuments(): Flow<List<LibraryDocument>> = documents

    override suspend fun document(id: String): AppResult<LibraryDocument?> = AppResult.Success(documents.value.firstOrNull { it.id == id })

    override suspend fun upsert(document: LibraryDocument): AppResult<Unit> {
        documents.update { list -> list.filter { it.id != document.id } + document }
        return AppResult.Success(Unit)
    }

    override suspend fun replaceChunks(documentId: String, chunks: List<LibraryChunk>, charCount: Int): AppResult<Unit> {
        this.chunks[documentId] = chunks
        edit(documentId) { it.copy(status = LibraryStatus.READY, chunkCount = chunks.size, charCount = charCount, error = null) }
        return AppResult.Success(Unit)
    }

    override suspend fun markFailed(documentId: String, error: String): AppResult<Unit> {
        edit(documentId) { it.copy(status = LibraryStatus.FAILED, error = error) }
        return AppResult.Success(Unit)
    }

    override suspend fun markInterrupted(error: String): AppResult<Int> {
        val n = documents.value.count { it.status == LibraryStatus.INDEXING }
        documents.update { list -> list.map { if (it.status == LibraryStatus.INDEXING) it.copy(status = LibraryStatus.FAILED, error = error) else it } }
        return AppResult.Success(n)
    }

    override suspend fun remove(documentId: String): AppResult<Unit> {
        chunks.remove(documentId)
        documents.update { list -> list.filter { it.id != documentId } }
        return AppResult.Success(Unit)
    }

    override suspend fun totalChars(excludeId: String?): AppResult<Int> =
        AppResult.Success(documents.value.filter { it.id != excludeId }.sumOf { it.charCount })

    override suspend fun search(query: String, limit: Int): AppResult<List<LibraryHit>> = AppResult.Success(emptyList())

    private fun edit(id: String, change: (LibraryDocument) -> LibraryDocument) {
        documents.update { list -> list.map { if (it.id == id) change(it) else it } }
    }
}
