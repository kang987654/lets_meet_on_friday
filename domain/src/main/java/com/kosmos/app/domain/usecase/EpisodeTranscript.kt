package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.agent.RejectionReplies
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.tool.Tokenizer

/**
 * 에피소드 원문을 "사용자:/비서:"(KO) 또는 "User:/Assistant:"(EN) 라벨로 이어 붙이고 토큰 상한에서 자릅니다.
 *
 * 요약([SummarizeEpisodeUseCase])과 자동 추출([ExtractFactsUseCase])이 같은 원문을 본다 —
 * 두 oneShot 이 다른 전사를 읽으면 "요약에는 있는데 추출에는 없는" 사실이 생긴다.
 *
 * [WHY] 에피소드는 예산 리셋 경계 덕에 보통 프리필 예산([com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS]) 이내지만, catch-up 이
 * 소급 배정한 고아 구간은 더 길 수 있다. 상한을 넘으면 **앞쪽을 보존**한다 — 에피소드의
 * 주제는 첫 발화들이 정하고, 꼬리는 대개 그 변주다.
 */
internal fun buildEpisodeTranscript(
    messages: List<ChatMessage>,
    tokenizer: Tokenizer,
    maxTokens: Int,
    lang: AppLanguage = AppLanguage.KO
): String {
    val en = lang == AppLanguage.EN
    val lines = StringBuilder()
    val userLabel = if (en) "User" else "사용자"
    for (m in messages) {
        // [WHY] 승인 거절 고정 문장은 사용자 쪽 "취소함" 줄로 바꾼다 — 비서 줄로 두면 요약이 실패로 썼다(exp96 0/9 → 9/9, [RejectionReplies]).
        val cancel = if (m.role != ChatMessage.Role.USER) RejectionReplies.cancelLine(m.content) else null
        val line = when {
            cancel != null -> "$userLabel: $cancel\n"
            m.role == ChatMessage.Role.USER -> "$userLabel: ${m.content}\n"
            else -> "${if (en) "Assistant" else "비서"}: ${m.content}\n"
        }
        if (tokenizer.sizeInTokens(lines.toString() + line) > maxTokens) break
        lines.append(line)
    }
    return lines.toString().trimEnd()
}
