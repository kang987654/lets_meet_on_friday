package com.kosmos.app.feature.settings

import androidx.annotation.DrawableRes
import com.kosmos.app.R
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.tool.ToolNames

/** 활동 기록 한 줄의 표시값 — 종류 아이콘 · 한국어 이름 · 내용 한 줄. */
data class AuditDisplay(@DrawableRes val icon: Int, val title: String, val summary: String)

/**
 * 감사 기록 → 화면 표기(0.38.3 M3). 기록 원문(`Tool: …`·`Result: {JSON}`·`Prompt:/Output:`)은 그대로 저장하고
 * 화면에서만 바꾼다 — 원문은 행을 눌러 펼치면 보인다.
 *
 * [WHY] enum 이름(`TOOL_CALL`)과 툴 클래스 이름(`AddReminder`)이 화면에 그대로 나왔다(AGENTS §4-7 사용자 표기는 한국어).
 */
object AuditLabels {

    private const val SUMMARY_MAX = 80

    private val TOOL_KOREAN = mapOf(
        ToolNames.ADD_SCHEDULE to "일정 추가",
        ToolNames.GET_SCHEDULE to "일정 조회",
        ToolNames.ADD_MEMORY to "메모 저장",
        ToolNames.SEARCH_MEMORY to "기억 검색",
        ToolNames.SEARCH_WIKIPEDIA to "위키백과 검색",
        ToolNames.ADD_REMINDER to "리마인더 추가",
        "ReminderEdit(화면)" to "리마인더 수정"
    )

    /** 툴 이름의 한국어 — 모르는 이름은 원문 그대로. */
    fun toolKorean(name: String): String = TOOL_KOREAN[name.trim()] ?: name.trim()

    fun of(event: AuditEvent): AuditDisplay {
        val details = event.details
        return when (event.type) {
            AuditEventType.MODEL_RUN -> AuditDisplay(R.drawable.ic_ph_chat_text, "모델 응답", summarize(field(details, "Output") ?: details))
            AuditEventType.TOOL_CALL -> {
                val tool = field(details, "Tool")
                // [WHY] 결과 JSON 은 내용 줄에 두지 않는다 — 읽을 수 없는 기계 형식이다. 원문은 행을 펼치면 보인다.
                val result = field(details, "Result")?.takeUnless { it.startsWith("{") || it.startsWith("[") }
                AuditDisplay(toolIcon(tool), "${tool?.let(::toolKorean) ?: "도구"} 실행", summarize(result.orEmpty()))
            }
            AuditEventType.APPROVAL_GRANTED -> AuditDisplay(R.drawable.ic_ph_check_circle, "승인함", summarize(withKoreanTool(details)))
            AuditEventType.APPROVAL_REJECTED -> AuditDisplay(R.drawable.ic_ph_x, "거절함", summarize(withKoreanTool(details)))
            AuditEventType.ERROR -> AuditDisplay(R.drawable.ic_ph_warning_circle, "오류", summarize(details))
            AuditEventType.EXPORT -> AuditDisplay(R.drawable.ic_ph_download_simple, "백업 내보내기", summarize(field(details, "Status") ?: details))
            AuditEventType.IMPORT -> AuditDisplay(R.drawable.ic_ph_folder_open, "백업 가져오기", summarize(field(details, "Status") ?: details))
            AuditEventType.SEARCH_USED -> AuditDisplay(R.drawable.ic_ph_globe, "웹 검색", summarize(field(details, "Query") ?: details))
            AuditEventType.THERMAL_WARNING -> AuditDisplay(R.drawable.ic_ph_thermometer, "발열 경고", summarize(field(details, "Temperature") ?: details))
            AuditEventType.THERMAL_SHUTDOWN -> AuditDisplay(R.drawable.ic_ph_thermometer, "발열로 멈춤", summarize(field(details, "Temperature") ?: details))
        }
    }

    @DrawableRes
    private fun toolIcon(tool: String?): Int = when (tool) {
        ToolNames.ADD_SCHEDULE, ToolNames.GET_SCHEDULE -> R.drawable.ic_ph_calendar_blank
        ToolNames.ADD_REMINDER, "ReminderEdit(화면)" -> R.drawable.ic_ph_bell
        ToolNames.ADD_MEMORY -> R.drawable.ic_ph_note
        ToolNames.SEARCH_MEMORY -> R.drawable.ic_ph_magnifying_glass
        ToolNames.SEARCH_WIKIPEDIA -> R.drawable.ic_ph_globe
        else -> R.drawable.ic_ph_lightbulb
    }

    /** "Key: 값" 줄의 값 — 기록 형식은 [com.kosmos.app.domain.audit.AuditTrailService] 가 정한다. */
    private fun field(details: String, key: String): String? =
        details.lineSequence().firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    /** 승인 기록 "AddReminder: 설명" 의 앞 툴 이름을 한국어로. */
    private fun withKoreanTool(details: String): String {
        val head = details.substringBefore(':', missingDelimiterValue = "")
        return if (head.isNotEmpty() && head in TOOL_KOREAN) toolKorean(head) + ":" + details.substringAfter(':') else details
    }

    private fun summarize(text: String): String {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length > SUMMARY_MAX) line.take(SUMMARY_MAX).trimEnd() + "…" else line
    }
}
