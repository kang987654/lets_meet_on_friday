package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.tool.Tokenizer
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields
import javax.inject.Inject

/**
 * [GenerateWeeklyReviewUseCase]
 * 지난 7일의 요약된 에피소드로 주간 회고(3~5문장)를 만들어 지식 노트로 저장합니다 (0.30.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Cleanup)
 * - **Dependencies**: [EpisodeRepository], [KnowledgeRepository], [ModelRunner](oneShot), [Tokenizer]
 *
 * ### Key Flow
 * 1. `now` 기준 7일 안에 시작한 SUMMARIZED 에피소드를 모은다. 없으면 모델을 부르지 않는다.
 * 2. 입력이 예산을 넘으면 **오래된 것부터** 뺀다(최근 일이 회고의 중심).
 * 3. oneShot(exp42 회고 원문) → 주차 키 id(`weekly-review-2026-W40`) 노트로 저장 — 같은 주를 다시 정리하면 교체된다.
 *
 * [WHY] 지식 노트로 저장한다(사용자 결정 D1) — `search_memory` 로 "이번 주 뭐 했지?"에 회수되고, 기억 화면에서 지울 수 있다.
 */
class GenerateWeeklyReviewUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val modelRunner: ModelRunner,
    private val tokenizer: Tokenizer
) {

    /** @return 저장한 회고 노트, 대상 에피소드가 없으면 Success(null) */
    suspend operator fun invoke(
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AppResult<KnowledgeNote?> {
        val episodes = when (val loaded = episodeRepository.getByStatus(EpisodeStatus.SUMMARIZED)) {
            is AppResult.Success -> loaded.data
            is AppResult.Failure -> return AppResult.Failure(loaded.error)
        }
        val lines = episodes
            .filter { it.startAt in (now - WINDOW_MS)..now && !it.summary.isNullOrBlank() }
            .sortedByDescending { it.startAt }
            .map { ep ->
                val date = Instant.ofEpochMilli(ep.startAt).atZone(zoneId)
                "- ${date.monthValue}월 ${date.dayOfMonth}일 · ${ep.title.orEmpty()}: ${ep.summary.orEmpty()}"
            }
        if (lines.isEmpty()) return AppResult.Success(null)

        val kept = mutableListOf<String>()
        var budget = MAX_INPUT_TOKENS
        for (line in lines) {
            val cost = tokenizer.sizeInTokens(line)
            if (cost > budget) break
            kept += line
            budget -= cost
        }
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = REVIEW_SYSTEM,
            history = emptyList(),
            // [WHY] 시간순으로 다시 세운다 — 예산 컷은 최신부터 담았으므로.
            currentInput = "지난 7일 대화 요약:\n" + kept.reversed().joinToString("\n"),
            oneShot = true
        )
        val text = when (val result = modelRunner.generate(prompt)) {
            is AppResult.Success -> result.data.text.trim()
            is AppResult.Failure -> return AppResult.Failure(result.error)
        }
        if (text.isBlank()) return AppResult.Success(null)

        val week = weekKey(now, zoneId)
        val note = KnowledgeNote(
            id = "weekly-review-$week",
            content = text,
            tags = listOf(TAG, week),
            createdAt = now,
            updatedAt = now,
            source = KnowledgeNote.SOURCE_AUTO
        )
        return when (val saved = knowledgeRepository.save(note)) {
            is AppResult.Success -> AppResult.Success(note)
            is AppResult.Failure -> AppResult.Failure(saved.error)
        }
    }

    companion object {
        const val SESSION_ID = "weekly-review"
        const val TAG = "주간 회고"
        private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
        // [WHY] 에피소드 요약은 건당 100토큰 안팎 — 1,000 이면 7일치가 들어가고 프리필 예산(1,700) 안에 지시가 남는다.
        private const val MAX_INPUT_TOKENS = 1_000

        /** "2026-W40" — ISO 주차. 같은 주에 다시 정리하면 같은 키라 노트가 교체된다. */
        fun weekKey(now: Long, zoneId: ZoneId): String {
            val date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            return "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        }

        /** exp42 회고 원문 — 바꾸면 그 실측(지어내지 않음·3~5문장)이 무효가 된다. */
        val REVIEW_SYSTEM = """
            너는 사용자의 개인 비서다. 아래는 지난 7일 동안 나눈 대화의 요약이다.
            이번 주를 3~5문장의 한국어로 회고한다. 무엇에 시간을 썼는지와 다가오는 일을 짚는다.
            요약에 없는 사실·숫자·이름은 절대 지어내지 않는다. 목록이나 제목 없이 문단 하나로 쓴다.
        """.trimIndent()
    }
}
