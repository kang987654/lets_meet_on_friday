package com.kosmos.app.assistant.context

import org.json.JSONObject

/**
 * [ToolParser]
 * 스트리밍되는 LLM 응답 텍스트 내에서 특정 태그(`<|think|>`, `<tool_call>`)를 실시간으로 추출하고 파싱하는 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context/Parsing)
 * - **Dependencies**: None (순수 Kotlin/JSON 객체 기반)
 *
 * ### Key Flow
 * 1. 전체 응답 텍스트에서 `<|think|>` 블록을 정규식으로 탐색하여 에이전트의 사고 과정(thinking) 추출 및 본문에서 제거
 * 2. `<tool_call>` 블록을 정규식으로 탐색 후 내부 JSON 문자열을 [org.json.JSONObject]로 파싱하여 [ToolCallData] 객체 리스트 생성
 * 3. 최종적으로 UI 렌더링을 위한 본문 텍스트, 사고 과정, 툴 콜 정보를 캡슐화한 [ParsedStream] 반환
 */
object ToolParser {
    
    data class ParsedStream(
        val content: String,
        val thinking: String?,
        val toolCalls: List<ToolCallData>
    )

    data class ToolCallData(
        val name: String,
        val args: Map<String, Any>
    )

    fun parseStream(rawString: String): ParsedStream {
        var text = rawString
        var thinkingProcess: String? = null
        val toolCalls = mutableListOf<ToolCallData>()

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
                val argsObject = jsonObject.optJSONObject("args")
                val argsMap = mutableMapOf<String, Any>()
                
                if (argsObject != null) {
                    val keys = argsObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        argsMap[key] = argsObject.get(key)
                    }
                }
                
                if (name.isNotEmpty()) {
                    toolCalls.add(ToolCallData(name, argsMap))
                }
            } catch (e: Exception) {
                // Ignore parse errors for individual tools
            }
            // Remove the parsed tool call from the text
            text = text.replace(match.value, "")
        }

        return ParsedStream(
            content = text.trimStart(),
            thinking = thinkingProcess,
            toolCalls = toolCalls
        )
    }
}
