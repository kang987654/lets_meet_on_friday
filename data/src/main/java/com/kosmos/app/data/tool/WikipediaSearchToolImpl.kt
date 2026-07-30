package com.kosmos.app.data.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.tool.WikipediaSearchTool
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
 * - **Dependencies**: [OkHttpClient]
 *
 * ### Key Flow
 * 1. 지정된 언어(lang)와 토픽(topic)으로 Wikipedia API 엔드포인트를 구성합니다 (쿼리 인코딩 적용).
 * 2. OkHttp를 통해 네트워크 요청을 수행하고 JSON 결과를 파싱합니다.
 * 3. 검색된 페이지의 요약(extract)을 추출하고 언어별 최대 캡을 적용하여 반환합니다.
 */
class WikipediaSearchToolImpl @Inject constructor(
    private val client: OkHttpClient
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
                .addQueryParameter("origin", "*")
                .build()

            val searchRequest = Request.Builder().url(searchUrl).build()
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

                // For MVP, we just return the extract since it's the most important text.
                // Infobox fetching can be added later if needed, but extract is usually sufficient for RAG.
                var finalResult = ""
                if (extract.isNotEmpty()) {
                    finalResult += "--- SUMMARY ---\n$extract"
                }

                if (finalResult.isBlank()) {
                    return@withContext AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("Found page '$title' but no text was available."))
                }

                // Language-based safety caps
                val maxChars = when (safeLang) {
                    "zh" -> 1500
                    "fr" -> 4300
                    "es" -> 4500
                    else -> 5000
                }

                if (finalResult.length > maxChars) {
                    finalResult = finalResult.substring(0, maxChars) + "\n\n... [TRUNCATED TO SAVE CONTEXT]"
                }

                AppResult.Success("Title: $title\n$finalResult")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(com.kosmos.app.core.common.AppError.NetworkUnavailable("Failed to query Wikipedia: ${e.message}"))
        }
    }
}
