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
 * [ExtractFactsUseCase]
 * 요약된 에피소드의 원문에서 "앞으로도 기억할 사실 0~3개"를 뽑습니다 (C′2 리셋 시점 자동 추출).
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelRunner], [Tokenizer]
 *
 * ### Key Flow
 * 1. 요약과 같은 전사([buildEpisodeTranscript])를 oneShot 프롬프트에 싣는다
 * 2. "프로필:/지식:" 라벨 줄을 파싱해 [ExtractedFacts] 로 돌려준다 — 프로필 급은 승인 카드,
 *    지식 급은 자동 저장(호출자 [EpisodeFactExtractor] 몫)
 *
 * [WHY] 프롬프트는 exp36 v2 실측 문구 **원문 그대로**다 — 실대화 9 + 합성 6 에피소드에서
 * 형식 준수 15/15, 심은 사실 회수 8/8, 잡담 오추출 0. v1 초안은 "규칙:" 라벨을 모델이 따라 써
 * 지식 절에 섞였고 일회성 일정(치과 예약)을 뽑았다 — 그래서 규칙이 라벨 없는 문장이고
 * "예약·약속 제외"가 명시돼 있다. 문구를 바꾸면 그 측정이 무효가 되므로 exp36 을 다시 돌릴 것.
 *
 * [WHY] `oneShot = true` — 부수 계산이다. 빼면 채팅 KV 가 파괴된다 (ADR-010·014).
 */
class ExtractFactsUseCase @Inject constructor(
    private val modelRunner: ModelRunner,
    private val tokenizer: Tokenizer
) {

    /** 추출 결과. [profile] 은 "항목 → 값", [knowledge] 는 한 문장 사실. 둘 다 비어 있을 수 있다. */
    data class ExtractedFacts(
        val profile: List<Pair<String, String>>,
        val knowledge: List<String>
    ) {
        val isEmpty: Boolean get() = profile.isEmpty() && knowledge.isEmpty()
    }

    suspend operator fun invoke(messages: List<ChatMessage>): AppResult<ExtractedFacts> {
        if (messages.isEmpty()) {
            return AppResult.Failure(AppError.ValidationError("messages", "빈 에피소드에서는 추출할 것이 없습니다"))
        }

        val transcript = buildEpisodeTranscript(messages, tokenizer, MAX_INPUT_TOKENS)
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = SYSTEM_INSTRUCTION,
            history = emptyList(),
            currentInput = USER_PREFIX + transcript,
            oneShot = true
        )

        return when (val result = modelRunner.generate(prompt)) {
            is AppResult.Success -> {
                val facts = parse(result.data.text)
                if (facts == null) {
                    // [WHY] 라벨 줄이 하나도 없으면 형식 이탈이다. 요약과 달리 재시도 대상은
                    // 아니지만(호출자가 로그만 남긴다) 빈 성공과 구분해 감사에 남긴다.
                    AppResult.Failure(AppError.ModelInferenceError("자동 추출 형식 파싱 실패"))
                } else {
                    AppResult.Success(facts)
                }
            }
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    /**
     * "프로필:/지식:" 라벨 줄을 수집합니다. 라벨 줄이 0개면 null(형식 이탈).
     *
     * [WHY] exp36 실측에서 모델은 ① 항목마다 라벨을 반복하고("지식: A\n지식: B") ② 빈 절의
     * "프로필: 없음" 줄을 생략하고 ③ 한 줄에 여러 항목을 쉼표로 잇는다("이름: 진우, 직업: 개발자").
     * 셋 다 내용은 온전하므로 수용한다 — 라벨 줄 1개 이상 = 형식 준수. 라벨 다음의 불릿 줄도
     * 그 절의 항목으로 받는다(실측에는 없었지만 값싼 상위 호환).
     *
     * [WHY] 상한: 프로필 ≤ 2, 지식 ≤ 3, **합계 ≤ 3**(프로필 우선) — 스펙의 "0~3개". 프로필
     * 급이 더 값지고(항상 주입), 지식은 다음 에피소드에서 또 나온다.
     */
    fun parse(text: String): ExtractedFacts? {
        val profileBodies = mutableListOf<String>()
        val knowledgeBodies = mutableListOf<String>()
        var current: MutableList<String>? = null
        var labeled = 0

        for (raw in text.lines()) {
            val labelMatch = LABEL_LINE.find(raw)
            if (labelMatch != null) {
                labeled++
                current = if (labelMatch.groupValues[1] == "프로필") profileBodies else knowledgeBodies
                val body = labelMatch.groupValues[2].trim()
                if (body.isNotEmpty() && !body.startsWith(NONE)) current.add(body)
                continue
            }
            val bullet = BULLET_LINE.find(raw)
            if (bullet != null && current != null) {
                val body = bullet.groupValues[1].trim()
                if (body.isNotEmpty() && !body.startsWith(NONE)) current.add(body)
            }
        }
        if (labeled == 0) return null

        val profile = profileBodies.flatMap(::splitProfileLine).take(MAX_PROFILE)
        val knowledge = knowledgeBodies
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(minOf(MAX_KNOWLEDGE, MAX_TOTAL - profile.size))
        return ExtractedFacts(profile = profile, knowledge = knowledge)
    }

    /** "이름: 진우, 직업: 개발자" → [이름→진우, 직업→개발자]. 콜론 없는 조각은 앞 값에 붙인다(값 안의 쉼표 보존). */
    private fun splitProfileLine(body: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (piece in body.split(",").map { it.trim() }) {
            val sep = piece.indexOfFirst { it == ':' || it == '：' }
            if (sep > 0) {
                val key = piece.substring(0, sep).trim()
                val value = piece.substring(sep + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) out.add(key to value)
            } else if (piece.isNotEmpty() && out.isNotEmpty()) {
                val (k, v) = out.removeAt(out.lastIndex)
                out.add(k to "$v, $piece")
            }
        }
        return out
    }

    companion object {
        const val SESSION_ID = "fact-extraction"

        internal const val USER_PREFIX = "다음 대화에서 기억할 사실을 골라내세요.\n\n"

        // exp36 v2 원문 (scratch/lab/exp36_fact_extraction.py SYSTEM) — 바꾸기 전에 exp36 을 다시 돌릴 것.
        internal val SYSTEM_INSTRUCTION = """
            [System]
            You extract durable facts about the user from a conversation log.
            당신은 대화 기록에서 앞으로도 유효한 사용자 관련 사실을 골라내는 기록 담당자입니다.
            대화에 명시된 사실만 고르고, 추측·일반 상식·일회성 잡담·비서의 발언은 제외합니다. 예약·약속처럼 한 번 지나가는 일정은 캘린더가 맡으므로 제외합니다. 날짜·숫자는 원문 그대로, 사실 하나는 한 줄에 하나씩 씁니다.
            아래 형식만 출력하세요.
            프로필: (이름·호칭·말투 선호·직업·거주지·가족처럼 항상 관련 있는 사실 — "항목: 값" 형태, 최대 2개, 없으면 "없음")
            지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 최대 3개, 없으면 "없음")
        """.trimIndent()

        private const val NONE = "없음"
        private const val MAX_PROFILE = 2
        private const val MAX_KNOWLEDGE = 3
        private const val MAX_TOTAL = 3

        private val LABEL_LINE = Regex("^\\s*[-*•]?\\s*(프로필|지식)\\s*[:：]\\s*(.*)$")
        private val BULLET_LINE = Regex("^\\s*[-*•]\\s+(.+)$")

        // [WHY] 요약과 같은 입력 상한 — 프리필 예산(1,700)에서 지시·구조 여유를 뺀 값.
        val MAX_INPUT_TOKENS = Constants.MAX_CONTEXT_TOKENS - 300
    }
}
