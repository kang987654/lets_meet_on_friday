package com.kosmos.app.assistant.tool

import javax.inject.Inject

/**
 * 정규 툴 이름([com.kosmos.app.domain.tool.ToolNames]) → 실행기 조회표입니다.
 *
 * 실행기 목록은 `AgentModule` 의 `@IntoSet` 바인딩이 단일 출처다.
 */
class ToolRegistry @Inject constructor(
    executors: Set<@JvmSuppressWildcards ToolExecutor>
) {
    private val executors: Map<String, ToolExecutor> = executors.associateBy { it.name }.also {
        // [WHY] 같은 이름이 둘이면 associateBy 가 뒤의 것으로 조용히 덮는다 — 조립 시점에 막는다.
        require(it.size == executors.size) { "중복된 툴 이름: ${executors.map { e -> e.name }}" }
    }

    fun getExecutor(name: String): ToolExecutor? = executors[name]
}
