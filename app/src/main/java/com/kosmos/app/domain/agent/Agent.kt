package com.kosmos.app.domain.agent

interface Agent {
    suspend fun execute(input: String, context: Map<String, Any>): AgentResult
}
