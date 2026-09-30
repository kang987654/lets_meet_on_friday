package com.kosmos.app.testing

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 정해진 [turns] 를 차례로 돌려주는 [ModelRunner] 대역 — 텍스트는 [chunkSize] 글자씩 토큰으로 흘린다.
 *
 * [WHY] ModelRunner 의 여섯 메서드를 테스트마다 손으로 구현하던 것을 모았다. 이미지·오디오 진입점은
 * 텍스트와 같은 대본을 쓴다(에이전트 루프 검증에는 입력 종류가 상관없다). 프롬프트 **내용에 따라**
 * 응답이 갈려야 하는 대역(ToolApprovalE2ETest 의 승인/거절 분기)은 순번 대본으로 표현할 수 없어
 * 각자 둔다.
 */
class ScriptedModelRunner(
    private val turns: List<ModelTurn>,
    private val chunkSize: Int = 3
) : ModelRunner {
    private var index = 0

    // [WHY] 스레드 안전 목록 — E2E 는 파이프라인 코루틴이 쓰는 동안 테스트 스레드가 폴링하며 읽는다.
    private val prompts = java.util.concurrent.CopyOnWriteArrayList<ChatPrompt>()

    /** 받은 프롬프트 순서대로 — 루프가 무엇을 되돌렸는지 단언할 때 쓴다. */
    val receivedPrompts: List<ChatPrompt> get() = prompts

    override val loadState: StateFlow<ModelLoadState> = MutableStateFlow(
        ModelLoadState.Ready(ModelInfo("fake", "fake", "1.0", "int8", 0L))
    )

    override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<ModelTurn> {
        prompts += prompt
        val turn = turns.getOrElse(index) { ModelTurn("") }
        index++
        turn.text.chunked(chunkSize).forEach { onToken?.invoke(it) }
        return AppResult.Success(turn)
    }

    override suspend fun generateWithImage(
        prompt: ChatPrompt,
        imageBytes: ByteArray,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = generate(prompt, onToken)

    override suspend fun generateWithAudio(
        prompt: ChatPrompt,
        audioPath: String,
        onToken: ((String) -> Unit)?
    ): AppResult<ModelTurn> = generate(prompt, onToken)

    override suspend fun cancel() = Unit
    override suspend fun warmUp() = Unit
    override fun close() = Unit
}
