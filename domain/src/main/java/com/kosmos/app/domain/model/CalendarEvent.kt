package com.kosmos.app.domain.model

/**
 * 화면·툴이 공유하는 일정 한 건입니다.
 *
 * @property source 어디서 온 일정인지 — 월 캘린더가 점 색으로 구분한다(0.28.0).
 *   [WHY] 기본값 [Source.APP] — 앱 일정 생성처가 여럿이고 기기 캘린더 매핑(AndroidCalendarTool)만
 *   [Source.DEVICE] 를 명시하면 된다.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val startIso: String,
    val endIso: String,
    val location: String? = null,
    val description: String? = null,
    val source: Source = Source.APP
) {
    /** 일정 출처. */
    enum class Source { APP, DEVICE }
}
