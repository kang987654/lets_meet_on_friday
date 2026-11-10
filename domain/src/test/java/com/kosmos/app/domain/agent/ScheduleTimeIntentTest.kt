package com.kosmos.app.domain.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScheduleTimeIntentTest]
 * 일정 가드의 날짜·시각 판정 (0.41.11 M1) — 회귀 세트의 시각 문장(TIMED)과 사실 문장(FACTS)을 갈라야 한다.
 */
class ScheduleTimeIntentTest {

    @Test
    fun `시각·날짜 표현은 잡는다`() {
        listOf(
            "회의는 오후 4시 30분에 시작해", "내일 아침 9시에 치과 예약 있어", "모레 오전 10시 회의 잡아줘", "이번 주 토요일 일정 뭐 있어?",
            "다음 달 3일에 병원 가", "세 시에 미팅", "14:30 회의", "내일 3시", "Meeting tomorrow at 3pm", "dentist on friday", "다음주 화요일"
        ).forEach { assertTrue(it, ScheduleTimeIntent.hasTime(it)) }
    }

    @Test
    fun `사실 문장과 시각 없는 부탁은 잡지 않는다`() {
        listOf(
            "주차는 지하 2층 C-14에 했어", "택배 송장번호 6043 1122 9087이야", "내 사물함 번호는 214번이야", "새 와이파이 비번은 sky7788이야",
            "우리 강아지 이름은 콩이야", "치과 예약 잡아줘", "I parked at spot C-14"
        ).forEach { assertFalse(it, ScheduleTimeIntent.hasTime(it)) }
    }

    @Test
    fun `앞 비서 말에 시각이 있으면 막지 않는다`() {
        assertTrue(ScheduleTimeIntent.shouldBlock("응", "메모해 둘까요?"))
        assertFalse(ScheduleTimeIntent.shouldBlock("응", "내일 오후 3시로 잡을까요?"))
    }
}
