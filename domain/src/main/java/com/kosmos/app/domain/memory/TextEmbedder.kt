package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult

interface TextEmbedder {
    /**
     * 텍스트를 임베딩 벡터로 변환합니다.
     *
     * [WHY] `suspend` 인 이유는 구현체가 온디바이스 추론 그래프를 로드하고 실행하기 때문이다.
     * 이전에는 논블로킹 시그니처였고, 그래서 구현체가 그래프 로드를 생성자 `init` 블록으로
     * 밀어내 첫 주입 시 호출 스레드(메인일 수 있음)를 수십~수백 ms 붙잡았다.
     */
    suspend fun embed(text: String): AppResult<FloatArray>
}
