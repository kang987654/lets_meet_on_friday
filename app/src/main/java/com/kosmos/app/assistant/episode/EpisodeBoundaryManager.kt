package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.modelrunner.ConversationResetEvent
import com.kosmos.app.domain.modelrunner.ModelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EpisodeBoundaryManager]
 * 대화 타임라인의 에피소드 경계를 감지하고 열림/닫힘 상태를 관리합니다 (ADR-022).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Episode)
 * - **Dependencies**: [EpisodeRepository], [ConversationRepository], [ModelRunner]
 *
 * ### Key Flow
 * 1. [onUserMessage] — 유저 메시지 저장 직전(orchestrator)에 호출. 직전 메시지와 30분 이상
 *    벌어졌으면 열린 에피소드를 닫고 새로 연다. 배정할 episodeId 를 돌려준다.
 * 2. 런타임의 [ModelRunner.conversationResets] 를 구독 — **TOKEN_BUDGET 리셋만** 경계로
 *    취급한다(웹 검색 토글·응답 스타일 변경으로 주제를 가르면 안 된다).
 * 3. 닫힌 에피소드는 [closedEpisodes] 로 방출 — 요약 스케줄러가 구독한다. DB status 가
 *    진실이므로 방출이 유실돼도 catch-up 이 복원한다.
 */
@Singleton
class EpisodeBoundaryManager @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val conversationRepository: ConversationRepository,
    modelRunner: ModelRunner
) {

    /** 닫힘 트리거 — 스케줄러가 실행 시점을 정하는 데 쓴다 (무활동=즉시, 리셋=지연). */
    enum class CloseTrigger { IDLE, RESET }

    data class ClosedEpisode(val episodeId: String, val trigger: CloseTrigger)

    private val _closedEpisodes = MutableSharedFlow<ClosedEpisode>(extraBufferCapacity = 16)
    val closedEpisodes: SharedFlow<ClosedEpisode> = _closedEpisodes

    // [WHY] 경계 판정과 열기/닫기는 직렬이어야 한다 — 리셋 이벤트(런타임 스레드)와
    // onUserMessage(오케스트레이터)가 겹치면 OPEN 에피소드가 둘 생길 수 있다.
    private val mutex = Mutex()

    // [WHY] 리셋 구독은 프로세스 수명 스코프가 필요하다. GemmaModelRunner 의 watchdogScope 와
    // 같은 전례 — @Singleton 이므로 이 스코프도 프로세스와 함께 산다.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            modelRunner.conversationResets.collect { event ->
                // [WHY] TOKEN_BUDGET 만 경계다. 나머지 사유는 대화 주제와 무관한 설정 변경이다.
                if (event.reason == ConversationResetEvent.Reason.TOKEN_BUDGET) {
                    onBudgetReset(event.sessionId)
                }
            }
        }
    }

    /**
     * 유저 메시지 저장 직전 훅. 배정할 episodeId 를 돌려줍니다 (실패 시 null — 배정 없이
     * 저장하고 catch-up 이 소급한다: 에피소드 배선 문제가 채팅 자체를 막으면 안 된다).
     */
    suspend fun onUserMessage(sessionId: String, now: Long): String? =
        boundaryFor(sessionId, now)

    /**
     * 비서가 스스로 시작한 발화(아침 브리핑, A4)용 훅 — 판정은 [onUserMessage] 와 동일하다.
     *
     * [WHY] 브리핑을 episodeId=null 로 저장하면 catch-up 이 비서 발화 1줄짜리 CLOSED
     * 에피소드를 소급 생성한다 — 무의미한 요약 추론 1회 + 아카이브 오염이 매일 쌓인다.
     * 여기서 새 OPEN 에피소드를 열어 두면 사용자의 답이 같은 에피소드에 붙는다.
     */
    suspend fun onAssistantInitiatedMessage(sessionId: String, now: Long): String? =
        boundaryFor(sessionId, now)

    private suspend fun boundaryFor(sessionId: String, now: Long): String? = mutex.withLock {
        try {
            val open = openEpisodeOf(sessionId)
            val lastAt = lastMessageAt(sessionId)

            if (open != null && lastAt != null && now - lastAt > IDLE_BOUNDARY_MS) {
                close(open, endAt = lastAt, trigger = CloseTrigger.IDLE)
                return@withLock newEpisode(sessionId, startAt = now).id
            }
            (open ?: newEpisode(sessionId, startAt = now)).id
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "경계 판정 실패 — 미배정으로 저장", e)
            null
        }
    }

    private suspend fun onBudgetReset(sessionId: String) = mutex.withLock {
        try {
            val open = openEpisodeOf(sessionId) ?: return@withLock
            // [WHY] endAt 은 마지막 메시지 시각 — 리셋 시각(지금)이 아니다. 리셋은 다음 턴의
            // 프리필 시점에 일어나므로 "지금"은 이미 새 주제의 시작일 수 있다.
            val endAt = lastMessageAt(sessionId) ?: open.startAt
            close(open, endAt = endAt, trigger = CloseTrigger.RESET)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "리셋 경계 처리 실패", e)
        }
    }

    private suspend fun openEpisodeOf(sessionId: String): Episode? =
        (episodeRepository.getByStatus(EpisodeStatus.OPEN) as? AppResult.Success)
            ?.data?.firstOrNull { it.sessionId == sessionId }

    private suspend fun lastMessageAt(sessionId: String): Long? =
        (conversationRepository.getRecentBySession(sessionId, 1) as? AppResult.Success)
            ?.data?.lastOrNull()?.createdAt

    private suspend fun close(episode: Episode, endAt: Long, trigger: CloseTrigger) {
        val messages = (conversationRepository.getByEpisode(episode.id) as? AppResult.Success)
            ?.data.orEmpty()
        val closed = episode.copy(
            status = EpisodeStatus.CLOSED,
            endAt = endAt,
            messageCount = messages.size,
            updatedAt = System.currentTimeMillis()
        )
        episodeRepository.update(closed)
        _closedEpisodes.tryEmit(ClosedEpisode(episode.id, trigger))
    }

    private suspend fun newEpisode(sessionId: String, startAt: Long): Episode {
        val now = System.currentTimeMillis()
        val episode = Episode(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            status = EpisodeStatus.OPEN,
            title = null,
            summary = null,
            tags = emptyList(),
            startAt = startAt,
            endAt = null,
            messageCount = 0,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        episodeRepository.insert(episode)
        return episode
    }

    companion object {
        private const val TAG = "EpisodeBoundary"

        /**
         * 무활동 경계 (30분).
         *
         * [WHY] exp33 실측 — 실기기 대화 79개를 이 간격으로 자르면 에피소드 9개가 나오고 전부
         * 주제가 일관했다(육안 판정). 값을 바꾸려면 exp33 segment 를 다시 돌려 볼 것.
         */
        const val IDLE_BOUNDARY_MS = 30 * 60 * 1000L
    }
}
