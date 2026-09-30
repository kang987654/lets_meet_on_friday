package com.kosmos.app.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * [IsoDateTimeParser]
 * ISO-8601 계열 문자열을 관용적으로 파싱하는 공용 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Util) — Pure Kotlin, UseCase와 ViewModel이 함께 사용
 * - **Dependencies**: java.time
 *
 * ### Key Flow
 * 1. 오프셋 포함(`+09:00`) → UTC(`Z`) → 로컬 일시 → 날짜-only 순서로 폴백 파싱합니다.
 * 2. 어떤 포맷에도 맞지 않으면 null을 반환해 호출부가 해당 항목을 건너뛸 수 있게 합니다.
 *
 * [WHY] 모델이 생성하는 일정 시각과 기기 캘린더가 돌려주는 시각의 포맷이 섞이기 때문에,
 * 파싱 규칙을 한 곳에서 관리해 화면·유즈케이스 간 판정이 어긋나지 않도록 한다.
 */
object IsoDateTimeParser {

    fun toEpochMillis(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(zoneId).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso).atStartOfDay(zoneId).toInstant().toEpochMilli() }.getOrNull()

    /** 기기 시간대 기준 달력 날짜를 반환합니다. 날짜 단위 필터링에 사용합니다. */
    fun toLocalDate(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
        toEpochMillis(iso, zoneId)?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        }

    /**
     * 사람이 읽는 한국어 표기 — "8월 20일 오후 4:00". 파싱 실패 시 null.
     * [WHY] ISO 원문("2026-08-20T16:00:00")이 화면·모델 답변에 그대로 노출되던 것을
     * 한 곳에서 바꾼다 (2026-08-15 사용자 피드백) — 표기 규칙이 흩어지면 화면마다 어긋난다.
     */
    fun toDisplayKorean(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            "%d월 %d일 %s".format(dt.monthValue, dt.dayOfMonth, timeKorean(dt.hour, dt.minute))
        }

    /**
     * 시각만의 한국어 표기 — "오전 9:00". 파싱 실패 시 null.
     * [WHY] 같은 날짜 문맥(일정 화면의 날짜 그룹, 위젯의 "오늘")에서는 날짜를 반복하지
     * 않는다 — CalendarScreen 이 같은 로직을 사설 복제하고 있던 것을 여기로 흡수(0.22.0).
     */
    fun toDisplayTimeKorean(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            timeKorean(dt.hour, dt.minute)
        }

    /**
     * 날짜만의 한국어 표기 — "9월 30일 (수)". 파싱 실패 시 null.
     * [WHY] 일정 승인 카드가 사설 파서로 `LocalDate.toString()`("2026-09-30")을 그대로 띄우고
     * 있었다 — ISO 노출 금지 규칙(AGENTS §4-7)의 마지막 구멍이었다.
     */
    fun toDisplayDateKorean(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            "%d월 %d일 (%s)".format(dt.monthValue, dt.dayOfMonth, weekdayKorean(dt.dayOfWeek))
        }

    /** "수" 처럼 한 글자 요일. */
    fun weekdayKorean(dayOfWeek: java.time.DayOfWeek): String =
        "월화수목금토일"[dayOfWeek.value - 1].toString()

    private fun timeKorean(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "오후" else "오전"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "%s %d:%02d".format(amPm, hour12, minute)
    }
}
