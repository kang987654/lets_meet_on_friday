package com.kosmos.app.core.security

/**
 * [Redaction]
 * 감사 로그 및 외부 전송 데이터에서 민감 정보나 장문 텍스트를 마스킹/생략 처리하는 유틸리티 싱글톤입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Security)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 텍스트 길이가 100자를 초과하고 마스킹 옵션이 활성화된 경우 `[내용 생략 - N자]` 형태로 변환합니다.
 */
object Redaction {
    private const val MAX_DETAIL_LENGTH = 100
    fun sanitize(text: String, redactionEnabled: Boolean): String =
        if (redactionEnabled && text.length > MAX_DETAIL_LENGTH)
            "[내용 생략 - ${text.length}자]"
        else text
}
