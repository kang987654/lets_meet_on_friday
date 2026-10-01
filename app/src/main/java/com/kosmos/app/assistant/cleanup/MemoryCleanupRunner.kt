package com.kosmos.app.assistant.cleanup

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.cleanup.ApplyEpisodeMergeUseCase
import com.kosmos.app.domain.cleanup.ApplyMemoryMergeUseCase
import com.kosmos.app.domain.cleanup.EpisodeMergeProposal
import com.kosmos.app.domain.cleanup.GenerateWeeklyReviewUseCase
import com.kosmos.app.domain.cleanup.MergeProposal
import com.kosmos.app.domain.cleanup.PlanEpisodeMergeUseCase
import com.kosmos.app.domain.cleanup.PlanMemoryMergeUseCase
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import com.kosmos.app.runtime.metrics.shouldDeferBackgroundInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MemoryCleanupRunner]
 * 설정의 "기억 정리" 버튼이 시작하는 수동 정리 — 주간 회고 → 병합 제안 → (사용자 체크) → 적용 (0.30.0, expand.md A5 대체).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Cleanup)
 * - **Dependencies**: [GenerateWeeklyReviewUseCase], [PlanMemoryMergeUseCase], [ApplyMemoryMergeUseCase],
 *   [BackgroundInferenceGate](자동 요약과 직렬화), [RuntimeMetricsCollector](발열), [ModelRunner](Ready 확인)
 *
 * ### Key Flow
 * 1. [start] — 발열·엔진 상태를 먼저 본다(아니면 [State.Blocked]). 잠금을 쥐고(드레인 중이면 끝날 때까지 대기) 회고 → 노트 병합 계획 → 에피소드 통합 계획(0.31.0).
 * 2. 결과는 [State.Review] — 회고 노트와 병합 제안. 적용은 사용자가 체크한 것만 [apply].
 * 3. [cancel] — 사용자 취소 또는 앱 onStop(엔진 해제). 부분 적용은 없다(적용은 Review 이후 별도 단계).
 *
 * [WHY] @Singleton 범위에서 돈다 — 정리 화면을 떠나도 계속되고, 돌아오면 상태를 다시 본다. 다만 엔진이 해제되는
 * onStop 에서는 취소한다(§2-⑥: 백그라운드 모델 로드 금지). 다시 누르면 처음부터 — 회고는 주차 키로 교체, 병합은 새로 계산(멱등).
 */
@Singleton
class MemoryCleanupRunner @Inject constructor(
    private val gate: BackgroundInferenceGate,
    private val generateWeeklyReview: GenerateWeeklyReviewUseCase,
    private val planMerge: PlanMemoryMergeUseCase,
    private val applyMerge: ApplyMemoryMergeUseCase,
    private val planEpisodeMerge: PlanEpisodeMergeUseCase,
    private val applyEpisodeMerge: ApplyEpisodeMergeUseCase,
    private val metricsCollector: RuntimeMetricsCollector,
    private val modelRunner: ModelRunner
) {
    /** 정리 진행 단계. */
    enum class Step { WEEKLY_REVIEW, MERGE, EPISODE_MERGE }

    /** 화면이 그리는 정리 상태. */
    sealed interface State {
        data object Idle : State
        data object Waiting : State
        data class Running(val step: Step, val done: Int, val total: Int) : State
        data class Review(
            val weeklyReview: KnowledgeNote?,
            val proposals: List<MergeProposal>,
            val episodeProposals: List<EpisodeMergeProposal> = emptyList()
        ) : State
        /** 체크한 것을 적용하는 중 — 에피소드 통합은 재요약 추론이 있어 수십 초 걸린다. */
        data object Applying : State
        data class Done(
            val weeklyReview: KnowledgeNote?,
            val merged: Int,
            val episodesMerged: Int = 0,
            /** 체크했지만 재요약이 두 주제로 갈려(또는 실패해) 합치지 않은 대화 묶음 수. */
            val episodesSkipped: Int = 0
        ) : State
        data class Blocked(val reason: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 정리를 시작합니다. 이미 도는 중이면 무시한다. */
    fun start() {
        if (job?.isActive == true) return
        blockReason()?.let {
            _state.value = State.Blocked(it)
            return
        }
        _state.value = State.Waiting
        job = scope.launch {
            gate.mutex.withLock {
                _state.value = State.Running(Step.WEEKLY_REVIEW, 0, 1)
                val review = when (val result = generateWeeklyReview()) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> {
                        AppLogger.w(TAG, "주간 회고 실패: ${result.error}")
                        null
                    }
                }
                // [WHY] 단계 사이에 발열을 다시 본다 — 회고 뒤 병합 판정은 수십 번 추론이라 이 지점이 마지막 기회다.
                blockReason()?.let {
                    _state.value = State.Blocked(it)
                    return@withLock
                }
                _state.value = State.Running(Step.MERGE, 0, 0)
                val proposals = when (val result = planMerge { done, total -> _state.value = State.Running(Step.MERGE, done, total) }) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> {
                        AppLogger.w(TAG, "병합 계획 실패: ${result.error}")
                        emptyList()
                    }
                }
                blockReason()?.let {
                    _state.value = State.Blocked(it)
                    return@withLock
                }
                _state.value = State.Running(Step.EPISODE_MERGE, 0, 0)
                val episodeProposals = when (
                    val result = planEpisodeMerge { done, total -> _state.value = State.Running(Step.EPISODE_MERGE, done, total) }
                ) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> {
                        AppLogger.w(TAG, "에피소드 통합 계획 실패: ${result.error}")
                        emptyList()
                    }
                }
                _state.value = State.Review(review, proposals, episodeProposals)
            }
        }
    }

    /**
     * 사용자가 체크한 제안만 적용합니다 — Review 상태에서만.
     * [WHY] 에피소드 통합은 재요약 추론을 하므로 자동 요약 드레인과 같은 잠금 안에서 돈다(같은 에피소드를 동시에 쓰지 않게).
     */
    suspend fun apply(selected: List<MergeProposal>, selectedEpisodes: List<EpisodeMergeProposal> = emptyList()) {
        val review = _state.value as? State.Review ?: return
        _state.value = State.Applying
        val merged = if (selected.isEmpty()) 0 else applyMerge(selected)
        val episodesMerged = if (selectedEpisodes.isEmpty()) 0 else gate.mutex.withLock { applyEpisodeMerge(selectedEpisodes) }
        _state.value = State.Done(review.weeklyReview, merged, episodesMerged, selectedEpisodes.size - episodesMerged)
    }

    /** 취소합니다 — 사용자 취소·앱 onStop. 아직 적용 전이라 데이터는 그대로다. */
    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value !is State.Done) _state.value = State.Idle
    }

    /** 결과 화면을 닫을 때 처음 상태로. */
    fun reset() {
        if (job?.isActive != true) _state.value = State.Idle
    }

    private fun blockReason(): String? = when {
        metricsCollector.shouldDeferBackgroundInference() -> "기기가 뜨거워요. 잠시 식힌 뒤 다시 시도해 주세요."
        modelRunner.loadState.value !is ModelLoadState.Ready -> "AI 모델이 준비되면 다시 시도해 주세요."
        else -> null
    }

    private companion object {
        const val TAG = "MemoryCleanupRunner"
    }
}
