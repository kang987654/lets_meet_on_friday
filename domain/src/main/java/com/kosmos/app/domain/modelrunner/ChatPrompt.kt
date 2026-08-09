package com.kosmos.app.domain.modelrunner

import com.kosmos.app.domain.model.ChatMessage

data class ChatPrompt(
    val sessionId: String,
    val systemInstruction: String,
    val history: List<ChatMessage>,
    val currentInput: String,
    /**
     * 이 턴에 모델에게 선언할 툴 이름 목록입니다.
     *
     * [WHY] 툴 설명을 시스템 프롬프트 텍스트로 넣던 방식을 버렸다 — 런타임이 모델의 정식
     * 함수호출 템플릿으로 선언을 주입해야 모델이 호출을 생성한다 (ADR-008).
     */
    val enabledTools: List<String> = emptyList(),
    /**
     * 사용자 대화가 아니라 **부수 계산**(음성 전사, 일정 요약 등)이면 true 입니다.
     *
     * [WHY] 런타임은 채팅 Conversation 하나를 캐시해 재사용한다(ADR-010 — 재사용이 깨지면
     * 시스템 지시 + 툴 선언 ~2천 토큰 + 히스토리를 매 턴 다시 프리필한다). 그런데 부수 계산은
     * 시스템 지시와 sessionId 가 다르므로, 그냥 보내면 **채팅 대화를 파괴한다.** 실제로
     * `GetTodayScheduleUseCase` 의 요약 호출 때문에 캘린더 화면을 열 때마다 채팅 대화가
     * 날아가고 있었다. 이 플래그가 붙은 턴은 임시 Conversation 을 만들어 쓰고 즉시 닫는다.
     */
    val oneShot: Boolean = false,
    /** 툴 실행 결과를 되돌리는 턴이면 채워진다. 이때 [currentInput] 은 무시된다. */
    val toolResponse: ToolResponseInput? = null,
    /**
     * 이 세션의 프리필 예산(토큰). 런타임이 Conversation 을 언제 재설정할지 판단하는 데 씁니다.
     *
     * [WHY] 예전에는 재설정 임계값이 런타임에 박힌 8000 이었다. 사용자가 설정에서 예산을
     * 1000 으로 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자랐다 — 설정이 메모리에
     * 아무 영향을 주지 못했다. 예산을 여기 실어 임계값을 설정과 이어 붙인다.
     */
    val contextBudgetTokens: Int = com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS
)
