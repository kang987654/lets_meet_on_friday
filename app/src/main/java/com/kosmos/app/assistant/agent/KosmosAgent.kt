package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.assistant.context.PromptAssembler
import com.kosmos.app.assistant.orchestrator.ChatRequest
import com.kosmos.app.assistant.tool.ToolRegistry
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.modelrunner.ModelRunner
import javax.inject.Inject

/**
 * [KosmosAgent]
 * 모든 대화를 처리하는 단일 에이전트입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent
 * - **Dependencies**: [BaseAgent], [PromptAssembler]
 *
 * ### Key Flow
 * 1. 사용 가능한 툴 전부를 선언하고 프롬프트를 조립합니다.
 * 2. 툴 선택은 모델이 런타임의 함수호출 기능으로 직접 수행합니다.
 *
 * [WHY] 이전에는 `IntentClassifier` 가 한국어 키워드로 의도를 분류해 `CalendarAgent` 와
 * `DefaultAgent` 중 하나로 보냈고, **에이전트마다 툴 목록이 달랐다.** 그래서 분류가 틀리면
 * 해당 툴이 프롬프트에서 사라지고 실행 단계에서도 차단됐다 — "내일 3시에 치과 예약 잡아줘"가
 * 캘린더로 가지 못한 실기기 버그가 그것이다(문맥 키워드 목록에 `약속`은 있지만 `예약`은
 * 없었다). 한국어 표현을 키워드로 다 덮는 것은 불가능하고, 네이티브 함수호출에서는 모델이
 * 선언된 툴 중에서 스스로 고르므로 사전 라우팅 자체가 불필요하다 (ADR-008).
 */
class KosmosAgent @Inject constructor(
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
        val initialPrompt = promptAssembler.assembleWithTools(
            context = context,
            userInput = request.message,
            availableTools = tools,
            systemRole = "personal assistant named Kosmos"
        )
        return executeToolLoop(request, initialPrompt, tools)
    }

    // [WHY] 웹 검색만 토글로 게이트한다 — 프라이버시 우선 원칙에 따라 사용자가 켠 경우에만
    // 외부 요청이 가능해야 한다. 나머지는 로컬 동작이므로 상시 노출한다(승인은 별개 관문).
    //
    // [WHY] 가시성을 public 으로 넓혔다 — 부수효과 없는 조회이고, "표현과 무관하게 일정·메모리
    // 툴이 항상 있다"가 라우터 폐지의 핵심 계약이라 테스트로 고정해야 한다.
    public override fun availableTools(context: ContextBuilder.Context): List<String> = buildList {
        add("AddSchedule")
        add("GetSchedule")
        add("AddMemory")
        // [WHY] 기억 조회는 예전에 매 턴 자동 주입(RAG)이었다. 임베더가 영어 전용이라
        // 한국어 검색이 무작위였고(ADR-013), 무관한 메모 3건이 매 턴 붙어 프리필만 축내고
        // 환각의 재료가 됐다. 모델이 필요할 때 키워드로 찾는 툴로 바꿨다.
        add("SearchMemory")
        if (context.webSearchEnabled) {
            add("SearchWikipedia")
        }
    }
}
