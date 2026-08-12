package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.data.local.prefs.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * [ContextBuilder]
 * LLM 모델에게 전달할 현재 세션의 컨텍스트(과거 대화 기록 등)를 구성하는 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Context Management)
 * - **Dependencies**: [ConversationRepository], [Tokenizer], [SettingsDataStore]
 *
 * ### Key Flow
 * 1. 세션 ID를 기반으로 최근 대화 기록 조회
 * 2. 프리필 예산([Constants.MAX_CONTEXT_TOKENS] 기본, 설정에서 조절)에서 시스템 지시·툴 선언
 *    오버헤드를 예약한 뒤, 남은 예산만큼 Token Sliding Window 적용
 * 3. 최적화된 [ChatMessage] 목록을 포함하는 [Context] 반환
 *
 * [WHY] 예전에는 여기서 매 턴 RAG 검색을 수행해 결과를 SYSTEM 메시지로 끼워 넣었다.
 * 임베더가 영어 전용이라 한국어 검색이 무작위였으므로 제거했고, 기억 조회는 `SearchMemory`
 * 툴로 옮겼다 (ADR-013).
 */
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val tokenizer: Tokenizer,
    private val settingsDataStore: SettingsDataStore
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String,
        val responseStyle: String,
        val webSearchEnabled: Boolean = false,
        /**
         * 설정에서 읽은 프리필 예산(토큰). 슬라이딩 윈도우뿐 아니라 런타임의 Conversation
         * 재설정 임계값에도 쓰이므로 컨텍스트에 실어 내보낸다.
         *
         * [WHY] 예전에는 이 값이 이 클래스 안에서만 쓰이고 사라졌다. 그래서 사용자가 예산을
         * 내려도 살아 있는 대화의 KV 는 런타임에 박힌 8000 토큰까지 자랐다 — 설정이 실제
         * 메모리 사용에 아무 영향을 주지 못했다.
         */
        val maxTokens: Int = Constants.MAX_CONTEXT_TOKENS
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        // 슬라이딩 윈도우에 넣을 후보를 넉넉히 가져온다.
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            Constants.MAX_RECENT_CONVERSATIONS
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

        // [WHY] 읽기 실패 시 프라이버시 우선 원칙에 따라 웹 검색은 비활성(false)으로 폴백한다.
        val webSearchEnabled = try {
            settingsDataStore.webSearchEnabledFlow.first()
        } catch (e: Exception) {
            false
        }

        return when (conversationsResult) {
            is AppResult.Success -> {
                // [WHY] **매 턴 자동 RAG 주입을 제거했다** (ADR-013). 예전에는 마지막 사용자
                // 발화를 임베딩해 상위 3건을 SYSTEM 메시지로 끼워 넣었는데, 앱이 싣고 있는
                // 임베더가 영어 전용이라 한국어에서는 검색이 **무작위**였다(PC 실측: 서로
                // 무관한 한국어 문장들의 쌍별 코사인 0.93~1.00, 관련/무관 분리도 0.000,
                // top-1 정확도 1/7 = 무작위 1/8 수준). 그래서 실제로 하던 일은 "무관한 메모
                // 3건을 매 턴 질문 앞에 붙이는 것"이었다 — 프리필을 축내고 환각의 재료가 됐다.
                // 기억 조회는 이제 모델이 필요할 때 부르는 `SearchMemory` 툴이 맡는다.
                val slidingWindow = applyTokenSlidingWindow(conversationsResult.data, maxTokens)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId,
                        responseStyle = responseStyle,
                        webSearchEnabled = webSearchEnabled,
                        maxTokens = maxTokens
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    /**
     * 대화 기록을 최신부터 담다가 예산을 넘으면 멈춥니다.
     *
     * [WHY] `maxTokens` 는 **프리필 전체 예산**인데 이 윈도우는 `ChatMessage.content` 만 센다.
     * 시스템 지시(실측 573), 툴 5종 선언(**652**), few-shot 시범(104)은 여기서 세지지 않으므로
     * 예산을 그대로 히스토리에 쓰면 실제 프리필이 설정값을 넘는다 — 설정이 뜻대로 동작하지
     * 않았다. 오버헤드를 먼저 예약하고 남은 것만 히스토리에 배분한다.
     *
     * [WHY] 예전 주석은 툴 선언을 "실측 ~2천 토큰" 이라고 적었고 예약도 2600 이었다. `token_count`
     * 로 실제로 재 보니 합계가 1,329 였다(`scratch/lab/measure_overhead.py`) — 과대 예약이
     * 히스토리 예산을 1,200 토큰 넘게 조용히 깎고 있었다.
     *
     * [WHY] 예약 후 예산이 바닥나도 [Constants.MIN_HISTORY_TOKENS] 는 남긴다. 사용자가 슬라이더를
     * 최소로 내렸을 때 히스토리가 0이 되면 대화가 아예 성립하지 않는다.
     */
    private fun applyTokenSlidingWindow(messages: List<ChatMessage>, maxTokens: Int): List<ChatMessage> {
        val historyBudget = (maxTokens - Constants.PREFILL_OVERHEAD_TOKENS)
            .coerceAtLeast(Constants.MIN_HISTORY_TOKENS)

        var currentTokens = 0
        val selectedMessages = mutableListOf<ChatMessage>()

        // messages is chronological (oldest first). We iterate from newest (end) to oldest.
        for (message in messages.reversed()) {
            val msgTokens = tokenizer.sizeInTokens(message.content) + 10 // overhead for tags
            if (currentTokens + msgTokens > historyBudget) {
                break
            }
            currentTokens += msgTokens
            selectedMessages.add(message)
        }

        // Restore chronological order
        return selectedMessages.reversed()
    }
}
