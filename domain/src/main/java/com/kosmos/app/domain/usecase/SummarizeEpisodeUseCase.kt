package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.tool.Tokenizer
import javax.inject.Inject

/**
 * [SummarizeEpisodeUseCase]
 * 닫힌 에피소드의 원문 대화를 색인용 문서(제목/태그/요약)로 요약합니다 (ADR-022).
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelRunner], [Tokenizer]
 *
 * [WHY] 프롬프트는 exp33 에서 검증된 형식 그대로다 — 실기기 대화 픽스처 9개 에피소드에서
 * 형식 준수 9/9, 다중 주제 분리 지시를 얹어도 9/9 유지 + 단일 주제 비분리(M0 게이트).
 * 문구를 바꾸면 그 측정이 무효가 되므로 바꾸기 전에 exp33 을 다시 돌릴 것.
 *
 * [WHY] `oneShot = true` — 부수 계산이다. 붙이지 않으면 시스템 지시와 sessionId 가 채팅과
 * 달라 런타임이 캐시된 채팅 대화를 파괴한다 (ADR-010·014, SummarizeScheduleUseCase 전례).
 */
class SummarizeEpisodeUseCase @Inject constructor(
    private val modelRunner: ModelRunner,
    private val tokenizer: Tokenizer
) {

    /** 요약 문서 한 편. 다중 주제 에피소드는 여러 편이 나올 수 있다. */
    data class EpisodeDoc(
        val title: String,
        val tags: List<String>,
        val summary: String
    )

    suspend operator fun invoke(messages: List<ChatMessage>): AppResult<List<EpisodeDoc>> {
        if (messages.isEmpty()) {
            return AppResult.Failure(AppError.ValidationError("messages", "빈 에피소드는 요약할 수 없습니다"))
        }

        val transcript = buildTranscript(messages)
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = SYSTEM_INSTRUCTION,
            history = emptyList(),
            currentInput = "다음 대화를 요약하세요.\n\n$transcript",
            oneShot = true
        )

        return when (val result = modelRunner.generate(prompt)) {
            is AppResult.Success -> {
                val docs = parse(result.data.text)
                if (docs.isEmpty()) {
                    // [WHY] 파싱 실패를 Failure 로 올려야 스케줄러의 재시도 카운트에 계상된다 —
                    // 빈 목록 성공으로 내리면 요약 없는 에피소드가 SUMMARIZED 로 오기록된다.
                    AppResult.Failure(AppError.ModelInferenceError("에피소드 요약 형식 파싱 실패"))
                } else {
                    AppResult.Success(docs)
                }
            }
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    /**
     * 원문을 "사용자:/비서:" 라벨로 이어 붙이고 토큰 상한에서 자릅니다.
     *
     * [WHY] 에피소드는 예산 리셋 경계 덕에 보통 프리필 예산(≈1,700토큰) 이내지만, catch-up 이
     * 소급 배정한 고아 구간은 더 길 수 있다. 상한을 넘으면 **앞쪽을 보존**한다 — 에피소드의
     * 주제는 첫 발화들이 정하고, 꼬리는 대개 그 변주다.
     */
    private fun buildTranscript(messages: List<ChatMessage>): String {
        val lines = StringBuilder()
        for (m in messages) {
            val label = if (m.role == ChatMessage.Role.USER) "사용자" else "비서"
            val line = "$label: ${m.content}\n"
            if (tokenizer.sizeInTokens(lines.toString() + line) > MAX_INPUT_TOKENS) break
            lines.append(line)
        }
        return lines.toString().trimEnd()
    }

    /**
     * "제목:/태그:/요약:" 블록을 파싱합니다. `---` 로 구분된 다중 문서를 지원합니다.
     *
     * [WHY] 세 필드가 모두 있는 블록만 문서로 인정한다 — 부분 형식은 검색 품질을 조용히
     * 깎으므로(태그 없는 문서는 태그 회수에 안 걸림) 실패로 취급해 재시도가 낫다.
     */
    internal fun parse(text: String): List<EpisodeDoc> {
        return text.split(DOC_SEPARATOR)
            .mapNotNull { block ->
                val title = FIELD_TITLE.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                val tagsCsv = FIELD_TAGS.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                val summary = FIELD_SUMMARY.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                if (title.isEmpty() || tagsCsv.isEmpty() || summary.isEmpty()) return@mapNotNull null
                EpisodeDoc(
                    title = title,
                    tags = tagsCsv.split(",", "、").mapNotNull { it.trim().ifEmpty { null } },
                    summary = summary
                )
            }
    }

    private companion object {
        const val SESSION_ID = "episode-summary"

        // [WHY] 시스템 지시는 다른 oneShot 과 같이 영어 [System] 프리픽스, 형식 본문은 exp33
        // 검증 문구(한국어) 그대로다.
        val SYSTEM_INSTRUCTION = """
            [System]
            You are a librarian who summarizes conversation logs into searchable index documents.
            당신은 대화 기록을 색인용 문서로 요약하는 사서입니다. 아래 형식만 출력하세요.
            제목: (한 줄)
            태그: (쉼표로 구분한 핵심 명사 5~8개 — 검색에 쓰이므로 동의어·구체 명사 포함)
            요약: (3~5문장. 날짜·시각·이름·숫자는 원문 그대로 보존)

            대화에 서로 무관한 주제가 2개 이상 섞여 있으면, 주제마다 위 형식을 반복하되 문서 사이를 '---' 한 줄로 구분하세요. 주제가 하나면 절대 나누지 마세요.
        """.trimIndent()

        val DOC_SEPARATOR = Regex("\\n-{3,}\\n")
        val FIELD_TITLE = Regex("제목\\s*[:：]\\s*(.+)")
        val FIELD_TAGS = Regex("태그\\s*[:：]\\s*(.+)")
        val FIELD_SUMMARY = Regex("요약\\s*[:：]\\s*(.+)", RegexOption.DOT_MATCHES_ALL)

        // [WHY] 요약 입력 상한 — 프리필 예산(1,700)에서 지시·구조 여유를 뺀 값. 에피소드가
        // 정상 경로(리셋 경계)로 닫혔다면 어차피 이 안이다.
        val MAX_INPUT_TOKENS = Constants.MAX_CONTEXT_TOKENS - 300
    }
}
