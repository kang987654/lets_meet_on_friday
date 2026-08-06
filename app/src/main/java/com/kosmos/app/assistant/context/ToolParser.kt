package com.kosmos.app.assistant.context

import com.kosmos.app.assistant.tool.ToolArguments
import org.json.JSONObject

/**
 * [ToolParser]
 * 스트리밍되는 LLM 응답 텍스트 내에서 특정 태그(`<|think|>`, `<tool_call>`)를 실시간으로 추출하고 파싱하는 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context/Parsing)
 * - **Dependencies**: org.json, [ToolArguments]
 *
 * ### Key Flow
 * 1. 전체 응답 텍스트에서 `<|think|>` 블록을 정규식으로 탐색하여 에이전트의 사고 과정(thinking) 추출 및 본문에서 제거
 * 2. `<tool_call>` 블록을 정규식으로 탐색 후 내부 JSON을 파싱해 [ToolCallData] 리스트 생성
 * 3. JSON 자체가 깨진 블록은 [ParsedStream.malformedToolCalls]로 분리해 호출부가 모델에게 알릴 수 있게 함
 * 4. 최종적으로 UI 렌더링을 위한 본문 텍스트, 사고 과정, 툴 콜 정보를 캡슐화한 [ParsedStream] 반환
 */
object ToolParser {

    data class ParsedStream(
        val content: String,
        val thinking: String?,
        val toolCalls: List<ToolCallData>,
        /**
         * JSON 파싱에 실패한 `<tool_call>` 블록의 원문입니다.
         *
         * [WHY] 기존에는 파싱 실패를 빈 catch로 삼켜서 툴 콜이 흔적 없이 사라졌다. 모델은
         * 오류를 받지 못하고 그 턴은 평문 답변으로 처리되므로, 사용자는 요청이 무시된 것처럼
         * 보인다. 실패를 밖으로 드러내 호출부가 모델에게 되돌릴 수 있게 한다.
         */
        val malformedToolCalls: List<String> = emptyList()
    )

    data class ToolCallData(
        val name: String,
        val args: ToolArguments
    )

    fun parseStream(rawString: String): ParsedStream {
        var text = rawString
        var thinkingProcess: String? = null
        val toolCalls = mutableListOf<ToolCallData>()
        val malformed = mutableListOf<String>()

        // 1. Extract <|think|> blocks
        // [WHY] findAll 이다 — 예전에는 find(단수)여서 두 번째 이후 생각 블록이 본문에 남아
        // 프로토콜 문법이 그대로 노출됐다.
        // [WHY] `|$` 대안은 스트리밍 때문에 필요하다. 닫는 태그가 아직 안 온 생각 블록도
        // 본문에서 걷어내야 한다(그 구간의 content 는 비게 되고, 호출부가 null 로 정규화한다).
        val thinkRegex = Regex("<\\|think\\|>(.*?)(</\\|think\\|>|$)", RegexOption.DOT_MATCHES_ALL)
        val thinkMatches = thinkRegex.findAll(text).toList()
        if (thinkMatches.isNotEmpty()) {
            thinkingProcess = thinkMatches
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .ifEmpty { null }
            thinkMatches.forEach { text = text.replace(it.value, "") }
        }

        // 2. Extract <tool_call> blocks
        val toolRegex = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)
        val toolMatches = toolRegex.findAll(text)
        
        for (match in toolMatches) {
            val jsonString = match.groupValues[1].trim()
            try {
                val jsonObject = JSONObject(jsonString)
                val name = jsonObject.optString("name", "")
                // [WHY] args 객체를 평탄화하지 않고 그대로 감싸 넘긴다 — 인자 해석은 스키마를
                // 아는 executor 의 책임이고, 파서가 원시 org.json 값을 Map 으로 흘리면
                // JSONArray·JSONObject.NULL 같은 함정이 호출부마다 복제된다.
                val args = jsonObject.optJSONObject("args")
                    ?.let { ToolArguments(it) }
                    ?: ToolArguments.empty()

                if (name.isNotEmpty()) {
                    toolCalls.add(ToolCallData(name, args))
                } else {
                    malformed.add(jsonString)
                }
            } catch (e: Exception) {
                malformed.add(jsonString)
            }
            // Remove the parsed tool call from the text
            text = text.replace(match.value, "")
        }

        return ParsedStream(
            content = stripIncompleteTag(text).trimStart(),
            thinking = thinkingProcess,
            toolCalls = toolCalls,
            malformedToolCalls = malformed
        )
    }

    /**
     * 아직 닫히지 않은 태그와 토큰 경계에 걸린 태그 조각을 본문에서 잘라냅니다.
     *
     * [WHY] `toolRegex` 는 닫는 태그를 요구하므로, 스트리밍 중 열려 있는 `<tool_call>` 은
     * 매칭되지 않고 본문에 그대로 남아 화면에 렌더됐다. 툴 콜 턴에서는 가시 구간 대부분 동안
     * `<tool_call>{"name":"AddSch` 가 한 글자씩 자라는 것이 보였다.
     *
     * [WHY] 꼬리의 부분 접두사(`<tool_c`, `<|th`, 심지어 `<`)도 자른다. 다음 토큰이 오면
     * 복원되므로 손실이 없고, 자르지 않으면 태그가 완성되는 순간까지 조각이 노출된다.
     * 모델이 실제로 쓴 `<` 하나가 한 틱 늦게 보이는 것은 그 대가로 받아들인다.
     *
     * [WHY] `parseStream` 밖에서도 쓸 수 있게 공개했다. 태그가 아직 완성되지 않은 구간에서는
     * 전체 정규식 파싱이 불필요하지만(누적 문자열이 곧 본문) 꼬리 조각은 여전히 잘라야 한다.
     * `BaseAgent` 가 O(n²) 파싱을 피하면서 이 함수만 호출한다.
     */
    fun stripIncompleteTag(text: String): String {
        // 닫히지 않은 채 남은 완전한 여는 태그부터 잘라낸다.
        val openIndex = OPEN_TAGS.mapNotNull { tag ->
            text.indexOf(tag).takeIf { it >= 0 }
        }.minOrNull()
        if (openIndex != null) return text.substring(0, openIndex)

        // 꼬리에 걸린 부분 접두사를 잘라낸다.
        for (tag in OPEN_TAGS) {
            for (length in (tag.length - 1) downTo 1) {
                if (text.endsWith(tag.substring(0, length))) {
                    return text.substring(0, text.length - length)
                }
            }
        }
        return text
    }

    private val OPEN_TAGS = listOf("<tool_call>", "<|think|>")
}
