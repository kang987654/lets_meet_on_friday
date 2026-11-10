package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage

/**
 * 일정 추가가 날짜·시각을 근거로 하는지 판정합니다 — 근거 없는 `add_schedule` 은 실행(승인 시트) 전에 막는다 (0.41.11 M1, ADR-030 장치 목록).
 *
 * [WHY] 앞 기록이 있으면 모델이 "주차는 지하 2층 C-14에 했어" 같은 사실을 지금 시각 일정으로 만들었다(회귀 세트 fact h3, exp86b) — 엉뚱한 승인 시트가 뜬다.
 * 시각 없이 일정을 부탁하면 모델은 원래 툴 없이 언제인지 되묻고, 시각을 답하면 그 말에 시각이 있어 통과한다(exp86) — 정상 경로는 그대로다.
 * [WHY] 바로 앞 비서 말도 본다 — "내일 3시로 잡을까요?" 뒤의 "응"은 막으면 안 된다.
 */
object ScheduleTimeIntent {
    private val TIME_WORDS = Regex(
        "\\d+\\s*(시|분|시간)|몇\\s*시|(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열한|열두)\\s*시|" +
            "오전|오후|아침|점심|저녁|밤|새벽|정오|자정|지금|이따|오늘|내일|모레|글피|어제|요일|주말|" +
            "(다음|이번|담)\\s*(주|달)|\\d+\\s*월|\\d+\\s*일|\\d{1,2}:\\d{2}|" +
            "\\b(today|tomorrow|tonight|morning|noon|afternoon|evening|night|now|am|pm|o'?clock|weekend|" +
            "monday|tuesday|wednesday|thursday|friday|saturday|sunday|next\\s+(week|month))\\b|\\d+\\s*(am|pm)\\b",
        RegexOption.IGNORE_CASE
    )

    fun hasTime(text: String?): Boolean = text != null && TIME_WORDS.containsMatchIn(text)

    /** 이번 턴 사용자 말과 바로 앞 비서 말 어디에도 날짜·시각이 없으면 막는다. */
    fun shouldBlock(userText: String, previousAssistant: String?): Boolean = !hasTime(userText) && !hasTime(previousAssistant)

    // exp86c G2 실측 원문(막은 뒤 답이 일정 이야기 없이 "메모해 둘까요?"만 물음, 67토큰).
    const val NO_TIME = "실행하지 않았습니다. 사용자의 말에 날짜나 시각이 없으니 일정 요청이 아닙니다. 일정 이야기는 꺼내지 말고, 알려 준 내용을 확인한 뒤 \"메모해 둘까요?\"라고 물어보세요. 일정을 잡아 달라는 말이었다면 언제인지만 물어보세요."
    const val NO_TIME_EN = "Not executed. The user's message has no date or time, so it is not a scheduling request. Do not bring up the schedule; confirm what they told you and ask \"Would you like me to save this as a note?\". If they did ask to schedule something, ask only when it should be."

    fun noTime(language: AppLanguage = AppLanguage.KO): String = if (language == AppLanguage.EN) NO_TIME_EN else NO_TIME
}
