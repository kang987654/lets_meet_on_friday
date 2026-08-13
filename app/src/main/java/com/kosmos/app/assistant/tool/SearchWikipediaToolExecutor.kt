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
                //
                // [WHY] 근거 고정 지침을 데이터 **뒤에** 붙인다. 실기기에서 모델이 요약에 없는
                // 데뷔일을 지어내 답했다(2026-08-13) — 요약이 도입부(exintro)라 질문의 답이
                // 없을 수 있는데, 그때 파라미터 지식으로 메꾼다. 툴 회신 턴은 턴 지침(exp20)이
                // `currentInput=""` 로 덮여 사라지는 턴이라 시스템 지시로부터 가장 먼 턴이고,
                // 데이터 옆이 유일한 근거리 주입 지점이다. SearchMemoryToolExecutor 가 같은
                // 방식으로 실기기에서 검증됐다. 문구는 exp25 픽스처와 문자 단위로 동일해야
                // 한다 — 바꾸면 그 측정이 무효가 된다.
                JSONObject().put("status", "success")
                    .put("data", res.data + GROUNDING_GUIDANCE)
                    .toString()
            }
            is AppResult.Failure -> {
                JSONObject().put("status", "error").put("message", "검색 중 오류 발생: ${res.error}").toString()
            }
        }
    }

    companion object {
        // [WHY] 토큰 비용은 Constants.TOOL_RESULT_ENVELOPE_RESERVE_TOKENS 안에서 회계된다.
        const val GROUNDING_GUIDANCE =
            "\n\n위 요약에 있는 내용만으로 답하세요. 질문의 답(날짜·숫자·이름 등)이 요약에 없으면 요약에 없다고 답하세요. 절대 지어내지 마세요."
    }
}
