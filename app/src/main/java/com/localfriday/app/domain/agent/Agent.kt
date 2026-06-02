package com.localfriday.app.domain.agent

interface Agent {
    suspend fun execute(input: String, context: Map<String, Any>): AgentResult
}
