package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    
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
