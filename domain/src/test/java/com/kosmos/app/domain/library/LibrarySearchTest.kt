package com.kosmos.app.domain.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문서 보관함 검색의 순수 계산부(0.41.0 M1) — 색인 글자, 질의 토큰 고르기, matchinfo 해석, 재순위.
 */
class LibrarySearchTest {

    private fun chunk(path: String, text: String, article: String? = null) = LibraryChunk(0, path, article, null, text)

    @Test
    fun `색인 글자는 머리말과 본문 바이그램을 정렬해 공백으로 잇고 라틴 표지의 콜론을 뺀다`() {
        val text = LibrarySearch.indexText(chunk("민법 > 제4조(성년)", "Adult 19세"))

        val tokens = text.split(" ")
        assertEquals(tokens.sorted(), tokens)
        assertTrue("성년" in tokens)
        assertTrue("19" in tokens)
        assertTrue("wadult" in tokens)
        assertTrue(tokens.none { ":" in it })
    }

    @Test
    fun `질의의 조문 번호를 읽는다`() {
        assertEquals("162", LibrarySearch.articleOf("민법 제162조 알려줘"))
        assertEquals("750", LibrarySearch.articleOf("750 조"))
        assertEquals("5의2", LibrarySearch.articleOf("제5조의2"))
        assertNull(LibrarySearch.articleOf("소멸시효 기간"))
    }

    @Test
    fun `흔한 토큰은 빼고 드문 토큰만 질의하며 모두 흔하면 가장 드문 몇 개를 쓴다`() {
        val df = mapOf("소멸" to 5, "멸시" to 4, "시효" to 30, "기간" to 300)

        assertEquals(listOf("멸시", "소멸", "시효"), LibrarySearch.queryTerms("소멸시효 기간", df, total = 1000))
        assertEquals("멸시 OR 소멸 OR 시효", LibrarySearch.matchQuery(listOf("멸시", "소멸", "시효")))
        val allCommon = mapOf("소멸" to 600, "멸시" to 500, "시효" to 700, "기간" to 900)
        assertEquals("가장 드문 3개", listOf("멸시", "소멸", "시효"), LibrarySearch.queryTerms("소멸시효 기간", allCommon, total = 1000).sorted())
        assertEquals(emptyList<String>(), LibrarySearch.queryTerms("없는말", df, total = 1000))
    }

    @Test
    fun `matchinfo x 를 풀고 이 행에 나온 토큰의 idf 만 더한다`() {
        // 구 2개 × (이 행 적중, 전체 적중, 적중 행 수) — 첫 구만 이 행에 있다.
        val blob = byteArrayOf(1, 0, 0, 0, 7, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 2, 1, 0, 0, 9, 0, 0, 0)
        val ints = LibrarySearch.decodeMatchinfo(blob)

        assertArrayEquals(intArrayOf(1, 7, 3, 0, 258, 9), ints)
        assertEquals(2.5, LibrarySearch.candidateScore(ints, listOf(2.5, 4.0)), 1e-9)
        assertTrue(LibrarySearch.idf(1, 1000) > LibrarySearch.idf(500, 1000))
    }

    @Test
    fun `재순위는 머리말 일치를 본문보다 크게 보고 조문 번호와 문서 이름 일치를 더한다`() {
        val title = LibraryCandidate("형법", chunk("형법 > 제38장 절도와 강도의 죄 > 제329조(절도)", "타인의 재물을 절취한 자는", "329"))
        val bodyOnly = LibraryCandidate("형법", chunk("형법 > 제1장 > 제1조(범죄의 성립과 처벌)", "절도에 관한 일반 규정", "1"))
        assertEquals(title, LibrarySearch.rank("절도", listOf(bodyOnly, title)).first())

        val a750 = LibraryCandidate("민법", chunk("민법 > 제750조(불법행위의 내용)", "고의 또는 과실로 인한 위법행위", "750"))
        val other = LibraryCandidate("민법", chunk("민법 > 제751조(재산 이외의 손해의 배상)", "불법행위 손해 제750조 참조", "751"))
        assertEquals(a750, LibrarySearch.rank("민법 제750조", listOf(other, a750)).first())

        val civil = LibraryCandidate("민법", chunk("민법 > 제4조(성년)", "19세로 성년", "4"))
        val privacy = LibraryCandidate("개인정보 보호법 시행령", chunk("개인정보 보호법 시행령 > 제4조(성년)", "19세로 성년", "4"))
        assertEquals(privacy, LibrarySearch.rank("개인정보 보호법 시행령 제4조", listOf(civil, privacy)).first())
    }
}
