package com.kosmos.app.domain.agent

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.tool.ToolNames

/**
 * 승인 시트를 사용자가 거절했을 때 비서가 남기는 고정 문장과, 요약 전사에서 그 자리를 대신할 "사용자가 취소함" 줄 (0.42.3 M1).
 *
 * [WHY] 거절 문장("일정을 추가하지 않았어요.")만 남으면 요약이 "비서가 추가하지 않았다"(실패)로 쓰고 주간 회고가 "추가하지 못해 아쉬우셨을 것"이
 * 됐다 — exp96: 지금 요약 0/9 가 취소로 씀 → 비서 줄을 사용자 줄 "(승인 창에서 … 취소함)"으로 바꾸면 9/9, 회고의 실패·사과 7 → 0.
 * 답 만들기([com.kosmos.app.assistant.agent.BaseAgent])와 전사가 같은 목록을 봐야 해서 도메인에 둔다.
 */
object RejectionReplies {

    /** 툴 거절 뒤 비서 답 — 바이트가 바뀌면 다음 턴 모델 입력이 바뀐다(회귀 세트 대상). */
    fun reply(toolName: String, language: AppLanguage = AppLanguage.KO): String {
        val en = language == AppLanguage.EN
        return when (toolName) {
            ToolNames.ADD_SCHEDULE -> if (en) "I didn't add the event." else "일정을 추가하지 않았어요."
            ToolNames.ADD_REMINDER -> if (en) "I didn't set the reminder." else "알림을 등록하지 않았어요."
            // [WHY] "저장하지 않았어요"는 다음 턴에 "그 정보는 없다"로 읽혀, 바로 앞에서 들은 값을 "찾지 못했어요"라고 했다(실기기 T7, exp60 2/6 → 5/6).
            ToolNames.ADD_MEMORY -> if (en) "I didn't save that as a note." else "메모에는 남기지 않았어요."
            else -> if (en) "I didn't run the request." else "요청을 실행하지 않았어요."
        }
    }

    private val CANCEL_LINES: Map<String, String> = buildMap {
        put(reply(ToolNames.ADD_SCHEDULE), "(승인 창에서 일정 추가를 취소함)")
        put(reply(ToolNames.ADD_REMINDER), "(승인 창에서 알림 등록을 취소함)")
        put(reply(ToolNames.ADD_MEMORY), "(승인 창에서 메모 저장을 취소함)")
        put(reply(""), "(승인 창에서 요청을 취소함)")
        // [WHY] 영어 줄은 실측하지 않았다(exp96 은 한국어) — 같은 모양으로 옮긴다.
        put(reply(ToolNames.ADD_SCHEDULE, AppLanguage.EN), "(Canceled adding the event in the approval sheet)")
        put(reply(ToolNames.ADD_REMINDER, AppLanguage.EN), "(Canceled setting the reminder in the approval sheet)")
        put(reply(ToolNames.ADD_MEMORY, AppLanguage.EN), "(Canceled saving the note in the approval sheet)")
        put(reply("", AppLanguage.EN), "(Canceled the request in the approval sheet)")
    }

    /** 비서 메시지가 거절 고정 문장이면 그 자리에 쓸 사용자 쪽 취소 줄, 아니면 null. */
    fun cancelLine(assistantText: String): String? = CANCEL_LINES[assistantText.trim()]
}
