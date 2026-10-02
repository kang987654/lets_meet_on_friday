package com.kosmos.app.domain.document

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * [CsvReader]
 * csv 를 [Sheet] 로 읽습니다 — RFC 4180(따옴표 안의 쉼표·줄바꿈, `""` 이스케이프) (0.34.0).
 *
 * ### Key Flow
 * 1. 상한([DocumentLimits.maxCsvBytes])까지 읽는다 — 넘으면 마지막 줄바꿈까지만 쓰고 `truncated`.
 * 2. 문자셋: BOM 제거 → UTF-8 엄격 디코딩 → 실패하면 CP949.
 * 3. 구분자: 첫 줄에 쉼표가 없고 탭(또는 세미콜론)이 있으면 그것으로.
 *
 * [WHY] CP949 폴백 — 한국어 엑셀의 "CSV(쉼표로 분리)" 저장은 UTF-8 이 아니라 CP949 다. UTF-8 로만 읽으면 한글이 전부 깨진다.
 */
class CsvReader(private val limits: DocumentLimits = DocumentLimits()) {

    fun read(input: InputStream, name: String): DocumentResult<Sheet> = try {
        val (bytes, overflow) = readCapped(input)
        DocumentResult.Ok(parse(decode(bytes), name, overflow))
    } catch (e: java.io.IOException) {
        DocumentResult.Fail(DocumentError.UNREADABLE)
    }

    private fun readCapped(input: InputStream): Pair<ByteArray, Boolean> {
        val cap = limits.maxCsvBytes.toInt()
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        input.use { stream ->
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                if (buffer.size() > cap) {
                    val all = buffer.toByteArray()
                    val lastNewline = all.lastIndexOf('\n'.code.toByte(), cap)
                    return all.copyOf(if (lastNewline > 0) lastNewline else cap) to true
                }
            }
        }
        return buffer.toByteArray() to false
    }

    private fun ByteArray.lastIndexOf(b: Byte, before: Int): Int {
        for (i in minOf(before, size - 1) downTo 0) if (this[i] == b) return i
        return -1
    }

    internal fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        val utf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
            String(body, koreanCharset())
        }
    }

    private fun koreanCharset(): Charset =
        listOf("x-windows-949", "MS949", "EUC-KR").firstNotNullOfOrNull { name ->
            runCatching { Charset.forName(name) }.getOrNull()
        } ?: Charsets.ISO_8859_1

    internal fun parse(text: String, name: String, overflow: Boolean): Sheet {
        val delimiter = delimiterOf(text)
        val rows = mutableListOf<SheetRow>()
        var cells = mutableListOf<SheetCell>()
        val field = StringBuilder()
        var column = 0
        var rowIndex = 0
        var maxColumn = -1
        var inQuotes = false
        var truncated = overflow
        var i = 0

        fun endField() {
            if (field.isNotEmpty()) {
                cells += SheetCell(column, field.toString())
                if (column > maxColumn) maxColumn = column
            }
            field.setLength(0)
            column++
        }

        fun endRow(): Boolean {
            endField()
            if (cells.isNotEmpty()) rows += SheetRow(rowIndex, cells)
            cells = mutableListOf()
            column = 0
            rowIndex++
            if (rowIndex >= limits.maxRows) return false
            return true
        }

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> if (field.isEmpty()) inQuotes = true else field.append(ch)
                delimiter -> endField()
                '\r' -> Unit
                '\n' -> if (!endRow()) {
                    truncated = truncated || text.substring(i + 1).isNotBlank()
                    return Sheet(name, rows, maxColumn + 1, truncated = truncated)
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || cells.isNotEmpty()) endRow()
        return Sheet(name, rows, maxColumn + 1, truncated = truncated)
    }

    private fun delimiterOf(text: String): Char {
        val firstLine = text.substringBefore('\n')
        return when {
            ',' in firstLine -> ','
            '\t' in firstLine -> '\t'
            ';' in firstLine -> ';'
            else -> ','
        }
    }
}
