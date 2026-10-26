package com.kosmos.app.assistant.tool

import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.RecallConflict
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
 * 2. **랭킹 = 바이그램 점수**([BigramMatcher.score]) — 항이 많이 맞고 온전히 들어 있을수록 앞. 동점은 출처 순(직접 저장한 메모 →
 *    자동 메모 → 과거 대화), 그다음 최신순. 결과 글은 메모를 앞에, 과거 대화를 뒤에 싣는다.
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
    private val tokenizer: com.kosmos.app.domain.tool.Tokenizer,
    // 지금 대화의 사용자 말(결과 맨 위 "방금 이 대화" 줄) — 없으면 그 줄을 싣지 않는다(단위 테스트 생성부 호환).
    private val conversations: com.kosmos.app.domain.memory.ConversationRepository? = null
) : ToolExecutor {
    override val name: String = ToolNames.SEARCH_MEMORY

    // [WHY] actionType 을 재정의하지 않는다(ToolExecutor 기본값 null = 승인 없음) — 기억 **읽기**는 로컬 조회라
    // 캘린더 읽기와 같은 정책이다(PRD F4/F6). 쓰기는 AddMemory 가 승인을 받는다.

    /**
     * 메모(Knowledge)와 에피소드 문서(ADR-022)를 하나의 랭킹으로 합치는 공통 표현입니다.
     * [WHY] episodeId 가 null 이 아니면 회수 칩(🧠)의 출처가 된다 — 성공 JSON 의 meta 로
     * 동봉되어 BaseAgent 가 뽑아 쓴다(모델에게는 전달되지 않는다).
     * [scoreText] 는 표시 문구("(과거 대화) …")를 뺀 순수 본문+태그 — 점수 오염 방지(MemorySearchText).
     * [tier] 는 동점일 때의 출처 순위(작을수록 앞) — [TIER_MANUAL]·[TIER_AUTO]·[TIER_EPISODE].
     */
    private data class Hit(
        val key: String,
        val text: String,
        val scoreText: String,
        val tags: List<String>,
        val createdAt: Long,
        val episodeId: String?,
        val tier: Int
    )

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val keyword = args.requireString("keyword")
        val tokens = keyword.split(WHITESPACE)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TOKENS)

        // [WHY] 지금 대화에서 사용자가 방금 말한 값을 맨 위에 — 검색된 옛 값보다 최신이다(0.38.8 결정 2, [com.kosmos.app.domain.memory.CurrentConversationLines]).
        val head = currentHead(sessionId, tokens)

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
            return formatHits(tokens, ranked).let { successJson(head + it.text, episodeIds = it.episodeIds, recall = it.recall) }
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
            return formatHits(tokens, scanned).let { successJson(head + it.text, episodeIds = it.episodeIds, recall = it.recall) }
        }

        // ── 4. 태그 목록 폴백 ───────────────────────────────────────────────────
        // [WHY] "없다"로 끝내지 않고 **태그 목록을 돌려 재호출을 권한다** — 어휘 검색은 동의어를 못 넘어 저장돼 있는데도
        // 놓칠 수 있다. 태그는 모델 자신이 붙인 것이라 알아보고, 기억이 늘어도 목록이 짧다. 툴 루프 상한 3턴 안에
        // "조회 실패 → 태그로 재조회 → 답변"이 들어간다.
        if (allNotes.isEmpty() && recentEpisodes.none { it.title != null }) {
            // [WHY] "없다"만 시키면 바로 앞 대화에 사용자가 말한 답도 "기억이 없어요"라고 했다 — 대화 속 답을 허용한다(exp58c 1/6 → 6/6, 지어냄 0/4).
            return successJson(head + "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요.")
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
            // [WHY] 위 빈 결과와 같은 이유 — 대화 속 답 1/6 → 5/6, 지어냄 0/4(exp58b RES1).
            append("\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요. 절대 지어내지 마세요.")
        }
        return successJson(head + data)
    }

    /** 결과 맨 위 "- (방금 이 대화) …" 줄들 — 없으면 빈 문자열. */
    private suspend fun currentHead(sessionId: String, terms: List<String>): String {
        val recent = (conversations?.getRecentBySession(sessionId, CURRENT_SCAN_LIMIT) as? AppResult.Success)?.data.orEmpty()
        val lines = com.kosmos.app.domain.memory.CurrentConversationLines.pick(recent, terms, System.currentTimeMillis())
        return lines.joinToString("") { "- (방금 이 대화) $it\n" }
    }

    /**
     * 바이그램 점수 내림차순, 동점은 출처 순위([Hit.tier]) 다음 최신순, 상위 [Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS].
     * [minTermOverlap] 이 있으면 가장 잘 맞은 항의 겹침이 그 이상인 문서만 남긴다(전수 스캔용).
     *
     * [WHY] 동점에서 출처를 먼저 본다 — 긴 과거 대화 요약은 질의 낱말을 거의 다 담아 직접 저장한 메모와 동점이 되기 쉽고, 요약이 나중에
     * 만들어지면 최신순에서 앞선다. 실기기(2026-10-12)에서 요약 두 건이 앞에 서고 결과 절단에 메모가 잘려 옛 값(4321)으로 답했다.
     */
    private fun rank(terms: List<String>, hits: Collection<Hit>, minTermOverlap: Double? = null): List<Hit> =
        hits.asSequence()
            .map { hit -> Triple(hit, BigramMatcher.bigrams(hit.scoreText), 0.0) }
            .filter { (_, bigrams, _) ->
                minTermOverlap == null || BigramMatcher.bestTermOverlap(terms, bigrams) >= minTermOverlap
            }
            .map { (hit, bigrams, _) -> hit to BigramMatcher.score(terms, bigrams) }
            .sortedWith(
                compareByDescending<Pair<Hit, Double>> { it.second }
                    .thenBy { it.first.tier }
                    .thenByDescending { it.first.createdAt }
            )
            .take(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)
            .map { it.first }
            .toList()

    private fun format(notes: List<KnowledgeNote>): String = notes.joinToString("\n") { note ->
        val tagPart = if (note.tags.isEmpty()) "" else " [${note.tags.joinToString(", ")}]"
        "- ${note.content}$tagPart"
    }

    // [WHY] 메모를 앞에 싣는다 — 결과는 토큰 상한에서 뒤부터 잘리므로(successJson), 수십 토큰짜리 과거 대화 요약이 짧은 메모를 밀어내지 않게.
    // sortedBy 는 안정 정렬이라 같은 출처 안의 랭킹 순서는 그대로다.
    //
    // [WHY] 메모와 과거 대화가 섞이면 맨 앞에 [MIXED_SOURCE_GUIDE] — 메모가 맨 앞이어도 요약 두 건이 다른 값을 적고 있으면 모델이 요약을
    // 따랐다(실기기 0.36.1: 134 대신 4321). exp50 조건 B: 충돌 0/2 → 2/2, 과거만·일치·메모만 대조 6/6 무손상.
    //
    // [WHY] 메모와 다른 값을 적은 요약은 한 줄로 줄인다([RecallConflict]) — 그런 요약이 3건 이상이면 안내가 있어도 많은 쪽을 따랐다(exp52).
    // exp53 조건 C2: 충돌 5/8 → 8/8, 대조 4/4. 줄인 요약은 답의 근거가 아니므로 회수 칩(meta)에서도 뺀다.
    private fun formatHits(terms: List<String>, hits: List<Hit>): Formatted {
        val notes = hits.filter { it.episodeId == null }.map { it.text }
        val (contradicting, kept) = hits.partition { it.episodeId != null && RecallConflict.contradicts(it.text, notes) }
        val lines = kept.sortedBy { it.episodeId != null }.map { hit ->
            val tagPart = if (hit.tags.isEmpty()) "" else " [${hit.tags.joinToString(", ")}]"
            "- ${hit.text}$tagPart"
        } + listOfNotNull(
            contradicting.takeIf { it.isNotEmpty() }?.let {
                "- (과거 대화) ${it.size}건에는 다른 값이 적혀 있습니다: ${RecallConflict.otherValues(it.map(Hit::text), notes).joinToString(", ")})"
            }
        )
        val mixed = notes.isNotEmpty() && hits.any { it.episodeId != null }
        // [WHY] 결과가 있어도 질문의 답이 아닐 수 있다 — 관계없는 지난 대화가 약하게 걸리면 모델이 바로 앞에서 들은 값을 두고 "찾지 못했어요"라고 했다(실기기 T7).
        // 빈 결과와 같은 안내를 끝에 붙인다(exp60: 대화 속 답 4/6 → 5/6, 관련 결과·충돌 판정은 그대로).
        val body = lines.joinToString("\n") + HIT_TAIL
        // [WHY] 화면 근거(회수 칩·기억 카드)에는 질의 낱말이 충분히 겹친 지난 대화만 — "회의실"이 "팀 회의" 요약에 겹침 0.5 로 걸려 관계없는 대화가
        // 근거 카드가 됐다(실기기 T7, 0.38.7 M2). 모델 입력(본문)은 그대로라 실측 대상이 아니다.
        val shown = kept.filter { it.episodeId == null || BigramMatcher.bestTermOverlap(terms, it.scoreText) >= SHOWN_EPISODE_OVERLAP }
        return Formatted(if (mixed) "$MIXED_SOURCE_GUIDE\n$body" else body, shown.mapNotNull { it.episodeId }, recallOf(hits, shown, contradicting, notes))
    }

    /**
     * 결과 카드의 근거(0.38.2) — 1순위 메모(없으면 과거 대화만), 메모와 다른 값을 적은 요약의 건수·값.
     * 모델에게 가는 문구와 같은 판정([RecallConflict])을 쓴다 — 화면과 모델이 같은 사실을 본다.
     */
    private fun recallOf(hits: List<Hit>, kept: List<Hit>, contradicting: List<Hit>, notes: List<String>): JSONObject {
        val topNote = hits.firstOrNull { it.episodeId == null }
        val meta = JSONObject()
        if (topNote != null) meta.put("noteAt", topNote.createdAt)
        // 화면 전용 — 지난 대화만 근거인 카드를 누르면 여는 대화(0.38.6 M3). 메모가 근거면 메모 화면으로 간다.
        if (topNote == null) kept.firstNotNullOfOrNull(Hit::episodeId)?.let { meta.put("episodeId", it) }
        if (contradicting.isNotEmpty()) {
            meta.put("conflictCount", contradicting.size)
            meta.put("otherValues", org.json.JSONArray(RecallConflict.otherValues(contradicting.map(Hit::text), notes)))
            // 화면 전용(모델 입력에서는 meta 를 뗀다) — 상황 칩 [과거 기록 정리]가 여는 대화(0.38.2 M6).
            meta.put("conflictEpisodeIds", org.json.JSONArray(contradicting.mapNotNull(Hit::episodeId)))
        }
        return JSONObject().put("ref", topNote?.key.orEmpty()).put("meta", meta)
    }

    private data class Formatted(val text: String, val episodeIds: List<String>, val recall: JSONObject)

    private fun KnowledgeNote.toHit() = Hit(
        key = id, text = content, scoreText = searchText(), tags = tags, createdAt = createdAt, episodeId = null,
        tier = if (source == KnowledgeNote.SOURCE_AUTO) TIER_AUTO else TIER_MANUAL
    )

    private fun com.kosmos.app.domain.model.Episode.toHit() = Hit(
        // [WHY] "ep:" 프리픽스 — 메모와 에피소드의 id 가 우연히 같아도 랭킹 키가 충돌하지 않는다.
        key = "ep:$id",
        text = "(과거 대화) ${title.orEmpty()}: ${summary.orEmpty()}",
        scoreText = searchText(),
        tags = tags,
        createdAt = createdAt,
        episodeId = id,
        tier = TIER_EPISODE
    )

    // [WHY] 메모 본문에 따옴표·개행이 있어도 JSON 이 깨지지 않도록 JSONObject 로 조립한다.
    //
    // [WHY] data 는 툴 결과 토큰 예산에서 문장 경계로 자른다 — 에피소드 요약이 합류하면서
    // 결과가 길어질 수 있는데, 캡을 어기면 툴 회신 턴(재생성 금지)의 KV 를 무예산으로 먹는다
    // (ADR-020, WikipediaSearchToolImpl 과 같은 방어).
    //
    // [WHY] meta.episodeIds 는 회수 칩(🧠)의 출처다. BaseAgent 가 뽑아 쓰고 **모델에게
    // 되돌리기 전에 제거**한다 — 모델이 id 를 답변에 에코하는 것을 막는다.
    private fun successJson(data: String, episodeIds: List<String> = emptyList(), recall: JSONObject? = null): String {
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
        if (recall != null) {
            ToolResultJson.withResult(
                json, com.kosmos.app.domain.model.MessageResult.Kind.MEMORY_RECALL.name,
                ref = recall.getString("ref"), meta = recall.getJSONObject("meta")
            )
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

        /**
         * 화면 근거에 올릴 지난 대화의 최소 항 겹침 — 전수 스캔 통과선([Constants.BIGRAM_MIN_TERM_OVERLAP])보다 높다.
         * 세 글자 낱말이 두 글자만 겹친 경우(0.5)는 빼고, "자물쇠번호"↔"자물쇠 비밀번호"(0.75)는 남는다.
         */
        const val SHOWN_EPISODE_OVERLAP = 0.75

        /** 지금 대화를 훑는 최근 메시지 수 — 30분 창 안의 대화는 보통 이보다 짧다. */
        const val CURRENT_SCAN_LIMIT = 30

        const val HIT_TAIL = "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."

        const val TIER_MANUAL = 0
        const val TIER_AUTO = 1
        const val TIER_EPISODE = 2

        // exp50 조건 B 실측 원문.
        const val MIXED_SOURCE_GUIDE =
            "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."
    }
}
