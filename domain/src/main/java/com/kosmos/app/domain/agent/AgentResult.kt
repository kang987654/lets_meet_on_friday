package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppError

sealed class AgentResult {
    /**
     * @param searchUsed 이 응답을 만들며 외부 검색(위키백과)을 실제로 수행했는지 여부입니다.
     *
     * [WHY] `ChatMessage.searchUsed` 컬럼은 있었지만 항상 false 로 저장되고 화면에서도 읽히지
     * 않았다 — 골격을 툴 루프로 바꿀 때 값을 채우던 경로가 사라졌다. 네트워크로 나간 답변과
     * 온디바이스만으로 만든 답변을 사용자가 구분할 수 있어야 하므로 다시 잇는다.
     */
    data class Text(
        val content: String,
        val thinkingProcess: String? = null,
        val searchUsed: Boolean = false
    ) : AgentResult()

    data class Error(val error: AppError) : AgentResult()
}
