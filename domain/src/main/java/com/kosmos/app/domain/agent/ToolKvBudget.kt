package com.kosmos.app.domain.agent

/**
 * [ToolKvBudget]
 * 한 턴 안에서 툴을 하나 더 실행해도 대화 KV 가 프리필 천장 안에 남는가 — 런타임 KV 가드의 판정식(0.41.6 M2).
 *
 * ### Architecture Context
 * - **Layer**: Domain 순수 함수 — `BaseAgent` 가 한 턴 2번째 툴부터 실행 **전에** 부른다.
 *
 * [WHY] 예산 불변식(`TokenBudgetInvariantTest`)은 "대화 상한 + 툴 결과 1개" 만 미리 계산해 보장한다. 두 번째 결과부터는 최악을 가정하면
 * 예산이 바로 바닥나므로, 실제로 찬 양을 재서 넘을 때만 멈춘다(ADR-020 보충).
 */
object ToolKvBudget {

    /**
     * @param currentKv 지금 대화의 KV 토큰 수. 모르면(null — 엔진·대화 없음, 조회 실패) 막지 않는다(지금 동작 유지).
     * @param resultCap 이번 툴 결과가 KV 에 더할 수 있는 최대 토큰.
     * @param ceiling 프리필 천장 — 그 위는 답을 쓸 여유다.
     */
    fun fits(currentKv: Int?, resultCap: Int, ceiling: Int): Boolean =
        currentKv == null || currentKv + resultCap <= ceiling
}
