package com.localfriday.app.assistant.context

import com.localfriday.app.domain.model.ModelOutput
import org.json.JSONObject
import javax.inject.Inject

class ResponseParser @Inject constructor() {

    fun parse(jsonString: String): ModelOutput {
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
            // Fallback for invalid JSON (e.g., model hallucinates free text instead of JSON)
            ModelOutput.TextOutput(content = jsonString)
        }
    }
}
