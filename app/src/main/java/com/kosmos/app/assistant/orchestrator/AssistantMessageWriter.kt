package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import java.util.UUID

/**
 * [AssistantMessageWriter]
 * 대화 말풍선 저장과 "오류 말풍선 + 감사 기록" 쌍의 단일 출처입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Orchestration)
 * - **Dependencies**: [ConversationRepository], [AuditTrailService]
 *
 * ### Key Flow
 * 1. [save] — id·시각을 채워 [ChatMessage] 를 저장한다(시각은 `now` 인자, AGENTS §2-④).
 * 2. [saveError] — 감사에는 진단 원문을, 말풍선에는 [ErrorMessages] 의 사용자 문구를 남긴다.
 *
 * [WHY] 같은 `createAndSaveMessage` 가 BaseAgent·AssistantOrchestrator 에, `handleErrorAndReturn` 이
 * 두 곳에 복제돼 있었고 실제로 한쪽만 고쳐진 전례가 있다(0.11.0 — 오케스트레이터가 내부 오류
 * 문자열을 말풍선에 저장). DI 대상이 아니라 호출자가 이미 가진 의존성으로 만드는 값 객체다 —
 * 생성자를 늘리면 에이전트를 직접 조립하는 테스트들의 생성부가 전부 바뀐다.
 */
class AssistantMessageWriter(
    private val conversationRepository: ConversationRepository,
    private val auditTrailService: AuditTrailService
) {

    suspend fun save(
        sessionId: String,
        role: ChatMessage.Role,
        content: String,
        inputType: InputType,
        searchUsed: Boolean = false,
        thinkingProcess: String? = null,
        episodeId: String? = null,
        recallEpisodeIds: List<String> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): AppResult<Unit> = conversationRepository.save(
        ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            inputType = inputType,
            searchUsed = searchUsed,
            createdAt = now,
            thinkingProcess = thinkingProcess,
            episodeId = episodeId,
            recallEpisodeIds = recallEpisodeIds
        )
    )

    /**
     * @param auditDetail 감사 로그에 남길 진단 원문.
     * @param error 사용자에게 보여줄 오류. null 이면 [auditDetail] 을 담은 일반 추론 오류로 취급한다.
     */
    suspend fun saveError(
        sessionId: String,
        auditDetail: String,
        error: AppError? = null
    ): AgentResult.Error {
        auditTrailService.logError(sessionId, auditDetail)
        val shown = error ?: AppError.ModelInferenceError(auditDetail)
        save(sessionId, ChatMessage.Role.ASSISTANT, ErrorMessages.userMessage(shown), InputType.TEXT)
        return AgentResult.Error(shown)
    }
}
