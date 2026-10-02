package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.util.IsoDateTimeParser
import org.json.JSONArray
import org.json.JSONObject

/**
 * [ToolArgumentException]
 * 툴 인자가 없거나 기대한 모양이 아닐 때 던지는 예외입니다.
 *
 * [WHY] 사유를 나눠야 모델이 무엇을 고칠지 안다 — 둘을 뭉개면 이미 보낸 값을 다시 요구받아 같은 응답을 반복한다.
 * MISSING(안 보냄), WRONG_TYPE(문자열이 아님), BAD_FORMAT(문자열이지만 형식이 틀림 — "문자열로 보내라"로는 못 고친다),
 * PAST(형식은 맞지만 지난 일시 — 실행기가 승인 요청 단계에서 던져 실행될 수 없는 카드를 승인하지 않게 한다).
 */
class ToolArgumentException(
    val field: String,
    val reason: Reason
) : Exception("Tool argument '$field' is ${reason.name.lowercase()}") {

    enum class Reason { MISSING, WRONG_TYPE, BAD_FORMAT, PAST }
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
 * 1. 네이티브 함수호출 인자(Map)는 [of] 로, 텍스트 폴백([ToolParser])은 JSON 객체를 그대로 감싸 executor 에 넘깁니다.
 * 2. executor가 [requireString]/[optString]/[stringList]로 필요한 인자만 꺼냅니다.
 * 3. 읽기 실패는 [ToolArgumentException]이 되어 `BaseAgent`가 모델에게 되돌립니다.
 *
 * [WHY] 인자 해석을 한 곳에 모은다 — executor 마다 `as? String` 으로 꺼내면 배열(`JSONArray`) 태그 유실, 타입 오류의
 * "값 없음" 위장, `JSONObject.NULL` 함정이 executor 수만큼 복제된다.
 */
class ToolArguments(private val json: JSONObject) {

    /**
     * 키가 존재하고 값이 JSON null이 아닌지 확인합니다.
     *
     * [WHY] `json.has(key)`만으로는 부족하다 — `{"x": null}`에서 `has`는 true이고 `get`은 non-null인
     * [JSONObject.NULL] 센티널을 돌려준다.
     */
    fun has(key: String): Boolean = json.has(key) && !json.isNull(key)

    /**
     * 문자열 인자를 읽습니다. 없거나 JSON null이면 null을 반환합니다.
     *
     * [WHY] 숫자·불린은 문자열로 강제 변환한다 — 모델이 따옴표를 빠뜨리는 것은 흔한 실수다. 배열·객체는 의미가
     * 다른 값이라 뭉개지 않고 타입 오류로 보고한다.
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
     * ISO 8601 일시 인자를 요구합니다. 검증만 하고 파싱 가능한 문자열을 반환합니다.
     *
     * [WHY] 승인 카드 **이전**에 검증한다 — 모델이 `202026-081717T010000` 같은 깨진 값을 만들면 BAD_FORMAT 으로 자가수정
     * 루프에 돌려보내야 승인 카드·Room·기기 캘린더에 원문이 흘러가지 않는다(ADR-021). 하류가 ISO 문자열 계약이라
     * 문자열을 돌려주고, 판정은 하류와 같은 [IsoDateTimeParser] 로 한다.
     */
    fun requireIsoDateTime(key: String): String =
        validIsoOrThrow(key, requireString(key))

    /** ISO 8601 일시 인자를 읽습니다. 없거나 공백이면 null, 있는데 형식이 틀리면 BAD_FORMAT 입니다. */
    fun optIsoDateTime(key: String): String? {
        val value = optString(key)?.takeIf { it.isNotBlank() } ?: return null
        return validIsoOrThrow(key, value)
    }

    // [WHY] 공백 구분("2026-08-17 15:00")만 'T' 치환으로 받아준다(모호하지 않은 변형). 반환도 치환된 값이어야 하류
    // 파서에서 다시 실패하지 않는다.
    private fun validIsoOrThrow(key: String, value: String): String {
        if (IsoDateTimeParser.toEpochMillis(value) != null) return value
        val normalized = value.replace(' ', 'T')
        if (IsoDateTimeParser.toEpochMillis(normalized) != null) return normalized
        throw ToolArgumentException(key, ToolArgumentException.Reason.BAD_FORMAT)
    }

    /**
     * 문자열 목록 인자를 읽습니다. 없으면 빈 목록을 반환합니다.
     *
     * [WHY] 배열(`JSONArray`·Kotlin `Collection`), 콤마 문자열(하위 호환), 단일 값을 모두 받는다.
     */
    fun stringList(key: String): List<String> {
        if (!has(key)) return emptyList()
        return when (val value = json.get(key)) {
            is JSONArray -> (0 until value.length())
                .mapNotNull { index -> value.opt(index)?.takeIf { it != JSONObject.NULL } }
                .map { it.toString().trim() }
                .filter { it.isNotEmpty() }

            // [WHY] 네이티브 함수호출 경로에서는 배열이 Kotlin List 로 도착한다([of] 참고).
            is Collection<*> -> value
                .filterNotNull()
                .filter { it != JSONObject.NULL }
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

        /**
         * 런타임이 돌려준 구조화된 인자 맵을 감쌉니다.
         *
         * [WHY] `JSONObject(Map)` 은 값 타입을 그대로 보존한다 — 배열이 Kotlin List 로 남아 [stringList] 의 `Collection`
         * 분기가 필요한 이유다.
         */
        fun of(args: Map<String, Any?>): ToolArguments =
            ToolArguments(JSONObject(args.filterValues { it != null }))
    }
}
