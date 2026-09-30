package com.kosmos.app.assistant.tool

import org.json.JSONObject

/**
 * 툴 결과 JSON(`{"status": …}`)의 단일 조립 지점입니다.
 *
 * [WHY] `JSONObject().put("status", "error").put("message", …)` 가 실행기·BaseAgent 에 열 곳 넘게
 * 손으로 적혀 있었다. BaseAgent 는 `status` 값으로 성공 여부를 판정하므로(ToolOutcome) 키·값
 * 철자가 한 곳에서 어긋나면 실패한 검색에 "참고했어요" 뱃지가 붙는 부류의 결함이 된다.
 *
 * [WHY] 문구는 바꾸지 않는다 — 툴 결과는 모델 입력(프롬프트 표면)이라 문구 변경은 실험실 실측이
 * 선행돼야 한다(AGENTS §2-⑤). 이 객체는 조립 방식만 모은다.
 */
object ToolResultJson {
    const val STATUS = "status"
    const val SUCCESS = "success"
    const val ERROR = "error"

    /** `{"status":"success"}` 에 필드를 더 얹을 수 있는 객체. */
    fun success(): JSONObject = JSONObject().put(STATUS, SUCCESS)

    /** `{"status":"error","message":…}` 에 필드를 더 얹을 수 있는 객체. */
    fun errorObject(message: String): JSONObject = JSONObject().put(STATUS, ERROR).put("message", message)

    fun error(message: String): String = errorObject(message).toString()

    /** 실행기가 돌려준 결과가 오류로 표시됐는가. 파싱이 안 되면 오류가 아닌 것으로 본다. */
    fun isError(resultJson: String): Boolean =
        runCatching { JSONObject(resultJson).optString(STATUS) == ERROR }.getOrDefault(false)
}
