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
 * [CalendarAgent]
 * 캘린더 관련 작업(조회 및 등록)을 전문으로 처리하는 에이전트입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent
 * - **Dependencies**: [BaseAgent], [PromptAssembler]
 *
 * ### Key Flow
 * 1. 캘린더 전용 시스템 롤(Calendar Assistant)을 주입받아 Prompt를 구성합니다.
 * 2. `AddSchedule`, `GetSchedule` 도구만을 허용하여 모델 추론의 범위를 한정합니다.
 */
class CalendarAgent @Inject constructor(
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
        val initialPrompt = promptAssembler.assembleWithTools(context, request.message, tools, systemRole = "Calendar Assistant")
        return executeToolLoop(request, initialPrompt, tools)
    }

    // 캘린더 전용 도구 한정 — 프롬프트 노출과 실행 시점 검증에 동일 목록 사용
    override fun availableTools(context: ContextBuilder.Context): List<String> =
        listOf("AddSchedule", "GetSchedule")
}
