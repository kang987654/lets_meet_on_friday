package com.kosmos.app.assistant.orchestrator

/**
 * 첨부 문서를 대화에 저장하는 형식 — 모델 입력(히스토리)과 말풍선 표시가 같은 정의를 쓴다.
 *
 * [WHY] SYSTEM 이 아니라 USER 역할의 구분된 블록이다 — 문서 안의 지시문이 시스템 프롬프트로 승격되지 않게(AssistantOrchestrator).
 * 형식은 모델 입력이라 바꾸지 않는다. 화면은 [bodyOrNull] 로 알아보고 원문 대신 접힌 칩으로 그린다(0.36.0 D6).
 */
object AttachedDocumentBlock {
    private const val HEADER = "[Attached Document]\n\"\"\"\n"
    private const val FOOTER = "\n\"\"\""

    fun wrap(documentText: String): String = "$HEADER$documentText$FOOTER"

    /** 첨부 블록이면 본문, 아니면 null. */
    fun bodyOrNull(content: String): String? =
        if (content.startsWith(HEADER) && content.endsWith(FOOTER)) content.removePrefix(HEADER).removeSuffix(FOOTER) else null
}
