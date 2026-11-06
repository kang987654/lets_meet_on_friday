package com.kosmos.app.domain.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 엔진이 준비된 뒤 채팅을 열기 전에 하는 일의 단계 (0.41.7 M2).
 *
 * - [IDLE] 시작 단계가 돌지 않는다(시작 전·엔진 오류·테스트) — 화면은 지금처럼 엔진 상태만 본다.
 * - [WAITING_ENGINE] 엔진을 올리는 중이거나 Ready 직후 첫 단계를 고르는 중.
 * - [SUMMARIZING]·[BRIEFING]·[PREPARING] 지난 대화 정리·오늘 브리핑·대화 미리 준비.
 * - [DONE] 끝 — 채팅을 연다.
 */
enum class StartupStage {
    IDLE, WAITING_ENGINE, SUMMARIZING, BRIEFING, PREPARING, DONE;

    /** Ready 뒤 시작 단계가 아직 돌고 있다 — 스플래시는 채팅으로 넘어가지 않고, 채팅 머리글은 단계 문구를 보인다. */
    val running: Boolean get() = this == WAITING_ENGINE || this == SUMMARIZING || this == BRIEFING || this == PREPARING
}

/**
 * 시작 단계를 읽는 쪽(화면·오케스트레이터)의 경계 — 구현은 앱의 `StartupCoordinator`.
 *
 * [WHY] 인터페이스로 둔다 — ChatScreen 단독 compose·수동 ViewModel 주입 테스트(AGENTS §2-②)가 [NONE] 기본값으로 지금처럼 동작한다.
 */
interface StartupProgress {
    val stage: StateFlow<StartupStage>

    /** 사용자가 요청을 보냈다 — 남은 시작 단계를 건너뛴다(이미 엔진 안에서 도는 일은 끝까지 간다). */
    fun onUserActivity() {}

    companion object {
        val NONE: StartupProgress = object : StartupProgress {
            override val stage: StateFlow<StartupStage> = MutableStateFlow(StartupStage.IDLE).asStateFlow()
        }
    }
}
