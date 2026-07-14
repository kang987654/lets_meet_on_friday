package com.kosmos.app.assistant.context

import org.json.JSONObject

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
