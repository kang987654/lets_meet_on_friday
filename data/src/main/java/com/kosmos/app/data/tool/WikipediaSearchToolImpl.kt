package com.kosmos.app.data.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.tool.WikipediaSearchTool
import com.kosmos.app.domain.util.SentenceTruncator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/**
 * [WikipediaSearchToolImpl]
 * Wikipedia API를 사용하여 주제에 대한 검색을 수행하고 요약 텍스트를 반환하는 도구입니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Tool)
 * - **Dependencies**: [OkHttpClient], [Tokenizer]
 *
 * ### Key Flow
 * 1. 지정된 언어(lang)와 토픽(topic)으로 Wikipedia API 엔드포인트를 구성합니다 (쿼리 인코딩 적용).
 *    `exsentences` 로 도입부의 완결된 문장 10개만 요청해 전문을 받아 버리는 낭비를 없앱니다.
 * 2. OkHttp를 통해 네트워크 요청을 수행하고 JSON 결과를 파싱합니다.
 * 3. 검색된 페이지의 요약(extract)을 **토큰 예산** 이내로 문장 경계에서 잘라 반환합니다.
 */
class WikipediaSearchToolImpl @Inject constructor(
    private val client: OkHttpClient,
    private val tokenizer: Tokenizer
) : WikipediaSearchTool {

    override suspend fun search(topic: String, lang: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            // [WHY] lang은 호스트명에 삽입되므로 위키피디아 언어 코드 형식만 허용해 호스트 조작을 차단한다.
            val safeLang = if (lang.matches(Regex("^[a-zA-Z-]{2,12}$"))) lang.lowercase() else "ko"

            // HttpUrl.Builder가 쿼리 파라미터를 인코딩하므로 한국어/특수문자 토픽도 안전하다.
            val searchUrl = HttpUrl.Builder()
                .scheme("https")
                .host("$safeLang.wikipedia.org")
                .addPathSegments("w/api.php")
                .addQueryParameter("action", "query")
                .addQueryParameter("format", "json")
                .addQueryParameter("generator", "search")
                .addQueryParameter("gsrsearch", topic)
                .addQueryParameter("gsrlimit", "1")
                .addQueryParameter("prop", "extracts")
                .addQueryParameter("explaintext", "1")
                .addQueryParameter("exintro", "1")
                // [WHY] `exintro` 는 "첫 섹션까지" 라는 뜻이지 **길이 제한이 아니다.** 길이
                // 파라미터가 없으면 도입부 전문이 내려오고(실측: `제2차 세계 대전` 1,203자 이상),
                // 우리는 그중 400토큰(약 480자)만 쓴 뒤 나머지를 버린다 — 필요한 양의 몇 배를
                // 모바일 데이터·배터리·레이턴시로 지불한다. 아래 `SentenceTruncator` 가 예산을
                // 지키므로 결과물은 같고, 낭비만 사라진다.
                //
                // [WHY] 10 은 API 최대값이다. 한국어 문서 10건 실측에서 10문장은 평균 424자·최대
                // 730자로, 예산이 담을 수 있는 양보다 **살짝 많다** — 그래서 병목이 API 가 아니라
                // 예산 쪽에 남고, 짧게 받는다고 담을 수 있는 내용을 놓치지 않는다.
                //
                // [WHY] `exchars` 를 쓰지 않는다. 실측에서 `exchars=1200` 은 `…아시아와 태평양을...`
                // 처럼 **문장 중간을** 자르고, `exsentences=3` 은 `…총력전이다.` 로 문장을 완결한다.
                // 잘린 조각은 모델이 없는 사실을 채워 넣는 재료가 된다.
                .addQueryParameter("exsentences", "10")
                // [WHY] origin=* 를 제거했다 — 브라우저 CORS 용 파라미터라 네이티브 클라이언트에는
                // 의미가 없고, 요청을 익명 처리해 오히려 레이트리밋에 걸리기 쉽다.
                .build()

            // [WHY] Wikimedia 는 식별 가능한 User-Agent 를 요구한다. 없으면 403 이나
            // 레이트리밋으로 간헐 실패한다 (실기기에서 위키 검색이 안 되던 원인 후보).
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(searchRequest).execute().use { searchResponse ->
                if (!searchResponse.isSuccessful) {
                    return@withContext AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("Wikipedia API HTTP error: ${searchResponse.code}"))
                }

                val searchBody = searchResponse.body?.string() ?: ""
                val searchJson = JSONObject(searchBody)

                if (!searchJson.has("query") || !searchJson.getJSONObject("query").has("pages")) {
                    return@withContext AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("No Wikipedia articles found matching '$topic' in language '$safeLang'."))
                }

                val pages = searchJson.getJSONObject("query").getJSONObject("pages")
                val firstPageKey = pages.keys().next()
                val page = pages.getJSONObject(firstPageKey)

                val title = page.optString("title", "")
                val extract = page.optString("extract", "")

                // [WHY] 도입부(extract)만 쓴다. gallery 원본은 action=parse 로 INFOBOX 까지
                // 긁어 붙이지만 그만큼 토큰 예산을 먹고, 우리 상한(TOOL_RESULT_MAX_TOKENS 500)
                // 에서는 도입부만으로도 꽉 찬다 — 필요해지면 예산 계산과 함께 재검토한다.
                var finalResult = ""
                if (extract.isNotEmpty()) {
                    finalResult += "--- SUMMARY ---\n$extract"
                }

                if (finalResult.isBlank()) {
                    return@withContext AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("Found page '$title' but no text was available."))
                }

                // [WHY] 캡을 자수가 아니라 **토큰 예산에서 파생**한다. 예전의 언어별 자수 캡
                // (zh 1500 / fr 4300 / es 4500 / 기타 5000)은 프리필 예산이 6000~8000 이던 최초
                // 커밋 시절 값이라, 0.13.0 에서 예산을 엔진 용량(4096)에서 파생시킨 뒤에도 ko
                // 5000자(실측 약 2,500토큰)가 턴 중간에 무예산으로 KV 에 들어갔다 — 툴 회신
                // 턴은 재생성 금지 턴이라 이 캡이 유일한 방어선이다 (ADR-020). 언어 분기가
                // 사라지는 이유는 토큰 추정기가 문자 클래스로 언어 밀도를 이미 반영하기
                // 때문이다(exp26 — 중국어도 비ASCII 클래스가 덮는다).
                val budgetedResult = SentenceTruncator.truncate(
                    text = "Title: $title\n$finalResult",
                    maxTokens = Constants.TOOL_RESULT_MAX_TOKENS - Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS,
                    tokenizer = tokenizer,
                    marker = "\n\n... [TRUNCATED TO SAVE CONTEXT]"
                )

                AppResult.Success(budgetedResult)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("Failed to query Wikipedia: ${e.message}"))
        }
    }

    private companion object {
        const val USER_AGENT = "KOSMOS/0.8.0 (on-device personal assistant; personal project)"
    }
}
