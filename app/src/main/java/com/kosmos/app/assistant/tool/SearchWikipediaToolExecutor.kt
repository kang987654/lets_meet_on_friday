package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.usecase.QueryWikipediaUseCase
import javax.inject.Inject

class SearchWikipediaToolExecutor @Inject constructor(
    private val queryWikipediaUseCase: QueryWikipediaUseCase
) : ToolExecutor {
    override val name: String = "SearchWikipedia"

    override suspend fun execute(args: Map<String, Any>, sessionId: String): String {
        val topic = args["topic"] as? String ?: return "{\"status\": \"error\", \"message\": \"topic is required\"}"
        val lang = args["lang"] as? String ?: "ko"

        return when (val res = queryWikipediaUseCase(topic, lang)) {
            is AppResult.Success -> {
                // Escape string properly for JSON output if needed, but returning JSON string directly is fine since ToolExecutor just returns String
                // Actually, the result might have newlines which breaks JSON sometimes if not escaped properly. 
                // Using JSON string formatting.
                val formatted = res.data.replace("\"", "\\\"").replace("\n", "\\n")
                "{\"status\": \"success\", \"data\": \"$formatted\"}"
            }
            is AppResult.Failure -> {
                "{\"status\": \"error\", \"message\": \"검색 중 오류 발생: ${res.error}\"}"
            }
        }
    }
}
