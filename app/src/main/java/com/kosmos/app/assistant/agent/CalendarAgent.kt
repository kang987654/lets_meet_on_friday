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
        // 캘린더 전용 프롬프트 조합 (도구: AddSchedule, GetSchedule 한정)
        val availableTools = listOf("AddSchedule", "GetSchedule")
        return promptAssembler.assembleWithTools(context, userInput, availableTools, systemRole = "Calendar Assistant")
    }
}
