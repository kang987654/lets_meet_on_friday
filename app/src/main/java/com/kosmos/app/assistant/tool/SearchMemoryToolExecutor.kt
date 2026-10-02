package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.search.BigramMatcher
import com.kosmos.app.domain.search.searchText
import org.json.JSONObject
import javax.inject.Inject

/**
 * [SearchMemoryToolExecutor]
 * 모델의 `SearchMemory` 툴 콜을 받아 저장된 기억(메모)과 에피소드 문서에서 키워드로 찾는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [KnowledgeRepository], [com.kosmos.app.domain.memory.EpisodeRepository], [BigramMatcher]
 *
 * ### Key Flow (0.25.0, C1+C′3)
 * 1. **정밀 후보**: 키워드를 공백으로 쪼개 토큰마다 본문 LIKE·태그 정확 일치로 후보를 모은다.
 * 2. **랭킹 = 바이그램 점수**([BigramMatcher.score]) — 항이 많이 맞고 온전히 들어 있을수록 앞. 동점은 최신순.
 * 3. **정밀 0건 → 전수 스캔**: 최근 문서 [Constants.MEMORY_SCAN_LIMIT] 건을 바이그램으로 훑고, 가장 잘 맞은
 *    항의 겹침이 [Constants.BIGRAM_MIN_TERM_OVERLAP] 이상인 문서만 결과로 인정한다("자물쇠번호" ↔ "자물쇠 비밀번호").
 * 4. **그래도 0건 → 태그 목록 폴백**: 저장된 분류를 돌려주고 `search_memory` 재호출을 유도한다.
 *
 * [WHY] 벡터 검색이 아니라 **모델이 뽑은 키워드 + 어휘 검색**이다 — 쓸 수 있던 임베더는 영어 전용이라 한국어 문장을
 * 전혀 분별하지 못했고(ADR-013), 온디바이스 LLM 이 이미 한국어를 이해하므로 질의어 추출을 맡기는 것이 가장 싸다.
 *
 * [WHY] 정밀 LIKE 단계를 **그대로 두고** 바이그램을 뒤에 얹는다 — LIKE 는 정확할 때 가장 싸고
 * (인덱스 없이도 수백 건), 바이그램은 글자가 어긋난 미스를 건지는 2차 통로다. 인메모리 스코어러인
 * 이유와 FTS5 규모 게이트는 [BigramMatcher] KDoc·ADR-025.
 */
class SearchMemoryToolExecutor @Inject constructor(
    private val repository: KnowledgeRepository,
    private val episodeRepository: com.kosmos.app.domain.memory.EpisodeRepository,
    private val tokenizer: com.kosmos.app.domain.tool.Tokenizer
) : ToolExecutor {
    override val name: String = ToolNames.SEARCH_MEMORY

    // [WHY] actionType 을 재정의하지 않는다(ToolExecutor 기본값 null = 승인 없음) — 기억 **읽기**는 로컬 조회라
    // 캘린더 읽기와 같은 정책이다(PRD F4/F6). 쓰기는 AddMemory 가 승인을 받는다.

    /**
     * 메모(Knowledge)와 에피소드 문서(ADR-022)를 하나의 랭킹으로 합치는 공통 표현입니다.
     * [WHY] episodeId 가 null 이 아니면 회수 칩(🧠)의 출처가 된다 — 성공 JSON 의 meta 로
     * 동봉되어 BaseAgent 가 뽑아 쓴다(모델에게는 전달되지 않는다).
     * [scoreText] 는 표시 문구("(과거 대화) …")를 뺀 순수 본문+태그 — 점수 오염 방지(MemorySearchText).
     */
    private data class Hit(
        val key: String,
        val text: String,
        val scoreText: String,
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

        // ── 1. 정밀 후보 ─────────────────────────────────────────────────────────
        // [WHY] 토큰마다 따로 조회한 뒤 합친다. SQLite LIKE 는 `%자전거 비밀번호%` 처럼
        // 어절이 붙은 패턴만 맞히므로, 쪼개지 않으면 어순이 조금만 달라도 놓친다.
        //
        // [WHY] 본문뿐 아니라 **태그도** 본다 — 모델이 의미로 뽑은 키워드는 본문과 글자가 어긋나기 쉽고("좋아하는 것" ↔
        // "커피보다 녹차를 더 좋아함"), 저장 때 모델이 붙인 태그가 그 간극을 메운다.
        //
        // [WHY] 에피소드 문서(자동 요약된 과거 대화)도 같은 랭킹에 합류한다 — 키만 "ep:" 프리픽스로
        // 충돌을 막는다 (ADR-022).
        val candidates = LinkedHashMap<String, Hit>()
        for (token in tokens) {
            val byContent = repository.search(token, PER_TOKEN_LIMIT)
            if (byContent is AppResult.Failure) return errorJson(byContent.error.toString())
            val byTag = repository.searchByTags(listOf(token), PER_TOKEN_LIMIT)
            if (byTag is AppResult.Failure) return errorJson(byTag.error.toString())
            val epByContent = episodeRepository.search(token, PER_TOKEN_LIMIT)
            if (epByContent is AppResult.Failure) return errorJson(epByContent.error.toString())
            val epByTag = episodeRepository.searchByTags(token, PER_TOKEN_LIMIT)
            if (epByTag is AppResult.Failure) return errorJson(epByTag.error.toString())

            ((byContent as AppResult.Success).data + (byTag as AppResult.Success).data)
                .distinctBy { it.id }.forEach { candidates.putIfAbsent(it.id, it.toHit()) }
            ((epByContent as AppResult.Success).data + (epByTag as AppResult.Success).data)
                .distinctBy { it.id }.forEach { hit -> hit.toHit().let { candidates.putIfAbsent(it.key, it) } }
        }

        // ── 2. 랭킹 = 바이그램 점수 ──────────────────────────────────────────────
        // [WHY] "맞은 토큰 수" 대신 바이그램 겹침 합 — 두 항이 맞은 문서(2.0)가 한 항만 맞은 문서(1.0)
        // 보다 앞서는 기존 계약은 그대로 성립하고, 항이 온전히 들어 있는 정도까지 반영된다(exp33 원문).
        val ranked = rank(tokens, candidates.values)
        if (ranked.isNotEmpty()) {
            return successJson(formatHits(ranked), episodeIds = ranked.mapNotNull { it.episodeId })
        }

        // ── 3. 정밀 0건 → 바이그램 전수 스캔 ─────────────────────────────────────
        // [WHY] LIKE 가 놓치는 것은 글자가 어긋난 경우다("자물쇠번호" ↔ "자물쇠 비밀번호", 붙여쓰기·
        // 조사). 최근 문서를 훑어 바이그램으로 건진다. **최소 항 겹침 임계**가 핵심 — 없으면 아무
        // 문서나 조금씩 겹쳐 올라와 "없다"고 답해야 할 질의에 잡음을 돌려준다.
        val recent = repository.searchRecent(Constants.MEMORY_SCAN_LIMIT)
        if (recent is AppResult.Failure) return errorJson(recent.error.toString())
        val allNotes = (recent as AppResult.Success).data
        val recentEpisodes = (episodeRepository.getEpisodes(0, Constants.MEMORY_SCAN_LIMIT) as? AppResult.Success)
            ?.data.orEmpty()

        val scanned = rank(
            tokens,
            (allNotes.map { it.toHit() } + recentEpisodes.filter { it.title != null }.map { it.toHit() }),
            minTermOverlap = Constants.BIGRAM_MIN_TERM_OVERLAP
        )
        if (scanned.isNotEmpty()) {
            return successJson(formatHits(scanned), episodeIds = scanned.mapNotNull { it.episodeId })
        }

        // ── 4. 태그 목록 폴백 ───────────────────────────────────────────────────
        // [WHY] "없다"로 끝내지 않고 **태그 목록을 돌려 재호출을 권한다** — 어휘 검색은 동의어를 못 넘어 저장돼 있는데도
        // 놓칠 수 있다. 태그는 모델 자신이 붙인 것이라 알아보고, 기억이 늘어도 목록이 짧다. 툴 루프 상한 3턴 안에
        // "조회 실패 → 태그로 재조회 → 답변"이 들어간다.
        if (allNotes.isEmpty() && recentEpisodes.none { it.title != null }) {
            return successJson("저장된 기억이 하나도 없습니다. 사용자에게 저장된 것이 없다고 답하세요. 추측하지 마세요.")
        }

        // [WHY] 2차 회수의 태그 목록은 메모 ∪ 에피소드 — 과거 대화의 태그로도 재조회가 가능해야
        // "그 고깃집" 류 질문이 에피소드 문서에 닿는다.
        val tags = (allNotes.flatMap { it.tags } + recentEpisodes.flatMap { it.tags })
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
            append(format(allNotes.take(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)))
            append("\n이 목록에 질문의 답이 없으면 없다고 답하세요. 절대 지어내지 마세요.")
        }
        return successJson(data)
    }

    /**
     * 바이그램 점수 내림차순, 동점 최신순, 상위 [Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS].
     * [minTermOverlap] 이 있으면 가장 잘 맞은 항의 겹침이 그 이상인 문서만 남긴다(전수 스캔용).
     */
    private fun rank(terms: List<String>, hits: Collection<Hit>, minTermOverlap: Double? = null): List<Hit> =
        hits.asSequence()
            .map { hit -> Triple(hit, BigramMatcher.bigrams(hit.scoreText), 0.0) }
            .filter { (_, bigrams, _) ->
                minTermOverlap == null || BigramMatcher.bestTermOverlap(terms, bigrams) >= minTermOverlap
            }
            .map { (hit, bigrams, _) -> hit to BigramMatcher.score(terms, bigrams) }
            .sortedWith(compareByDescending<Pair<Hit, Double>> { it.second }.thenByDescending { it.first.createdAt })
            .take(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)
            .map { it.first }
            .toList()

    private fun format(notes: List<KnowledgeNote>): String = notes.joinToString("\n") { note ->
        val tagPart = if (note.tags.isEmpty()) "" else " [${note.tags.joinToString(", ")}]"
        "- ${note.content}$tagPart"
    }

    private fun formatHits(hits: List<Hit>): String = hits.joinToString("\n") { hit ->
        val tagPart = if (hit.tags.isEmpty()) "" else " [${hit.tags.joinToString(", ")}]"
        "- ${hit.text}$tagPart"
    }

    private fun KnowledgeNote.toHit() = Hit(
        key = id, text = content, scoreText = searchText(), tags = tags, createdAt = createdAt, episodeId = null
    )

    private fun com.kosmos.app.domain.model.Episode.toHit() = Hit(
        // [WHY] "ep:" 프리픽스 — 메모와 에피소드의 id 가 우연히 같아도 랭킹 키가 충돌하지 않는다.
        key = "ep:$id",
        text = "(과거 대화) ${title.orEmpty()}: ${summary.orEmpty()}",
        scoreText = searchText(),
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
        val json = ToolResultJson.success().put("data", capped)
        if (episodeIds.isNotEmpty()) {
            json.put("meta", JSONObject().put("episodeIds", org.json.JSONArray(episodeIds)))
        }
        return json.toString()
    }

    private fun errorJson(reason: String): String =
        ToolResultJson.error("기억 검색 중 오류가 발생했습니다: $reason")

    private companion object {
        val WHITESPACE = Regex("\\s+")

        /** 모델이 문장을 통째로 넣어도 조회 횟수가 폭주하지 않게 자른다. */
        const val MAX_TOKENS = 4

        /** 토큰 하나가 흔한 글자일 때 상위 정렬 후보를 넉넉히 확보하기 위한 여유분. */
        const val PER_TOKEN_LIMIT = 20

        /** 태그 목록이 프롬프트를 잠식하지 않도록 자른다. */
        const val MAX_TAGS = 20
    }
}
