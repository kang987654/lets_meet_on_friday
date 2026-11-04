package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.CurrentLanguage
import com.kosmos.app.domain.library.EmbedderProvider
import com.kosmos.app.domain.library.LibraryHit
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.tool.ToolNames
import com.kosmos.app.domain.util.SentenceTruncator
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * [SearchDocumentsToolExecutor]
 * 모델의 `SearchDocuments` 툴 콜을 받아 문서 보관함에서 관련 조각을 찾아 돌려주는 실행기입니다 (0.41.2 M4).
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [LibraryRepository](바이그램·결합 검색), [EmbedderProvider](뜻 검색 모델이 있을 때만), [Tokenizer]
 *
 * ### Key Flow
 * 1. 검색어(`query`, 여럿이면 `queries`)로 보관함을 찾는다 — 모델이 있고 벡터가 다 준비됐으면 결합, 아니면 바이그램(저장소가 판정).
 * 2. 상위 [Constants.LIBRARY_RESULT_COUNT] 조각을 "문서 > 위치 + 본문 앞부분" 묶음으로 만든다(exp78 형식).
 * 3. 출처 카드용 대상(문서·위치)을 `meta.result` 에 싣는다 — BaseAgent 가 모델에게 되돌리기 전에 뗀다.
 *
 * [WHY] 문서 검색 토글이 켜졌을 때만 선언된다(KosmosAgent) — 켜 둔 사용자만 선언·트리거 비용을 낸다(0.41.2 Q1 = B, exp78).
 */
class SearchDocumentsToolExecutor @Inject constructor(
    private val library: LibraryRepository,
    private val embedders: EmbedderProvider,
    private val tokenizer: Tokenizer,
    private val currentLanguage: CurrentLanguage = CurrentLanguage.KO
) : ToolExecutor {

    override val name: String = ToolNames.SEARCH_DOCUMENTS

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val en = currentLanguage.current == AppLanguage.EN
        val queries = queriesOf(args)
        if (queries.isEmpty()) throw ToolArgumentException("query", ToolArgumentException.Reason.MISSING)
        val embedder = embedders.current()
        val ranked = mutableListOf<List<LibraryHit>>()
        for (query in queries) {
            when (val res = library.search(query, Constants.LIBRARY_RESULT_COUNT, embedder)) {
                is AppResult.Success -> ranked += res.data
                is AppResult.Failure -> return ToolResultJson.error(if (en) "Failed to search documents" else "문서 검색 실패")
            }
        }
        val hits = interleave(ranked).take(Constants.LIBRARY_RESULT_COUNT)
        if (hits.isEmpty()) {
            return ToolResultJson.success().put("data", if (en) NOT_FOUND_EN else NOT_FOUND_KO).toString()
        }
        val capped = SentenceTruncator.truncate(
            text = render(hits, en),
            maxTokens = Constants.TOOL_RESULT_MAX_TOKENS - Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS,
            tokenizer = tokenizer,
            marker = "\n\n... [TRUNCATED TO SAVE CONTEXT]"
        )
        val json = ToolResultJson.success().put("data", capped)
        return ToolResultJson.withResult(json, MessageResult.Kind.DOC_SOURCE.name, ref = hits.first().documentId, meta = sourcesMeta(hits))
            .toString()
    }

    companion object {
        // [WHY] exp78 형식 — 결과 묶음 문구는 모델 입력이라 바꾸면 실측이 선행된다(AGENTS §2-⑤).
        private const val HEADER_KO = "[등록 문서에서 찾은 내용]"
        private const val HEADER_EN = "[Found in your registered documents]"
        private const val NOT_FOUND_KO = "등록 문서에서 관련 내용을 찾지 못했어요."
        private const val NOT_FOUND_EN = "No related passage was found in the registered documents."

        /** 출처 카드 meta 의 키 — 화면(ResultCardMapper)과 함께 쓴다. */
        const val META_SOURCES = "sources"
        const val META_DOC_ID = "d"
        const val META_DOC_NAME = "n"
        const val META_SECTION = "s"
        const val META_PAGE = "p"
        const val META_INDEX = "i"

        /** `query` 하나 또는 `queries` 목록(최대 3) — 모델이 어느 쪽으로 보내도 받는다. */
        internal fun queriesOf(args: ToolArguments): List<String> {
            val list = if (args.has("queries")) runCatching { args.stringList("queries") }.getOrDefault(emptyList()) else emptyList()
            val single = listOfNotNull(args.optString("query"))
            return (list + single).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_QUERIES)
        }

        /** 검색어별 순위를 번갈아 합친다(같은 조각은 한 번) — 한 검색어가 결과를 독차지하지 않게. */
        internal fun interleave(ranked: List<List<LibraryHit>>): List<LibraryHit> {
            val seen = HashSet<Pair<String, Int>>()
            val out = mutableListOf<LibraryHit>()
            val depth = ranked.maxOfOrNull { it.size } ?: 0
            for (i in 0 until depth) {
                for (list in ranked) {
                    val hit = list.getOrNull(i) ?: continue
                    if (seen.add(hit.documentId to hit.chunk.index)) out += hit
                }
            }
            return out
        }

        /** 모델에게 보낼 묶음 — "n) 문서 > 마지막 위치" + 본문 앞부분. */
        internal fun render(hits: List<LibraryHit>, en: Boolean): String = buildString {
            append(if (en) HEADER_EN else HEADER_KO)
            hits.forEachIndexed { n, hit ->
                append("\n${n + 1}) ${hit.documentName} > ${locationOf(hit)}\n")
                append(hit.chunk.text.take(Constants.LIBRARY_RESULT_CHARS))
            }
        }

        /** 조각의 마지막 머리말(조문·장 제목). 머리말이 없으면 쪽, 그것도 없으면 문서 이름. */
        internal fun locationOf(hit: LibraryHit): String {
            val last = hit.chunk.sectionPath.split(" > ").last().trim()
            return when {
                last.isNotEmpty() && last != hit.documentName -> last
                hit.chunk.page != null -> "p.${hit.chunk.page}"
                else -> hit.documentName
            }
        }

        internal fun sourcesMeta(hits: List<LibraryHit>): JSONObject = JSONObject().put(
            META_SOURCES,
            JSONArray(hits.map { hit ->
                JSONObject()
                    .put(META_DOC_ID, hit.documentId)
                    .put(META_DOC_NAME, hit.documentName)
                    .put(META_SECTION, locationOf(hit))
                    .put(META_INDEX, hit.chunk.index)
                    .apply { hit.chunk.page?.let { put(META_PAGE, it) } }
            })
        )

        private const val MAX_QUERIES = 3
    }
}
