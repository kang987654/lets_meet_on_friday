package com.kosmos.app.domain.input

import com.kosmos.app.core.common.Constants
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [InputActivity]
 * 사용자가 입력란에 글자를 치는(녹음 중인) 시각 — 조용히 도는 GPU 일이 **새로 시작하기 전에** 보는 공용 판정입니다 (0.41.7 M3).
 *
 * ### Architecture Context
 * - **Layer**: Domain — 채팅 화면이 [mark] 하고(대화 미리 준비 경유), 대화 미리 준비·에피소드 요약 드레인·사실 추출·기억/문서 벡터 준비가 [awaitQuiet] 한다.
 *
 * [WHY] 조용히 도는 일이 GPU 를 수 초 붙잡는 동안 입력하면 화면 프레임이 0.8~0.9초씩 멈췄다(0.41.7 M0 실기기). 대화 미리 준비만 입력을
 * 보고 있었다(0.40.7). 이미 시작한 추론은 끊지 않는다 — 엔진 안 추론 취소는 대화를 망가뜨리는 경로다(0.38.6). 그래서 "시작 전"과 묶음 사이에서만 본다.
 * [WHY] 판정은 [now] 를 인자로 받는다(AGENTS §2-④).
 */
@Singleton
class InputActivity @Inject constructor() {

    @Volatile
    var lastActivityAt: Long = 0L
        private set

    /** 글자가 바뀌었다·녹음을 시작했다 — 녹음은 상한이 끝나는 시각을 넘겨 녹음 내내 미룬다. */
    fun mark(now: Long = System.currentTimeMillis()) {
        lastActivityAt = now
    }

    /** 마지막 활동 뒤 [quietMs] 조용해지기까지 남은 시간(ms) — 이미 지났으면 0. */
    fun quietRemainingMs(now: Long, quietMs: Long = Constants.PREWARM_QUIET_MS): Long =
        (lastActivityAt + quietMs - now).coerceAtLeast(0)

    /** 입력이 [quietMs] 동안 멎을 때까지 기다린다 — 입력이 없었으면 바로 돌아온다. */
    suspend fun awaitQuiet(quietMs: Long = Constants.PREWARM_QUIET_MS, since: Long = 0L) {
        while (true) {
            val last = maxOf(lastActivityAt, since)
            val remaining = (last + quietMs - System.currentTimeMillis()).coerceAtLeast(0)
            if (remaining <= 0) return
            // [WHY] 남은 시간을 한 번에 자지 않는다 — 녹음은 상한 끝을 미리 넣는데 일찍 멈추면 [mark] 가 당겨진다. 짧게 끊어 다시 본다.
            delay(remaining.coerceAtMost(RECHECK_MS))
        }
    }

    private companion object {
        const val RECHECK_MS = 250L
    }
}
