package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.usecase.SaveKnowledgeUseCase
import javax.inject.Inject

class AddMemoryToolExecutor @Inject constructor(
    private val saveKnowledgeUseCase: SaveKnowledgeUseCase
) : ToolExecutor {
    override val name: String = "AddMemory"

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        val content = args["content"] as? String ?: return "{\"status\": \"error\", \"message\": \"content is required\"}"
        val tagsRaw = args["tags"]
        
        val tags = if (tagsRaw is List<*>) {
            tagsRaw.filterIsInstance<String>()
        } else if (tagsRaw is String) {
            tagsRaw.split(",").map { it.trim() }
        } else emptyList()

        return when (val res = saveKnowledgeUseCase(content, tags)) {
            is AppResult.Success -> {
                "{\"status\": \"success\", \"message\": \"Successfully saved to memory.\"}"
            }
            is AppResult.Failure -> {
                "{\"status\": \"error\", \"message\": \"Failed to save memory: ${res.error}\"}"
            }
        }
    }
}
