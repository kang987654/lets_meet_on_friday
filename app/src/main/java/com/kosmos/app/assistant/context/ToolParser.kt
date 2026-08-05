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
        val thinkRegex = Regex("<\\|think\\|>(.*?)(</\\|think\\|>|$)", RegexOption.DOT_MATCHES_ALL)
        val thinkMatch = thinkRegex.find(text)
        if (thinkMatch != null) {
            thinkingProcess = thinkMatch.groupValues[1].trim()
            text = text.replace(thinkMatch.value, "")
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
            content = text.trimStart(),
            thinking = thinkingProcess,
            toolCalls = toolCalls,
            malformedToolCalls = malformed
        )
    }
}
