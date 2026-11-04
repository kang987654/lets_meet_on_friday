package com.kosmos.app.domain.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 뜻 검색의 순수 계산부(0.41.2 M1) — 입력 형식, int8 벡터, 결합 순위, 결합 검색 전환 판정.
 */
class LibrarySemanticTest {

    private fun chunk(path: String, text: String, article: String? = null) = LibraryChunk(0, path, article, null, text)

    @Test
    fun `질의와 문서 입력은 실험실과 같은 형식이다`() {
        assertEquals("task: search result | query: 절도", LibraryEmbedding.queryInput("  절도 "))
        assertEquals(
            "title: 형법 제329조(절도) | text: 타인의 재물을 절취한 자는",
            LibraryEmbedding.documentInput("형법", chunk("형법 > 제2편 각칙 > 제329조(절도)", "타인의 재물을 절취한 자는"))
        )
    }

    @Test
    fun `머리말이 문서 이름뿐이면 제목에 이름을 두 번 쓰지 않는다`() {
        assertEquals("title: 메모 | text: 본문", LibraryEmbedding.documentInput("메모", chunk("메모", "본문")))
    }

    @Test
    fun `int8 은 내적을 거의 보존하고 저장 형식을 왕복한다`() {
        val doc = floatArrayOf(0.6f, -0.8f, 0.0f).normalized()
        val query = floatArrayOf(0.5f, -0.5f, 0.7071f).normalized()
        val exact = doc.indices.sumOf { (doc[it] * query[it]).toDouble() }

        val q8 = Int8Vector.quantize(doc)
        assertTrue(abs(q8.dot(query) - exact) < 0.01)

        val back = Int8Vector.fromBytes(q8.toBytes())
        requireNotNull(back)
        assertArrayEquals(q8.values, back.values)
        assertEquals(q8.scale, back.scale, 0f)
        assertNull(Int8Vector.fromBytes(byteArrayOf(1, 2)))
    }

    @Test
    fun `영벡터와 차원이 다른 질의는 0 점이다`() {
        assertEquals(0f, Int8Vector.quantize(FloatArray(3)).dot(floatArrayOf(1f, 0f, 0f)), 0f)
        assertEquals(0f, Int8Vector.quantize(floatArrayOf(1f, 0f)).dot(floatArrayOf(1f, 0f, 0f)), 0f)
    }

    @Test
    fun `결합 순위 - 바꿔 말한 질문은 dense 가, 용어가 같은 조각은 sparse 가 끌어올린다`() {
        // 조각 1: 뜻은 맞지만 글자가 안 겹침, 2: 글자만 겹침, 3: 둘 다 애매
        val dense = mapOf(1L to 0.82, 2L to 0.70, 3L to 0.75)
        val sparse = mapOf(2L to 4.0, 3L to 1.0)
        assertEquals(listOf(1L, 3L, 2L), LibraryFusion.fuse(dense, sparse, 0.05).map { it.first })

        // 같은 dense 면 sparse 가 가른다
        val tie = LibraryFusion.fuse(mapOf(1L to 0.8, 2L to 0.8), mapOf(2L to 2.0), 0.05)
        assertEquals(2L, tie.first().first)
    }

    @Test
    fun `sparse 가 비면 dense 순서, 같은 점수는 키 순`() {
        assertEquals(listOf(2L, 1L, 3L), LibraryFusion.fuse(mapOf(1L to 0.5, 2L to 0.9, 3L to 0.5), emptyMap()).map { it.first })
    }

    @Test
    fun `결합 검색은 READY 조각 전부에 지금 모델 벡터가 있을 때만`() {
        assertTrue(SemanticStatus("eg2", readyChunks = 10, embeddedChunks = 10, pendingDocuments = 0).semanticReady)
        assertFalse(SemanticStatus("eg2", readyChunks = 10, embeddedChunks = 9, pendingDocuments = 1).semanticReady)
        assertFalse(SemanticStatus(null, readyChunks = 10, embeddedChunks = 10, pendingDocuments = 0).semanticReady)
        assertFalse(SemanticStatus("eg2", readyChunks = 0, embeddedChunks = 0, pendingDocuments = 0).semanticReady)
    }

    @Test
    fun `scored 는 rank 와 같은 점수를 들어온 순서대로 준다`() {
        val a = LibraryCandidate("형법", chunk("형법 > 제329조(절도)", "타인의 재물을 절취한 자", "329"))
        val b = LibraryCandidate("민법", chunk("민법 > 제750조(불법행위의 내용)", "고의 또는 과실로", "750"))
        val scored = LibrarySearch.scored("절도", listOf(b, a))
        assertEquals(listOf(b, a), scored.map { it.first })
        assertTrue(scored[1].second > scored[0].second)
        assertEquals(listOf(a, b), LibrarySearch.rank("절도", listOf(b, a)))
    }
}
