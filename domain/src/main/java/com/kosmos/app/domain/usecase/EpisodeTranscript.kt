package com.kosmos.app.domain.usecase

import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer

/**
 * 에피소드 원문을 "사용자:/비서:" 라벨로 이어 붙이고 토큰 상한에서 자릅니다.
 *
 * 요약([SummarizeEpisodeUseCase])과 자동 추출([ExtractFactsUseCase])이 같은 원문을 본다 —
 * 두 oneShot 이 다른 전사를 읽으면 "요약에는 있는데 추출에는 없는" 사실이 생긴다.
 *
 * [WHY] 에피소드는 예산 리셋 경계 덕에 보통 프리필 예산(≈1,700토큰) 이내지만, catch-up 이
 * 소급 배정한 고아 구간은 더 길 수 있다. 상한을 넘으면 **앞쪽을 보존**한다 — 에피소드의
 * 주제는 첫 발화들이 정하고, 꼬리는 대개 그 변주다.
 */
internal fun buildEpisodeTranscript(
    messages: List<ChatMessage>,
    tokenizer: Tokenizer,
    maxTokens: Int
): String {
    val lines = StringBuilder()
    for (m in messages) {
        val label = if (m.role == ChatMessage.Role.USER) "사용자" else "비서"
        val line = "$label: ${m.content}\n"
        if (tokenizer.sizeInTokens(lines.toString() + line) > maxTokens) break
        lines.append(line)
    }
    return lines.toString().trimEnd()
}
