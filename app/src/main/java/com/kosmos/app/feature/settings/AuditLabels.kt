package com.kosmos.app.feature.settings

import com.kosmos.app.core.common.AppLanguage

import androidx.annotation.DrawableRes
import com.kosmos.app.R
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.tool.ToolNames

/** 펼친 활동 기록의 한 항목 — "물은 말"·"답"·"결과" 같은 이름과 값. */
data class AuditField(val label: String, val value: String)

/**
 * 활동 기록 한 줄의 표시값 — 종류 아이콘 · 한국어 이름 · 내용 한 줄, 펼치면 [fields]·[tags](쓸 수 있던 도구).
 */
data class AuditDisplay(
    @DrawableRes val icon: Int,
    val title: String,
    val summary: String,
    val fields: List<AuditField> = emptyList(),
    val tags: List<String> = emptyList()
)

/**
 * 감사 기록 → 화면 표기(0.38.3 M3, 펼침은 0.38.4 M2). 기록 원문(`Tool: …`·`Result: {JSON}`·`Prompt:/Output:`)은 그대로 저장하고
 * 화면에서만 바꾼다 — 원문은 펼친 아래 "원문 보기"에서(AGENTS §4-7 예외, 사용자 결정).
 *
 * [WHY] enum 이름(`TOOL_CALL`)과 툴 클래스 이름(`AddReminder`)·결과 JSON·영어 오류 원문이 화면에 그대로 나왔다(AGENTS §4-7 사용자 표기는 한국어).
 */
object AuditLabels {

    private const val SUMMARY_MAX = 80

    private val TOOL_KOREAN = mapOf(
        ToolNames.ADD_SCHEDULE to "일정 추가",
        ToolNames.GET_SCHEDULE to "일정 조회",
        ToolNames.ADD_MEMORY to "메모 저장",
        ToolNames.SEARCH_MEMORY to "기억 검색",
        ToolNames.SEARCH_WIKIPEDIA to "위키백과 검색",
        ToolNames.ADD_REMINDER to "알림 추가",
        ToolNames.LIST_REMINDERS to "알림 목록 조회",
        "ReminderEdit(화면)" to "알림 수정",
        // 기억 정리 실행기(0.30.0) — 모델 툴이 아니라 정리 화면이 남긴다.
        "MemoryMerge" to "기억 합치기",
        "EpisodeMerge" to "대화 합치기"
    )

    private val TOOL_ENGLISH = mapOf(
        ToolNames.ADD_SCHEDULE to "Add event",
        ToolNames.GET_SCHEDULE to "View events",
        ToolNames.ADD_MEMORY to "Save note",
        ToolNames.SEARCH_MEMORY to "Search memory",
        ToolNames.SEARCH_WIKIPEDIA to "Search Wikipedia",
        ToolNames.ADD_REMINDER to "Add reminder",
        ToolNames.LIST_REMINDERS to "List reminders",
        "ReminderEdit(화면)" to "Edit reminder",
        "MemoryMerge" to "Merge memories",
        "EpisodeMerge" to "Merge conversations"
    )

    /** 기록 원문의 "키:" 줄 이름 — 여러 줄 값을 다음 키 전까지 읽는 데 쓴다. */
    private val KEYS = listOf("Prompt", "Output", "Tools declared", "Tool", "Result", "Note", "Query", "Status", "Temperature")

    /** 툴 이름의 한국어/영어 — 모르는 이름은 원문 그대로. */
    fun toolKorean(
        name: String,
        lang: com.kosmos.app.core.common.AppLanguage = com.kosmos.app.core.common.AppLanguage.KO
    ): String {
        val map = if (lang == com.kosmos.app.core.common.AppLanguage.EN) TOOL_ENGLISH else TOOL_KOREAN
        return map[name.trim()] ?: name.trim()
    }

    fun of(
        event: AuditEvent,
        lang: com.kosmos.app.core.common.AppLanguage = com.kosmos.app.core.common.AppLanguage.KO
    ): AuditDisplay {
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        val details = event.details
        return when (event.type) {
            AuditEventType.MODEL_RUN -> modelRun(details, lang)
            AuditEventType.TOOL_CALL -> toolCall(details, lang)
            AuditEventType.APPROVAL_GRANTED -> approval(details, R.drawable.ic_ph_check_circle, if (en) "Approved" else "승인함", lang)
            AuditEventType.APPROVAL_REJECTED -> approval(details, R.drawable.ic_ph_x, if (en) "Cancelled" else "취소함", lang)
            AuditEventType.ERROR -> {
                val reason = errorKorean(details, lang)
                AuditDisplay(R.drawable.ic_ph_warning_circle, if (en) "Error" else "오류", summarize(reason), listOf(AuditField(if (en) "Reason" else "사유", reason)))
            }
            AuditEventType.EXPORT -> simple(R.drawable.ic_ph_download_simple, if (en) "Export backup" else "백업 내보내기", if (en) "Status" else "상태", field(details, "Status") ?: details)
            AuditEventType.IMPORT -> simple(R.drawable.ic_ph_folder_open, if (en) "Import backup" else "백업 가져오기", if (en) "Status" else "상태", field(details, "Status") ?: details)
            AuditEventType.SEARCH_USED -> simple(R.drawable.ic_ph_globe, if (en) "Web search" else "웹 검색", if (en) "Query" else "찾은 말", field(details, "Query") ?: details)
            AuditEventType.THERMAL_WARNING -> simple(R.drawable.ic_ph_thermometer, if (en) "Heat warning" else "발열 경고", if (en) "Temp" else "온도", field(details, "Temperature") ?: details)
            AuditEventType.THERMAL_SHUTDOWN -> simple(R.drawable.ic_ph_thermometer, if (en) "Paused due to heat" else "발열로 멈춤", if (en) "Temp" else "온도", field(details, "Temperature") ?: details)
        }
    }

    private fun simple(@DrawableRes icon: Int, title: String, label: String, value: String) =
        AuditDisplay(icon, title, summarize(value), listOf(AuditField(label, value)))

    /**
     * 모델 실행 — 채팅 답("물은 말·답·쓸 수 있던 도구"), 아침 브리핑 작성, 기억 추출(대화 정리 뒤 사실 골라내기)을 나눈다.
     * 기록하는 쪽의 프롬프트 머리(`morning briefing (`·`fact extraction (`)로 가른다.
     */
    private fun modelRun(details: String, lang: com.kosmos.app.core.common.AppLanguage): AuditDisplay {
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        val prompt = block(details, "Prompt").orEmpty()
        val output = plain(block(details, "Output") ?: details)
        return when {
            prompt.startsWith("morning briefing") ->
                AuditDisplay(
                    R.drawable.ic_ph_sun,
                    if (en) "Morning briefing" else "아침 브리핑 작성",
                    summarize(output),
                    listOf(AuditField(if (en) "Briefing" else "브리핑", output))
                )
            prompt.startsWith("fact extraction") -> {
                val summary = factSummary(output, lang)
                AuditDisplay(
                    R.drawable.ic_ph_lightbulb,
                    if (en) "Memory extraction" else "기억 추출",
                    summary,
                    listOf(AuditField(if (en) "Result" else "결과", summary))
                )
            }
            else -> {
                val tools = block(details, "Tools declared")
                    ?.trim('[', ']')?.split(',')?.map { toolKorean(it, lang) }?.filter { it.isNotBlank() }.orEmpty()
                AuditDisplay(
                    R.drawable.ic_ph_chat_text,
                    if (en) "Model reply" else "모델 답",
                    summarize(output),
                    listOfNotNull(
                        prompt.takeIf { it.isNotBlank() }?.let { AuditField(if (en) "Prompt" else "물은 말", it) },
                        AuditField(if (en) "Reply" else "답", output)
                    ),
                    tools
                )
            }
        }
    }

    /** `profile=0 knowledge=1 savedKnowledge=1 filtered=0 suggested=0` → "메모 1개 저장, 프로필 제안 0개". 모르는 형식은 그대로. */
    private fun factSummary(output: String, lang: com.kosmos.app.core.common.AppLanguage): String {
        val values = Regex("(\\w+)=(\\d+)").findAll(output).associate { it.groupValues[1] to it.groupValues[2] }
        val saved = values["savedKnowledge"] ?: return output
        val suggested = values["suggested"] ?: "0"
        return if (lang == com.kosmos.app.core.common.AppLanguage.EN) {
            "Saved $saved notes, $suggested profile suggestions"
        } else {
            "메모 ${saved}개 저장, 프로필 제안 ${suggested}개"
        }
    }

    private fun toolCall(details: String, lang: com.kosmos.app.core.common.AppLanguage): AuditDisplay {
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        val tool = field(details, "Tool")
        val result = resultKorean(block(details, "Result").orEmpty(), lang)
        val fields = listOfNotNull(
            result.takeIf { it.isNotEmpty() }?.let { AuditField(if (en) "Result" else "결과", it) },
            field(details, "Note")?.let { AuditField(if (en) "Note" else "참고", it) }
        )
        val title = if (en) {
            "Ran ${tool?.let { toolKorean(it, lang) } ?: "tool"}"
        } else {
            "${tool?.let { toolKorean(it, lang) } ?: "도구"} 실행"
        }
        return AuditDisplay(toolIcon(tool), title, summarize(result), fields)
    }

    /**
     * 툴 결과 → 한 줄. `{"status":"success","message":…}` 는 "성공, 메시지", 오류는 "실패, 메시지", 상태·메시지가 없는 JSON 은 빈칸, JSON 이 아니면(기억 합치기 "a + b → c") 그대로.
     * [WHY] 결과 JSON 은 기계 형식이다 — 상태와 메시지만 사람 말로(0.38.4 M2 결정 5). 나머지는 "원문 보기".
     */
    private fun resultKorean(raw: String, lang: com.kosmos.app.core.common.AppLanguage): String {
        if (!raw.startsWith("{")) return raw
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        // [WHY] 정규식으로 두 값만 — org.json 은 안드로이드 런타임이 필요해 JVM 테스트에서 돌지 않는다. 결과 형식은 ToolResultJson 이 정한다.
        val status = when (STATUS.find(raw)?.groupValues?.get(1)) {
            "success" -> if (en) "Success" else "성공"
            "error" -> if (en) "Failed" else "실패"
            else -> null
        }
        val message = MESSAGE.find(raw)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
            ?.let { readable(it, lang = lang) }?.let { if (en) it else toolMessageKorean(it) }?.takeIf { it.isNotBlank() }
        return listOfNotNull(status, message).joinToString(", ")
    }

    /**
     * 툴 결과 문구(모델 입력)를 화면 말투로 — 합니다체·내부 이름("리마인더", 인자 이름)을 해요체·"알림"으로.
     *
     * [WHY] 결과 문구 자체는 모델 입력이라 바꾸면 실측이 필요하다 — 활동 기록 표시에서만 바꾼다(0.38.7 결정 4). "원문 보기"는 원문 그대로.
     */
    private fun toolMessageKorean(message: String): String =
        TOOL_MESSAGES.fold(message) { text, (pattern, replace) -> pattern.replace(text) { replace(it) } }

    private fun approval(details: String, @DrawableRes icon: Int, title: String, lang: com.kosmos.app.core.common.AppLanguage): AuditDisplay {
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        val head = details.substringBefore(':', missingDelimiterValue = "")
        val (tool, rawBody) = if (head.isNotEmpty() && head in TOOL_KOREAN) toolKorean(head, lang) to details.substringAfter(':').trim() else null to details
        // [WHY] 승인 내용에는 사용자가 한 말(메모 내용 등)이 들어 있다 — "…하세요"로 끝나도 지우지 않고 시각만 바꾼다.
        val body = readable(rawBody, dropInstructions = false, lang = lang)
        val summary = if (tool != null) "$tool: $body" else body
        return AuditDisplay(
            icon,
            title,
            summarize(summary),
            listOfNotNull(
                tool?.let { AuditField(if (en) "Tool" else "도구", it) },
                AuditField(if (en) "Details" else "내용", body)
            )
        )
    }

    /**
     * 오류 원문 → 한국어/영어 사유. 앱이 남기는 영어 진단 문구를 알아보고, 한국어로 시작하는 것은 그대로, 모르는 영어는 "앱 내부 오류".
     */
    private fun errorKorean(details: String, lang: com.kosmos.app.core.common.AppLanguage): String {
        val en = lang == com.kosmos.app.core.common.AppLanguage.EN
        val line = details.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return when {
            line.startsWith("Blocked tool call outside allowlist:") ->
                if (en) "Attempted disallowed tool: ${toolKorean(line.substringAfter(':'), lang)}"
                else "허용하지 않은 도구를 부르려 했어요: ${toolKorean(line.substringAfter(':'), lang)}"
            line.startsWith("Invalid tool argument:") ->
                if (en) "Invalid tool argument: ${toolKorean(line.substringAfter(':').trim().substringBefore('.'), lang)}"
                else "도구에 넘긴 값이 잘못됐어요: ${toolKorean(line.substringAfter(':').trim().substringBefore('.'), lang)}"
            line.contains("Model inference failed") || line.contains("ModelInferenceError") ->
                if (en) "Couldn't generate a reply" else "답을 만들지 못했어요"
            // [WHY] "음성 전사 실패: SttError(reason=…)"처럼 오류 객체 원문이 붙은 줄은 사유만 남긴다 — 원문은 "원문 보기"에 그대로 있다.
            HANGUL.containsMatchIn(line) -> line.replace(ERROR_OBJECT) { it.groupValues[1] }
            else -> if (en) line.ifEmpty { "Internal app error" } else "앱 내부 오류"
        }
    }

    @DrawableRes
    private fun toolIcon(tool: String?): Int = when (tool) {
        ToolNames.ADD_SCHEDULE, ToolNames.GET_SCHEDULE -> R.drawable.ic_ph_calendar_blank
        ToolNames.ADD_REMINDER, ToolNames.LIST_REMINDERS, "ReminderEdit(화면)" -> R.drawable.ic_ph_bell
        ToolNames.ADD_MEMORY, "MemoryMerge" -> R.drawable.ic_ph_note
        ToolNames.SEARCH_MEMORY -> R.drawable.ic_ph_magnifying_glass
        ToolNames.SEARCH_WIKIPEDIA -> R.drawable.ic_ph_globe
        "EpisodeMerge" -> R.drawable.ic_ph_chat_text
        else -> R.drawable.ic_ph_lightbulb
    }

    /** "Key: 값" 첫 줄의 값 — 기록 형식은 [com.kosmos.app.domain.audit.AuditTrailService] 가 정한다. */
    private fun field(details: String, key: String): String? =
        details.lineSequence().firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    /** "Key:" 부터 다음 키 줄 전까지(여러 줄 값 — 모델 답·결과는 줄바꿈을 담는다). */
    private fun block(details: String, key: String): String? {
        val lines = details.lines()
        val start = lines.indexOfFirst { it.startsWith("$key:") }.takeIf { it >= 0 } ?: return null
        val rest = lines.drop(start + 1).takeWhile { line -> KEYS.none { line.startsWith("$it:") } }
        return (listOf(lines[start].substringAfter(':').trim()) + rest).joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    /**
     * 툴 결과·승인 내용을 사람이 읽게 — ISO 일시는 한국어/영어로(AGENTS §4-7), 모델에게 하는 지시 문장("…알리세요"·"…답하세요")은 뺀다(0.38.5 M1).
     * [WHY] 툴 결과 문구는 모델 입력이라 원문은 바꾸지 않는다(§2-⑤ 실측 없이 고치지 않는다) — 화면에서만. 원문은 "원문 보기".
     */
    internal fun readable(
        text: String,
        dropInstructions: Boolean = true,
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        lang: com.kosmos.app.core.common.AppLanguage = com.kosmos.app.core.common.AppLanguage.KO
    ): String =
        text.lines().mapNotNull { line ->
            SENTENCE_END.split(line.trim()).filter {
                it.isNotBlank() && !(dropInstructions && (TO_MODEL.containsMatchIn(it) || TO_MODEL_EN.containsMatchIn(it)))
            }.joinToString(" ").takeIf { it.isNotBlank() }
        }.joinToString("\n").replace(ISO) { m ->
            com.kosmos.app.domain.util.IsoDateTimeParser.toDisplayKorean(m.value, zoneId, lang = lang) ?: m.value
        }

    /** 모델 출력의 마크다운 기호(`**`·`__`·`` ` ``·줄 앞 `#`)를 뗀다 — 기록 화면은 서식을 그리지 않는다. */
    private fun plain(text: String): String =
        text.replace("**", "").replace("__", "").replace("`", "")
            .lines().joinToString("\n") { it.replace(Regex("^#+\\s*"), "") }.trim()

    private fun summarize(text: String): String {
        val line = plain(text).lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return if (line.length > SUMMARY_MAX) line.take(SUMMARY_MAX).trimEnd() + "…" else line
    }

    // 툴 결과 문구 → 화면 말투(문구 원문은 BaseAgent·각 실행기). 맞지 않는 문구는 그대로 둔다.
    private val TOOL_MESSAGES: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("일정이 성공적으로 추가되었습니다\\.") to { _ -> "일정을 추가했어요." },
        Regex("웹 검색이 비활성화되어 있습니다\\.") to { _ -> "웹 검색이 꺼져 있어요." },
        Regex("이 에이전트에서 사용할 수 없는 도구입니다: (\\S+)") to { m -> "쓸 수 없는 도구예요: ${toolKorean(m.groupValues[1])}" },
        Regex("알 수 없는 Tool입니다: (\\S+)") to { m -> "알 수 없는 도구예요: ${m.groupValues[1]}" },
        Regex("'\\w+' 인자가 없습니다\\.") to { _ -> "필요한 값이 빠졌어요." },
        Regex("'\\w+' 인자의 형식이 올바르지 않습니다\\.") to { _ -> "값의 형식이 맞지 않아요." },
        Regex("'\\w+' 인자가 ISO 8601 일시 형식이 아닙니다\\.") to { _ -> "일시 형식이 맞지 않아요." },
        Regex("이미 지난 시각입니다\\.") to { _ -> "이미 지난 시각이에요." },
        Regex("저장에 실패했습니다\\.") to { _ -> "저장하지 못했어요." },
        Regex("저장하지 않았습니다\\. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다\\.") to { _ -> "되묻는 말이라 메모하지 않았어요." },
        // 뒤 문장("…물어보세요")은 모델 지시라 표시 전에 빠지고 이 문장만 남는다.
        Regex("아직 저장하지 않았습니다\\.") to { _ -> "저장 부탁이 아니라 먼저 물어보게 했어요." },
        Regex("기억 검색 중 오류가 발생했습니다:") to { _ -> "기억 검색 중 오류가 났어요:" },
        Regex("리마인더 등록 실패") to { _ -> "알림 등록 실패" },
        Regex("리마인더 등록:") to { _ -> "알림 등록:" }
    )

    private val ERROR_OBJECT = Regex("""\b[A-Z]\w*\((?:\w+=[^,()]*, )*reason=([^()]*)\)""")
    private val HANGUL = Regex("[가-힣]")
    /** 문장 끝(마침표 뒤 공백)에서 나눈다 — 마침표는 앞 문장에 남는다. */
    private val SENTENCE_END = Regex("(?<=[.?!])\\s+")
    /**
     * 모델에게 하는 명령 — 명령형으로 끝나고 모델에게 시키는 말(답하·알리·호출·지어내…)이 든 문장.
     * [WHY] 명령형만 보면 기억 검색 결과 속 사용자 메모("…하세요")까지 지워진다 — 툴 결과의 지시 문구(BaseAgent·실행기)에 쓰는 낱말로 좁힌다.
     */
    private val TO_MODEL = Regex("(사용자|답하|알리|호출|보내|물어보|지어내|추측|안내).*(세요|십시오)[.!]?$")
    private val TO_MODEL_EN = Regex("(?i)^(?:please\\s+)?(?:tell|inform|ask|do not|don't|call|use|respond|reply|never)\\b.*[.!]?$")
    private val ISO = Regex("\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2})?)?")
    private val STATUS = Regex("\"status\"\\s*:\\s*\"(\\w+)\"")
    private val MESSAGE = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
}
