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
    fun `날짜·시간대 낱말만 있으면 몇 시를 묻게 막는다(0·41·15 M2)`() {
        listOf("아침 운동 일정 추가해줘", "저녁 약속 일정 넣어줘", "내일 저녁 미용실 예약 잡아줘", "금요일 오후에 치과 예약", "다음주 화요일 회의", "3시간짜리 워크숍 내일",
            "Add dinner with friends tonight", "Schedule a meeting tomorrow afternoon"
        ).forEach { assertTrue(it, ScheduleTimeIntent.needsClock(it, null)) }
    }

    @Test
    fun `몇 시가 있으면 통과한다 — 숫자·한글 수·HH_MM·정오·n분 뒤·am pm`() {
        listOf("내일 아침 7시", "저녁 7시 반", "세 시에 미팅", "14:30 회의", "정오에 점심 약속", "30분 뒤 회의", "2시간 후에 통화", "지금 회의 잡아줘",
            "7pm", "Meeting tomorrow at 3 pm", "at noon", "in 30 minutes", "at 5 o'clock"
        ).forEach { assertFalse(it, ScheduleTimeIntent.needsClock(it, null)) }
        assertFalse("앞 비서 말에 몇 시가 있으면 '응'도 통과", ScheduleTimeIntent.needsClock("응", "내일 저녁 7시로 잡을까요?"))
        assertTrue("앞 비서가 몇 시인지 물은 것은 시각이 아니다", ScheduleTimeIntent.needsClock("내일", "몇 시에 잡을까요?"))
        assertFalse("요일 가드가 날짜만 되물은 뒤의 'Yes' — 시각은 앞 사용자 말에", ScheduleTimeIntent.needsClock("Yes", "Wednesday is Nov 18. Confirm?", "Wednesday at 11am"))
        assertTrue("앞 사용자 말에도 없으면 막는다", ScheduleTimeIntent.needsClock("내일", "몇 시에 잡을까요?", "아침 운동 일정 추가해줘"))
    }

    @Test
    fun `앞 비서 말에 시각이 있으면 막지 않는다`() {
        assertTrue(ScheduleTimeIntent.shouldBlock("응", "메모해 둘까요?"))
        assertFalse(ScheduleTimeIntent.shouldBlock("응", "내일 오후 3시로 잡을까요?"))
    }

    @Test
    fun `앞 사용자 말의 시각은 짧은 긍정일 때만 쓴다 — 다른 요청의 시각은 새지 않는다`() {
        // 0.42.2 휴대폰 확인: 앞 요청 "금요일 오후 3시 치과" 뒤 새 요청이 그 시각으로 통과해 00:00 시트가 떴다.
        assertTrue(ScheduleTimeIntent.needsClock("목요일에 미용실 예약 잡아줘", "일정을 추가하지 않았어요.", "금요일 오후 3시에 치과 예약 잡아줘"))
        assertFalse(ScheduleTimeIntent.needsClock("응", "목요일은 11월 19일이에요. 맞나요?", "목요일 오전 11시 미용실 예약해줘"))
    }
}
