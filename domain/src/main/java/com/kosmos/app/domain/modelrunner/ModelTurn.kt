package com.kosmos.app.domain.modelrunner

/**
 * 모델이 요청한 툴 호출입니다.
 *
 * [WHY] 이전에는 툴 호출을 응답 텍스트에서 `<tool_call>{...}</tool_call>` 정규식으로 긁어냈다.
 * 그 형식은 Gemma 의 채팅 템플릿에 없는 자체 규약이라 온디바이스 모델이 따르지 못했고,
 * 실기기에서 툴이 **한 번도** 호출되지 않았다(모델은 평문으로 "저는 저장할 수 없습니다"라고
 * 답했다). 런타임의 정식 함수호출 기능을 쓰면 호출이 구조화된 값으로 도착하므로, 계약에
 * 자리를 만든다 (ADR-008).
 */
data class ModelToolCall(
    val name: String,
    val args: Map<String, Any?>
)

/**
 * 한 번의 추론 결과입니다. 텍스트와 툴 호출이 함께 올 수 있습니다.
 */
data class ModelTurn(
    val text: String,
    val toolCalls: List<ModelToolCall> = emptyList()
)

/**
 * 툴 실행 결과를 모델에게 되돌리는 입력입니다.
 *
 * [WHY] 이전에는 `<tool_response>...</tool_response>` 텍스트를 사용자 턴으로 위장해 보냈다.
 * 런타임의 함수호출 경로에서는 전용 응답 타입으로 보내야 모델 템플릿과 역할이 맞는다.
 */
data class ToolResponseInput(
    val name: String,
    val resultJson: String
)
