package com.kosmos.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RecentDocumentsTest]
 * 최근 문서 규칙 — 같은 URI 는 맨 위로 갱신, 최대 20개, 저장 형식 왕복(이름의 탭·줄바꿈은 공백으로).
 */
class RecentDocumentsTest {

    private fun doc(n: Int, name: String = "문서$n.xlsx") = RecentDocument("content://docs/$n", name, "application/pdf", n.toLong())

    @Test
    fun `같은 문서는 맨 위로 올리고 하나만 남긴다`() {
        val list = listOf(doc(1), doc(2), doc(3))
        val updated = RecentDocuments.add(list, doc(2).copy(openedAt = 99))
        assertEquals(listOf("content://docs/2", "content://docs/1", "content://docs/3"), updated.map { it.uri })
        assertEquals(99L, updated.first().openedAt)
    }

    @Test
    fun `최신순 20개까지`() {
        val list = (1..20).map { doc(it) }
        val updated = RecentDocuments.add(list, doc(21))
        assertEquals(20, updated.size)
        assertEquals("content://docs/21", updated.first().uri)
        assertEquals("가장 오래된 끝 항목이 밀려난다", "content://docs/19", updated.last().uri)
    }

    @Test
    fun `저장 형식 왕복`() {
        val list = listOf(doc(1, "탭\t이름\n줄바꿈.pdf"), RecentDocument("content://x/2", "b.csv", null, 5))
        val decoded = RecentDocuments.decode(RecentDocuments.encode(list))
        assertEquals(listOf("탭 이름 줄바꿈.pdf", "b.csv"), decoded.map { it.name })
        assertEquals(listOf("application/pdf", null), decoded.map { it.mimeType })
        assertEquals(emptyList<RecentDocument>(), RecentDocuments.decode(null))
        assertEquals("깨진 줄은 건너뛴다", 1, RecentDocuments.decode("깨진 줄\ncontent://a\ta\t\t3").size)
    }

    @Test
    fun `지우기`() {
        assertEquals(listOf("content://docs/1"), RecentDocuments.remove(listOf(doc(1), doc(2)), "content://docs/2").map { it.uri })
    }
}
