package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import java.util.UUID
import javax.inject.Inject

/**
 * [AssistantOrchestrator]
 * 유저 입력을 저장하고 컨텍스트를 구성해 에이전트에 위임하는 핵심 오케스트레이터입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration)
 * - **Dependencies**: [ContextBuilder], [AuditTrailService], [ConversationRepository]
 *
 * ### Key Flow
 * 1. 유저 메시지 DB 저장
 * 2. [ContextBuilder]를 통한 대화 기록 로드 (Token Sliding Window 적용)
 * 3. [PromptAssembler]로 최종 프롬프트 조립
 * 4. [ModelRunner] 실행 및 JSON 결과 파싱 (내부 루프를 통해 Tool 재귀 실행)
 * 5. Guard 정책 검사 후 결과 반환
 */
class AssistantOrchestrator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val contextBuilder: ContextBuilder,
    private val auditTrailService: AuditTrailService,
    private val agent: com.kosmos.app.assistant.agent.KosmosAgent,
    private val transcribeAudioUseCase: com.kosmos.app.domain.usecase.TranscribeAudioUseCase
) {

    suspend fun processRequest(request: ChatRequest): AgentResult {
        // 1. 음성이면 먼저 전사한다.
        //
        // [WHY] 예전에는 사용자 메시지를 `"(음성 메시지)"` 로 저장하고 오디오를 그대로 대화에
        // 실어 보냈다. 그러면 (a) 대화 기록에 사용자가 무슨 말을 했는지 남지 않아 나중에 다시
        // 열면 자기 발화가 비어 있고, (b) 오디오가 대화 KV 에 남아 컨텍스트를 더 먹는다.
        // 전사해서 **텍스트 턴**으로 돌리면 저장·검색·히스토리가 전부 정상이 되고, PRD F2 의
        // 원래 의도("음성을 텍스트로 변환 후 채팅과 동일한 흐름")에도 부합한다 (ADR-014).
        val transcript = if (request.audioFilePath != null) {
            when (val result = transcribeAudioUseCase(request.audioFilePath)) {
                is AppResult.Success -> result.data
                // [WHY] 전사에 실패하면 **사용자 메시지를 저장하지 않는다.** 내용 없는 말풍선을
                // 남기는 대신 재시도 안내만 띄운다 (PRD EC3).
                is AppResult.Failure -> return AgentResult.Error(result.error)
            }
        } else {
            null
        }
        // 전사가 끝났으면 임시 파일은 남길 이유가 없다(캐시에 그대로 두면 마지막 녹음이 계속 남는다).
        if (request.audioFilePath != null) {
            runCatching { java.io.File(request.audioFilePath).delete() }
        }

        // 2. 유저 메시지 저장
        val content = transcript ?: request.message
        val inputType = if (request.audioFilePath != null) InputType.VOICE else if (request.imageBytes != null) InputType.IMAGE else InputType.TEXT
        val saveUserResult = createAndSaveMessage(request.sessionId, ChatMessage.Role.USER, content, inputType)
        if (saveUserResult is AppResult.Failure) {
            return AgentResult.Error(saveUserResult.error)
        }

        // 3. 문서 텍스트 저장
        // [WHY] 첨부 문서를 SYSTEM 역할로 저장하면 문서 안의 지시문이 시스템 프롬프트로 승격되어
        // 세션 내내 지속되는 프롬프트 인젝션 경로가 된다. USER 역할의 구분된 블록으로 저장한다.
        if (request.documentText != null) {
            val documentBlock = "[Attached Document]\n\"\"\"\n${request.documentText}\n\"\"\""
            val saveDocResult = createAndSaveMessage(request.sessionId, ChatMessage.Role.USER, documentBlock, InputType.TEXT)
            if (saveDocResult is AppResult.Failure) {
                return AgentResult.Error(saveDocResult.error)
            }
        }

        // 4. 컨텍스트 구성
        val contextResult = contextBuilder.build(request.sessionId)
        val context = when (contextResult) {
            is AppResult.Success -> contextResult.data
            is AppResult.Failure -> return handleErrorAndReturn(request.sessionId, "대화 문맥을 구성하지 못했습니다: ${contextResult.error}")
        }

        // 5. 에이전트 위임
        // [WHY] 사전 라우팅을 폐지했다 — 툴 선택은 모델이 런타임 함수호출로 직접 한다.
        // 키워드 기반 라우팅은 분류가 틀리면 툴이 사라지는 실패 모드를 만들었다 (ADR-008).
        //
        // [WHY] 음성이었다면 여기서부터는 **순수 텍스트 턴**이다 — 전사문을 message 로 넘기고
        // audioFilePath 를 비운다. 에이전트가 오디오를 다시 보낼 이유가 없다.
        val agentRequest = if (transcript != null) {
            request.copy(message = transcript, audioFilePath = null)
        } else {
            request
        }
        return agent.execute(agentRequest, context)
    }

    private suspend fun handleErrorAndReturn(sessionId: String, errorMsg: String): AgentResult.Error {
        auditTrailService.logError(sessionId, errorMsg)
        createAndSaveMessage(sessionId, ChatMessage.Role.ASSISTANT, errorMsg, InputType.TEXT)
        return AgentResult.Error(com.kosmos.app.core.common.AppError.ModelInferenceError(errorMsg))
    }

    private suspend fun createAndSaveMessage(
        sessionId: String, 
        role: ChatMessage.Role, 
        content: String, 
        inputType: InputType,
        searchUsed: Boolean = false, 
        thinkingProcess: String? = null
    ): AppResult<Unit> {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            inputType = inputType,
            searchUsed = searchUsed,
            createdAt = System.currentTimeMillis(),
            thinkingProcess = thinkingProcess
        )
        return conversationRepository.save(message)
    }
}
