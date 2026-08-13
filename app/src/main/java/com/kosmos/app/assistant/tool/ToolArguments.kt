package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.util.IsoDateTimeParser
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
 *
 * [WHY] BAD_FORMAT 은 "문자열이긴 한데 요구된 형식이 아니다"이다. WRONG_TYPE 과 구분해야
 * 안내가 갈린다 — WRONG_TYPE 의 처방은 "문자열로 보내라"인데, 깨진 타임스탬프는 이미
 * 문자열이므로 그 안내로는 모델이 고칠 수 없다.
 */
class ToolArgumentException(
    val field: String,
    val reason: Reason
) : Exception("Tool argument '$field' is ${reason.name.lowercase()}") {

    enum class Reason { MISSING, WRONG_TYPE, BAD_FORMAT }
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
     * ISO 8601 일시 인자를 요구합니다. 검증만 하고 파싱 가능한 문자열을 반환합니다.
     *
     * [WHY] 실기기에서 모델이 `202026-081717T010000` 같은 깨진 타임스탬프를 생성했고, 앱은
     * 인자 값을 어디서도 변형하지 않으므로 그 원문이 승인 카드·Room·기기 캘린더까지 그대로
     * 흘러갔다(2026-08-13 관측). 형식 검증이 여기(승인 카드 이전) 있으면 깨진 값은
     * BAD_FORMAT 으로 모델 자가수정 루프에 되돌아가고, 하류는 파싱 가능한 값만 받는다.
     *
     * [WHY] epoch 로 바꾸지 않고 문자열을 돌려준다 — `CalendarDraft.startIso` 이하의 표시·저장
     * 경로가 전부 ISO 문자열 계약이다. 판정은 [IsoDateTimeParser] 로 하는데, 하류(기기 캘린더
     * 동기화)가 쓰는 파서와 같아야 "여기서 통과한 값이 하류에서 실패"하는 틈이 없다.
     */
    fun requireIsoDateTime(key: String): String =
        validIsoOrThrow(key, requireString(key))

    /** ISO 8601 일시 인자를 읽습니다. 없거나 공백이면 null, 있는데 형식이 틀리면 BAD_FORMAT 입니다. */
    fun optIsoDateTime(key: String): String? {
        val value = optString(key)?.takeIf { it.isNotBlank() } ?: return null
        return validIsoOrThrow(key, value)
    }

    // [WHY] 공백 구분("2026-08-17 15:00")만 'T' 치환으로 관용 수용한다 — optString 의 숫자
    // 강제변환과 같은 철학이다(모호하지 않은 변형은 받아준다). 이때 반환도 치환된 값이다.
    // 원문을 돌려주면 받아준 값이 하류 파서에서 다시 실패한다. 깨진 값은 치환으로 살아나지
    // 않으므로 검증이 느슨해지지는 않는다.
    private fun validIsoOrThrow(key: String, value: String): String {
        if (IsoDateTimeParser.toEpochMillis(value) != null) return value
        val normalized = value.replace(' ', 'T')
        if (IsoDateTimeParser.toEpochMillis(normalized) != null) return normalized
        throw ToolArgumentException(key, ToolArgumentException.Reason.BAD_FORMAT)
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

            // [WHY] 런타임의 네이티브 함수호출 경로에서는 배열이 Kotlin List 로 도착한다
            // (JSONObject.wrap 이 감싸주지 않는 경우가 있어 원본 타입이 남는다).
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
         * [WHY] 네이티브 함수호출 경로의 인자는 `Map<String, Any?>` 로 도착한다. executor 4개와
         * 승인 경로가 이미 [ToolArguments] 를 쓰므로, 여기서 형태만 맞춰 주면 그 아래는
         * 변경이 없다. `JSONObject(Map)` 은 값 타입을 그대로 보존하므로 [stringList] 의
         * `Collection` 분기가 필요하다.
         */
        fun of(args: Map<String, Any?>): ToolArguments =
            ToolArguments(JSONObject(args.filterValues { it != null }))
    }
}
