package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>

    /**
     * 살아있는 대화가 리셋(파기 후 재생성)될 때의 이벤트 스트림입니다.
     *
     * [WHY] 기본 구현이 "영원히 침묵하는 흐름"인 이유: 테스트 fake 들이 이 계약을 구현할 필요가
     * 없게 하기 위해서다 — 리셋 개념이 없는 fake 에서 방출할 것도 없다. 실제 런타임
     * (GemmaModelRunner)만 상태 있는 MutableSharedFlow 로 재정의한다.
     */
    val conversationResets: SharedFlow<ConversationResetEvent>
        get() = MutableSharedFlow()

    suspend fun generate(
        prompt: ChatPrompt,
        onToken: ((String) -> Unit)? = null
    ): AppResult<ModelTurn>

    /**
     * [WHY] `imageTokenBudget: Int = 280` 파라미터를 **제거했다.** 계약부터 구현까지 4개 파일을
     * 거쳐 배관됐지만 `GemmaModelRunner` 구현 본문에서 **한 번도 읽히지 않았다** — 동작하는
     * 조절 장치처럼 보여서 누가 값을 바꿔도 아무 일이 없는 함정이었다. 시각 토큰 예산이 실제로
     * 필요해지면 litertlm 의 `ExperimentalFlags.visualTokenBudget` 을 쓰면 된다.
     */
    suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        onToken: ((String) -> Unit)? = null
    ): AppResult<ModelTurn>

    suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)? = null
    ): AppResult<ModelTurn>

    /** 진행 중인 생성을 끊는다. 턴 사이(생성 전)에 불리면 다음 채팅 턴을 시작하지 않는다. */
    suspend fun cancel()

    /** 새 요청을 시작할 때 지난 요청의 늦게 도착한 [cancel] 을 지운다. */
    fun clearPendingCancel() {}

    suspend fun warmUp()
    fun close()

    /**
     * 다음 채팅 턴에서 대화 문맥을 DB 히스토리로 **다시 만들게** 한다 — 화면에서 고친 사실(예: 알림 미룸)을 모델이 알게(0.38.2 M4).
     *
     * [WHY] 기본 구현은 아무것도 하지 않는다 — 테스트 가짜 러너가 그대로 컴파일되게. 대화를 캐시하는 런타임이 덮어쓴다.
     */
    fun invalidateConversation() {}

    /**
     * 다음 채팅 턴이 쓸 대화를 **지금** 준비한다 — 다시 만들어야 하는 대화(재생성 표식·예산 초과)일 때만, 아니면 아무것도 하지 않는다.
     * [prompt] 는 다음 턴과 같은 지시·툴·기록이다(입력 문장은 쓰지 않는다).
     *
     * [WHY] 다시 만드는 비용(실기기 +6~7초)을 다음 질문이 아니라 답을 읽는 동안 치른다(0.39.2 M3). 기본 구현은 테스트 가짜 러너용.
     */
    suspend fun prepareConversation(prompt: ChatPrompt) {}

    /**
     * 연속된 `oneShot` 부수 추론 묶음 동안 매 호출 끝의 채팅 대화 복원(`getTokenCount`)을 미룹니다 (0.40.6 M3, exp73 defer).
     */
    fun beginDeferRestore() {}

    /**
     * [beginDeferRestore] 로 시작한 묶음을 마칩니다. 중첩 깊이가 0 이 되고 [restore] 가 `true` 이며
     * 그사이 `oneShot` 추론이 1회 이상 돌았으면(대화가 stale 이 아닐 때) 채팅 대화를 한 번 복원합니다.
     */
    suspend fun endDeferRestore(restore: Boolean = true) {}
}

/**
 * [block] 안의 연속된 `oneShot` 추론에서 매번 치르던 채팅 대화 복원(`getTokenCount`)을 묶음 끝 1회로 미룹니다 (0.40.6 M3).
 *
 * [WHY] `PlanMemoryMergeUseCase`·`EpisodeSummarizeScheduler` 처럼 어느 `oneShot` 호출이 마지막인지 미리 알 수 없는 배치에서
 * 유스케이스나 `ChatPrompt` 시그니처를 오염시키지 않고 호출부 스코프만으로 중간 복원을 생략한다 (exp73 defer: 3회 연속 시 복원 9.40s → 3.99s).
 * [block] 이 끝까지 돌았을 때만 복원한다 — 취소·예외는 물론 바깥 람다로의 비지역 반환(`return@withLock` 등: 발열 중단·사용자
 * 끼어들기)으로 빠져나가도 `restore = false` 로 건너뛰고, 그 비용은 다음 채팅 턴의 첫 호출이 치른다.
 */
suspend inline fun <T> ModelRunner.deferRestore(block: suspend () -> T): T {
    beginDeferRestore()
    var completedNormally = false
    return try {
        block().also { completedNormally = true }
    } finally {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            endDeferRestore(restore = completedNormally)
        }
    }
}
