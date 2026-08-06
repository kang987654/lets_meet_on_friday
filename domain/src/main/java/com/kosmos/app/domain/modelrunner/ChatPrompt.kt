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
    /** 툴 실행 결과를 되돌리는 턴이면 채워진다. 이때 [currentInput] 은 무시된다. */
    val toolResponse: ToolResponseInput? = null
)
