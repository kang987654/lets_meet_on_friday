package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.usecase.SearchKnowledgeUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import java.util.UUID

/**
 * [ContextBuilder]
 * LLM 모델에게 전달할 현재 세션의 컨텍스트(과거 대화 기록 등)를 구성하는 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context Management)
 * - **Dependencies**: [ConversationRepository], [Tokenizer], [SettingsDataStore], [SearchKnowledgeUseCase]
 *
 * ### Key Flow
 * 1. 세션 ID를 기반으로 최근 대화 기록 조회
 * 2. 가장 최근 유저 메시지 추출 후 RAG 메모리 검색(SearchKnowledgeUseCase) 수행
 * 3. 검색된 메모리를 System 메시지로 변환하여 컨텍스트에 삽입
 * 4. 모델의 Context Window 한도(예: 3000 토큰)를 넘지 않도록 Token Sliding Window 적용
 * 5. 최적화된 [ChatMessage] 목록을 포함하는 [Context] 반환
 */
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val searchKnowledgeUseCase: SearchKnowledgeUseCase,
    private val tokenizer: Tokenizer,
    private val settingsDataStore: SettingsDataStore
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String,
        val responseStyle: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // Fetch up to 150 recent conversations to apply sliding window
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            150
        )

        val responseStyle = try {
            settingsDataStore.responseStyleFlow.first()
        } catch (e: Exception) {
            "DEFAULT"
        }

        val maxTokens = try {
            settingsDataStore.maxTokensFlow.first()
        } catch (e: Exception) {
            Constants.MAX_CONTEXT_TOKENS
        }

        return when (conversationsResult) {
            is AppResult.Success -> {
                val messages = conversationsResult.data.toMutableList()
                
                // RAG: 가장 마지막 사용자 입력 메시지를 추출하여 벡터 검색 수행
                val lastUserMessage = messages.lastOrNull { it.role == ChatMessage.Role.USER }?.content
                if (!lastUserMessage.isNullOrBlank()) {
                    val searchResult = searchKnowledgeUseCase(query = lastUserMessage, limit = 3)
                    if (searchResult is AppResult.Success && searchResult.data.isNotEmpty()) {
                        val memoryText = searchResult.data.joinToString("\n\n") { note ->
                            "- ${note.content}"
                        }
                        
                        val ragMessage = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            role = ChatMessage.Role.SYSTEM,
                            content = "다음은 사용자에 대한 과거 기억(Memory)입니다. 답변에 필요하다면 적극 활용하세요:\n$memoryText",
                            inputType = com.kosmos.app.domain.model.InputType.TEXT,
                            searchUsed = false,
                            createdAt = System.currentTimeMillis()
                        )
                        // 시스템 메시지이므로 과거 대화 기록 최상단 근처에 배치 (원하는 위치에 추가)
                        // 여기서는 프롬프트 어셈블러가 System 메시지를 별도로 파싱하므로 단순히 목록 끝이나 처음에 넣어도 됨
                        // PromptAssembler는 `filter { it.role == SYSTEM }` 으로 추출하므로 순서 무관
                        messages.add(ragMessage)
                    }
                }

                val slidingWindow = applyTokenSlidingWindow(messages, maxTokens)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId,
                        responseStyle = responseStyle
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    private fun applyTokenSlidingWindow(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        var currentTokens = 0
        val selectedMessages = mutableListOf<ChatMessage>()
        
        // messages is chronological (oldest first). We iterate from newest (end) to oldest.
        for (message in messages.reversed()) {
            val msgTokens = tokenizer.sizeInTokens(message.content) + 10 // overhead for tags
            if (currentTokens + msgTokens > maxTokens) {
                break
            }
            currentTokens += msgTokens
            selectedMessages.add(message)
        }
        
        // Restore chronological order
        return selectedMessages.reversed()
    }
}
