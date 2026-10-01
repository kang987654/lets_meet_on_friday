package com.kosmos.app.assistant.cleanup

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 같은 기억 데이터를 다루는 백그라운드 추론(자동 요약 드레인 ↔ 수동 기억 정리)을 한 줄로 세우는 잠금입니다 (0.30.0).
 *
 * [WHY] 추론 자체는 llmDispatcher(병렬 1)가 이미 줄 세우지만, 그건 **추론 호출 단위**다. 정리는 "노트를 읽고 → 여러 번 판정하고
 * → 쓰는" 긴 작업이라 그 사이에 자동 요약·추출이 끼어들면 막 읽은 목록과 다른 데이터를 합치게 된다. 드레인과 정리가 같은
 * 뮤텍스를 쥐면 서로의 앞뒤로만 돈다(계획서 0.30.0 고정 제약).
 */
@Singleton
class BackgroundInferenceGate @Inject constructor() {
    val mutex = Mutex()
}
