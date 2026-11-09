package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ConversationResetEvent

/**
 * 캐시된 채팅 대화를 식별하는 값 — 셋 중 하나라도 바뀌면 대화를 다시 만든다.
 *
 * [WHY] 세 필드를 한 값으로 묶는다 — 따로 들면 하나만 비우고 나머지를 남기는 실수가 가능하다.
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
 * 1. **툴 회신 턴은 재사용이 최우선** — 승인 대기 사이에 재생성하면 모델이 "호출한 적 없는 툴"의 응답을 받는다(0.8.5).
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
 * [WHY] 사용자 설정(프리필 예산)에서 파생시켜야 설정이 실제 메모리에 반영된다. 하한([Constants.MIN_CONVERSATION_RESET_TOKENS])
 * 아래면 오버헤드만으로 매 턴 재생성되고, 천장([Constants.PREFILL_CEILING_TOKENS]) 위면 대화가 KV 용량을 넘게 자란다.
 */
internal fun conversationResetThreshold(contextBudgetTokens: Int): Int =
    contextBudgetTokens
        .coerceAtLeast(Constants.MIN_CONVERSATION_RESET_TOKENS)
        .coerceAtMost(Constants.PREFILL_CEILING_TOKENS)

/**
 * greedy(topK=1) 샘플러 — 채팅과 oneShot 이 같은 값을 쓴다.
 *
 * [WHY] 샘플링이 남아 있으면 툴 호출이 확률적으로 뭉개지고 숫자도 왜곡된다(0.8.3). greedy 의 근거는 공식 문서가 아니라
 * 우리 실측이다(exp15·exp22 — 반복 동일, 자릿수 36/36). topK=1 이면 temperature/topP 는 효력이 없지만 샘플링을 다시
 * 열 때의 출발점으로 남긴다.
 */
internal val GREEDY_SAMPLER = SamplerConfig(temperature = 1.0, topK = 1, topP = 0.95)

/**
 * 생각 모드를 끕니다 — 대화 설정과 매 send 에 같은 값을 싣는다.
 *
 * [WHY] 생각 모드만 켜면 툴 호출이 4/4 → 2/4 로 떨어졌다(exp24, 일정 등록 쪽). 켜져도 출력에 `<|think|>` 마커가
 * 나오지 않아 응답으로는 켜졌는지 알 수 없으므로, 키 이름 맵이 아니라 타입 있는 `ThinkingConfig` 로 명시한다.
 * [WHY] 대화 설정에도 싣는다(0.41.9) — 앞부분 미리 읽기(`prefillPrefaceOnInit`)를 켜면 엔진이 앞부분을 **대화 설정의** 생각 모드로
 * 렌더링한다(LiteRT-LM `conversation.cc`). 비워 두면 앞부분과 send 의 템플릿이 어긋날 수 있다.
 */
internal val THINKING_OFF = ThinkingConfig(enableThinking = false)

/**
 * 채팅 대화 설정 — 히스토리·툴 선언을 싣는다.
 *
 * @param speculative 추측 디코딩(MTP) 설정 — GPU 엔진일 때만 [MtpMode.toConfig], 아니면 null(미지정, 0.41.1).
 * @param prefillPreface 대화를 만들 때 앞부분(시스템 지시·기록·툴 선언)을 바로 프리필할지 — 기본은 꺼짐(첫 send 가 읽는다, 0.41.9 M2 실측 전).
 * [WHY] few-shot 시범은 싣지 않는다 — 툴 호출의 진짜 원인은 지침 거리였고 턴 리마인더가 해결한다(ADR-017, exp35 11/11).
 */
internal fun chatConversationConfig(
    prompt: ChatPrompt,
    speculative: SpeculativeDecodingConfig? = null,
    prefillPreface: Boolean = false
): ConversationConfig = ConversationConfig(
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
    samplerConfig = GREEDY_SAMPLER,
    prefillPrefaceOnInit = prefillPreface,
    thinkingConfig = THINKING_OFF,
    speculativeDecodingConfig = speculative
)

/**
 * 부수 계산(전사·요약 등) 전용 임시 대화 설정.
 *
 * [WHY] 툴도 히스토리도 싣지 않는다 — 툴 선언(수백 토큰)이 빠져 프리필이 짧은 시스템 지시뿐이다.
 */
internal fun oneShotConversationConfig(
    prompt: ChatPrompt,
    speculative: SpeculativeDecodingConfig? = null
): ConversationConfig = ConversationConfig(
    systemInstruction = Contents.of(prompt.systemInstruction),
    automaticToolCalling = false,
    samplerConfig = GREEDY_SAMPLER,
    thinkingConfig = THINKING_OFF,
    speculativeDecodingConfig = speculative
)
