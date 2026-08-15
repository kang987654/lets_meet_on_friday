package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import org.json.JSONObject
import javax.inject.Inject

/**
 * [SearchMemoryToolExecutor]
 * 모델의 `SearchMemory` 툴 콜을 받아 저장된 기억(메모)에서 키워드로 찾는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [KnowledgeRepository]
 *
 * ### Key Flow
 * 1. 모델이 뽑아 준 키워드를 공백으로 쪼개 각 토큰으로 부분 일치 검색을 수행합니다.
 * 2. **일치한 토큰 수**가 많은 노트를 우선하고, 같으면 최신순으로 정렬해 상위 몇 건만 돌려줍니다.
 *
 * [WHY] 벡터 검색을 쓰지 않는다. 앱이 싣고 있는 임베더(`universal_sentence_encoder.tflite`)는
 * **영어 전용**이라 한국어에서 의미를 전혀 분별하지 못한다 — PC 실측(ADR-013): 서로 무관한
 * 한국어 문장 8개의 쌍별 코사인이 0.93~1.00(평균 0.964)으로 한 점에 뭉치고, 같은 문장쌍을
 * 영어로 바꾸면 0.63~0.82로 정상 분포한다. 관련쌍과 무관쌍의 분리도가 **0.000** 이라
 * 임계값으로도 가를 수 없고, 실제 검색 정확도는 top-1 1/7 로 무작위(1/8)와 같았다.
 *
 * [WHY] 반대로 **모델이 키워드를 뽑아 주면** 어휘 검색이 잘 동작한다. 사용자가 던진 문장 전체
 * ("내 자전거 비밀번호 뭐였지?")로는 LIKE 가 아무것도 못 맞히지만, 모델이 "자전거 비밀번호"를
 * 뽑아 주면 정확히 맞는다. 온디바이스 LLM 이 이미 한국어를 이해하므로 질의어 추출을 그쪽에
 * 맡기는 것이 이 앱에서 가장 값싼 의미 검색이다.
 */
class SearchMemoryToolExecutor @Inject constructor(
    private val repository: KnowledgeRepository,
    private val episodeRepository: com.kosmos.app.domain.memory.EpisodeRepository,
    private val tokenizer: com.kosmos.app.domain.tool.Tokenizer
) : ToolExecutor {
    override val name: String = "SearchMemory"

    // [WHY] actionType 은 null — 기억 **읽기**는 승인이 필요 없다(쓰기는 AddMemory 가 받는다).
    // 로컬 조회이고 외부로 나가는 것이 없으므로 캘린더 읽기와 같은 정책이다 (PRD F4/F6).

    /**
     * 메모(Knowledge)와 에피소드 문서(ADR-022)를 하나의 랭킹으로 합치는 공통 표현입니다.
     * [WHY] episodeId 가 null 이 아니면 회수 칩(🧠)의 출처가 된다 — 성공 JSON 의 meta 로
     * 동봉되어 BaseAgent 가 뽑아 쓴다(모델에게는 전달되지 않는다).
     */
    private data class Hit(
        val key: String,
        val text: String,
        val tags: List<String>,
        val createdAt: Long,
        val episodeId: String?
    )

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val keyword = args.requireString("keyword")
        val tokens = keyword.split(WHITESPACE)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TOKENS)

        // [WHY] 토큰마다 따로 조회한 뒤 합친다. SQLite LIKE 는 `%자전거 비밀번호%` 처럼
        // 어절이 붙은 패턴만 맞히므로, 쪼개지 않으면 어순이 조금만 달라도 놓친다.
        //
        // [WHY] 본문뿐 아니라 **태그도** 본다. 모델은 의미로 키워드를 뽑으므로 본문과 글자가
        // 어긋나는 경우가 실측됐다("좋아하는 것" ↔ "커피보다 녹차를 더 좋아함"). 저장 시
        // 모델이 붙인 태그(`선호도`)가 그 간극을 메우는 두 번째 통로다.
        //
        // [WHY] 에피소드 문서(자동 요약된 과거 대화)도 같은 랭킹에 합류한다 — 검색 방식은
        // 동일(본문 LIKE + 태그)하고, 키만 "ep:" 프리픽스로 충돌을 막는다 (ADR-022, exp33:
        // 이 어휘 검색 + 모델 키워드 추출 조합이 에피소드 회수 recall@1 16/16).
        val scored = LinkedHashMap<String, Pair<Hit, Int>>()
        for (token in tokens) {
            val byContent = repository.search(token, PER_TOKEN_LIMIT)
            if (byContent is AppResult.Failure) return errorJson(byContent.error.toString())
            val byTag = repository.searchByTags(listOf(token), PER_TOKEN_LIMIT)
            if (byTag is AppResult.Failure) return errorJson(byTag.error.toString())
            val epByContent = episodeRepository.search(token, PER_TOKEN_LIMIT)
            if (epByContent is AppResult.Failure) return errorJson(epByContent.error.toString())
            val epByTag = episodeRepository.searchByTags(token, PER_TOKEN_LIMIT)
            if (epByTag is AppResult.Failure) return errorJson(epByTag.error.toString())

            val noteHits = ((byContent as AppResult.Success).data + (byTag as AppResult.Success).data)
                .distinctBy { it.id }
                .map { it.toHit() }
            val episodeHits = ((epByContent as AppResult.Success).data + (epByTag as AppResult.Success).data)
                .distinctBy { it.id }
                .map { it.toHit() }

            (noteHits + episodeHits).forEach { hit ->
                val current = scored[hit.key]
                scored[hit.key] = if (current == null) hit to 1 else current.first to current.second + 1
            }
        }

        val ranked = scored.values
            .sortedWith(
                compareByDescending<Pair<Hit, Int>> { it.second }
                    .thenByDescending { it.first.createdAt }
            )
            .take(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)
            .map { it.first }

        if (ranked.isNotEmpty()) {
            return successJson(
                formatHits(ranked),
                episodeIds = ranked.mapNotNull { it.episodeId }
            )
        }

        // [WHY] 한 건도 못 맞혔을 때 그냥 "없다"로 끝내면, 실제로는 저장돼 있는데 **글자가
        // 어긋났을 뿐인** 경우까지 없는 것으로 답하게 된다. 어휘 검색은 동의어를 못 넘는데
        // 모델은 의미로 키워드를 뽑기 때문이다 — 실측에서 "좋아하는 것"(모델) 대
        // "커피보다 녹차를 더 좋아함"·태그 `선호도`(저장본)가 한 글자도 겹치지 않았다.
        //
        // [WHY] 그래서 **태그 목록을 돌려주고 재호출을 권한다.** 태그는 저장 시 모델 자신이
        // 붙인 것이라 모델이 알아보고, 기억이 몇 백 건이 되어도 목록이 짧게 유지된다 —
        // 최근 몇 건을 흘려보내는 것과 달리 규모를 탄다. 툴 루프 상한이 3턴이므로
        // "조회 실패 → 태그로 재조회 → 답변"이 정확히 들어간다.
        val recent = repository.searchRecent(TAG_SCAN_LIMIT)
        if (recent is AppResult.Failure) return errorJson(recent.error.toString())
        val all = (recent as AppResult.Success).data
        // [WHY] 2차 회수의 태그 목록은 메모 ∪ 에피소드 — 과거 대화의 태그로도 재조회가 가능해야
        // "그 고깃집" 류 질문이 에피소드 문서에 닿는다.
        val recentEpisodes = (episodeRepository.getEpisodes(0, TAG_SCAN_LIMIT) as? AppResult.Success)
            ?.data.orEmpty()

        if (all.isEmpty() && recentEpisodes.none { it.title != null }) {
            return successJson("저장된 기억이 하나도 없습니다. 사용자에게 저장된 것이 없다고 답하세요. 추측하지 마세요.")
        }

        val tags = (all.flatMap { it.tags } + recentEpisodes.flatMap { it.tags })
            .distinct().take(MAX_TAGS)
        val data = buildString {
            append("'$keyword' 로는 일치하는 기억이 없습니다. ")
            if (tags.isNotEmpty()) {
                append("저장된 기억에 붙은 분류는 다음과 같습니다: ")
                append(tags.joinToString(", "))
                append(".\n이 중 질문에 해당할 만한 분류가 있으면 그 단어로 `search_memory` 를 ")
                append("한 번 더 호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n")
            }
            append("가장 최근에 저장된 기억:\n")
            append(format(all.take(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)))
            append("\n이 목록에 질문의 답이 없으면 없다고 답하세요. 절대 지어내지 마세요.")
        }
        return successJson(data)
    }

    private fun format(notes: List<KnowledgeNote>): String = notes.joinToString("\n") { note ->
        val tagPart = if (note.tags.isEmpty()) "" else " [${note.tags.joinToString(", ")}]"
        "- ${note.content}$tagPart"
    }

    private fun formatHits(hits: List<Hit>): String = hits.joinToString("\n") { hit ->
        val tagPart = if (hit.tags.isEmpty()) "" else " [${hit.tags.joinToString(", ")}]"
        "- ${hit.text}$tagPart"
    }

    private fun KnowledgeNote.toHit() = Hit(
        key = id, text = content, tags = tags, createdAt = createdAt, episodeId = null
    )

    private fun com.kosmos.app.domain.model.Episode.toHit() = Hit(
        // [WHY] "ep:" 프리픽스 — 메모와 에피소드의 id 가 우연히 같아도 랭킹 키가 충돌하지 않는다.
        key = "ep:$id",
        text = "(과거 대화) ${title.orEmpty()}: ${summary.orEmpty()}",
        tags = tags,
        createdAt = createdAt,
        episodeId = id
    )

    // [WHY] 메모 본문에 따옴표·개행이 있어도 JSON 이 깨지지 않도록 JSONObject 로 조립한다.
    //
    // [WHY] data 는 툴 결과 토큰 예산에서 문장 경계로 자른다 — 에피소드 요약이 합류하면서
    // 결과가 길어질 수 있는데, 캡을 어기면 툴 회신 턴(재생성 금지)의 KV 를 무예산으로 먹는다
    // (ADR-020, WikipediaSearchToolImpl 과 같은 방어).
    //
    // [WHY] meta.episodeIds 는 회수 칩(🧠)의 출처다. BaseAgent 가 뽑아 쓰고 **모델에게
    // 되돌리기 전에 제거**한다 — 모델이 id 를 답변에 에코하는 것을 막는다.
    private fun successJson(data: String, episodeIds: List<String> = emptyList()): String {
        val capped = com.kosmos.app.domain.util.SentenceTruncator.truncate(
            text = data,
            maxTokens = Constants.TOOL_RESULT_MAX_TOKENS - Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS,
            tokenizer = tokenizer,
            marker = "\n\n... [TRUNCATED TO SAVE CONTEXT]"
        )
        val json = JSONObject().put("status", "success").put("data", capped)
        if (episodeIds.isNotEmpty()) {
            json.put("meta", JSONObject().put("episodeIds", org.json.JSONArray(episodeIds)))
        }
        return json.toString()
    }

    private fun errorJson(reason: String): String = JSONObject()
        .put("status", "error")
        .put("message", "기억 검색 중 오류가 발생했습니다: $reason")
        .toString()

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /** 모델이 문장을 통째로 넣어도 조회 횟수가 폭주하지 않게 자른다. */
        const val MAX_TOKENS = 4

        /** 토큰 하나가 흔한 글자일 때 상위 정렬 후보를 넉넉히 확보하기 위한 여유분. */
        const val PER_TOKEN_LIMIT = 20

        /** 실패 시 분류(태그)를 모으려고 훑는 최근 기억 수. */
        const val TAG_SCAN_LIMIT = 50

        /** 태그 목록이 프롬프트를 잠식하지 않도록 자른다. */
        const val MAX_TAGS = 20
    }
}
