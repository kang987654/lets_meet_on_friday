package com.kosmos.app.assistant.orchestrator

/**
 * 스트리밍 중 UI 에 전달되는 **파싱된** 상태입니다.
 *
 * [WHY] 예전 계약은 `onToken: ((String) -> Unit)?` 로 원시 델타 토큰만 넘겼다. 그러면 UI 가
 * 스스로 누적하고 파싱해야 하는데, 계약에 **턴 경계 신호가 없어서** 툴 루프가 다음 턴으로
 * 넘어갈 때 누적기를 비울 수 없었다. 그 결과 1턴 문장이 2턴에 이어붙어 보이다가 완료 시
 * 마지막 턴만 커밋돼 텍스트가 줄어드는 것처럼 보였다. 이미 턴 경계를 아는 `BaseAgent` 가
 * 파싱 결과를 넘기면 UI 는 누적기도 파서도 가질 이유가 없다 (ADR-007).
 *
 * @property content 화면에 보일 본문. 보일 것이 없으면 `null` 이다 — 빈 문자열이 아니다.
 *   [WHY] 빈 문자열을 넘기면 UI 가 "텍스트 있음"으로 오해해 타이핑 인디케이터를 숨겼고,
 *   생각 블록만 스트리밍되는 구간에서 빈 버블에 스피너도 없는 상태가 됐다.
 * @property thinking 누적된 사고 과정. 없으면 `null`.
 */
data class StreamUpdate(
    val content: String?,
    val thinking: String?
)

data class ChatRequest(
    val sessionId: String,
    val message: String,
    val imageBytes: ByteArray? = null,
    val documentText: String? = null,
    val audioFilePath: String? = null,
    val imageTokenBudget: Int = 280,
    val onStream: ((StreamUpdate) -> Unit)? = null
)
