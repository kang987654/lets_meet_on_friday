package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
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
    auditTrailService: AuditTrailService,
    conversationRepository: ConversationRepository,
    approvalCoordinator: com.kosmos.app.assistant.approval.ApprovalCoordinator,
    private val promptAssembler: PromptAssembler
) : BaseAgent(
    modelRunner,
    toolRegistry,
    auditTrailService,
    conversationRepository,
    approvalCoordinator
) {

    override suspend fun execute(request: ChatRequest, context: ContextBuilder.Context): AgentResult {
        val tools = availableTools(context)
        val initialPrompt = promptAssembler.assembleWithTools(context, request.message, tools, systemRole = "General Assistant")
        return executeToolLoop(request, initialPrompt, tools)
    }

    // [WHY] 웹 검색은 프라이버시 우선 원칙에 따라 전역 토글이 켜진 경우에만 노출/허용된다.
    // AddMemory는 승인(MEMORY_WRITE) 기반으로 상시 제공한다.
    override fun availableTools(context: ContextBuilder.Context): List<String> = buildList {
        add("AddMemory")
        if (context.webSearchEnabled) {
            add("SearchWikipedia")
        }
    }
}
