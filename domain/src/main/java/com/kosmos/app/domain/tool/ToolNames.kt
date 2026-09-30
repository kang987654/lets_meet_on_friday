package com.kosmos.app.domain.tool

/**
 * 앱 내부의 정규(Pascal) 툴 이름 — 실행기 `name`, 에이전트의 활성 목록, 트리거 규칙, 선언 매핑의 단일 출처.
 *
 * [WHY] 같은 문자열이 실행기·KosmosAgent·BaseAgent·PromptAssembler·KosmosToolDeclarations 여섯
 * 곳에 흩어져 있었다 — 한 곳의 오타는 컴파일을 통과하고 "툴이 조용히 안 불림"으로만 드러난다.
 * 모델에 보이는 snake_case 이름(`add_schedule` 등)은 프롬프트 표면이라 선언 파일에 그대로 둔다.
 */
object ToolNames {
    const val ADD_SCHEDULE = "AddSchedule"
    const val GET_SCHEDULE = "GetSchedule"
    const val ADD_MEMORY = "AddMemory"
    const val SEARCH_MEMORY = "SearchMemory"
    const val SEARCH_WIKIPEDIA = "SearchWikipedia"
    const val ADD_REMINDER = "AddReminder"
}
