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

    suspend fun cancel()
    suspend fun warmUp()
    fun close()
}
