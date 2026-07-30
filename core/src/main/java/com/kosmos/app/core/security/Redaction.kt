package com.kosmos.app.core.security

/**
 * [Redaction]
 * 감사 로그 및 외부 전송 데이터에서 민감 정보(이메일, 전화번호)를 마스킹하고
 * 장문 텍스트를 절단 처리하는 유틸리티 싱글톤입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Security)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 이메일/전화번호 패턴을 `[EMAIL]`/`[PHONE]` 토큰으로 치환합니다.
 * 2. 마스킹 후 텍스트가 상한(500자)을 초과하면 절단 표식과 함께 잘라냅니다.
 */
object Redaction {
    private const val MAX_DETAIL_LENGTH = 500
    private val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE_PATTERN = Regex("\\b01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b")

    fun sanitize(text: String, redactionEnabled: Boolean = true): String {
        if (!redactionEnabled) return text
        var masked = EMAIL_PATTERN.replace(text, "[EMAIL]")
        masked = PHONE_PATTERN.replace(masked, "[PHONE]")
        return if (masked.length > MAX_DETAIL_LENGTH) {
            masked.take(MAX_DETAIL_LENGTH) + "... [REDACTED_DUE_TO_LENGTH]"
        } else {
            masked
        }
    }
}
