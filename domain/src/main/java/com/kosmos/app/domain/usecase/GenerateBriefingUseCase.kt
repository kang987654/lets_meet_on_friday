package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.util.IsoDateTimeParser
import javax.inject.Inject

/**
 * [GenerateBriefingUseCase]
 * 아침 브리핑 본문을 생성합니다 — 인사 + 오늘 일정 + 미완료 할 일 + **최근 에피소드 기반
 * 후속 질문 1개** (expand.md A4·A4+, 반응형→선제형 전환의 첫 조각).
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelRunner], [Tokenizer]
 *
 * [WHY] `oneShot = true` — 붙이지 않으면 시스템 지시와 sessionId 가 채팅과 달라 런타임이
 * 캐시된 채팅 대화를 파괴한다 (ADR-010·014, SummarizeEpisodeUseCase 전례).
 *
 * [WHY] 출력을 파싱하지 않는다 — 브리핑은 자유 텍스트 그대로 비서 메시지가 되므로
 * 형식 실패 모드가 없다 (요약 유스케이스들과 다른 점).
 */
class GenerateBriefingUseCase @Inject constructor(
    private val modelRunner: ModelRunner,
    private val tokenizer: Tokenizer
) {

    /**
     * 브리핑 재료. 수집은 호출자(생성기) 몫 — 이 유스케이스는 직렬화·추론만 담당한다.
     * [dateLabel] 예: "8월 21일 금요일". [deviceCalendarFailed] 가 true 면 기기 캘린더를
     * 못 읽었음을 브리핑 문구에 반영해야 한다 (PRD EC4 — 조용히 생략하면 오표시).
     */
    data class BriefingMaterials(
        val dateLabel: String,
        val events: List<CalendarEvent>,
        val deviceCalendarFailed: Boolean,
        val pendingTasks: List<TaskItem>,
        val recentEpisodes: List<Episode>,
        val zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
    )

    suspend operator fun invoke(materials: BriefingMaterials): AppResult<String> {
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = SYSTEM_INSTRUCTION,
            history = emptyList(),
            currentInput = buildInput(materials),
            oneShot = true
        )

        return when (val result = modelRunner.generate(prompt)) {
            is AppResult.Success -> {
                val text = result.data.text.trim()
                if (text.isEmpty()) {
                    AppResult.Failure(AppError.ModelInferenceError("브리핑 생성 결과가 비어 있습니다"))
                } else {
                    AppResult.Success(text)
                }
            }
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    /**
     * 재료를 한국어 블록으로 직렬화하고 토큰 상한에서 자릅니다.
     *
     * [WHY] 상한 초과 시 **에피소드 → 할 일 → 일정** 역순으로 뒤에서부터 항목을 떨군다 —
     * 브리핑의 뼈대는 오늘 일정이고, 후속 질문 재료(에피소드)는 있으면 좋은 층이다.
     * 일정 시각은 [IsoDateTimeParser.toDisplayKorean] — 표기 규칙 단일 출처 (0.19.2).
     */
    internal fun buildInput(materials: BriefingMaterials): String {
        val header = "오늘은 ${materials.dateLabel}입니다. 아래 재료로 아침 브리핑을 작성하세요.\n"

        val scheduleBlock = buildString {
            appendLine("[오늘 일정]")
            if (materials.deviceCalendarFailed) {
                appendLine("(주의: 기기 캘린더를 읽지 못했습니다 — 아래는 앱에 저장된 일정만이며, 브리핑에서 이 사실을 알려야 합니다)")
            }
            if (materials.events.isEmpty()) {
                appendLine("- 없음")
            } else {
                materials.events.forEach { event ->
                    val time = IsoDateTimeParser.toDisplayKorean(event.startIso) ?: event.startIso
                    appendLine("- ${event.title} ($time)")
                }
            }
        }

        val taskLines = materials.pendingTasks.map { "- ${it.title}" }
        // [WHY] 에피소드마다 날짜를 붙인다 — 날짜가 없으면 모델이 지시문 예시를 따라 두 달 전 대화도 "어제"라고 불렀다(0.36.0 실기기, exp49
        // brief: 날짜만 붙이면 효과 없고 예시 교체와 함께여야 0/4).
        val episodeLines = materials.recentEpisodes.map { episode ->
            val day = IsoDateTimeParser.monthDayKorean(episode.startAt, materials.zoneId)
            "- ($day) ${episode.title ?: "(제목 없음)"}: ${episode.summary.orEmpty()}"
        }

        // 뒤에서부터 항목을 떨구며 상한에 맞춘다 — 에피소드 먼저, 그다음 할 일.
        var tasks = taskLines
        var episodes = episodeLines
        while (true) {
            val input = assemble(header, scheduleBlock, tasks, episodes)
            if (tokenizer.sizeInTokens(input) <= MAX_INPUT_TOKENS) return input
            when {
                episodes.isNotEmpty() -> episodes = episodes.dropLast(1)
                tasks.isNotEmpty() -> tasks = tasks.dropLast(1)
                else -> return input // 일정만 남았다 — 더 줄일 층이 없으니 그대로 보낸다
            }
        }
    }

    private fun assemble(
        header: String,
        scheduleBlock: String,
        tasks: List<String>,
        episodes: List<String>
    ): String = buildString {
        append(header)
        appendLine()
        append(scheduleBlock)
        appendLine()
        appendLine("[미완료 할 일]")
        if (tasks.isEmpty()) appendLine("- 없음") else tasks.forEach { appendLine(it) }
        appendLine()
        appendLine("[최근 대화 기억]")
        if (episodes.isEmpty()) appendLine("- 없음") else episodes.forEach { appendLine(it) }
    }.trimEnd()

    companion object {
        /** 감사 기록과 프롬프트가 같은 식별자를 쓴다 — 생성기(app)가 logModelRun 에 재사용. */
        const val SESSION_ID = "morning-briefing"

        // [WHY] 예시 문장은 exp49 brief 조건 C 원문 — 바꾸면 그 측정이 무효다.
        // [WHY] oneShot 공통 형식([System] + 영어 역할 + 한국어 역할 + "~하세요" 목록). 후속 질문은
        // "정확히 1개" — 여러 개면 심문이 되고, 0개면 A4+ 의 존재 이유가 사라진다.
        // [WHY] 인사·해요체 줄과 캘린더 규칙 삭제는 exp54 c2 — "밝은 인사"가 매번 "활기찬 ○요일을 시작해 보세요!"가 됐고,
        // 캘린더 규칙은 못 읽은 날이 아니어도 "읽지 못했습니다"를 쓰게 했다. 알림은 재료의 주의 줄이 그날에만 맡는다.
        private val SYSTEM_INSTRUCTION = """
            [System]
            You are a personal assistant writing a short morning briefing in Korean.
            당신은 사용자의 하루를 여는 아침 브리핑을 쓰는 개인 비서입니다. 아래 규칙을 지키세요.
            - "좋은 아침이에요."처럼 짧은 인사 한마디로 시작하세요. 느낌표나 "활기찬" 같은 꾸밈말은 쓰지 마세요.
            - 모든 문장을 해요체(…해요, …있어요)로 쓰세요. 합니다체(…합니다)는 쓰지 마세요.
            - 오늘 일정과 미완료 할 일을 간결히 정리하세요. 시각·이름·숫자는 재료의 원문 그대로 보존하세요.
            - 마지막에 [최근 대화 기억]을 근거로 자연스러운 후속 질문을 **정확히 1개** 하세요.
              (예: "지난번에 말씀하신 ○○은 잘 돼가요?" — 기억 앞의 날짜를 보고 "어제"·"지난주"를 맞게 쓰세요) 기억이 없으면 오늘 계획을 묻는 가벼운 질문 1개로 대체하세요.
            - 전체 5~8문장, 과장 없이. 목록이 비어 있으면 억지로 채우지 마세요.
        """.trimIndent()

        // [WHY] 프리필 예산 상한([Constants.MAX_CONTEXT_TOKENS])에서 지시·구조 여유를 뺀 값 — SummarizeEpisodeUseCase 와
        // 같은 유도. 재료가 이 상한을 넘는 날(일정 폭주)은 뒤층부터 떨어져 나간다.
        private val MAX_INPUT_TOKENS = Constants.MAX_CONTEXT_TOKENS - 300
    }
}
