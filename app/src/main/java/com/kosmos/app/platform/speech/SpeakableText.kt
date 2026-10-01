package com.kosmos.app.platform.speech

/**
 * 화면용 답변(마크다운)을 TTS 가 읽을 텍스트로 바꾸고, 엔진 입력 상한에 맞게 나눕니다 (0.29.0).
 *
 * [WHY] 순수 함수로 둔다 — 마크다운 기호 제거와 문장 경계 분할은 엔진 없이 표로 검증해야 한다. 기호를 남기면
 * 엔진이 "별표 별표"처럼 읽고, 상한(`TextToSpeech.getMaxSpeechInputLength()`, 보통 4000자)을 넘기면 speak 가 실패한다.
 */
object SpeakableText {

    private val codeFence = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`([^`]*)`")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val bareUrl = Regex("https?://\\S+")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val listMarker = Regex("(?m)^\\s*(?:[-*+]|\\d+[.)])\\s+")
    private val quote = Regex("(?m)^\\s*>\\s?")
    private val emphasis = Regex("(\\*\\*|__|\\*|_|~~)")
    private val tableRule = Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$")
    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")
    private val spaces = Regex("[ \\t]+")
    private val blankLines = Regex("\\n{2,}")
    private val sentenceEnd = Regex("(?<=[.!?。…\\n])")

    /** 마크다운·URL·이모지를 걷어낸 낭독용 텍스트. 남는 게 없으면 빈 문자열. */
    fun from(markdown: String): String = markdown
        // [WHY] 코드 블록은 통째로 뺀다 — 기호 덩어리를 소리로 읽어 봐야 의미가 없다.
        .replace(codeFence, " ")
        .replace(inlineCode, "$1")
        .replace(link, "$1")
        .replace(bareUrl, " ")
        .replace(tableRule, "")
        .replace(heading, "")
        .replace(listMarker, "")
        .replace(quote, "")
        .replace(emphasis, "")
        .replace("|", " ")
        .replace(emoji, "")
        .replace(spaces, " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(blankLines, "\n")
        .trim()

    /**
     * [maxLength] 이하 조각으로 나눕니다 — 문장 경계에서 자르고, 한 문장이 상한을 넘으면 그 문장만 글자 수로 자른다.
     */
    fun chunks(text: String, maxLength: Int): List<String> {
        require(maxLength > 0)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        text.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }.forEach { sentence ->
            sentence.chunked(maxLength).forEach { piece ->
                if (current.isNotEmpty() && current.length + 1 + piece.length > maxLength) {
                    result += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
