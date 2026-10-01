package com.kosmos.app.domain.util

import java.time.LocalDate
import java.time.MonthDay

/**
 * 대한민국 공휴일 이름을 돌려줍니다 — 월 캘린더의 빨간 날짜와 선택일 이름 줄(0.33.0).
 *
 * [WHY] 앱 내장 표다(사용자 결정 D-B1) — 기기의 공휴일 캘린더 구독을 이름으로 판별하는 방식은 제조사·언어마다 달라 불안정하고,
 * 오프라인 원칙에도 내장 표가 맞다. 대가는 **매년 갱신**이다.
 * - 양력 고정 공휴일은 모든 해에 적용한다(규칙이 바뀌지 않는 한 결정적).
 * - 음력 공휴일(설·부처님오신날·추석)·대체공휴일·선거일은 해마다 날짜가 달라 [YEARLY] 표에 있는 해만 표시한다.
 *   표에 없는 해는 양력 고정 공휴일만 보인다 — 갱신 시점은 CHANGELOG Known Issue.
 * - 표의 날짜는 한국천문연구원 월력요항으로 다시 확인해야 한다(작성 시점에 원문 대조를 하지 못했다 — CHANGELOG 0.33.0).
 */
object KoreanHolidays {

    private val FIXED: Map<MonthDay, String> = mapOf(
        MonthDay.of(1, 1) to "신정",
        MonthDay.of(3, 1) to "삼일절",
        MonthDay.of(5, 5) to "어린이날",
        MonthDay.of(6, 6) to "현충일",
        MonthDay.of(8, 15) to "광복절",
        MonthDay.of(10, 3) to "개천절",
        MonthDay.of(10, 9) to "한글날",
        MonthDay.of(12, 25) to "성탄절"
    )

    private fun d(text: String) = LocalDate.parse(text)

    /** 해마다 달라지는 공휴일 — 음력·대체공휴일·선거일. */
    private val YEARLY: Map<LocalDate, String> = mapOf(
        // 2026
        d("2026-02-16") to "설날 연휴",
        d("2026-02-17") to "설날",
        d("2026-02-18") to "설날 연휴",
        d("2026-03-02") to "대체공휴일(삼일절)",
        d("2026-05-24") to "부처님오신날",
        d("2026-05-25") to "대체공휴일(부처님오신날)",
        d("2026-06-03") to "전국동시지방선거",
        d("2026-08-17") to "대체공휴일(광복절)",
        d("2026-09-24") to "추석 연휴",
        d("2026-09-25") to "추석",
        d("2026-09-26") to "추석 연휴",
        d("2026-10-05") to "대체공휴일(개천절)",
        // 2027
        d("2027-02-05") to "설날 연휴",
        d("2027-02-06") to "설날",
        d("2027-02-07") to "설날 연휴",
        d("2027-02-08") to "대체공휴일(설날)",
        d("2027-05-13") to "부처님오신날",
        d("2027-08-16") to "대체공휴일(광복절)",
        d("2027-09-14") to "추석 연휴",
        d("2027-09-15") to "추석",
        d("2027-09-16") to "추석 연휴",
        d("2027-10-04") to "대체공휴일(개천절)",
        d("2027-10-11") to "대체공휴일(한글날)",
        d("2027-12-27") to "대체공휴일(성탄절)"
    )

    /** 표가 있는 해 — 이 밖의 해는 양력 고정 공휴일만. */
    val coveredYears: IntRange = 2026..2027

    /** [date] 의 공휴일 이름, 공휴일이 아니면 null. */
    fun nameOf(date: LocalDate): String? = YEARLY[date] ?: FIXED[MonthDay.from(date)]
}
