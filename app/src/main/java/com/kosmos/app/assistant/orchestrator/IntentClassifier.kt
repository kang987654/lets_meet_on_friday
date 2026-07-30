package com.kosmos.app.assistant.orchestrator

import javax.inject.Inject

enum class Intent {
    CHAT,
    CALENDAR_READ,
    CALENDAR_WRITE
    // TODO(v1): KNOWLEDGE, SEARCH
}

class IntentClassifier @Inject constructor() {

    fun classify(input: String): Intent {
        val normalizedInput = input.trim().lowercase()

        // [WHY] 실행 시점 allowlist 강제 도입으로 라우팅 누락 = 툴 차단이 되므로,
        // "약속", "미팅" 등 일정성 대상 키워드를 캘린더 문맥에 포함한다.
        val calendarContextKeywords = listOf("일정", "캘린더", "회의", "약속", "미팅", "스케줄")

        // 1. 캘린더 쓰기(생성/수정) 의도 파악
        val writeKeywords = listOf("등록", "추가", "잡아줘", "예약", "일정 만들어")
        if (writeKeywords.any { normalizedInput.contains(it) } &&
            calendarContextKeywords.any { normalizedInput.contains(it) }) {
            return Intent.CALENDAR_WRITE
        }

        // 2. 캘린더 읽기(조회) 의도 파악
        val readKeywords = listOf("뭐", "확인", "보여", "알려", "있어")
        if (readKeywords.any { normalizedInput.contains(it) } &&
            calendarContextKeywords.any { normalizedInput.contains(it) }) {
            return Intent.CALENDAR_READ
        }

        // 3. 기본은 일반 대화
        return Intent.CHAT
    }
}
