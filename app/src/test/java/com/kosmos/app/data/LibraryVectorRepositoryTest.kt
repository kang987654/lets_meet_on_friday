package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.repository.DataResetRepositoryImpl
import com.kosmos.app.data.local.repository.LibraryRepositoryImpl
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.library.LibraryChunker
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.domain.library.normalized
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.testing.InMemoryKosmosDbRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [LibraryVectorRepositoryTest]
 * 보관함 뜻 벡터(0.41.2 M2) — 실제 Room 스키마 위에서 준비 상태·벡터 만들 조각·저장·결합 검색·삭제.
 *
 * [WHY] 결합 검색은 SQL(벡터 쪽 읽기·후보 조회)과 도메인 계산(LibraryFusion)이 이어지는 곳이라 순수 함수 테스트만으로는 못 본다.
 * 임베더는 낱말로 축을 고르는 가짜 — "몰래 가져가면" 처럼 글자가 안 겹치는 질문을 뜻으로만 찾게 만든다.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryVectorRepositoryTest {

    @get:Rule
    val dbRule = InMemoryKosmosDbRule()

    private lateinit var repo: LibraryRepositoryImpl

    /** 개념 축: 0 절도, 1 소멸시효, 2 성년, 3 손해배상. */
    private class FakeEmbedder(override val modelId: String = "fake-1") : TextEmbedder {
        private val axes = listOf(
            listOf("절도", "절취", "몰래"), listOf("소멸시효", "오래"), listOf("성년", "어른"), listOf("손해", "피해", "불법행위")
        )

        fun vector(text: String): FloatArray =
            FloatArray(axes.size) { i -> if (axes[i].any { it in text }) 1f else 0.05f }.normalized()

        override suspend fun embedQuery(query: String) = vector(query)
        override suspend fun embedDocuments(inputs: List<String>) = inputs.map { vector(it) }
    }

    private val embedder = FakeEmbedder()

    private fun <T> AppResult<T>.ok(): T {
        assertTrue(toString(), this is AppResult.Success)
        return (this as AppResult.Success).data
    }

    private fun add(id: String, name: String, lines: List<String>) = runBlocking {
        repo.upsert(LibraryDocument(id, "content://doc/$id", name, "text/plain", addedAt = 1, status = LibraryStatus.INDEXING)).ok()
        repo.replaceChunks(id, LibraryChunker.chunk(name, LibraryChunker.fromPlain(lines.joinToString("\n"))), lines.sumOf { it.length }).ok()
    }

    private fun embedAll(e: FakeEmbedder = embedder, documentId: String? = null) = runBlocking {
        val inputs = repo.chunksNeedingVectors(e.modelId, documentId, 100).ok()
        repo.saveVectors(e.modelId, inputs.map { it to Int8Vector.quantize(e.vector(it.input)) }).ok()
        inputs.size
    }

    @Before
    fun setUp() {
        repo = LibraryRepositoryImpl(dbRule.db)
        add("d1", "민법", listOf("제4조(성년) 사람은 19세로 성년에 이르게 된다.", "제162조(채권, 재산권의 소멸시효) 채권은 10년간 행사하지 아니하면 소멸시효가 완성한다."))
        add("d2", "형법", listOf("제329조(절도) 타인의 재물을 절취한 자는 6년 이하의 징역에 처한다.", "제250조(살인) 사람을 살해한 자는 사형에 처한다."))
    }

    @Test
    fun `준비 상태 - 모델이 없으면 0, 벡터가 모자라면 남은 문서 수, 다 차면 결합`() = runBlocking {
        val none = repo.semanticStatus(null).ok()
        assertEquals(4, none.readyChunks)
        assertFalse(none.semanticReady)

        val before = repo.semanticStatus(embedder.modelId).ok()
        assertEquals(2, before.pendingDocuments)
        assertFalse(before.semanticReady)

        assertEquals(2, embedAll(documentId = "d2"))
        assertEquals(1, repo.semanticStatus(embedder.modelId).ok().pendingDocuments)

        embedAll()
        val after = repo.semanticStatus(embedder.modelId).ok()
        assertEquals(0, after.pendingDocuments)
        assertTrue(after.semanticReady)
        assertTrue("다 만들면 더 만들 조각이 없다", repo.chunksNeedingVectors(embedder.modelId, null, 100).ok().isEmpty())
    }

    @Test
    fun `벡터 만들 조각의 입력은 문서 이름과 조 제목을 붙인 형식이다`() = runBlocking {
        val inputs = repo.chunksNeedingVectors(embedder.modelId, "d2", 100).ok()
        assertEquals(2, inputs.size)
        assertTrue(inputs.all { it.documentId == "d2" })
        assertTrue(inputs.first().input, inputs.first().input.startsWith("title: 형법 제329조(절도) | text: "))
    }

    @Test
    fun `바꿔 말한 질문은 벡터가 다 차야 뜻으로 찾는다`() = runBlocking {
        val query = "남의 물건을 몰래 가져가면"
        assertNotEquals("벡터 없이는 바이그램만", "329", repo.search(query, 3, embedder).ok().firstOrNull()?.chunk?.article)

        embedAll(documentId = "d2")
        assertNotEquals("일부만 있으면 아직 바이그램", "329", repo.search(query, 3, embedder).ok().firstOrNull()?.chunk?.article)

        embedAll()
        val hit = repo.search(query, 3, embedder).ok().first()
        assertEquals("329", hit.chunk.article)
        assertEquals("형법", hit.documentName)
        assertEquals("조문 번호는 결합에서도 1위", "162", repo.search("민법 제162조", 3, embedder).ok().first().chunk.article)
    }

    @Test
    fun `다시 색인하면 그 문서 벡터가, 다른 모델로 저장하면 옛 모델 벡터가, 초기화하면 전부 지워진다`() = runBlocking {
        embedAll()
        add("d2", "형법", listOf("제1조(범죄의 성립) 범죄의 성립은 행위 시의 법률에 따른다."))
        val status = repo.semanticStatus(embedder.modelId).ok()
        assertEquals(1, status.pendingDocuments)
        assertFalse(status.semanticReady)

        val other = FakeEmbedder("fake-2")
        embedAll(other, documentId = "d2")
        assertEquals("옛 모델 벡터는 남지 않는다", 0, repo.semanticStatus(embedder.modelId).ok().embeddedChunks)

        repo.clearVectors().ok()
        assertEquals(0, repo.semanticStatus(other.modelId).ok().embeddedChunks)

        embedAll(other)
        DataResetRepositoryImpl(dbRule.db).reset(setOf(ResetTarget.LIBRARY)).ok()
        dbRule.db.query("SELECT COUNT(*) FROM library_vector", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
}
