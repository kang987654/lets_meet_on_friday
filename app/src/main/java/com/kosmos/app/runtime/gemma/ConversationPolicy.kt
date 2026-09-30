package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ConversationResetEvent

/**
 * 캐시된 채팅 대화를 식별하는 값 — 셋 중 하나라도 바뀌면 대화를 다시 만든다.
 *
 * [WHY] 예전에는 GemmaModelRunner 가 세 필드(sessionId·systemInstruction·enabledTools)를 따로 들고
 * 따로 비웠다 — 하나만 비우고 나머지를 남기는 실수가 가능한 모양이었다.
 */
internal data class ConversationKey(
    val sessionId: String,
    val systemInstruction: String,
    val enabledTools: List<String>
) {
    companion object {
        fun of(prompt: ChatPrompt) = ConversationKey(prompt.sessionId, prompt.systemInstruction, prompt.enabledTools)
    }
}

/** 캐시된 대화를 이번 턴에 어떻게 쓸지. */
internal sealed interface ConversationDecision {
    data object Reuse : ConversationDecision

    /** @property resetReason 같은 세션의 살아있는 대화를 버릴 때만 non-null — 리셋 이벤트로 방출된다. */
    data class Recreate(val resetReason: ConversationResetEvent.Reason?) : ConversationDecision
}

/**
 * 채팅 턴(oneShot 아님)의 대화 재사용·재생성 판정입니다 — 네이티브 없이 테스트할 수 있는 순수 함수.
 *
 * [WHY] 규칙 우선순위(순서가 곧 계약이다):
 * 1. **툴 회신 턴은 재사용이 최우선** — 0.8.5 실기기에서 토큰 초과 판정이 승인 대기 사이에 대화를
 *    재생성해, 모델이 "자기가 호출한 적 없는 툴"의 응답을 받고 뜬금없는 답을 만들었다.
 * 2. 세션·시스템 지시(응답 스타일 등)·툴 목록(웹 검색 토글 등)이 같고 KV 가 임계값 이하면 재사용.
 * 3. 그 외 재생성. 같은 세션을 버릴 때만 리셋 사유를 붙인다 — 세션 전환은 주제가 끝난 사건이
 *    아니다(ADR-022). 사유 우선순위는 판정 순서와 같다: 예산 → 시스템 지시 → 툴.
 *
 * @param cachedTokens 캐시 대화의 KV 토큰 수. 필요할 때만 부른다(네이티브 호출).
 */
internal fun decideConversation(
    cached: ConversationKey?,
    prompt: ChatPrompt,
    cachedTokens: () -> Int
): ConversationDecision {
    if (cached == null) return ConversationDecision.Recreate(resetReason = null)
    val sameSession = cached.sessionId == prompt.sessionId
    val sameSystem = cached.systemInstruction == prompt.systemInstruction
    val sameTools = cached.enabledTools == prompt.enabledTools

    if (prompt.toolResponse != null && sameSession && sameTools) return ConversationDecision.Reuse

    val exceeded = cachedTokens() > conversationResetThreshold(prompt.contextBudgetTokens)
    if (sameSession && sameSystem && sameTools && !exceeded) return ConversationDecision.Reuse

    val reason = if (!sameSession) null else when {
        exceeded -> ConversationResetEvent.Reason.TOKEN_BUDGET
        !sameSystem -> ConversationResetEvent.Reason.SYSTEM_INSTRUCTION
        else -> ConversationResetEvent.Reason.TOOLS
    }
    return ConversationDecision.Recreate(reason)
}

/**
 * 대화를 버리고 다시 만드는 KV 토큰 임계값.
 *
 * [WHY] 사용자 설정(프리필 예산)에서 파생시킨다. 예전에는 런타임에 박힌 8000 이어서, 설정에서 예산을
 * 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자랐다 — 설정이 메모리에 아무 영향을 주지 못했다.
 * [WHY] 하한 — 예산 최소값(1000)을 그대로 쓰면 시스템 지시 + 툴 선언만으로 이미 임계값을 넘어 **매 턴
 * 재생성**되고, 재생성이야말로 우리가 없애려는 비용이다.
 * [WHY] 천장 — 예전에는 하한이 4000 이라 천장(3328)보다 컸고, 예산을 낮춰도 임계값이 밀려 올라가
 * **대화가 용량을 넘도록 자라는 것을 허용**했다 (Constants.ENGINE_MAX_TOKENS 참조).
 */
internal fun conversationResetThreshold(contextBudgetTokens: Int): Int =
    contextBudgetTokens
        .coerceAtLeast(Constants.MIN_CONVERSATION_RESET_TOKENS)
        .coerceAtMost(Constants.PREFILL_CEILING_TOKENS)

/**
 * greedy(topK=1) 샘플러 — 채팅과 oneShot 이 같은 값을 쓴다.
 *
 * [WHY] 샘플링이 남아 있으면 호출 시작 토큰이 최빈이 아닐 때 툴 호출이 확률적으로 뭉개지고, 숫자
 * 왜곡("1234"→"12", 0.8.3 실기기)도 난다. topK=1 에서 temperature/topP 는 효력이 없다 — 지우지 않는
 * 이유는 나중에 샘플링을 열 때 어떤 값에서 출발했는지가 남아 있어야 하기 때문이다.
 * [WHY] 공식 문서에는 **함수 호출용 샘플링 지침이 없다.** 근거는 우리 실측이다 — 반복 2회가 완전히
 * 동일했고 자릿수도 36/36 온전했다(exp15·exp22). gallery 의 greedy 강제는 가설의 출처일 뿐이다
 * (`.agents/04_MODEL_EVIDENCE.md`).
 */
internal val GREEDY_SAMPLER = SamplerConfig(temperature = 1.0, topK = 1, topP = 0.95)

/**
 * 채팅 대화 설정 — 히스토리·툴 선언을 싣는다.
 *
 * [WHY] few-shot 시범(104토큰)은 0.23.0 에서 제거했다 — 도입 진단(ADR-010 시절 "시범이 없으면 호출
 * 안 함")은 ADR-017 이 철회했고(진짜 원인은 지침 거리 → 턴 리마인더가 해결), exp35 재실측에서 시범
 * 없이 툴 선택 11/11 이 유지됐다. 회수한 104토큰은 프로필 상시 주입(C′1)의 재원이다.
 */
internal fun chatConversationConfig(prompt: ChatPrompt): ConversationConfig = ConversationConfig(
    systemInstruction = Contents.of(prompt.systemInstruction),
    initialMessages = prompt.history.map {
        if (it.role == ChatMessage.Role.USER) Message.user(it.content) else Message.model(it.content)
    },
    // [WHY] 툴 선언을 런타임에 넘겨 모델의 정식 함수호출 템플릿으로 주입한다.
    // 시스템 프롬프트에 형식을 글로 설명하던 방식은 실기기에서 무시됐다 (ADR-008).
    tools = KosmosToolDeclarations.providersFor(prompt.enabledTools),
    // [WHY] false 여야 런타임이 툴을 스스로 실행하지 않고 우리에게 호출을 넘긴다 —
    // 승인 다이얼로그(PRD F4)를 거쳐야 하므로 자동 실행을 쓸 수 없다.
    automaticToolCalling = false,
    samplerConfig = GREEDY_SAMPLER
)

/**
 * 부수 계산(전사·요약 등) 전용 임시 대화 설정.
 *
 * [WHY] 툴도 히스토리도 싣지 않는다. 그래서 채팅 대화보다 훨씬 싸다 — 툴 선언이 수백 토큰이므로
 * (exp34b·exp35 실측) 그것이 빠지면 프리필이 짧은 시스템 지시뿐이다.
 */
internal fun oneShotConversationConfig(prompt: ChatPrompt): ConversationConfig = ConversationConfig(
    systemInstruction = Contents.of(prompt.systemInstruction),
    automaticToolCalling = false,
    samplerConfig = GREEDY_SAMPLER
)
