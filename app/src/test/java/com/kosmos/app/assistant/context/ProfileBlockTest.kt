package com.kosmos.app.assistant.context

import com.kosmos.app.domain.model.ProfileEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ProfileBlockTest]
 * 프로필 블록 렌더의 **바이트-안정 계약**을 고정합니다 — 같은 항목이면 입력 순서와 무관하게
 * 같은 문자열이어야 한다. 흔들리면 시스템 지시 비교(GemmaModelRunner)가 매 턴 전체
 * 재프리필을 만든다 (ADR-010 실측 0.5s→3.8s).
 */
class ProfileBlockTest {

    private fun entry(key: String, value: String, updatedAt: Long = 0L) =
        ProfileEntry(key = key, value = value, updatedAt = updatedAt)

    @Test
    fun `키 사전순으로 렌더되고 입력 순서와 무관하다`() {
        val a = renderProfileBlock(listOf(entry("이름", "진우"), entry("말투", "친근하게")))
        val b = renderProfileBlock(listOf(entry("말투", "친근하게"), entry("이름", "진우")))

        assertEquals(a, b)
        assertEquals("[User Profile]\n- 말투: 친근하게\n- 이름: 진우", a)
    }

    @Test
    fun `updatedAt 이 달라도 렌더는 같다 - 바이트 안정`() {
        val a = renderProfileBlock(listOf(entry("이름", "진우", updatedAt = 1L)))
        val b = renderProfileBlock(listOf(entry("이름", "진우", updatedAt = 999L)))

        assertEquals(a, b)
    }

    @Test
    fun `빈 목록과 공백 항목은 빈 문자열 - 블록 자체가 생략된다`() {
        assertEquals("", renderProfileBlock(emptyList()))
        assertEquals("", renderProfileBlock(listOf(entry("  ", "값"), entry("키", "  "))))
    }

    @Test
    fun `키와 값의 앞뒤 공백은 정규화된다`() {
        assertEquals(
            "[User Profile]\n- 이름: 진우",
            renderProfileBlock(listOf(entry(" 이름 ", " 진우 ")))
        )
    }
}
