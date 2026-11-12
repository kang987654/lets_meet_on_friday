package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ScheduleWeekdayIntentTest]
 * 요일 날짜 가드 판정 단위 테스트 (0.41.13 M1) — 시간대 UTC 명시(AGENTS §2-④).
 */
class ScheduleWeekdayIntentTest {

    private val utc = ZoneOffset.UTC
    // 2026-11-09 월요일 10:53 UTC
    private val mondayNow = LocalDate.of(2026, 11, 9).atTime(10, 53).toInstant(utc).toEpochMilli()
    // 2026-11-12 목요일 10:53 UTC
    private val thursdayNow = LocalDate.of(2026, 11, 12).atTime(10, 53).toInstant(utc).toEpochMilli()

    @Test
    fun `단일 요일과 start_time 요일이 일치하면 통과한다 — 어느 주인지는 판정하지 않는다`() {
        // 이번 주 금요일(11-13)
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "금요일 저녁 7시",
                startTime = "2026-11-13T19:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
        // 다음 주 화요일(11-17)도 화요일이므로 통과
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "다음 주 화요일 오후 3시",
                startTime = "2026-11-17T15:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
        // 축약형('금욜') 일치
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "금욜 저녁 7시",
                startTime = "2026-11-13T19:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
    }

    @Test
    fun `단일 요일과 start_time 요일이 다르면 오늘 이후 가장 가까운 그 요일 날짜로 막는다`() {
        // 월요일(11-09) 기준 "금요일 저녁 7시"인데 오늘 날짜(11-09 월)로 보낸 경우 → 11월 13일 안내
        val msgMon = ScheduleWeekdayIntent.mismatchGuidance(
            userText = "금요일 저녁 7시",
            startTime = "2026-11-09T19:00:00",
            now = mondayNow,
            zoneId = utc
        )
        assertEquals("실행하지 않았습니다. 금요일은 11월 13일입니다. 그 날짜로 다시 추가하세요.", msgMon)

        // 목요일(11-12) 기준 "수요일 오전 11시"인데 오늘 날짜(11-12 목)로 보낸 경우 → 다음 수요일 11월 18일 안내
        val msgThu = ScheduleWeekdayIntent.mismatchGuidance(
            userText = "수요일 오전 11시",
            startTime = "2026-11-12T11:00:00",
            now = thursdayNow,
            zoneId = utc
        )
        assertEquals("실행하지 않았습니다. 수요일은 11월 18일입니다. 그 날짜로 다시 추가하세요.", msgThu)
    }

    @Test
    fun `요일이 둘 이상이면 통과한다`() {
        assertNull(ScheduleWeekdayIntent.extractSingleWeekday("금요일이나 토요일 저녁 7시"))
        assertNull(ScheduleWeekdayIntent.extractSingleWeekday("Monday or Wednesday at 2pm"))
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "금요일에서 토요일로 넘어가는 일정",
                startTime = "2026-11-09T19:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
    }

    @Test
    fun `요일이 없으면 통과한다 — 일반 명사 속 글자도 요일로 오인하지 않는다`() {
        assertNull(ScheduleWeekdayIntent.extractSingleWeekday("내일 오후 3시"))
        assertNull(ScheduleWeekdayIntent.extractSingleWeekday("금고 점검 일정, 월세 납부일"))
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "내일 오후 3시",
                startTime = "2026-11-09T15:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
    }

    @Test
    fun `start_time 파싱 실패거나 비어 있으면 통과한다`() {
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "금요일 저녁 7시",
                startTime = "not-an-iso-date",
                now = mondayNow,
                zoneId = utc
            )
        )
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "금요일 저녁 7시",
                startTime = null,
                now = mondayNow,
                zoneId = utc
            )
        )
    }

    @Test
    fun `영어 모드에서도 요일 추출과 안내 문구가 동작한다`() {
        assertEquals(DayOfWeek.WEDNESDAY, ScheduleWeekdayIntent.extractSingleWeekday("Wednesday at 11am"))
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "Friday at 7pm",
                startTime = "2026-11-13T19:00:00",
                now = mondayNow,
                zoneId = utc,
                language = AppLanguage.EN
            )
        )
        val msgEn = ScheduleWeekdayIntent.mismatchGuidance(
            userText = "Wednesday at 11am",
            startTime = "2026-11-12T11:00:00",
            now = thursdayNow,
            zoneId = utc,
            language = AppLanguage.EN
        )
        assertEquals("Not executed. Wednesday is Nov 18. Add the event again with that date.", msgEn)
    }

    @Test
    fun `오늘이 그 요일이고 오늘 날짜로 보냈으면 요일이 같아 통과한다`() {
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "월요일 오후 2시",
                startTime = "2026-11-09T14:00:00",
                now = mondayNow,
                zoneId = utc
            )
        )
        assertNull(
            ScheduleWeekdayIntent.mismatchGuidance(
                userText = "Thursday at 2pm",
                startTime = "2026-11-12T14:00:00",
                now = thursdayNow,
                zoneId = utc,
                language = AppLanguage.EN
            )
        )
    }
}
