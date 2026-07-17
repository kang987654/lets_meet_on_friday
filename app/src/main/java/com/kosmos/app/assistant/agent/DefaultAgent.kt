package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.assistant.context.ResponseParser
import com.kosmos.app.assistant.guard.PreExecutionGuard
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import javax.inject.Inject

/**
 * [DefaultAgent]
 * 일상적인 대화 및 웹 검색 등 일반적인 목적의 작업을 처리하는 에이전트입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent
 * - **Dependencies**: [BaseAgent], [PromptAssembler]
 *
 * ### Key Flow
 * 1. 일반 어시스턴트 역할(General Assistant)을 주입받아 Prompt를 구성합니다.
 * 2. `SearchWeb` 등의 일반 도구를 허용하여 대화를 수행합니다.
 */
class DefaultAgent @Inject constructor(
    modelRunner: ModelRunner,
    toolRegistry: ToolRegistry,
    responseParser: ResponseParser,
    preExecutionGuard: PreExecutionGuard,
    auditTrailService: AuditTrailService,
    conversationRepository: ConversationRepository,
    private val promptAssembler: PromptAssembler
) : BaseAgent(
    modelRunner,
    toolRegistry,
    responseParser,
    preExecutionGuard,
    auditTrailService,
    conversationRepository
) {

    override suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult {
        val initialPrompt = assemblePrompt(context, request.message)
        return executeToolLoop(request, initialPrompt)
    }

    override fun assemblePrompt(context: ContextBuilder.Context, userInput: String): ChatPrompt {
        // 일상 대화 또는 기본 봇 프롬프트 조합 (기본 도구 사용)
        val availableTools = listOf("SearchWeb") // 캘린더 제외
        return promptAssembler.assembleWithTools(context, userInput, availableTools, systemRole = "General Assistant")
    }
}
