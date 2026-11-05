package com.kosmos.app.platform.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.library.EmbedderProvider
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.memory.MemoryVectorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MemoryEmbedder]
 * 기억(메모·요약된 지난 대화)의 뜻 벡터를 만듭니다 (0.41.3) — 처음은 사용자가 시작하고, 그 뒤 새 기억은 조용할 때 자동.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Memory) — [MemoryVectorRepository](대상·저장·변경 신호), [EmbedderProvider](설치됐을 때만).
 * - 앱 스코프 코루틴. 백그라운드에서는 돌지 않는다(KosmosApp 의 전경·후경 알림, AGENTS §2-⑥).
 *
 * ### Key Flow
 * 1. [prepareAll] — "기억 뜻 검색 준비" 버튼: 지금 모델의 벡터가 없는 기억 전부.
 * 2. 자동 — 지금 모델의 벡터가 하나라도 있으면(= 사용자가 한 번 준비했다) 답이 끝날 때·앱이 화면에 올 때
 *    [Constants.EMBED_RESUME_DELAY_MS] 뒤 모자란 것만 만든다.
 * 3. 채팅이 시작되면 [onUserActivity] 로 멈추고, 답이 끝나면 [onTurnFinished] 가 같은 시간 뒤 잇는다. 앱이 백그라운드로 가면 [onBackground] 로 멈춘다.
 *
 * [WHY] "한 번 눌렀는가"를 따로 저장하지 않는다 — 지금 모델의 벡터가 있다는 것이 곧 그 표시다. 모델을 지우면 벡터도 지워져 다시 버튼부터(0.41.3 Q2).
 * [WHY] Room 의 표 변경 Flow 를 구독하지 않는다 — 앱 수명 내내 구독하면 Robolectric 이 띄운 앱(KosmosApp)이 테스트가 닫은 DB 를 계속 읽어 다음 테스트로 예외가
 * 샜다(0.41.3 M2 게이트). 기억이 바뀌는 길은 모두 위 계기 중 하나로 이어진다(메모 화면에서 고친 것·대화 요약은 다음 계기에 — 그 사이 준비 판정이 미완이라 검색은 어휘로 돌아간다).
 * [WHY] 새 메모 저장 경로에서 바로 임베딩하지 않는다 — 임베딩 엔진(GPU)을 답 생성과 겹치게 올리면 버벅임·발열(0.40.7), 저장이 엔진 로드를 기다린다.
 */
@Singleton
class MemoryEmbedder @Inject constructor(
    private val repository: MemoryVectorRepository,
    private val embedders: EmbedderProvider
) {
    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data object Failed : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // [WHY] 테스트가 기다림을 줄이는 손잡이 — Hilt 생성자에 Long 을 넣으면 바인딩이 필요해진다(LibraryEmbedder 와 같은 결).
    internal var resumeDelayMs: Long = Constants.EMBED_RESUME_DELAY_MS

    private var job: Job? = null
    @Volatile private var foreground = false
    @Volatile private var chatting = false

    /** 버튼 — 지금 모델의 벡터가 없는 기억 전부, 기다리지 않고 바로. */
    @Synchronized
    fun prepareAll() {
        if (embedders.current() == null) return
        job?.cancel()
        job = scope.launch { drain() }
    }

    /** 앱이 화면에 왔다 — 모자란 것이 있으면 조용해진 뒤 만든다. */
    fun onForeground() {
        foreground = true
        requestAuto()
    }

    /** 앱이 백그라운드로 갔다 — 하던 일을 멈춘다(다음 전경·버튼이 남은 것부터). */
    @Synchronized
    fun onBackground() {
        foreground = false
        stop()
    }

    /** 채팅이 시작됐다 — 하던 일과 기다리던 일을 멈춘다. */
    @Synchronized
    fun onUserActivity() {
        chatting = true
        stop()
    }

    /** 답이 끝났다 — 조용해진 뒤 모자란 것을 만든다(대화 요약으로 새 기억이 생겼을 수 있다). */
    fun onTurnFinished() {
        chatting = false
        requestAuto()
    }

    @Synchronized
    private fun stop() {
        job?.cancel()
        job = null
        if (_state.value is State.Running) _state.value = State.Idle
    }

    @Synchronized
    private fun requestAuto() {
        if (!foreground || chatting || job?.isActive == true) return
        val embedder = embedders.current() ?: return
        job = scope.launch {
            delay(resumeDelayMs)
            val status = (repository.status(embedder.modelId) as? AppResult.Success)?.data ?: return@launch
            // [WHY] 한 번도 준비하지 않았으면(벡터 0) 자동으로 시작하지 않는다 — 처음은 사용자가 버튼으로(Q2).
            if (status.embedded == 0 || status.pending == 0) return@launch
            drain()
        }
    }

    private suspend fun drain() {
        val embedder = embedders.current() ?: return
        val total = (repository.status(embedder.modelId) as? AppResult.Success)?.data?.pending ?: 0
        var done = 0
        _state.value = State.Running(done, total)
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = (repository.pending(embedder.modelId, BATCH) as? AppResult.Success)?.data ?: return failed("대상 읽기 실패")
            if (batch.isEmpty()) {
                _state.value = State.Idle
                return
            }
            val vectors = embedder.embedDocuments(batch.map { it.input }) ?: return failed("임베딩 실패")
            currentCoroutineContext().ensureActive()
            if (vectors.size != batch.size) return failed("임베딩 수 불일치")
            if (repository.save(embedder.modelId, batch.zip(vectors) { input, v -> input.key to Int8Vector.quantize(v) }) is AppResult.Failure) {
                return failed("벡터 저장 실패")
            }
            done += batch.size
            _state.value = State.Running(done, maxOf(total, done))
        }
    }

    private fun failed(reason: String) {
        AppLogger.w(TAG, "기억 뜻 검색 준비 멈춤: $reason")
        _state.value = State.Failed
    }

    private companion object {
        const val TAG = "MemoryEmbedder"

        /** 묶음 크기 — 저장 단위이자 멈춤·취소의 반응 단위(LibraryEmbedder 와 같다). */
        const val BATCH = 16
    }
}
