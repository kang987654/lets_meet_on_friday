package com.kosmos.app.platform.document

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.library.EmbedderProvider
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.TextEmbedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LibraryEmbedder]
 * 보관함 조각의 뜻 벡터를 만듭니다 (0.41.2 M3) — 사용자가 시작한 일만 한다(기억 정리와 같은 결, 사용자 결정 2026-11-04).
 *
 * ### Architecture Context
 * - **Layer**: Platform (Document) — [EmbedderProvider](설치됐을 때만 임베더), [LibraryRepository](벡터 만들 조각·저장).
 * - 앱 스코프 코루틴 — 문서 화면을 떠나도 계속, 앱이 백그라운드로 가면 [cancel](KosmosApp onStop — 임베딩 엔진도 그때 내린다).
 *
 * ### Key Flow
 * 1. [prepareAll] — 보관함의 "뜻 검색 준비" 버튼: 벡터 없는 조각 전부. [prepare] — 색인이 끝난 새 문서 하나(LibraryIndexer 가 부른다).
 * 2. [BATCH] 개씩 임베딩 → int8 → 저장. 묶음마다 저장하므로 끊겨도 다음에 남은 조각부터(멱등).
 * 3. 채팅을 시작하면 [onUserActivity] 로 멈추고([State.Paused]), 답이 끝나면 [onTurnFinished] 가 [Constants.EMBED_RESUME_DELAY_MS] 뒤 이어 간다.
 *
 * [WHY] 자동 백그라운드로 돌리지 않는다 — 채팅 중 모르는 사이 연산이 돌면 버벅임·발열이 생긴다(0.40.7). 새 문서까지 수동이면 문서 하나 때문에
 * 보관함 전체가 바이그램으로 돌아가므로 등록에 이어서는 자동이다(그 문서만).
 */
@Singleton
class LibraryEmbedder @Inject constructor(
    private val repository: LibraryRepository,
    private val embedders: EmbedderProvider
) {
    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data class Paused(val done: Int, val total: Int) : State
        data object Failed : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 할 일 하나 — [documentId] 가 null 이면 보관함 전체. */
    private data class Target(val documentId: String?)

    // [WHY] 대상을 감싼다 — "전체"를 null 로 두면 빈 큐의 firstOrNull() 과 구별되지 않아 전체 작업이 "할 일 없음"으로 읽혔다(0.41.2 M3 테스트에서 발견).
    private val queue = ArrayDeque<Target>()
    private val all = Target(null)
    private var job: Job? = null

    /** 버튼 — 벡터 없는 조각 전부. */
    fun prepareAll() = request(null)

    /** 색인이 끝난 문서 하나 — 모델이 없으면 아무것도 하지 않는다. */
    fun prepare(documentId: String) = request(documentId)

    // [WHY] 테스트가 기다림을 줄이는 손잡이 — Hilt 생성자에 Long 을 넣으면 바인딩이 필요해진다(ConversationPrewarmer.quietMs 와 같은 결).
    internal var resumeDelayMs: Long = Constants.EMBED_RESUME_DELAY_MS

    /** 채팅이 시작됐다 — 하던 일(이어 가기 대기 포함)을 멈추고 답이 끝나면 이어 간다. */
    @Synchronized
    fun onUserActivity() {
        when (val current = _state.value) {
            is State.Running -> _state.value = State.Paused(current.done, current.total)
            is State.Paused -> Unit
            else -> return
        }
        job?.cancel()
        job = null
    }

    /** 답이 끝났다 — 멈춘 일이 있으면 [resumeDelayMs] 뒤 이어 간다(그 사이 새 채팅이면 [onUserActivity] 가 다시 미룬다). */
    @Synchronized
    fun onTurnFinished() {
        if (_state.value !is State.Paused || job?.isActive == true) return
        job = scope.launch {
            delay(resumeDelayMs)
            drain()
        }
    }

    /** 앱 백그라운드·사용자 취소 — 남은 일을 버린다(다음 버튼·등록이 남은 조각부터 다시). */
    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
        queue.clear()
        _state.value = State.Idle
    }

    @Synchronized
    private fun request(documentId: String?) {
        if (embedders.current() == null) return
        val target = Target(documentId)
        if (target !in queue && !(documentId != null && all in queue)) queue.addLast(target)
        if (_state.value !is State.Paused) launchIfIdle()
    }

    @Synchronized
    private fun launchIfIdle() {
        if (job?.isActive == true) return
        job = scope.launch { drain() }
    }

    private suspend fun drain() {
        while (true) {
            val target = synchronized(this) { queue.firstOrNull() } ?: run {
                synchronized(this) { if (queue.isEmpty()) _state.value = State.Idle }
                return
            }
            val finished = runTarget(target.documentId)
            if (!finished) return
            synchronized(this) { queue.remove(target) }
        }
    }

    /** 한 대상의 벡터를 다 만들면 true, 실패면 false(상태 [State.Failed]). 취소는 예외로 빠진다. */
    private suspend fun runTarget(documentId: String?): Boolean {
        // [WHY] 그 사이 모델을 지웠으면 할 일이 없다 — 실패가 아니라 끝.
        val embedder = embedders.current() ?: return true
        val total = remainingFor(embedder, documentId)
        var done = 0
        _state.value = State.Running(done, total)
        // [WHY] 이 작업 자신의 취소를 본다 — scope.isActive(앱 스코프)를 보면 멈춘 뒤에도 묶음이 저장되고 Paused 를 Running 으로 덮었다(0.41.2 Q3 테스트에서 발견).
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = (repository.chunksNeedingVectors(embedder.modelId, documentId, BATCH) as? AppResult.Success)?.data
                ?: return failed("조각 읽기 실패")
            if (batch.isEmpty()) return true
            val vectors = embedder.embedDocuments(batch.map { it.input }) ?: return failed("임베딩 실패")
            currentCoroutineContext().ensureActive()
            if (vectors.size != batch.size) return failed("임베딩 수 불일치")
            if (repository.saveVectors(embedder.modelId, batch.zip(vectors) { input, v -> input to Int8Vector.quantize(v) }) is AppResult.Failure) {
                return failed("벡터 저장 실패")
            }
            done += batch.size
            _state.value = State.Running(done, maxOf(total, done))
        }
    }

    private suspend fun remainingFor(embedder: TextEmbedder, documentId: String?): Int {
        if (documentId == null) {
            val status = (repository.semanticStatus(embedder.modelId) as? AppResult.Success)?.data ?: return 0
            return (status.readyChunks - status.embeddedChunks).coerceAtLeast(0)
        }
        val doc = (repository.document(documentId) as? AppResult.Success)?.data
        return if (doc?.status == LibraryStatus.READY) doc.chunkCount else 0
    }

    private fun failed(reason: String): Boolean {
        AppLogger.w(TAG, "뜻 검색 준비 멈춤: $reason")
        synchronized(this) { queue.clear() }
        _state.value = State.Failed
        return false
    }

    private companion object {
        const val TAG = "LibraryEmbedder"

        /** 묶음 크기 — 저장 단위이자 멈춤·취소의 반응 단위. */
        const val BATCH = 16
    }
}
