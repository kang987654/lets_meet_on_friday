package com.kosmos.app.domain

import com.kosmos.app.domain.search.BigramMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BigramMatcherTest]
 * exp33 `score()` 포팅의 수치 계약과 임계 판정, 규모 계측을 고정합니다 (C1 M1).
 *
 * [WHY] 수치가 exp33 과 같아야 그 실험(recall@1 16/16)이 이 구현의 근거가 된다 — "자물쇠번호" 의
 * 바이그램 4개 중 3개가 "자물쇠 비밀번호"에 있으므로 정확히 0.75 다.
 */
class BigramMatcherTest {

    @Test
    fun `바이그램은 공백과 기호를 지우고 2글자 창을 낸다`() {
        assertEquals(setOf("자물", "물쇠", "쇠번", "번호"), BigramMatcher.bigrams("자물쇠번호"))
        assertEquals(setOf("자물", "물쇠", "쇠번", "번호"), BigramMatcher.bigrams(" 자물쇠-번호! "))
        assertEquals(emptySet<String>(), BigramMatcher.bigrams("것"))
        assertEquals(emptySet<String>(), BigramMatcher.bigrams(""))
    }

    @Test
    fun `자물쇠번호 대 자물쇠 비밀번호 는 0_75 다`() {
        val s = BigramMatcher.score(listOf("자물쇠번호"), "자물쇠 비밀번호 4936")
        assertEquals(0.75, s, 1e-9)
        assertEquals(0.75, BigramMatcher.bestTermOverlap(listOf("자물쇠번호"), "자물쇠 비밀번호 4936"), 1e-9)
    }

    @Test
    fun `두 항이 맞은 문서가 한 항만 맞은 문서보다 앞선다`() {
        val terms = listOf("자전거", "비밀번호")
        val both = BigramMatcher.score(terms, "자전거 비밀번호는 1234")
        val one = BigramMatcher.score(terms, "현관 비밀번호는 5678")
        assertEquals(2.0, both, 1e-9)
        assertEquals(1.0, one, 1e-9)
        assertTrue(both > one)
    }

    @Test
    fun `조사가 붙어도 어근 바이그램이 살아 맞는다`() {
        // LIKE 부분 일치도 "회의" ⊂ "회의를" 은 맞히지만, "회의시간" 같은 붙여쓰기 변형은 바이그램만 넘는다.
        assertEquals(1.0, BigramMatcher.score(listOf("회의"), "팀 회의를 3시로 잡았다"), 1e-9)
        assertTrue(BigramMatcher.score(listOf("회의시간"), "팀 회의를 3시로 잡았다") > 0.3)
    }

    @Test
    fun `1글자 항은 점수에 기여하지 않는다`() {
        assertEquals(0.0, BigramMatcher.score(listOf("것"), "좋아하는 것"), 1e-9)
        assertEquals(0.0, BigramMatcher.bestTermOverlap(listOf("것"), "좋아하는 것"), 1e-9)
    }

    @Test
    fun `임계 0_5 는 태그 목록 폴백 계약을 보존한다`() {
        // MemoryPipelineIntegrationTest "못 맞히면 분류 목록을 주고 재호출을 유도한다" 의 질의·문서.
        val overlap = BigramMatcher.bestTermOverlap(listOf("좋아하는", "것"), "커피보다 녹차를 더 좋아함 선호도 음료")
        assertTrue("0.33 이어야 폴백으로 떨어진다: $overlap", overlap < 0.5)
    }

    @Test
    fun `2000 문서 전수 스캔이 정확하고 빠르다`() {
        // [WHY] FTS5 규모 게이트의 근거 수치 — 소요 시간은 로그만 남기고 단언하지 않는다(CI 기계 편차).
        val docs = (0 until 2000).map { i -> "문서 $i 번 — 일정 ${i % 24}시 회의와 메모 ${i * 7 % 1000}" } +
            "자전거 자물쇠 비밀번호는 4936"
        val terms = listOf("자물쇠번호")
        val started = System.nanoTime()
        val prepared = docs.map { it to BigramMatcher.bigrams(it) }
        val best = prepared.maxByOrNull { (_, b) -> BigramMatcher.score(terms, b) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        println("BigramMatcher 2,001 문서 스캔: ${elapsedMs}ms")

        assertEquals("자전거 자물쇠 비밀번호는 4936", best!!.first)
    }
}
