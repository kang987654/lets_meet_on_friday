package com.kosmos.app.core.security

object Redaction {
    private const val MAX_DETAIL_LENGTH = 100
    fun sanitize(text: String, redactionEnabled: Boolean): String =
        if (redactionEnabled && text.length > MAX_DETAIL_LENGTH)
            "[내용 생략 - ${text.length}자]"
        else text
}
