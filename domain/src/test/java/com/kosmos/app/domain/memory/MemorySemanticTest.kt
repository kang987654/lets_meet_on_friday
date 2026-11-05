package com.kosmos.app.domain.memory

import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MemorySemanticTest]
 * 기억 뜻 검색의 순수 부분(0.41.3 M1) — 입력 형식(exp80 과 같음), 결합 순위, 준비 판정.
 */
class MemorySemanticTest {

    private fun note(content: String, tags: List<String>) = KnowledgeNote(id = "n", content = content, tags = tags, createdAt = 1, updatedAt = 1)

    private fun episode(title: String?, summary: String?, tags: List<String>) = Episode(
        id = "e", sessionId = "s", status = EpisodeStatus.SUMMARIZED, title = title, summary = summary, tags = tags,
        startAt = 0, endAt = 1, messageCount = 2, retryCount = 0, createdAt = 1, updatedAt = 1
    )

    @Test
    fun `메모 입력은 태그를 제목 자리에 두고 없으면 none`() {
        assertEquals("title: 노트북 비밀번호 | text: 노트북 로그인 PIN 은 0912", MemoryEmbedding.noteInput(note("노트북 로그인 PIN 은 0912", listOf("노트북", "비밀번호"))))
        assertEquals("title: none | text: 메모", MemoryEmbedding.noteInput(note(" 메모 ", emptyList())))
    }

    @Test
    fun `대화 입력은 제목과 태그를 제목 자리에 두고 요약을 본문으로`() {
        assertEquals("title: 치과 예약 치과 병원 | text: 내일 3시 치과", MemoryEmbedding.episodeInput(episode("치과 예약", "내일 3시 치과", listOf("치과", "병원"))))
        assertEquals("title: none | text: ", MemoryEmbedding.episodeInput(episode(null, null, emptyList())))
    }

    @Test
    fun `결합 순위는 바이그램이 없는 기억도 뜻으로 끌어오고 바이그램을 조금 더한다`() {
        val a = MemoryVectorKey.note("a")
        val b = MemoryVectorKey.note("b")
        val e = MemoryVectorKey.episode("e")
        // 교차 언어: 바이그램 0 인 a 가 뜻으로 1위
        assertEquals(listOf(a, e), MemoryFusion.rank(mapOf(a to 0.72, b to 0.60, e to 0.65), emptyMap(), limit = 2))
        // 뜻이 비슷하면 바이그램이 순서를 정한다
        assertEquals(listOf(b, a), MemoryFusion.rank(mapOf(a to 0.700, b to 0.690), mapOf(b to 2.0), limit = 3).take(2))
    }

    @Test
    fun `준비는 모델이 있고 대상 전부에 벡터가 있을 때만`() {
        assertTrue(MemorySemanticStatus("m", targets = 10, embedded = 10).ready)
        assertFalse(MemorySemanticStatus("m", targets = 10, embedded = 9).ready)
        assertFalse(MemorySemanticStatus(null, targets = 10, embedded = 10).ready)
        assertFalse("기억이 없으면 어휘 경로", MemorySemanticStatus("m", targets = 0, embedded = 0).ready)
        assertEquals(1, MemorySemanticStatus("m", 10, 9).pending)
    }

    @Test
    fun `벡터 키 코드는 저장 형식과 왕복한다`() {
        MemoryVectorKey.Kind.entries.forEach { assertEquals(it, MemoryVectorKey.Kind.of(it.code)) }
        assertEquals(null, MemoryVectorKey.Kind.of("x"))
    }
}
