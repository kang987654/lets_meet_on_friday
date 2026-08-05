package com.kosmos.app.assistant.tool

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.security.ApprovalRules
import com.kosmos.app.domain.usecase.QueryWikipediaUseCase
import org.json.JSONObject
import javax.inject.Inject

/**
 * [SearchWikipediaToolExecutor]
 * 모델의 `SearchWikipedia` 툴 콜을 받아 위키피디아 요약 검색을 수행하는 실행기입니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant (Tool)
 * - **Dependencies**: [QueryWikipediaUseCase]
 *
 * ### Key Flow
 * 1. 웹 검색 허용은 건별 승인이 아닌 전역 토글(webSearchEnabled)로 제어됩니다 —
 *    토글 OFF 시 에이전트 allowlist에서 제외되어 이 실행기에 도달하지 못합니다.
 * 2. 검색 결과를 JSON(JSONObject 이스케이프 적용) 문자열로 반환합니다.
 */
class SearchWikipediaToolExecutor @Inject constructor(
    private val queryWikipediaUseCase: QueryWikipediaUseCase
) : ToolExecutor {
    override val name: String = "SearchWikipedia"

    override val actionType: ApprovalRules.ActionType = ApprovalRules.ActionType.WEB_SEARCH

    override suspend fun execute(args: ToolArguments, sessionId: String): String {
        val topic = args.requireString("topic")
        val lang = args.optString("lang") ?: "ko"

        return when (val res = queryWikipediaUseCase(topic, lang)) {
            is AppResult.Success -> {
                // [WHY] JSONObject가 따옴표·개행·백슬래시를 모두 이스케이프하므로 수동 replace보다 안전하다.
                JSONObject().put("status", "success").put("data", res.data).toString()
            }
            is AppResult.Failure -> {
                JSONObject().put("status", "error").put("message", "검색 중 오류 발생: ${res.error}").toString()
            }
        }
    }
}
