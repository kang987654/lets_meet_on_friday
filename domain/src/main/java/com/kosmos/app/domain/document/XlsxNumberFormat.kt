package com.kosmos.app.domain.document

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/**
 * [XlsxNumberFormat]
 * xlsx 숫자 셀의 저장값(`<v>`)을 셀 서식에 맞춰 보이는 글자로 바꿉니다 — 엑셀과 똑같이가 아니라 "읽을 수 있게"가 목표다.
 *
 * ### Key Flow
 * 1. 서식 id(내장) 또는 서식 코드(사용자 정의)로 종류를 판별 — 날짜·시각·날짜시각·백분율·숫자·텍스트.
 * 2. 날짜류는 일련번호를 날짜로(1900/1904 기준), 숫자는 소수 자릿수·천 단위 구분만 반영한다.
 *
 * [WHY] 엑셀은 날짜를 **숫자로 저장하고 서식으로만 날짜로 보인다** — 서식을 무시하면 "2026-10-02" 가 "46297" 로 보인다.
 * 통화 기호·색·음수 괄호 같은 장식은 버린다(값 읽기에 영향이 없다).
 */
internal object XlsxNumberFormat {

    enum class Kind { GENERAL, NUMBER, PERCENT, DATE, TIME, DATETIME, TEXT }

    /** 판별 결과 — [decimals]·[grouping] 은 NUMBER/PERCENT, [seconds]·[elapsedHours] 는 시각류에서만 의미가 있다. */
    data class Spec(
        val kind: Kind,
        val decimals: Int = 0,
        val grouping: Boolean = false,
        val seconds: Boolean = false,
        val elapsedHours: Boolean = false
    )

    // 내장 서식 — 14~22 날짜·시각, 27~36·50~58 은 한국어 등 동아시아 로캘의 내장 날짜 서식이다.
    private val BUILTIN_CODES = mapOf(
        1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%",
        14 to "yyyy-mm-dd", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
        18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss", 22 to "yyyy-mm-dd h:mm",
        37 to "#,##0", 38 to "#,##0", 39 to "#,##0.00", 40 to "#,##0.00",
        45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mm:ss.0", 49 to "@"
    )
    private val CJK_DATE_IDS = (27..36).toSet() + (50..58).toSet()

    fun spec(numFmtId: Int, customCode: String?): Spec {
        if (numFmtId in CJK_DATE_IDS) return Spec(Kind.DATE)
        val code = customCode ?: BUILTIN_CODES[numFmtId] ?: return Spec(Kind.GENERAL)
        return analyze(code)
    }

    fun analyze(code: String): Spec {
        val section = firstSection(code)
        val stripped = strip(section)
        val lower = stripped.lowercase()
        if (lower.isBlank() || lower == "general") return Spec(Kind.GENERAL)
        if (lower == "@") return Spec(Kind.TEXT)
        val elapsed = Regex("""\[(h+|m+|s+)]""").containsMatchIn(section.lowercase())
        val core = lower.replace(Regex("""\[[^]]*]"""), "")
        val hasDate = 'y' in core || 'd' in core
        val hasTime = 'h' in core || 's' in core || elapsed
        val hasMonthOnly = 'm' in core && !hasTime
        return when {
            (hasDate || hasMonthOnly) && hasTime -> Spec(Kind.DATETIME, seconds = 's' in core)
            hasDate || hasMonthOnly -> Spec(Kind.DATE)
            hasTime -> Spec(Kind.TIME, seconds = 's' in core, elapsedHours = elapsed)
            '%' in core -> Spec(Kind.PERCENT, decimals(core), grouping(core))
            else -> Spec(Kind.NUMBER, decimals(core), grouping(core))
        }
    }

    /** 저장값 [raw] 를 서식대로 표시한다. 숫자로 못 읽으면 원문 그대로. */
    fun format(raw: String, spec: Spec, date1904: Boolean): String {
        val number = raw.trim().toBigDecimalOrNull() ?: return raw
        return when (spec.kind) {
            Kind.GENERAL, Kind.TEXT -> general(number)
            Kind.NUMBER -> fixed(number, spec.decimals, spec.grouping)
            Kind.PERCENT -> fixed(number.movePointRight(2), spec.decimals, spec.grouping) + "%"
            Kind.DATE -> date(number.toDouble(), date1904)?.toString() ?: general(number)
            Kind.TIME -> time(number.toDouble(), spec.seconds, spec.elapsedHours)
            Kind.DATETIME -> {
                val d = date(number.toDouble(), date1904) ?: return general(number)
                "$d ${time(number.toDouble(), spec.seconds, elapsed = false)}"
            }
        }
    }

    /** 엑셀 "일반" 서식과 비슷하게 — 유효 숫자 15자리, 끝 0 제거, 아주 크거나 작으면 지수 표기. */
    fun general(number: BigDecimal): String {
        if (number.signum() == 0) return "0"
        val rounded = number.round(MathContext(15, RoundingMode.HALF_UP)).stripTrailingZeros()
        val integerDigits = rounded.precision() - rounded.scale()
        return if (integerDigits > 15 || rounded.scale() > 10) rounded.toString() else rounded.toPlainString()
    }

    private fun fixed(number: BigDecimal, decimals: Int, grouping: Boolean): String {
        val pattern = buildString {
            append(if (grouping) "#,##0" else "0")
            if (decimals > 0) append('.').append("0".repeat(decimals))
        }
        val format = DecimalFormat(pattern, DecimalFormatSymbols(Locale.ROOT)).apply { roundingMode = RoundingMode.HALF_UP }
        return format.format(number)
    }

    /** 일련번호 → 날짜. 1900 기준은 엑셀의 1900-02-29(존재하지 않는 날, 일련번호 60) 버그를 따른다. */
    fun date(serial: Double, date1904: Boolean): LocalDate? {
        if (serial < 0 || serial > 2_958_465) return null // 9999-12-31 너머는 엑셀도 날짜로 보이지 않는다
        val days = kotlin.math.floor(serial).toLong()
        return when {
            date1904 -> LocalDate.of(1904, 1, 1).plusDays(days)
            days < 60 -> LocalDate.of(1899, 12, 31).plusDays(days)
            days == 60L -> LocalDate.of(1900, 2, 28) // 실재하지 않는 2/29 — 가장 가까운 날로
            else -> LocalDate.of(1899, 12, 30).plusDays(days)
        }
    }

    private fun time(serial: Double, seconds: Boolean, elapsed: Boolean): String {
        val totalSeconds = Math.round(serial * 86_400)
        val hours = if (elapsed) totalSeconds / 3600 else (totalSeconds / 3600) % 24
        val minutes = (totalSeconds / 60) % 60
        val secs = totalSeconds % 60
        val hh = if (elapsed) hours.toString() else hours.toString().padStart(2, '0')
        return if (seconds) "$hh:${two(minutes)}:${two(secs)}" else "$hh:${two(minutes)}"
    }

    private fun two(n: Long) = n.toString().padStart(2, '0')

    private fun firstSection(code: String): String {
        var inQuote = false
        code.forEachIndexed { i, ch ->
            if (ch == '"') inQuote = !inQuote
            if (ch == ';' && !inQuote) return code.substring(0, i)
        }
        return code
    }

    /** 따옴표 문자열·`\x` 이스케이프·`_x`/`*x` 채움 문자·색/로캘 대괄호를 지운다(경과 시간 `[h]` 는 남긴다). */
    private fun strip(section: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < section.length) {
            val ch = section[i]
            when {
                ch == '"' -> {
                    val end = section.indexOf('"', i + 1)
                    i = if (end < 0) section.length else end + 1
                    continue
                }
                ch == '\\' || ch == '_' || ch == '*' -> { i += 2; continue }
                ch == '[' -> {
                    val end = section.indexOf(']', i + 1)
                    val inner = if (end < 0) "" else section.substring(i + 1, end)
                    if (inner.isNotEmpty() && inner.all { it in "hHmMsS" }) sb.append('[').append(inner).append(']')
                    i = if (end < 0) section.length else end + 1
                    continue
                }
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString().trim()
    }

    private fun decimals(core: String): Int {
        val dot = core.indexOf('.')
        if (dot < 0) return 0
        return core.substring(dot + 1).takeWhile { it == '0' || it == '#' }.length
    }

    private fun grouping(core: String): Boolean = Regex("""[#0],[#0]""").containsMatchIn(core)
}
