package com.kosmos.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/**
 * [IsoDateTimeParser] 의 4단 폴백 계약을 고정합니다.
 *
 * [WHY] 이 파서는 툴 인자 검증(ToolArguments)과 기기 캘린더 동기화(AndroidCalendarTool)가
 * 공유하는 판정 기준이다. 특히 **오프셋 없는 로컬 일시**(`2026-08-07T15:00:00`)는 툴 선언이
 * 모델에게 지시하는 바로 그 형식인데, 과거 AndroidCalendarTool 의 자체 파서가 이 형식을
 * 지원하지 않아 정상 일정까지 전부 '현재 시각'으로 기기 캘린더에 들어갔다 — 폴백 하나가
 * 빠지는 것이 어떤 결함이 되는지를 이 테스트가 못박는다.
 */
class IsoDateTimeParserTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `오프셋 포함 일시를 파싱한다`() {
        val ms = IsoDateTimeParser.toEpochMillis("2026-08-17T10:00:00+09:00", seoul)
        assertNotNull(ms)
    }

    @Test
    fun `UTC Z 표기를 파싱한다`() {
        val ms = IsoDateTimeParser.toEpochMillis("2026-08-17T01:00:00Z", seoul)
        assertNotNull(ms)
    }

    @Test
    fun `오프셋 없는 로컬 일시를 파싱한다 - 툴 선언 예시 형식`() {
        val ms = IsoDateTimeParser.toEpochMillis("2026-08-07T15:00:00", seoul)
        assertNotNull(ms)
    }

    @Test
    fun `날짜만 있는 값은 자정으로 파싱한다`() {
        val ms = IsoDateTimeParser.toEpochMillis("2026-08-17", seoul)
        assertNotNull(ms)
        assertEquals(
            "같은 날짜의 로컬 자정과 같아야 한다",
            IsoDateTimeParser.toEpochMillis("2026-08-17T00:00:00", seoul),
            ms
        )
    }

    @Test
    fun `오프셋 표기와 로컬 표기가 같은 순간이면 같은 epoch 를 돌려준다`() {
        assertEquals(
            IsoDateTimeParser.toEpochMillis("2026-08-17T10:00:00+09:00", seoul),
            IsoDateTimeParser.toEpochMillis("2026-08-17T10:00:00", seoul)
        )
    }

    @Test
    fun `깨진 타임스탬프는 null 이다 - 실기기 관측값`() {
        // 2026-08-13 실기기에서 모델이 생성한 깨진 인자 원문 그대로다.
        assertNull(IsoDateTimeParser.toEpochMillis("202026-081717T010000", seoul))
    }

    @Test
    fun `시각만 있는 값은 null 이다`() {
        assertNull(IsoDateTimeParser.toEpochMillis("15:00", seoul))
    }

    @Test
    fun `임의 문자열은 null 이다`() {
        assertNull(IsoDateTimeParser.toEpochMillis("다음주 월요일", seoul))
    }

    @Test
    fun `시각만 표기는 오전-오후와 12시간제를 따른다`() {
        assertEquals("오전 9:05", IsoDateTimeParser.toDisplayTimeKorean("2026-08-28T09:05:00", seoul))
        assertEquals("오후 3:00", IsoDateTimeParser.toDisplayTimeKorean("2026-08-28T15:00:00", seoul))
        assertEquals("오후 12:00", IsoDateTimeParser.toDisplayTimeKorean("2026-08-28T12:00:00", seoul))
        assertEquals("오전 12:30", IsoDateTimeParser.toDisplayTimeKorean("2026-08-28T00:30:00", seoul))
        assertNull(IsoDateTimeParser.toDisplayTimeKorean("깨진 값", seoul))
    }

    @Test
    fun `날짜 포함 표기와 시각만 표기의 시각 부분이 일치한다`() {
        // [WHY] 두 함수가 갈라지면 화면(일정)과 위젯의 같은 일정이 다른 시각으로 보인다.
        val full = IsoDateTimeParser.toDisplayKorean("2026-08-20T16:00:00", seoul)
        val timeOnly = IsoDateTimeParser.toDisplayTimeKorean("2026-08-20T16:00:00", seoul)
        assertEquals("8월 20일 오후 4:00", full)
        assertEquals("오후 4:00", timeOnly)
    }
}
