package com.kosmos.app.domain.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.search.BigramMatcher
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * 에피소드 통합 제안 — 시간순으로 이어진 에피소드 묶음(2개 이상). 첫 에피소드로 나머지를 합친다 (0.31.0).
 */
data class EpisodeMergeProposal(val episodes: List<Episode>) {
    /** 미리보기 체크 상태의 키. */
    val key: String get() = episodes.joinToString("+") { it.id }
}

/**
 * [PlanEpisodeMergeUseCase]
 * 같은 일이 30분 무활동 경계로 끊긴 에피소드를 찾아 통합 **제안**을 만듭니다 — 적용은 하지 않는다 (0.31.0, 기억 정리 2차).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Cleanup)
 * - **Dependencies**: [EpisodeRepository], [ModelRunner](oneShot 판정)
 *
 * ### Key Flow
 * 1. SUMMARIZED 에피소드를 시작 시각순으로 놓고 **연속한 쌍**만 본다(앞 끝 ~ 뒤 시작 ≤ 24h — 사용자 결정 D1).
 * 2. 후보 규칙(exp44): 제목+태그 바이그램 containment ≥ 0.3 **또는** 공유 태그 ≥ 2.
 * 3. 쌍마다 oneShot 판정(exp44 원문). "같음"이 이어지면 한 묶음(A~B~C)으로 제안한다.
 *
 * [WHY] `oneShot = true` — 부수 계산이다. 빼면 채팅 KV 가 파괴된다 (ADR-010·014).
 */
class PlanEpisodeMergeUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val modelRunner: ModelRunner
) {

    suspend operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): AppResult<List<EpisodeMergeProposal>> {
        val episodes = when (val loaded = episodeRepository.getByStatus(EpisodeStatus.SUMMARIZED)) {
            is AppResult.Success -> loaded.data.sortedBy { it.startAt }
            is AppResult.Failure -> return AppResult.Failure(loaded.error)
        }
        val candidates = (0 until episodes.size - 1).filter { isCandidate(episodes[it], episodes[it + 1]) }
        onProgress(0, candidates.size)

        val joined = mutableSetOf<Int>()
        candidates.forEachIndexed { index, i ->
            if (judgeSame(episodes[i], episodes[i + 1], zoneId)) joined += i
            onProgress(index + 1, candidates.size)
        }

        // 연속한 "같음" 간선을 한 묶음으로: i 가 joined 면 i 와 i+1 이 한 묶음.
        val proposals = mutableListOf<EpisodeMergeProposal>()
        var current = mutableListOf<Episode>()
        for (i in episodes.indices) {
            if (current.isEmpty()) current.add(episodes[i])
            if (i in joined) {
                current.add(episodes[i + 1])
            } else {
                if (current.size >= 2) proposals += EpisodeMergeProposal(current.toList())
                current = mutableListOf()
            }
        }
        return AppResult.Success(proposals)
    }

    private suspend fun judgeSame(a: Episode, b: Episode, zoneId: ZoneId): Boolean {
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = JUDGE_SYSTEM,
            history = emptyList(),
            currentInput = "대화 A: ${describe(a, zoneId)}\n대화 B: ${describe(b, zoneId)}",
            oneShot = true
        )
        val text = (modelRunner.generate(prompt) as? AppResult.Success)?.data?.text ?: return false
        return VERDICT.find(text)?.groupValues?.get(1) == SAME
    }

    companion object {
        const val SESSION_ID = "episode-merge"
        private const val MAX_GAP_MS = 24L * 60 * 60 * 1000
        private const val MIN_OVERLAP = 0.3
        private const val MIN_SHARED_TAGS = 2
        private const val SAME = "같음"
        private val VERDICT = Regex("판정\\s*[:：]\\s*(같음|다름)")

        /**
         * 연속한 두 에피소드가 통합 후보인가 — exp44: containment 만이면 5/6("주말 러닝 계획 ↔ 러닝 준비물" 0.17 놓침),
         * 공유 태그 ≥ 2 를 더하면 6/6. 요약까지 넣으면 오히려 4/6 이라 제목+태그만 본다.
         */
        fun isCandidate(a: Episode, b: Episode): Boolean {
            if (b.startAt - (a.endAt ?: a.startAt) > MAX_GAP_MS) return false
            val textA = a.title.orEmpty() + " " + a.tags.joinToString(" ")
            val textB = b.title.orEmpty() + " " + b.tags.joinToString(" ")
            if (BigramMatcher.containment(textA, textB) >= MIN_OVERLAP) return true
            return a.tags.map { it.trim() }.toSet().intersect(b.tags.map { it.trim() }.toSet()).size >= MIN_SHARED_TAGS
        }

        /** exp44 입력 형식 — "9월 24일 10시 · 제목: … · 태그: … · 요약: …". */
        fun describe(e: Episode, zoneId: ZoneId): String {
            val t = Instant.ofEpochMilli(e.startAt).atZone(zoneId)
            return "${t.monthValue}월 ${t.dayOfMonth}일 ${t.hour}시 · 제목: ${e.title.orEmpty()} · " +
                "태그: ${e.tags.joinToString(", ")} · 요약: ${e.summary.orEmpty()}"
        }

        /** exp44 원문 — 바꾸면 그 실측(참 6/6·오병합 1)이 무효가 된다. */
        val JUDGE_SYSTEM = """
            너는 대화 기록 정리 도우미다. 아래 두 대화 요약이 같은 일이 이어진 것인지 판정한다.
            같은 일이란 같은 약속·작업·계획·문제를 계속 이야기한 것이다. 분야가 같아도(둘 다 일정, 둘 다 비밀번호) 다른 일이면 '다름'이다.
            출력은 정확히 한 줄: 판정: 같음 또는 다름
        """.trimIndent()
    }
}

/**
 * [ApplyEpisodeMergeUseCase]
 * 체크한 통합 제안을 적용합니다 — **재요약을 먼저** 하고 문서가 정확히 1편일 때만 DB 를 바꾼다 (0.31.0, exp44 갈린 점).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Cleanup)
 * - **Dependencies**: [ConversationRepository], [EpisodeRepository], [SummarizeEpisodeUseCase](exp33 검증 프롬프트 재사용), [AuditTrailService]
 *
 * [WHY] 요약이 2편 이상이면 모델이 "서로 무관한 주제"로 본 것이다 — 판정과 엇갈린 신호라 합치지 않는다. DB 를 건드리기 전에
 * 끝나므로 되돌릴 것이 없다. 자동 추출(C′2)은 부르지 않는다 — 이미 추출된 사실을 다시 제안하게 된다.
 */
class ApplyEpisodeMergeUseCase @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val episodeRepository: EpisodeRepository,
    private val summarizeEpisode: SummarizeEpisodeUseCase,
    private val auditTrailService: AuditTrailService
) {

    /** @return 실제로 합친 제안 수 */
    suspend operator fun invoke(proposals: List<EpisodeMergeProposal>, now: Long = System.currentTimeMillis()): Int {
        var applied = 0
        for (proposal in proposals) {
            val messages = proposal.episodes.flatMap { ep ->
                (conversationRepository.getByEpisode(ep.id) as? AppResult.Success)?.data.orEmpty()
            }.sortedBy { it.createdAt }
            if (messages.isEmpty()) continue
            val doc = (summarizeEpisode(messages) as? AppResult.Success)?.data?.singleOrNull() ?: continue

            val first = proposal.episodes.first()
            val merged = first.copy(
                status = EpisodeStatus.SUMMARIZED,
                title = doc.title,
                tags = doc.tags,
                summary = doc.summary,
                startAt = proposal.episodes.minOf { it.startAt },
                endAt = proposal.episodes.mapNotNull { it.endAt }.maxOrNull() ?: first.endAt,
                messageCount = messages.size,
                updatedAt = now
            )
            val ok = proposal.episodes.drop(1).all { episodeRepository.mergeInto(it.id, merged) is AppResult.Success }
            if (!ok) continue
            applied++
            auditTrailService.logToolCall(
                sessionId = PlanEpisodeMergeUseCase.SESSION_ID,
                toolName = "EpisodeMerge",
                resultJson = proposal.episodes.joinToString(" + ") { it.title.orEmpty() } + " → " + doc.title
            )
        }
        return applied
    }
}
