package com.kosmos.app.assistant.episode

import com.kosmos.app.core.common.Constants
import javax.inject.Inject

/**
 * 에피소드 요약을 시작하기 전에 기다리는 무활동 시간 — [EpisodeSummarizeScheduler] 가 마지막 턴 종료·엔진 Ready 뒤 이만큼 새 요청이 없을 때 돈다.
 *
 * [WHY] 값이 아니라 주입 객체인 이유: 스케줄러 테스트가 기다리지 않고 드레인을 확인할 수 있게 0 으로 바꿔 넣는다.
 */
open class SummaryQuietDelay @Inject constructor() {
    open val millis: Long get() = Constants.SUMMARY_QUIET_MILLIS
}
