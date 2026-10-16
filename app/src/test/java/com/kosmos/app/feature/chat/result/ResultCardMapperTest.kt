package com.kosmos.app.feature.chat.result

import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * 결과 카드(0.38.2 M3)의 상태 판정 — 카드는 그 순간의 사진이 아니라 대상의 **현재 상태**다(D6 ④).
 * 시간대는 명시한다(AGENTS §2-④). Robolectric — 근거 JSON 을 org.json(안드로이드 구현)으로 읽는다.
 */
@RunWith(RobolectricTestRunner::class)
class ResultCardMapperTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 15)
    private val reminder = TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00")

    @Test
    fun `리마인더 — 울리기 전·울렸음·완료·취소함 상태와 오늘 표기`() {
        val before = ResultCardMapper.reminder(reminder, today, zone)
        assertEquals("울리기 전", before.status)
        assertEquals("오후 1:56", before.value)
        assertEquals("오늘", before.sub)
        assertFalse(before.muted)

        assertEquals("울렸음", ResultCardMapper.reminder(reminder.copy(remindedAtMs = 1), today, zone).status)
        assertEquals("완료", ResultCardMapper.reminder(reminder.copy(isCompleted = true), today, zone).status)

        val cancelled = ResultCardMapper.reminder(reminder.copy(cancelledAt = 5), today, zone)
        assertEquals("취소함", cancelled.status)
        assertTrue("취소는 흐리게 + 취소선(사용자 결정)", cancelled.muted && cancelled.struck)
    }

    @Test
    fun `지운 대상은 지웠다고 흐리게 — 다른 날 일정은 날짜 표기`() {
        val gone = ResultCardMapper.reminder(null, today, zone)
        assertEquals("지운 알림", gone.title)
        assertTrue(gone.muted)

        val meeting = ResultCardMapper.schedule(
            TaskItem(id = "t5", title = "팀 회의", isCompleted = false, createdAt = 0, dueDateIso = "2026-10-19T10:00:00"), today, zone
        )
        assertEquals("오전 10:00", meeting.value)
        assertEquals("10월 19일 (월)", meeting.sub)
        assertTrue(ResultCardMapper.schedule(null, today, zone).muted)
    }

    @Test
    fun `기억 답 — 근거 메모 날짜와 충돌 값을 근거 줄로, 메모가 없으면 과거 대화가 근거`() {
        val note = KnowledgeNote(id = "k1", content = "자전거 비밀번호는 134", createdAt = 0, updatedAt = 0)
        val noteAt = java.time.ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val recall = ResultCardMapper.recall(note, """{"noteAt":$noteAt,"conflictCount":1,"otherValues":["4321"]}""", zone)

        assertEquals("자전거 비밀번호는 134", recall.title)
        assertNull("값은 메모 문장에 이미 있다 — 따로 크게 두지 않는다", recall.value)
        assertEquals(listOf("근거 · 저장된 메모 (10월 12일)", "과거 대화 1건엔 4321 — 메모 값을 따랐어요"), recall.evidence)

        assertEquals(listOf("근거 · 과거 대화"), ResultCardMapper.recall(null, null, zone).evidence)
    }
}
