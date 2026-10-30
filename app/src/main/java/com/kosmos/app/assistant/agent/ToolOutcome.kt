package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.tool.ToolResultJson

/**
 * 툴 실행 결과입니다.
 *
 * [WHY] 예전에는 JSON 문자열만 돌려줬다. 그러면 호출자가 "실제로 실행됐는가"를 알려면
 * 그 문자열에서 `"status":"error"` 를 찾아야 하는데, 오류 JSON 을 만드는 곳이 네 군데이고
 * 그중 하나는 공백이 다른 손수 만든 리터럴이라(`{"status": "error"...}`) 문자열 검사가
 * 조용히 빗나간다. 실행 여부는 값으로 들고 나온다.
 *
 * [WHY] `succeeded` 는 `executed` 와 다르다. executor 가 실패를 예외가 아니라
 * `{"status":"error"}` JSON 으로 돌려주면(위키 네트워크 실패가 그렇다) executed=true 인데,
 * 예전에는 그것만 보고 searchUsed 를 켜서 **검색이 실패한 답변에 "위키백과 검색 결과를
 * 참고했어요" 뱃지가 붙었다** — 검색 없이 만든 답을 근거 있는 답으로 오표시하는, PRD
 * V1-AC3·EC5 를 실질 무력화하는 결함이었다. 판정은 문자열 검색이 아니라 JSON 파싱이다 —
 * executor 출력은 전부 JSONObject 로 만들어져 형태가 균일하다.
 */
internal data class ToolOutcome(
    val resultJson: String,
    val executed: Boolean,
    val succeeded: Boolean = executed,
    // 사용자가 승인하지 않음(시간 초과 포함) — 루프가 고정 문장으로 끝낸다.
    val rejected: Boolean = false,
    // 사실 저장 가드가 막음 — 모델은 막힘 결과를 받고 먼저 묻는다.
    val factBlocked: Boolean = false
) {
    companion object {
        fun of(resultJson: String, executed: Boolean): ToolOutcome {
            val succeeded = executed && !ToolResultJson.isError(resultJson)
            return ToolOutcome(resultJson, executed, succeeded)
        }
    }
}
