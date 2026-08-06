package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool

/**
 * [KosmosToolDeclarations]
 * 모델에게 선언할 툴 스키마입니다. LiteRT 런타임이 이 어노테이션을 읽어 모델의 **정식
 * 함수호출 템플릿**으로 주입합니다.
 *
 * ### Architecture Context
 * - **Layer**: Runtime (`:app`)
 *
 * [WHY] 이전에는 시스템 프롬프트에 `<tool_call>{"name":...}</tool_call>` 형식을 글로 설명했다.
 * 그 규약은 Gemma 의 채팅 템플릿에 없어서 온디바이스 모델이 따르지 못했고, 실기기에서 툴이
 * 한 번도 호출되지 않았다. google-ai-edge/gallery 가 쓰는 방식(`@Tool` + `ToolSet`)으로
 * 바꾼다 (ADR-008).
 *
 * [WHY] **메서드 본문은 실행되지 않는다.** `automaticToolCalling = false` 로 두었기 때문에
 * 런타임은 호출을 우리에게 넘기고 실행하지 않는다 — 실행은 기존 `ToolExecutor` 와 승인
 * 경로(PRD F4 승인 요구)가 맡는다. 여기 어노테이션은 순수하게 **선언**이다.
 *
 * [WHY] 툴마다 별도 클래스인 이유: `tool(ToolSet)` 은 그 클래스의 모든 `@Tool` 메서드를
 * 한 묶음으로 만든다. 웹 검색은 토글로 켜질 때만 선언해야 하므로 개별 공급자로 쪼갠다.
 *
 * [WHY] 런타임이 함수 이름을 snake_case 로 바꿔 모델에게 알린다(`addSchedule` →
 * `add_schedule`). 그래서 [CANONICAL_NAMES] 로 우리 executor 이름과 다시 이어야 한다.
 */
object KosmosToolDeclarations {

    /** 런타임이 모델에게 알리는 snake_case 이름 → 우리 `ToolExecutor` 이름. */
    val CANONICAL_NAMES: Map<String, String> = mapOf(
        "add_schedule" to "AddSchedule",
        "get_schedule" to "GetSchedule",
        "add_memory" to "AddMemory",
        "search_wikipedia" to "SearchWikipedia"
    )

    private val PROVIDERS: Map<String, () -> ToolProvider> = mapOf(
        "AddSchedule" to { tool(AddScheduleDeclaration()) },
        "GetSchedule" to { tool(GetScheduleDeclaration()) },
        "AddMemory" to { tool(AddMemoryDeclaration()) },
        "SearchWikipedia" to { tool(SearchWikipediaDeclaration()) }
    )

    /** 활성화된 툴만 선언 목록으로 만듭니다. 알 수 없는 이름은 무시합니다. */
    fun providersFor(enabledTools: List<String>): List<ToolProvider> =
        enabledTools.mapNotNull { PROVIDERS[it]?.invoke() }

    /** 모델이 알린 이름을 우리 executor 이름으로 되돌립니다. */
    fun canonicalName(runtimeName: String): String =
        CANONICAL_NAMES[runtimeName] ?: runtimeName

    // [WHY] 런타임은 함수 이름뿐 아니라 **파라미터 이름도** snake_case 로 선언한다
    // (0.8.1 실기기 preface 에서 start_time/end_time 확인). 모델이 보내는 인자 키도 그
    // 이름이므로, camelCase 를 읽는 executor(예: requireString("startTime"))에 닿기 전에
    // 되돌려야 한다.
    fun camelArgName(runtimeName: String): String {
        if (!runtimeName.contains('_')) return runtimeName
        return runtimeName.split('_')
            .filter { it.isNotEmpty() }
            .mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")
    }

    // [WHY] 본문이 실행될 일이 없지만 시그니처는 필요하다(스키마의 출처). 만약 언젠가
    // automaticToolCalling 을 켜면 여기가 호출되므로, 조용히 성공하지 않도록 예외를 던진다.
    private fun notExecutedHere(): Nothing =
        throw IllegalStateException("툴 실행은 ToolExecutor 가 담당합니다 (automaticToolCalling=false)")

    private class AddScheduleDeclaration : ToolSet {
        // [WHY] 파라미터 이름 `description` 은 금지 — 스키마의 툴 설명 키와 충돌해 렌더링된
        // 선언의 properties 에서 **사라지고 required 에만 남는다**(0.8.1 실기기 preface 로 확인).
        // 존재하지 않는 필드를 필수로 요구하는 깨진 스키마가 되어 constrained decoding 의 문법
        // 생성까지 위협한다. `memo` 로 우회한다.
        //
        // [WHY] nullable(`String?`) 은 스키마에 `nullable:true` 로 렌더링되지만 required 에서
        // 빠지지는 않는다(같은 preface 로 확인). 그래도 문법상 null 허용의 여지가 있어 유지한다.
        // 실행부는 누락·null 인자를 이미 처리한다 (AddScheduleToolExecutor.optString).
        @Tool(description = "사용자의 캘린더에 일정을 추가한다. 약속·예약·미팅·병원·시험 등 앞으로 일어날 일을 등록할 때 쓴다.")
        fun addSchedule(
            @ToolParam(description = "일정 제목. 예: '치과 예약'") title: String,
            @ToolParam(description = "시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'") startTime: String,
            @ToolParam(description = "종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.") endTime: String?,
            @ToolParam(description = "메모. 없으면 빈 문자열.") memo: String?
        ): Map<String, Any> = notExecutedHere()
    }

    private class GetScheduleDeclaration : ToolSet {
        // [WHY] "조회 범위. 'today' 또는 'week'." 만으로는 모델이 'tomorrow' 나 '2026-08-09' 를
        // 보냈다(PC 실험). 실행부는 "week" 포함 여부만 보므로 그런 값은 조용히 **오늘** 일정을
        // 반환했다 — 내일을 물으면 오늘이 나오는 버그. 설명으로 값을 좁히고, 실행부에서도
        // today 가 아니면 주간으로 넓히도록 함께 고쳤다(GetScheduleToolExecutor).
        @Tool(description = "사용자의 캘린더 일정을 조회한다.")
        fun getSchedule(
            @ToolParam(
                description = "조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. " +
                    "내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다."
            ) date: String
        ): Map<String, Any> = notExecutedHere()
    }

    private class AddMemoryDeclaration : ToolSet {
        @Tool(description = "사용자에 관한 사실·선호·비밀번호 등을 영구 기억으로 저장한다. 사용자가 '기억해줘'라고 하거나 나중에 다시 필요할 정보를 말했을 때 반드시 쓴다.")
        fun addMemory(
            @ToolParam(description = "기억할 내용. 사용자가 말한 숫자와 고유명사는 절대 바꾸지 말고 그대로 적는다.") content: String,
            @ToolParam(description = "분류 태그 목록. 예: ['비밀번호', '자전거']") tags: List<String>
        ): Map<String, Any> = notExecutedHere()
    }

    private class SearchWikipediaDeclaration : ToolSet {
        @Tool(description = "위키백과에서 주제의 요약을 가져온다. 사용자가 사실 확인이나 설명을 요청할 때 쓴다.")
        fun searchWikipedia(
            @ToolParam(description = "검색 키워드") topic: String,
            @ToolParam(description = "언어 코드. 'ko' 또는 'en'.") lang: String
        ): Map<String, Any> = notExecutedHere()
    }
}
