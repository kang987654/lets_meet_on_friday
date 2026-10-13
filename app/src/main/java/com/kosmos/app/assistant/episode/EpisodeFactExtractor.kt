package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.memory.KnowledgeAutoSaveFilter
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.memory.ProfileSuggestionRepository
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.domain.usecase.ExtractFactsUseCase
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.runtime.metrics.shouldDeferBackgroundInference
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * [EpisodeFactExtractor]
 * 요약이 끝난 에피소드에서 기억할 사실을 뽑아 저장합니다 — 리셋 시점 자동 추출 (C′2).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Episode)
 * - **Dependencies**: [ExtractFactsUseCase], [SaveKnowledgeUseCase], [KnowledgeRepository],
 *   [ProfileRepository], [ProfileSuggestionRepository], [SettingsDataStore], [AuditTrailService], [AuditRepository]
 *
 * ### Key Flow
 * 1. 설정 토글·발열 게이트 → oneShot 추출([ExtractFactsUseCase])
 * 2. 지식 급: 중복(내용 포함 검색)이 아니고 [KnowledgeAutoSaveFilter] 를 통과하면 `source=auto` 로 **자동 저장**
 *    (거절한 내용·기존 메모와 다른 숫자·일회성 리마인더 요청·사용자가 말한 적 없는 숫자는 건너뜀, 태그는 내용에 든 것만)
 * 3. 프로필 급: 현재 프로필과 같은 값·이미 제안된 키-값이 아니면 PENDING 제안(승인 카드 몫)
 * 4. 감사 MODEL_RUN 1건(원문 미기록, 개수만)
 *
 * [WHY] 자체 구독이 없는 일반 클래스다 — [EpisodeSummarizeScheduler] 가 요약 성공(SUMMARIZED)
 * 직후 같은 드레인에서 부른다. "리셋 직전" 훅은 코드상 추론 불가(리셋은 다음 턴 프리필 안,
 * llmDispatcher 직렬)이므로 요약 파이프라인에 태우는 것이 유일한 경로다.
 *
 * [WHY] 지식 자동 저장은 G3(쓰기=승인)의 의도적 예외다 — 툴 콜(모델이 대화 중 쓰기)이 아니라
 * 사후 캡처이고, 검색으로만 회수되는 저위험 층이다. 통제 장치 = 출처 배지·기억 화면 삭제·
 * 설정 토글 (사용자 결정 2026-09-02).
 *
 * [WHY] 실패는 로그만, 재시도 없음 — 원문과 요약은 이미 남아 있어 손실이 없고, 지식은 다음
 * 에피소드에서 또 나온다.
 */
class EpisodeFactExtractor @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val extractFacts: ExtractFactsUseCase,
    private val saveKnowledge: SaveKnowledgeUseCase,
    private val knowledgeRepository: KnowledgeRepository,
    private val profileRepository: ProfileRepository,
    private val suggestionRepository: ProfileSuggestionRepository,
    private val auditTrailService: AuditTrailService,
    private val metricsCollector: RuntimeMetricsCollector,
    private val auditRepository: AuditRepository
) {

    /** 저장 결과 — 테스트·감사용 개수. */
    data class Outcome(val savedKnowledge: Int, val suggestedProfile: Int)

    suspend fun extract(
        episode: Episode,
        messages: List<ChatMessage>,
        now: Long = System.currentTimeMillis()
    ): Outcome? {
        if (!settingsDataStore.autoExtractEnabledFlow.first()) return null
        // [WHY] 발열 재확인 — 요약이 방금 추론 1회를 썼다. 경고 온도면 이 에피소드는 건너뛴다
        // (미루지 않음: 요약과 달리 재시도 큐가 없고, 다음 에피소드가 곧 온다).
        if (metricsCollector.shouldDeferBackgroundInference()) {
            AppLogger.w(TAG, "발열로 자동 추출 건너뜀: ${episode.id}")
            return null
        }

        val facts = when (val result = extractFacts(messages)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.w(TAG, "자동 추출 실패(${episode.id}): ${result.error}")
                return null
            }
        }

        var savedKnowledge = 0
        var filtered = 0
        val rejected = if (facts.knowledge.isEmpty()) emptyList() else rejectedMemories(episode, now)
        val existing = if (facts.knowledge.isEmpty()) emptyList()
        else (knowledgeRepository.getNotes(0, Constants.MEMORY_SCAN_LIMIT) as? AppResult.Success)?.data.orEmpty()
        val userText = messages.filter { it.role == ChatMessage.Role.USER }.joinToString("\n") { it.content }
        for (fact in facts.knowledge) {
            // [WHY] 내용 포함 검색으로 중복을 막는다 — 같은 비밀번호를 말한 에피소드가 둘이면
            // 둘째는 건너뛴다. 주기적 병합(A5)까지의 값싼 1차 방어.
            val duplicate = (knowledgeRepository.search(fact, 1) as? AppResult.Success)?.data?.isNotEmpty() ?: false
            if (duplicate) continue
            val skip = KnowledgeAutoSaveFilter.skipReason(fact, rejected, existing, userText)
            if (skip != null) {
                AppLogger.d(TAG, "자동 저장 건너뜀($skip): ${episode.id}")
                filtered++
                continue
            }
            val tags = KnowledgeAutoSaveFilter.tagsFor(fact, episode.tags, MAX_TAGS)
            if (saveKnowledge(fact, tags, KnowledgeNote.SOURCE_AUTO) is AppResult.Success) {
                savedKnowledge++
            }
        }

        val current = (profileRepository.getEntries() as? AppResult.Success)?.data.orEmpty()
            .associate { it.key to it.value }
        var suggested = 0
        for ((key, value) in facts.profile) {
            if (current[key] == value) continue
            // [WHY] 어느 상태로든 이미 제안된 키-값이면 다시 묻지 않는다 — 거절이 존중된다.
            if (suggestionRepository.exists(key, value)) continue
            val suggestion = ProfileSuggestion(
                id = UUID.randomUUID().toString(),
                key = key,
                value = value,
                episodeId = episode.id,
                status = ProfileSuggestionStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
            if (suggestionRepository.insert(suggestion) is AppResult.Success) suggested++
        }

        // [WHY] 원문은 기록하지 않는다(브리핑 전례) — 개수만. 감사 화면에서 "언제 무엇을 몇 개"가 보이면 충분하다.
        auditTrailService.logModelRun(
            sessionId = ExtractFactsUseCase.SESSION_ID,
            prompt = "fact extraction (episode=${episode.id}, messages=${messages.size})",
            output = "profile=${facts.profile.size} knowledge=${facts.knowledge.size} " +
                "savedKnowledge=$savedKnowledge filtered=$filtered suggested=$suggested"
        )
        return Outcome(savedKnowledge = savedKnowledge, suggestedProfile = suggested)
    }

    /**
     * 이 에피소드 구간에서 사용자가 거절한 `add_memory` 승인 설명들.
     *
     * [WHY] 거절은 대화 기록에 남지 않고(비서가 "기억하지 않았다"고 말할 뿐) 감사 기록에만 있다 — 추출 재료인 전사로는 알 수 없어
     * 감사를 읽는다. 조회 실패는 "거절 없음"으로 강등한다(추출을 막지 않는다).
     */
    private suspend fun rejectedMemories(episode: Episode, now: Long): List<String> {
        val prefix = "${ToolNames.ADD_MEMORY}:"
        val events = auditRepository.getEventsBetween(AuditEventType.APPROVAL_REJECTED, episode.startAt, episode.endAt ?: now)
        return (events as? AppResult.Success)?.data.orEmpty()
            .filter { it.details.startsWith(prefix) }
            .map { it.details.removePrefix(prefix).trim() }
    }

    private companion object {
        const val TAG = "EpisodeFactExtractor"
        // 지식 태그 검색은 정확 매칭이라 많을수록 잡음이 는다.
        const val MAX_TAGS = 3
    }
}
