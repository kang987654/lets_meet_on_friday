package com.kosmos.app.assistant.tool

import org.json.JSONArray
import org.json.JSONObject

/**
 * [ToolArgumentException]
 * 툴 인자가 없거나 기대한 모양이 아닐 때 던지는 예외입니다.
 *
 * [WHY] 누락(MISSING)과 타입 오류(WRONG_TYPE)를 구분해야 모델이 "안 보냈다"와
 * "잘못된 모양으로 보냈다" 중 무엇을 고쳐야 하는지 알 수 있다. 기존 구현은
 * `as? String ?: return "...가 필요합니다"`로 둘을 하나로 뭉개서, 모델이 이미 보낸 값을
 * 다시 요구받고 같은 응답을 반복하는 루프에 빠졌다.
 */
class ToolArgumentException(
    val field: String,
    val reason: Reason
) : Exception("Tool argument '$field' is ${reason.name.lowercase()}") {

    enum class Reason { MISSING, WRONG_TYPE }
}

/**
 * [ToolArguments]
 * 모델이 보낸 툴 콜 인자를 타입 인식 방식으로 읽는 래퍼입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: org.json
 *
 * ### Key Flow
 * 1. [ToolParser]가 `<tool_call>`의 `args` 객체를 그대로 감싸 executor에 넘깁니다.
 * 2. executor가 [requireString]/[optString]/[stringList]로 필요한 인자만 꺼냅니다.
 * 3. 읽기 실패는 [ToolArgumentException]이 되어 `BaseAgent`가 모델에게 되돌립니다.
 *
 * [WHY] 이전에는 `Map<String, Any>`에 `org.json`의 원시 값을 담고 각 executor가
 * `as? String`으로 꺼냈다. 그 결과 (a) JSON 배열이 `JSONArray`로 도착해 `is List<*>`
 * 분기가 절대 참이 되지 않아 `AddMemory`의 태그가 전부 유실됐고, (b) 타입 오류가
 * "값 없음"으로 위장됐고, (c) `JSONObject.NULL`이 non-null `Any`라는 함정이 각
 * executor에 복제됐다. 인자 해석을 이 한 곳으로 모아 세 결함을 함께 없앤다.
 */
class ToolArguments(private val json: JSONObject) {

    /**
     * 키가 존재하고 값이 JSON null이 아닌지 확인합니다.
     *
     * [WHY] `json.has(key)`만으로는 부족하다 — `{"x": null}`에서 `has`는 true이고
     * `get`은 non-null인 [JSONObject.NULL] 센티널을 돌려준다. 이 정규화를 래퍼 안에서
     * 끝내야 각 executor가 같은 함정을 반복해 밟지 않는다.
     */
    fun has(key: String): Boolean = json.has(key) && !json.isNull(key)

    /**
     * 문자열 인자를 읽습니다. 없거나 JSON null이면 null을 반환합니다.
     *
     * [WHY] 숫자·불린은 문자열로 강제 변환한다. 모델이 `{"startTime": 20260806}`처럼
     * 따옴표를 빠뜨리는 것은 흔한 실수인데, 여기서 거부하면 사용자가 정상 요청을 했음에도
     * 대화가 진행되지 않는다. 반면 배열·객체는 강제하지 않는다 — 문자열 자리에 온
     * 컬렉션은 의미가 다른 값이므로 조용히 뭉개는 대신 타입 오류로 보고해야 한다.
     */
    fun optString(key: String): String? {
        if (!has(key)) return null
        return when (val value = json.get(key)) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> throw ToolArgumentException(key, ToolArgumentException.Reason.WRONG_TYPE)
        }
    }

    /** 비어 있지 않은 문자열 인자를 요구합니다. 공백만인 값은 누락으로 취급합니다. */
    fun requireString(key: String): String {
        val value = optString(key)?.takeIf { it.isNotBlank() }
        return value ?: throw ToolArgumentException(key, ToolArgumentException.Reason.MISSING)
    }

    /**
     * 문자열 목록 인자를 읽습니다. 없으면 빈 목록을 반환합니다.
     *
     * [WHY] 세 가지 모양을 모두 받는다. `JSONArray`는 프롬프트가 지시한 정식 형태이며
     * 지금까지 유실되던 바로 그 경로다(`org.json`은 배열을 `List`가 아닌 `JSONArray`로
     * 준다). 콤마 문자열은 기존에 유일하게 동작하던 폴백이라 하위 호환으로 유지하고,
     * 단일 문자열도 관용적으로 받는다.
     */
    fun stringList(key: String): List<String> {
        if (!has(key)) return emptyList()
        return when (val value = json.get(key)) {
            is JSONArray -> (0 until value.length())
                .mapNotNull { index -> value.opt(index)?.takeIf { it != JSONObject.NULL } }
                .map { it.toString().trim() }
                .filter { it.isNotEmpty() }

            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            is Number, is Boolean -> listOf(value.toString())

            else -> throw ToolArgumentException(key, ToolArgumentException.Reason.WRONG_TYPE)
        }
    }

    /** 승인 카드 등에서 인자를 사람이 읽을 형태로 요약할 때 사용합니다. */
    override fun toString(): String = json.toString()

    companion object {
        fun empty(): ToolArguments = ToolArguments(JSONObject())
    }
}
