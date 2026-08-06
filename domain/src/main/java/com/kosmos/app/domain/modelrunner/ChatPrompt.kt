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
    val toolResponse: ToolResponseInput? = null,
    /**
     * 이 턴에만 유효한 **휘발성** 문맥입니다 — RAG 로 검색된 기억. 런타임이 사용자 턴 본문 앞에
     * 붙여 보냅니다. 검색 결과가 없으면 null 입니다.
     *
     * [WHY] 이 내용은 예전에 [systemInstruction] 안에 있었다. 그러면 시스템 지시가 턴마다
     * 달라져 런타임의 재사용 판정이 거의 항상 거짓이 되고, Conversation 을 파괴한 뒤 시스템
     * 지시 + 툴 선언(~2천 토큰) + few-shot + 히스토리 **전체를 매 턴 다시 프리필**했다.
     * PC 실측(데스크톱 GPU, 같은 3턴): 재사용 4.3/0.5/0.9초 대 재생성 4.0/3.8/4.0초 —
     * 2번째 턴부터 턴당 3초 이상을 프리필에만 썼다. 기기에서는 프리필이 더 느리다.
     *
     * [WHY] 보안상으로도 여기가 맞다. 검색된 기억을 시스템 지시에 넣으면 저장된 메모 안의
     * 문장이 **시스템 권한으로 승격**된다. 첨부 문서를 USER 역할의 구분된 블록으로 저장하는
     * 기존 결정([AssistantOrchestrator], ADR-003)과 같은 이유로 사용자 턴에 싣는다.
     *
     * [WHY] **날짜/시각 블록은 여기 담지 않는다.** `[System Data] 오늘=…` 같은 목록이 사용자
     * 발화 앞에 붙으면 조회 질문이 툴 호출로 샜다(PC 실측: 조회 3케이스 중 1/3 만 정상 —
     * "내 자전거 비밀번호 뭐였지?"에 `add_memory`, "내가 뭘 좋아한다고 했지?"에 `get_schedule`).
     * 날짜 블록은 시스템 지시에 남기고 대신 **하루 단위**로 만들어 재사용을 얻는다.
     */
    val turnContext: String? = null,
    /**
     * 이 세션의 프리필 예산(토큰). 런타임이 Conversation 을 언제 재설정할지 판단하는 데 씁니다.
     *
     * [WHY] 예전에는 재설정 임계값이 런타임에 박힌 8000 이었다. 사용자가 설정에서 예산을
     * 1000 으로 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자랐다 — 설정이 메모리에
     * 아무 영향을 주지 못했다. 예산을 여기 실어 임계값을 설정과 이어 붙인다.
     */
    val contextBudgetTokens: Int = com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS
)
