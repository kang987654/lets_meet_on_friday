package com.kosmos.app.assistant.context

import com.kosmos.app.domain.model.ModelOutput
import org.json.JSONObject
import javax.inject.Inject

class ResponseParser @Inject constructor() {

    fun parse(rawString: String): ModelOutput {
        // 로컬 LLM이 JSON을 Markdown 코드 블록으로 감싸서 보내는 경우를 대비해 전처리
        var jsonString = rawString.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
            
        jsonString = sanitizeJson(jsonString)
            
        return try {
            val jsonObject = JSONObject(jsonString)
            val type = jsonObject.optString("type", "")

            when (type) {
                "text" -> {
                    val content = jsonObject.optString("text", "")
                    if (content.isBlank()) {
                        ModelOutput.TextOutput(content = jsonString)
                    } else {
                        ModelOutput.TextOutput(content = content)
                    }
                }
                "calendar_draft" -> {
                    ModelOutput.CalendarDraftOutput(
                        title = jsonObject.optString("title", "Event"),
                        startIso = jsonObject.optString("startTime", ""),
                        endIso = jsonObject.optString("endTime", ""),
                        note = jsonObject.optString("description").takeIf { it.isNotBlank() },
                        confidence = jsonObject.optDouble("confidence", 1.0).toFloat()
                    )
                }
                "get_schedule" -> {
                    ModelOutput.GetScheduleOutput(
                        date = jsonObject.optString("date", "")
                    )
                }
                "search" -> {
                    ModelOutput.SearchOutput(
                        query = jsonObject.optString("query", ""),
                        reason = jsonObject.optString("reason", "")
                    )
                }
                "save_knowledge", "knowledge_save" -> {
                    val tagsArray = jsonObject.optJSONArray("tags")
                    val tagsList = mutableListOf<String>()
                    if (tagsArray != null) {
                        for (i in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.optString(i, ""))
                        }
                    }
                    ModelOutput.KnowledgeSaveOutput(
                        content = jsonObject.optString("content", ""),
                        tags = tagsList
                    )
                }
                else -> {
                    // Fallback for unknown type
                    ModelOutput.TextOutput(content = jsonString)
                }
            }
        } catch (e: Exception) {
            // Fallback: try parsing with regex if JSON object constructor fails
            tryRegexFallback(jsonString) ?: ModelOutput.TextOutput(content = rawString)
        }
    }

    private fun sanitizeJson(json: String): String {
        var clean = json.trim()
        // 1. Double commas (e.g. ,, or , ,) to single comma
        clean = clean.replace(Regex(",\\s*,"), ",")
        // 2. Trailing comma before closing brace/bracket (e.g. , } or , ])
        clean = clean.replace(Regex(",\\s*}"), "}")
        clean = clean.replace(Regex(",\\s*]"), "]")
        return clean
    }

    private fun tryRegexFallback(json: String): ModelOutput? {
        try {
            val typeRegex = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")
            val typeMatch = typeRegex.find(json) ?: return null
            val type = typeMatch.groupValues[1]

            return when (type) {
                "text" -> {
                    val textRegex = Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    val textMatch = textRegex.find(json)
                    val content = textMatch?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                    ModelOutput.TextOutput(content = content.takeIf { it.isNotBlank() } ?: json)
                }
                "calendar_draft" -> {
                    val titleRegex = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"")
                    val startTimeRegex = Regex("\"startTime\"\\s*:\\s*\"([^\"]*)\"")
                    val endTimeRegex = Regex("\"endTime\"\\s*:\\s*\"([^\"]*)\"")
                    val descRegex = Regex("\"description\"\\s*:\\s*\"([^\"]*)\"")

                    val title = titleRegex.find(json)?.groupValues?.get(1) ?: "Event"
                    val startIso = startTimeRegex.find(json)?.groupValues?.get(1) ?: ""
                    val endIso = endTimeRegex.find(json)?.groupValues?.get(1) ?: ""
                    val note = descRegex.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

                    ModelOutput.CalendarDraftOutput(
                        title = title,
                        startIso = startIso,
                        endIso = endIso,
                        note = note,
                        confidence = 1.0f
                    )
                }
                "get_schedule" -> {
                    val dateRegex = Regex("\"date\"\\s*:\\s*\"([^\"]*)\"")
                    val date = dateRegex.find(json)?.groupValues?.get(1) ?: ""
                    ModelOutput.GetScheduleOutput(date = date)
                }
                "search" -> {
                    val queryRegex = Regex("\"query\"\\s*:\\s*\"([^\"]*)\"")
                    val query = queryRegex.find(json)?.groupValues?.get(1) ?: ""
                    ModelOutput.SearchOutput(query = query, reason = "")
                }
                "save_knowledge", "knowledge_save" -> {
                    val contentRegex = Regex("\"content\"\\s*:\\s*\"([^\"]*)\"")
                    val content = contentRegex.find(json)?.groupValues?.get(1) ?: ""
                    ModelOutput.KnowledgeSaveOutput(content = content, tags = emptyList())
                }
                else -> null
            }
        } catch (e: Exception) {
            return null
        }
    }
}
