package com.kosmos.app.assistant.agent

import com.kosmos.app.assistant.tool.ToolResultJson
import com.kosmos.app.domain.memory.MemoSaveIntent
import com.kosmos.app.domain.memory.RepeatRequest
import com.kosmos.app.domain.tool.ToolNames

/**
 * [ToolCallGuards]
 * 모델이 부른 툴을 **실행(승인 시트) 전에** 막는 판정 — 막으면 모델에게 돌려줄 결과를, 통과면 null 을 돌려줍니다.
 *
 * ### Architecture Context
 * - **Layer**: Assistant Agent — [BaseAgent] 의 툴 루프가 실행기 조회·승인 전에 부른다. 순수 판정이라 의존성이 없다.
 *
 * ### Key Flow
 * allowlist → 되묻기에 불린 메모 저장 → 저장 부탁 없는 메모 저장(사실 저장 가드). 장치 목록과 각각의 근거는 ADR-030.
 */
internal object ToolCallGuards {

    /** 막힌 호출 — 모델에게 돌려줄 결과와, 감사에 남길 오류(있으면). */
    data class Block(val outcome: ToolOutcome, val auditError: String? = null)

    fun check(
        name: String,
        allowedTools: List<String>,
        userText: String,
        previousAssistant: String?,
        guardFact: Boolean
    ): Block? {
        // [WHY] allowlist가 프롬프트 텍스트에만 있으면 모델(또는 주입된 문서)이 임의 툴을 호출해도
        // 실행되므로, 실행 시점에 반드시 재검증한다.
        if (name !in allowedTools) {
            val message = if (name == ToolNames.SEARCH_WIKIPEDIA) {
                "웹 검색이 비활성화되어 있습니다. 사용자에게 채팅 화면 상단의 웹 검색 토글을 켜도록 안내하세요."
            } else {
                "이 에이전트에서 사용할 수 없는 도구입니다: $name"
            }
            return Block(ToolOutcome(ToolResultJson.error(message), executed = false), "Blocked tool call outside allowlist: $name")
        }

        // [WHY] 되묻기("송장번호 다시 알려줘")에 불린 메모 저장은 승인 시트를 띄우지 않고 막는다 — 프롬프트로는 안 고쳐졌고(exp61),
        // 시트에서 취소하면 물어본 값 대신 "메모에는 남기지 않았어요."로 끝났다. 결과 문구는 exp61c G2(막은 턴 3/3 값을 답함, 거짓 저장 주장 0).
        if (name == ToolNames.ADD_MEMORY && RepeatRequest.matches(userText)) {
            return Block(ToolOutcome(ToolResultJson.error(REPEAT_NOT_SAVE), executed = false))
        }

        // [WHY] 저장 낱말 없이 사실만 말한 턴의 메모 저장은 막고 먼저 묻게 한다 — 대화 기록에 끌린 호출이라 프롬프트로는 못 막았다(exp64, 0.38.9 결정 1).
        if (guardFact && name == ToolNames.ADD_MEMORY && MemoSaveIntent.shouldBlock(userText, previousAssistant)) {
            return Block(ToolOutcome(ToolResultJson.error(MemoSaveIntent.ASK_FIRST), executed = false, factBlocked = true))
        }
        return null
    }

    // exp61c G2 실측 원문.
    const val REPEAT_NOT_SAVE = "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다. 그 값을 답하고, 저장했다고 말하지 마세요."
}
