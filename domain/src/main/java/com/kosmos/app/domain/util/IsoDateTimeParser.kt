package com.kosmos.app.domain.util

import com.kosmos.app.core.common.AppLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * [IsoDateTimeParser]
 * ISO-8601 계열 문자열을 관용적으로 파싱하는 공용 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Util) — Pure Kotlin, UseCase와 ViewModel이 함께 사용
 * - **Dependencies**: java.time, [AppLanguage]
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

    /**
     * 시각 없이 날짜만 있는 ISO("2026-08-15")인가 — 종일 일정의 표기 규약(0.33.0, AndroidCalendarTool 이 종일 일정을 이 형식으로 낸다).
     */
    fun isDateOnly(iso: String): Boolean = runCatching { LocalDate.parse(iso) }.isSuccess

    /** 기기 시간대 기준 달력 날짜를 반환합니다. 날짜 단위 필터링에 사용합니다. */
    fun toLocalDate(iso: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
        toEpochMillis(iso, zoneId)?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
        }

    /**
     * 사람이 읽는 일시 표기 — 한국어 `"8월 20일 오후 4:00"`, 영어 `"Aug 20, 4:00 PM"`. 파싱 실패 시 null.
     * [WHY] ISO 원문("2026-08-20T16:00:00")이 화면·모델 답변에 그대로 노출되던 것을
     * 한 곳에서 바꾼다 (2026-08-15 사용자 피드백) — 표기 규칙이 흩어지면 화면마다 어긋난다.
     */
    fun toDisplayKorean(
        iso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            // [WHY] 종일 일정은 시각이 없다 — "오전 12:00"으로 읽히면 자정 일정으로 오해된다(0.33.0).
            if (lang == AppLanguage.EN) {
                val time = if (isDateOnly(iso)) ALL_DAY_EN else timeEnglish(dt.hour, dt.minute)
                "%s %d, %s".format(Locale.US, monthShortEn(dt.monthValue), dt.dayOfMonth, time)
            } else {
                val time = if (isDateOnly(iso)) ALL_DAY else timeKorean(dt.hour, dt.minute)
                "%d월 %d일 %s".format(Locale.KOREAN, dt.monthValue, dt.dayOfMonth, time)
            }
        }

    /**
     * 시작·종료 일시 범위 표기 — 같은 날 시간대 일정이면 `"8월 20일 오후 4:00 ~ 오후 5:00"` / `"Aug 20, 4:00 PM ~ 5:00 PM"`.
     * 다른 날에 걸친 일정이면 종료 쪽에도 날짜를 붙이고(`"8월 20일 오후 11:00 ~ 8월 21일 오전 1:00"`),
     * 종일 일정이거나 시작·종료 시각이 같으면 시작 표기만 반환합니다. 파싱 실패 시 null.
     *
     * [WHY] 일정 툴 결과와 아침 브리핑 재료에 시작 시각만 들어가면 "회의 몇 시에 끝나?"에 답할 수 없다 (0.40.6 M2, exp73).
     */
    fun toDisplayRangeKorean(
        startIso: String,
        endIso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String? {
        val startDisplay = toDisplayKorean(startIso, zoneId, lang) ?: return null
        if (isDateOnly(startIso) || startIso == endIso) return startDisplay
        val startMs = toEpochMillis(startIso, zoneId) ?: return startDisplay
        val endMs = toEpochMillis(endIso, zoneId) ?: return startDisplay
        if (endMs <= startMs) return startDisplay
        val startDt = Instant.ofEpochMilli(startMs).atZone(zoneId)
        val endDt = Instant.ofEpochMilli(endMs).atZone(zoneId)
        val endPart = if (startDt.toLocalDate() == endDt.toLocalDate()) {
            toDisplayTimeKorean(endIso, zoneId, lang) ?: return startDisplay
        } else {
            toDisplayKorean(endIso, zoneId, lang) ?: return startDisplay
        }
        return "$startDisplay ~ $endPart"
    }

    /**
     * 글 속의 ISO 날짜·일시를 사람이 읽는 표기로 — 한국어 `"10월 26일 오후 4:30"`, 영어 `"Oct 26, 4:30 PM"`.
     * 날짜만이면 `"10월 26일"` / `"Oct 26"`. 날짜로 읽히지 않는 숫자 묶음("1234-56-78")·전화번호·송장번호는 그대로 둔다.
     *
     * [WHY] 모델은 툴 인자로 ISO 를 계산하고 그 값을 답에 그대로 옮겨 적는다(exp64 I 1/12) — 프롬프트 대신 화면에 닿기 전에 바꾼다(0.38.9 결정 2).
     */
    fun humanizeInText(
        text: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String =
        if (!ISO_IN_TEXT.containsMatchIn(text)) text else ISO_IN_TEXT.replace(text) { m ->
            val iso = m.value
            if (isDateOnly(iso)) {
                runCatching { LocalDate.parse(iso) }.getOrNull()?.let {
                    if (lang == AppLanguage.EN) {
                        "%s %d".format(Locale.US, monthShortEn(it.monthValue), it.dayOfMonth)
                    } else {
                        "%d월 %d일".format(Locale.KOREAN, it.monthValue, it.dayOfMonth)
                    }
                } ?: iso
            } else {
                toDisplayKorean(iso, zoneId, lang) ?: iso
            }
        }

    /**
     * 스트리밍 중인 글 — 끝에 덜 쓴 일시("2026-10-26T16:3")가 있으면 다 쓸 때까지 그 꼬리를 보이지 않고 나머지를 [humanizeInText] 로.
     * [WHY] 꼬리를 그대로 두면 ISO 원문이 한 순간 보였다가 한국어로 바뀐다. 최종 답은 [humanizeInText] 가 통째로 처리한다.
     */
    fun humanizeStreaming(
        text: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String =
        humanizeInText(text.replace(ISO_TAIL, ""), zoneId, lang)

    /**
     * 시각만의 표기 — 한국어 `"오전 9:00"`, 영어 `"9:00 AM"`. 파싱 실패 시 null.
     * [WHY] 같은 날짜 문맥(일정 화면의 날짜 그룹, 위젯의 "오늘")에서는 날짜를 반복하지
     * 않는다 — CalendarScreen 이 같은 로직을 사설 복제하고 있던 것을 여기로 흡수(0.22.0).
     */
    fun toDisplayTimeKorean(
        iso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            if (lang == AppLanguage.EN) {
                if (isDateOnly(iso)) ALL_DAY_EN else timeEnglish(dt.hour, dt.minute)
            } else {
                if (isDateOnly(iso)) ALL_DAY else timeKorean(dt.hour, dt.minute)
            }
        }

    /**
     * 날짜만의 표기 — 한국어 `"9월 30일 (수)"`, 영어 `"Sep 30 (Wed)"`. 파싱 실패 시 null.
     * [WHY] 일정 승인 카드가 사설 파서로 `LocalDate.toString()`("2026-09-30")을 그대로 띄우고
     * 있었다 — ISO 노출 금지 규칙(AGENTS §4-7)의 마지막 구멍이었다.
     */
    fun toDisplayDateKorean(
        iso: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String? =
        toEpochMillis(iso, zoneId)?.let {
            val dt = Instant.ofEpochMilli(it).atZone(zoneId)
            if (lang == AppLanguage.EN) {
                "%s %d (%s)".format(Locale.US, monthShortEn(dt.monthValue), dt.dayOfMonth, weekdayShortEn(dt.dayOfWeek))
            } else {
                "%d월 %d일 (%s)".format(Locale.KOREAN, dt.monthValue, dt.dayOfMonth, weekdayKorean(dt.dayOfWeek))
            }
        }

    /**
     * epoch ms 의 `"9월 30일"` / `"Sep 30"` 표기.
     * [WHY] 같은 `ofPattern("M월 d일")` 이 채팅 칩·타임라인 구분선·드로어·에피소드 시트에 각자
     * 복제돼 있었다 — ISO 가 아니라 epoch 을 받을 뿐 표기 규칙은 여기가 단일 출처다(AGENTS §4-7).
     */
    fun monthDayKorean(
        epochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return if (lang == AppLanguage.EN) {
            "%s %d".format(Locale.US, monthShortEn(dt.monthValue), dt.dayOfMonth)
        } else {
            "%d월 %d일".format(Locale.KOREAN, dt.monthValue, dt.dayOfMonth)
        }
    }

    /** epoch ms 의 `"오후 4:00"` / `"4:00 PM"` 표기. */
    fun timeKorean(
        epochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return if (lang == AppLanguage.EN) timeEnglish(dt.hour, dt.minute) else timeKorean(dt.hour, dt.minute)
    }

    /** 하루 중 분(0..1439)의 `"오전 8:30"` / `"8:30 AM"` 표기 — 설정의 브리핑 시각(0.38.3). */
    fun clockKorean(minutesOfDay: Int, lang: AppLanguage = AppLanguage.KO): String =
        if (lang == AppLanguage.EN) timeEnglish(minutesOfDay / 60, minutesOfDay % 60)
        else timeKorean(minutesOfDay / 60, minutesOfDay % 60)

    /**
     * 날짜 구분 라벨 — 오늘/어제/"9월 28일" (영어: Today/Yesterday/"Sep 28").
     * [WHY] [today] 를 인자로 받는다(AGENTS §2-④) — 판정을 벽시계에 묶으면 자정 경계에서 테스트가 갈린다.
     */
    fun dayLabelKorean(
        date: LocalDate,
        today: LocalDate,
        lang: AppLanguage = AppLanguage.KO
    ): String = if (lang == AppLanguage.EN) {
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> "%s %d".format(Locale.US, monthShortEn(date.monthValue), date.dayOfMonth)
        }
    } else {
        when (date) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> "%d월 %d일".format(Locale.KOREAN, date.monthValue, date.dayOfMonth)
        }
    }

    /**
     * epoch ms 의 `"9월 30일 수요일"` / `"Wednesday, Sep 30"` 표기 — 아침 브리핑 프롬프트와 홈 위젯 헤더.
     * [WHY] 두 곳이 `ofPattern("M월 d일 EEEE", Locale.KOREAN)` 을 각자 들고 있었다. 브리핑 쪽은
     * **모델 입력**이라 출력 바이트가 바뀌면 안 된다 — 결과가 그 패턴과 같다는 것을 테스트가 고정한다.
     */
    fun longDateKorean(
        epochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zoneId)
        return if (lang == AppLanguage.EN) {
            val fullDay = dt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            "%s, %s %d".format(Locale.US, fullDay, monthShortEn(dt.monthValue), dt.dayOfMonth)
        } else {
            "%d월 %d일 %s요일".format(Locale.KOREAN, dt.monthValue, dt.dayOfMonth, weekdayKorean(dt.dayOfWeek))
        }
    }

    /** `"16일 금"` / `"Fri 16"` — 일정 화면 "다가오는 일정"의 날짜 칸(0.38.3). */
    fun dayWeekdayKorean(date: LocalDate, lang: AppLanguage = AppLanguage.KO): String =
        if (lang == AppLanguage.EN) {
            "%s %d".format(Locale.US, weekdayShortEn(date.dayOfWeek), date.dayOfMonth)
        } else {
            "%d일 %s".format(Locale.KOREAN, date.dayOfMonth, weekdayKorean(date.dayOfWeek))
        }

    /** `"수"` (영어 `"Wed"`). */
    fun weekdayKorean(dayOfWeek: java.time.DayOfWeek, lang: AppLanguage = AppLanguage.KO): String =
        if (lang == AppLanguage.EN) weekdayShortEn(dayOfWeek)
        else "월화수목금토일"[dayOfWeek.value - 1].toString()

    private fun weekdayShortEn(dayOfWeek: java.time.DayOfWeek): String =
        MONTH_WEEKDAY_EN_DAYS[dayOfWeek.value - 1]

    private fun monthShortEn(monthValue: Int): String =
        MONTH_SHORT_EN[monthValue - 1]

    private fun timeKorean(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "오후" else "오전"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "%s %d:%02d".format(Locale.KOREAN, amPm, hour12, minute)
    }

    private fun timeEnglish(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(Locale.US, hour12, minute, amPm)
    }

    private const val ALL_DAY = "종일"
    private const val ALL_DAY_EN = "All day"

    private val MONTH_SHORT_EN = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    private val MONTH_WEEKDAY_EN_DAYS = arrayOf(
        "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    )

    // 앞뒤가 숫자·하이픈이면 더 긴 번호의 일부라 건드리지 않는다. 날짜 뒤 "T"가 남으면(스트리밍 중 덜 쓴 일시) 다 쓸 때까지 둔다.
    private val ISO_IN_TEXT = Regex("(?<![\\d-])\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})?)?(?![\\d\\-T:])")
    private val ISO_TAIL = Regex("(?<![\\d-])\\d{4}-[\\d\\-T:.Z+]*$")
}
