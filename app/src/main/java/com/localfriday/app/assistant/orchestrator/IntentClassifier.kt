package com.localfriday.app.assistant.orchestrator

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

        // 1. 캘린더 쓰기(생성/수정) 의도 파악
        val writeKeywords = listOf("등록", "추가", "잡아줘", "예약", "일정 만들어")
        if (writeKeywords.any { normalizedInput.contains(it) } && 
            (normalizedInput.contains("일정") || normalizedInput.contains("캘린더") || normalizedInput.contains("회의"))) {
            return Intent.CALENDAR_WRITE
        }

        // 2. 캘린더 읽기(조회) 의도 파악
        val readKeywords = listOf("뭐", "확인", "보여", "알려", "있어")
        if (readKeywords.any { normalizedInput.contains(it) } && 
            (normalizedInput.contains("일정") || normalizedInput.contains("스케줄") || normalizedInput.contains("캘린더"))) {
            return Intent.CALENDAR_READ
        }

        // 3. 기본은 일반 대화
        return Intent.CHAT
    }
}
