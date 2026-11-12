package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * [ScheduleWeekdayIntent]
 * 이번 턴 사용자 말의 요일과 `add_schedule` 의 `start_time` 날짜 요일이 어긋나는지 판정합니다 (0.41.13 M1, ADR-030 장치 목록).
 *
 * ### Role
 * - 사용자 발화에 요일이 **정확히 하나** 들어 있고 `start_time` 날짜의 요일이 그와 다르면, 실행(승인 시트) 전에 막을 안내 문구를 돌려줍니다.
 * - 요일이 없거나 둘 이상이거나, `start_time` 이 파싱되지 않거나, 요일이 일치하면 `null` 을 돌려줘 그대로 통과시킵니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Agent) — 순수 판정, `ToolCallGuards` 에서 `ScheduleTimeIntent` 바로 뒤에 호출
 * - **Dependencies**: java.time, [AppLanguage], [IsoDateTimeParser]
 *
 * ### Key Flow
 * 1. [extractSingleWeekday] 로 이번 턴 사용자 말에서 고유 요일 1개를 뽑습니다(한/영 함께, 없거나 2개 이상이면 통과).
 * 2. [IsoDateTimeParser.toLocalDate] 로 `start_time` 의 달력 날짜를 읽습니다(파싱 실패면 기존 인자 검증에 맡기고 통과).
 * 3. 날짜의 요일이 발화 요일과 같으면 통과합니다(어느 주인지는 판정하지 않음).
 * 4. 다르면 오늘 이후 가장 가까운 해당 요일 날짜를 [IsoDateTimeParser.monthDayKorean] 으로 표기한 안내 문구를 반환합니다.
 *
 * [WHY] 두 턴 일정 요청("치과 예약 잡아줘" → "수요일 오전 11시")에서 모델이 `start_time` 을 오늘 날짜로 보내는 경우(exp88·exp89)를
 * 승인 시트 전에 막는다. 앱이 날짜를 몰래 고치면 승인 시트와 모델 기록이 어긋나므로(ADR-030) 오류 결과로 바른 날짜를 알려 준다.
 */
object ScheduleWeekdayIntent {

    private val KO_WEEKDAY = Regex("([월화수목금토일])(?:요일|욜)")
    private val EN_WEEKDAY = Regex(
        "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * 발화에서 언급된 고유 요일이 정확히 하나일 때 그 [DayOfWeek] 를 반환합니다. 없거나 둘 이상이면 `null`.
     */
    fun extractSingleWeekday(text: String?): DayOfWeek? {
        if (text.isNullOrBlank()) return null
        val found = LinkedHashSet<DayOfWeek>()
        KO_WEEKDAY.findAll(text).forEach { m ->
            koCharToDayOfWeek(m.groupValues[1].firstOrNull())?.let { found.add(it) }
        }
        EN_WEEKDAY.findAll(text).forEach { m ->
            enWordToDayOfWeek(m.groupValues[1])?.let { found.add(it) }
        }
        return if (found.size == 1) found.first() else null
    }

    /**
     * `start_time` 날짜의 요일이 이번 턴 사용자 말의 단일 요일과 다르면 차단 안내 문구를, 통과면 `null` 을 반환합니다.
     *
     * [WHY] 시각 판정은 `now`·`zoneId` 를 인자로 받는다(AGENTS §2-④). 안내 문구 원문은 exp89 G1 실측으로 고정했다(KO 27토큰 · EN 18토큰).
     */
    fun mismatchGuidance(
        userText: String,
        startTime: String?,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        language: AppLanguage = AppLanguage.KO
    ): String? {
        val wanted = extractSingleWeekday(userText) ?: return null
        if (startTime.isNullOrBlank()) return null
        val parsedDate = IsoDateTimeParser.toLocalDate(startTime, zoneId) ?: return null
        if (parsedDate.dayOfWeek == wanted) return null

        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return formatGuidance(today, wanted, zoneId, language)
    }

    /**
     * 기준일([today]) 이후 가장 가까운 [wanted] 요일을 계산해 한/영 안내 문구를 조립합니다.
     */
    fun formatGuidance(
        today: LocalDate,
        wanted: DayOfWeek,
        zoneId: ZoneId = ZoneId.systemDefault(),
        language: AppLanguage = AppLanguage.KO
    ): String {
        val nearest = today.with(TemporalAdjusters.nextOrSame(wanted))
        val nearestEpochMs = nearest.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val monthDay = IsoDateTimeParser.monthDayKorean(nearestEpochMs, zoneId, language)
        return if (language == AppLanguage.EN) {
            val wdEn = wanted.getDisplayName(TextStyle.FULL, Locale.US)
            "Not executed. $wdEn is $monthDay. Add the event again with that date."
        } else {
            val wdKo = "${IsoDateTimeParser.weekdayKorean(wanted)}요일"
            "실행하지 않았습니다. ${wdKo}은 ${monthDay}입니다. 그 날짜로 다시 추가하세요."
        }
    }

    private fun koCharToDayOfWeek(ch: Char?): DayOfWeek? = when (ch) {
        '월' -> DayOfWeek.MONDAY
        '화' -> DayOfWeek.TUESDAY
        '수' -> DayOfWeek.WEDNESDAY
        '목' -> DayOfWeek.THURSDAY
        '금' -> DayOfWeek.FRIDAY
        '토' -> DayOfWeek.SATURDAY
        '일' -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun enWordToDayOfWeek(word: String): DayOfWeek? = when (word.lowercase(Locale.US)) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }
}
