package com.kosmos.app.core

import com.kosmos.app.core.common.Tags
import com.kosmos.app.core.common.enumOrDefault
import com.kosmos.app.domain.model.EpisodeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CoreHelpersTest]
 * 저장소 매퍼들이 함께 쓰는 core 헬퍼의 계약을 고정합니다.
 */
class CoreHelpersTest {

    @Test
    fun `enumOrDefault 는 아는 이름이면 그 값, 모르거나 null 이면 기본값`() {
        assertEquals(EpisodeStatus.CLOSED, enumOrDefault("CLOSED", EpisodeStatus.FAILED))
        assertEquals(EpisodeStatus.FAILED, enumOrDefault("closed", EpisodeStatus.FAILED))
        assertEquals(EpisodeStatus.FAILED, enumOrDefault(null, EpisodeStatus.FAILED))
    }

    @Test
    fun `Tags encode 는 정규화를 거치고 decode 와 왕복한다`() {
        val encoded = Tags.encode(listOf("  회식   장소 ", "밥, 국", "", "밥 국"))
        assertEquals("회식 장소,밥 국", encoded)
        assertEquals(listOf("회식 장소", "밥 국"), Tags.decode(encoded))
    }

    @Test
    fun `Tags decode 는 빈 칸을 버린다`() {
        assertEquals(emptyList<String>(), Tags.decode(""))
        assertEquals(listOf("a", "b"), Tags.decode("a,, b ,"))
    }
}
