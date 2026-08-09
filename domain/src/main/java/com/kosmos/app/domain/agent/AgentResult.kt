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
    /**
     * @param searchFailed 웹 검색을 시도했으나 실패했는지 여부입니다.
     *
     * [WHY] 실패 JSON 은 모델에게만 가고 사용자는 아무것도 못 봤다. 모델이 그 사실을 답변에
     * 언급할지는 확률이라, 네트워크가 끊긴 줄 모른 채 온디바이스 답변을 검색 결과로 오해할 수
     * 있었다. PRD V1-AC3·EC5 는 "로컬 응답 유지 + '웹 검색 보강 실패' 안내"를 요구한다.
     */
    data class Text(
        val content: String,
        val thinkingProcess: String? = null,
        val searchUsed: Boolean = false,
        val searchFailed: Boolean = false
    ) : AgentResult()

    data class Error(val error: AppError) : AgentResult()
}
