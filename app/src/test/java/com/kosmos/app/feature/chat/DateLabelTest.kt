package com.kosmos.app.feature.chat

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.util.IsoDateTimeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [DateLabelTest]
 * 타임라인 날짜 구분선 판정을 고정 "오늘"로 검증합니다 — 벽시계에 묶였던 판정의 회귀 방지.
 */
class DateLabelTest {

    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 30)

    private fun msg(iso: String) = ChatMessage(
        id = iso, sessionId = "s", role = ChatMessage.Role.USER, content = "x", inputType = InputType.TEXT,
        createdAt = java.time.Instant.parse(iso).toEpochMilli()
    )

    @Test
    fun `같은 날이면 구분선이 없다`() {
        assertNull(dateLabelIfBoundary(msg("2026-09-30T10:00:00Z"), msg("2026-09-30T09:00:00Z"), today, utc))
    }

    @Test
    fun `오늘 어제 그 외 날짜를 가른다`() {
        assertEquals("오늘", dateLabelIfBoundary(msg("2026-09-30T01:00:00Z"), msg("2026-09-29T23:00:00Z"), today, utc))
        assertEquals("어제", dateLabelIfBoundary(msg("2026-09-29T01:00:00Z"), msg("2026-09-28T23:00:00Z"), today, utc))
        assertEquals("9월 27일", dateLabelIfBoundary(msg("2026-09-27T01:00:00Z"), msg("2026-09-26T23:00:00Z"), today, utc))
    }

    @Test
    fun `epoch 표기 헬퍼`() {
        val ms = java.time.Instant.parse("2026-09-30T16:05:00Z").toEpochMilli()
        assertEquals("9월 30일", IsoDateTimeParser.monthDayKorean(ms, utc))
        assertEquals("오후 4:05", IsoDateTimeParser.timeKorean(ms, utc))
    }
}
