package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 일정 시작 시각이 이미 지났는지 판정합니다 — 날짜 낱말 없이 시각만 말했는데 그 시각이 오늘 이미 지났으면 막고 내일인지 묻게 한다 (0.42.5 M1).
 *
 * [WHY] 시스템 지시 날짜 규칙이 "A time with no date word means today." 라, 오후에 "7시"라고 하면 이미 지난 오늘 07:00 으로 잡았다(11-17 휴대폰).
 * 규칙을 바꾸면 앞부분이 늘어 대신 가드로 — exp99(지금 15:20, 지난 시각 문항 6 × 2회): 가드 없음 내일로 바르게 0/12(오늘 과거로 12) → P1 **12/12**,
 * 대조(오후 5시 · "오늘 아침 9시에 있었던") 4/4 그대로. 모델이 "오늘 9시는 지났어요. 내일 9시로 잡아드릴까요?"라고 묻고 "응"이면 내일로 넣는다.
 * 사용자가 "오늘"·"어제"·날짜를 말했으면 지난 일을 기록하려는 것이라 막지 않는다. 날짜만 있는 종일 일정도 보지 않는다.
 */
object SchedulePastIntent {
    private val DATE_WORD = Regex("오늘|어제|그저께|아까|방금|\\d+\\s*월|\\d+\\s*일|\\b(today|yesterday|earlier)\\b", RegexOption.IGNORE_CASE)
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** 시작 시각이 지났고 사용자가 날짜를 말하지 않았으면 모델에게 돌려줄 문구, 아니면 null. 시각 판정은 [now]·[zoneId] 인자(AGENTS §2-④). */
    fun pastGuidance(
        userText: String,
        startTime: String?,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        language: AppLanguage = AppLanguage.KO
    ): String? {
        if (startTime.isNullOrBlank() || IsoDateTimeParser.isDateOnly(startTime)) return null
        val start = IsoDateTimeParser.toEpochMillis(startTime, zoneId) ?: return null
        if (start >= now || DATE_WORD.containsMatchIn(userText)) return null
        val hm = Instant.ofEpochMilli(start).atZone(zoneId).toLocalTime().format(HM)
        // exp99 P1 원문(가장 짧은 판 — P2·P3 도 12/12).
        return if (language == AppLanguage.EN) {
            "Not executed. $hm today has already passed. Ask the user whether they mean tomorrow at $hm."
        } else {
            "실행하지 않았습니다. 오늘 ${hm}은 이미 지났습니다. 내일 ${hm}인지 사용자에게 물어보세요."
        }
    }
}
