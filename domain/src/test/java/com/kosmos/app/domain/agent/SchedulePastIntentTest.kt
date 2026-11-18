package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * [SchedulePastIntentTest]
 * 지난 시각 일정 가드(0.42.5 M1) — 날짜 없이 말한 시각이 오늘 이미 지났으면 막고, 미래·날짜를 말한 경우·종일 일정은 통과.
 */
class SchedulePastIntentTest {
    private val utc = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 9, 15, 20).toInstant(utc).toEpochMilli()

    @Test
    fun `오후에 오전 시각을 말하면 오늘 그 시각은 지났다며 내일인지 묻게 한다`() {
        assertEquals(
            "실행하지 않았습니다. 오늘 07:00은 이미 지났습니다. 내일 07:00인지 사용자에게 물어보세요.",
            SchedulePastIntent.pastGuidance("7시", "2026-11-09T07:00:00", now, utc)
        )
        assertEquals(
            "Not executed. 09:00 today has already passed. Ask the user whether they mean tomorrow at 09:00.",
            SchedulePastIntent.pastGuidance("Schedule a meeting at 9am", "2026-11-09T09:00:00", now, utc, AppLanguage.EN)
        )
    }

    @Test
    fun `가운데가 공백인 시각도 실행기처럼 T 로 바꾸면 막는다`() {
        // 0.42.7 휴대폰: 모델이 "2026-11-18 07:00:00" 모양을 내자 파서가 못 읽어 통과했다 — 호출부(BaseAgent)가 바꿔 넘긴다.
        assertNull("바꾸지 않은 원문은 파서가 못 읽는다(호출부 정규화가 필요한 이유)", SchedulePastIntent.pastGuidance("7시", "2026-11-09 07:00:00", now, utc))
        assertEquals(
            "실행하지 않았습니다. 오늘 07:00은 이미 지났습니다. 내일 07:00인지 사용자에게 물어보세요.",
            SchedulePastIntent.pastGuidance("7시", "2026-11-09 07:00:00".replace(' ', 'T'), now, utc)
        )
    }

    @Test
    fun `미래 시각 · 날짜를 말한 경우 · 종일 일정 · 시각 없음은 막지 않는다`() {
        assertNull(SchedulePastIntent.pastGuidance("오후 5시에 회의 잡아줘", "2026-11-09T17:00:00", now, utc))
        assertNull(SchedulePastIntent.pastGuidance("7시", "2026-11-10T07:00:00", now, utc))
        assertNull("사용자가 오늘이라고 했으면 지난 일을 기록하려는 것", SchedulePastIntent.pastGuidance("오늘 아침 9시에 회의 있었는데 기록해줘", "2026-11-09T09:00:00", now, utc))
        assertNull(SchedulePastIntent.pastGuidance("11월 9일 9시 회의 기록해줘", "2026-11-09T09:00:00", now, utc))
        assertNull("날짜를 말했으면 그 날짜를 믿는다", SchedulePastIntent.pastGuidance("다음주 월요일 10시 팀 회의 일정 추가해줘", "2026-11-02T10:00:00", now, utc))
        assertNull(SchedulePastIntent.pastGuidance("내일 점심 12시 약속 잡아줘", "2026-11-09T12:00:00", now, utc))
        assertNull(SchedulePastIntent.pastGuidance("휴가 일정 넣어줘", "2026-11-09", now, utc))
        assertNull(SchedulePastIntent.pastGuidance("7시", null, now, utc))
    }
}
