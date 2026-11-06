package com.kosmos.app.assistant.context

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.CurrentLanguage
import com.kosmos.app.core.common.ResponseStyle
import com.kosmos.app.core.common.runCatchingCancellable
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.data.local.prefs.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import com.kosmos.app.domain.agent.RefusalTurns
import com.kosmos.app.domain.agent.RepeatedQuestions
import com.kosmos.app.domain.memory.ProfileRepository
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.libraryDisplayName
import com.kosmos.app.assistant.episode.EpisodeBoundaryManager
import com.kosmos.app.domain.agent.IdleGapHistory
import com.kosmos.app.domain.memory.EpisodeRepository
import com.kosmos.app.domain.model.EpisodeStatus

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
 * 2. 프리필 예산([Constants.DEFAULT_CONTEXT_TOKENS] 기본, 설정에서 조절)에서 시스템 지시·툴 선언
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
    private val settingsDataStore: SettingsDataStore,
    private val profileRepository: ProfileRepository,
    private val libraryRepository: LibraryRepository,
    private val currentLanguage: CurrentLanguage = CurrentLanguage.KO,
    // [WHY] 오래 쉬었다 돌아오면 지난 대화를 요약 한 줄로 싣는다(0.41.7 M4) — 요약을 읽는다. null 이면(테스트) 지금처럼 원문.
    private val episodeRepository: EpisodeRepository? = null
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String,
        val responseStyle: String,
        val webSearchEnabled: Boolean = false,
        /**
         * 문서 검색 토글이 켜져 있고 보관함에 쓸 수 있는 문서가 있는가(0.41.2) — 참이면 `search_documents` 선언·트리거가 붙고 그만큼 대화 기록 예산이 준다.
         */
        val docSearchEnabled: Boolean = false,
        /** 문서 검색 트리거 줄에 넣을 등록 문서 이름(0.41.2, exp79 a1) — 켜졌을 때만 채운다. [libraryNamesForPrompt] 참조. */
        val libraryNames: List<String> = emptyList(),
        /**
         * 렌더된 `[User Profile]` 블록(C′1) — 비어 있으면 시스템 지시에서 블록째 생략된다.
         *
         * [WHY] 매 턴 다시 읽는다(responseStyle 전례) — 프로필 편집이 다음 턴의 시스템
         * 지시에 반영되고, 런타임의 문자열 비교가 그때 1회만 재프리필을 만든다.
         */
        val profileText: String = "",
        /**
         * 설정에서 읽은 프리필 예산(토큰). 슬라이딩 윈도우뿐 아니라 런타임의 Conversation
         * 재설정 임계값에도 쓰이므로 컨텍스트에 실어 내보낸다.
         *
         * [WHY] 예전에는 이 값이 이 클래스 안에서만 쓰이고 사라졌다. 그래서 사용자가 예산을
         * 내려도 살아 있는 대화의 KV 는 런타임에 박힌 8000 토큰까지 자랐다 — 설정이 실제
         * 메모리 사용에 아무 영향을 주지 못했다.
         */
        val maxTokens: Int = Constants.DEFAULT_CONTEXT_TOKENS,
        /**
         * 지금 질문과 같은 앞 질문·답을 모델 기록에서 뺐는가([com.kosmos.app.domain.agent.RepeatedQuestions]).
         * [WHY] 뺐다면 살아 있는 대화(KV)에는 그 앞 답이 남아 있으므로 이 턴에 대화를 다시 만들어야 한다(KosmosAgent).
         */
        val repeatDropped: Boolean = false,
        /** 현재 앱 표시/비서 언어(0.40.0). */
        val language: AppLanguage = AppLanguage.KO,
        /**
         * 오래 쉬었다 돌아왔을 때 지난 대화의 요약 한 줄(`(과거 대화) 제목: 요약`, 0.41.7 M4) — 비어 있으면 붙이지 않는다.
         * 시스템 지시 끝에 붙고, 그 원문은 [recentConversations] 에서 빠진다([IdleGapHistory]).
         */
        val pastEpisodeLine: String = ""
    )

    /**
     * 지금 시각으로 [build] 합니다.
     *
     * [WHY] 기본값 인자(`now = …`)로 두지 않고 따로 둔다 — 목(mockk)이 `build("s1")` 을 기록하는 순간의 기본 시각까지 고정해 실제 호출과
     * 어긋났다(0.41.7 M4 게이트에서 미리 준비 테스트 4건). 한 인자 호출은 그대로 이 함수다.
     */
    suspend fun build(sessionId: String): AppResult<Context> = build(sessionId, System.currentTimeMillis())

    /**
     * @param now 지금 — 오래 쉰 간격을 가르는 기준(AGENTS §2-④).
     */
    suspend fun build(sessionId: String, now: Long): AppResult<Context> {
        // 슬라이딩 윈도우에 넣을 후보를 넉넉히 가져온다.
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            Constants.MAX_RECENT_CONVERSATIONS
        )

        // [WHY] runCatchingCancellable — 설정 읽기 실패는 기본값으로 강등하되, 턴 취소까지
        // 기본값으로 삼켜 파이프라인을 계속 돌리면 안 된다(plain catch(Exception) 이던 곳).
        val responseStyle = runCatchingCancellable { settingsDataStore.responseStyleFlow.first() }
            .getOrDefault(ResponseStyle.DEFAULT)

        val maxTokens = runCatchingCancellable { settingsDataStore.maxTokensFlow.first() }
            .getOrDefault(Constants.DEFAULT_CONTEXT_TOKENS)

        // [WHY] 읽기 실패 시 프라이버시 우선 원칙에 따라 웹 검색은 비활성(false)으로 폴백한다.
        val webSearchEnabled = runCatchingCancellable { settingsDataStore.webSearchEnabledFlow.first() }
            .getOrDefault(false)

        // [WHY] 보관함이 비면 토글이 켜져 있어도 선언하지 않는다(0.41.2 Q2) — 찾을 문서가 없는데 비용만 낸다. 읽기 실패는 꺼짐.
        val docToggle = runCatchingCancellable { settingsDataStore.docSearchEnabledFlow.first() }.getOrDefault(false)
        val libraryNames = if (!docToggle) emptyList() else runCatchingCancellable { libraryNamesForPrompt(libraryRepository.observeDocuments().first()) }.getOrDefault(emptyList())
        val docSearchEnabled = libraryNames.isNotEmpty()

        // [WHY] 읽기 실패는 "프로필 없음"으로 강등 — 프로필 배선 문제가 채팅을 막지 않는다.
        val profileText = when (val entries = profileRepository.getEntries()) {
            is AppResult.Success -> renderProfileBlock(entries.data)
            is AppResult.Failure -> ""
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
                // [WHY] 오래 쉬었다 돌아왔으면 지난 대화를 요약 한 줄로 바꾼다(0.41.7 M4, IdleGapHistory [WHY]) — 거절·반복 걸러내기보다 먼저
                // 나눠, 지금 대화 안에서만 걸러낸다. 요약이 아직 없으면 지금처럼 원문을 싣는다(F1 — 시작 단계가 대부분 미리 요약해 둔다).
                val (kept, pastEpisodeLine) = splitIdleGap(conversationsResult.data, now)
                val refusalKept = RefusalTurns.forModel(kept)
                // [WHY] 같은 질문의 앞 답을 뺀다 — 모델이 자기 앞 답을 되풀이했다(0.39.3 M4d, RepeatedQuestions [WHY]).
                val forModel = RepeatedQuestions.forModel(refusalKept)
                val lineTokens = if (pastEpisodeLine.isEmpty()) 0 else tokenizer.sizeInTokens(pastEpisodeLine)
                val slidingWindow = applyTokenSlidingWindow(forModel, maxTokens, docSearchEnabled, lineTokens)
                AppResult.Success(
                    Context(
                        recentConversations = slidingWindow,
                        sessionId = sessionId,
                        responseStyle = responseStyle,
                        webSearchEnabled = webSearchEnabled,
                        docSearchEnabled = docSearchEnabled,
                        libraryNames = libraryNames,
                        profileText = profileText,
                        maxTokens = maxTokens,
                        repeatDropped = forModel.size != refusalKept.size,
                        language = currentLanguage.current,
                        pastEpisodeLine = pastEpisodeLine
                    )
                )
            }
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }

    /** 오래 쉰 간격 앞을 요약 한 줄로 — (모델에 실을 메시지, 요약 줄). 요약을 못 읽으면 (전부, ""). */
    private suspend fun splitIdleGap(messages: List<ChatMessage>, now: Long): Pair<List<ChatMessage>, String> {
        val repo = episodeRepository ?: return messages to ""
        val split = IdleGapHistory.split(messages, now, EpisodeBoundaryManager.IDLE_BOUNDARY_MS)
        val lastPast = split.previous.lastOrNull() ?: return messages to ""
        val episode = lastPast.episodeId?.let { (repo.getById(it) as? AppResult.Success)?.data }
        val summary = episode?.summary?.takeIf { episode.status == EpisodeStatus.SUMMARIZED && it.isNotBlank() }
            ?: return messages to ""
        return split.current to IdleGapHistory.line(episode.title, summary, currentLanguage.current)
    }

    /**
     * 대화 기록을 최신부터 담다가 예산을 넘으면 멈춥니다.
     *
     * [WHY] `maxTokens` 는 **프리필 전체 예산**인데 이 윈도우는 `ChatMessage.content` 만 센다.
     * 시스템 지시·툴 선언·프로필 블록은 여기서 세지지 않으므로 예산을 그대로 히스토리에
     * 쓰면 실제 프리필이 설정값을 넘는다 — 오버헤드를 먼저 예약하고 남은 것만 히스토리에
     * 배분한다. few-shot(104)은 0.23.0 에서 제거돼 그 몫이 프로필 상한으로 넘어갔다
     * (합산 실측의 진실은 TokenBudgetInvariantTest.MEASURED_OVERHEAD 주석).
     *
     * [WHY] 예전 주석은 툴 선언을 "실측 ~2천 토큰" 이라고 적었고 예약도 2600 이었다. `token_count`
     * 로 실제로 재 보니 합계가 1,329 였다(`scratch/lab/measure_overhead.py`) — 과대 예약이
     * 히스토리 예산을 1,200 토큰 넘게 조용히 깎고 있었다.
     *
     * [WHY] 예약 후 예산이 바닥나도 [Constants.MIN_HISTORY_TOKENS] 는 남긴다. 사용자가 슬라이더를
     * 최소로 내렸을 때 히스토리가 0이 되면 대화가 아예 성립하지 않는다.
     */
    private fun applyTokenSlidingWindow(messages: List<ChatMessage>, maxTokens: Int, docSearchEnabled: Boolean, pastLineTokens: Int = 0): List<ChatMessage> {
        // [WHY] 문서 검색이 켜진 턴만 그 선언·트리거 몫을 더 예약한다(0.41.2 예산 ① 사용자 결정) — 꺼짐 사용자의 대화 예산은 그대로다.
        // [WHY] 지난 대화 요약 줄은 시스템 지시에 붙지만 예약([Constants.PREFILL_OVERHEAD_TOKENS])에는 없다 — 그만큼 대화 예산에서 뺀다(0.41.7 M4).
        val reserve = Constants.PREFILL_OVERHEAD_TOKENS + pastLineTokens + if (docSearchEnabled) Constants.DOC_SEARCH_OVERHEAD_TOKENS else 0
        val historyBudget = (maxTokens - reserve)
            .coerceAtLeast(Constants.MIN_HISTORY_TOKENS)

        var currentTokens = 0
        val selectedMessages = mutableListOf<ChatMessage>()

        // messages is chronological (oldest first). We iterate from newest (end) to oldest.
        for (message in messages.reversed()) {
            val msgTokens = tokenizer.sizeInTokens(message.content) + Constants.PER_MESSAGE_TEMPLATE_TOKENS
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

/**
 * 문서 검색 트리거 줄의 등록 문서 이름(0.41.2, exp79 a1) — 색인이 끝난 문서만, 등록 순, 확장자 없이.
 *
 * [WHY] 등록 순으로 고정한다 — 시스템 지시가 바이트 단위로 같아야 대화 KV 를 다시 쓴다(ADR-010). 문서가 늘거나 줄 때만 한 번 바뀐다.
 * [WHY] 글자 상한([Constants.LIBRARY_NAMES_MAX_CHARS])에서 끊는다 — 켜짐 예약([Constants.DOC_SEARCH_OVERHEAD_TOKENS])이 이 상한까지 덮는다.
 * 넘치면 들어간 이름 뒤에 "…" 하나를 붙인다(모델에게 목록이 더 있다는 표시).
 */
internal fun libraryNamesForPrompt(documents: List<LibraryDocument>): List<String> {
    val names = documents.filter { it.status == LibraryStatus.READY }
        .sortedWith(compareBy({ it.addedAt }, { it.id }))
        .map { libraryDisplayName(it.name) }
    val out = mutableListOf<String>()
    var used = 0
    for (name in names) {
        val cost = name.length + if (out.isEmpty()) 0 else 2
        if (used + cost > Constants.LIBRARY_NAMES_MAX_CHARS) {
            out += if (out.isEmpty()) name.take(Constants.LIBRARY_NAMES_MAX_CHARS) + "…" else "…"
            break
        }
        out += name
        used += cost
    }
    return out
}