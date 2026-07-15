package com.kosmos.app.assistant.tool

/**
 * [ToolExecutor]
 * 각 툴 콜(Tool Call)을 위임받아 수행하는 인터페이스입니다.
 * 단일 책임 원칙(SRP)에 따라 각 툴의 동작을 개별 클래스로 캡슐화합니다.
 */
interface ToolExecutor {
    /** 툴의 고유 이름 (예: "AddSchedule") */
    val name: String

    /** 
     * 파싱된 매개변수와 세션 ID를 받아 툴을 실행하고, 
     * 그 결과를 LLM 컨텍스트에 추가할 JSON/텍스트 문자열 형태로 반환합니다.
     */
    suspend fun execute(args: Map<String, Any>, sessionId: String): String
}
