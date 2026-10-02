package com.kosmos.app.domain.document

import com.kosmos.app.domain.document.XlsxNumberFormat.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DocumentParsingTest]
 * 문서 뷰어의 작은 순수 함수들 — 형식 판별, 셀 참조, xlsx 숫자 서식, csv.
 */
class DocumentParsingTest {

    // --- 형식 판별 ---

    @Test
    fun `MIME 이 알려진 형식이면 그것, 아니면 확장자`() {
        val cases = listOf(
            Triple("application/pdf", "a.bin", DocumentType.PDF),
            Triple("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null, DocumentType.XLSX),
            Triple("text/csv; charset=utf-8", null, DocumentType.CSV),
            Triple("application/octet-stream", "가계부.XLSX", DocumentType.XLSX),
            Triple("application/vnd.ms-excel", "주소록.csv", DocumentType.CSV), // Windows 관례 csv MIME
            Triple("application/vnd.ms-excel", "옛날.xls", DocumentType.UNSUPPORTED),
            Triple(null, "보고서.pdf", DocumentType.PDF),
            Triple(null, "확장자없음", DocumentType.UNSUPPORTED),
            Triple("application/msword", "a.doc", DocumentType.UNSUPPORTED),
            Triple("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null, DocumentType.DOCX),
            Triple("application/haansofthwpx", null, DocumentType.HWPX),
            Triple("application/octet-stream", "공문.hwpx", DocumentType.HWPX),
            Triple("application/x-hwp", "옛한글.hwp", DocumentType.UNSUPPORTED)
        )
        cases.forEach { (mime, name, expected) -> assertEquals("$mime / $name", expected, DocumentType.detect(mime, name)) }
    }

    @Test
    fun `파일 앞 바이트로 실제 형식 확인`() {
        assertTrue(DocumentSniffer.isZip("PK\u0003\u0004".toByteArray()))
        assertTrue(DocumentSniffer.isPdf("%PDF-1.7".toByteArray()))
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0)
        assertTrue(DocumentSniffer.isOleCompound(ole))
        assertFalse(DocumentSniffer.isZip(ole))
        assertFalse(DocumentSniffer.isPdf(byteArrayOf(1)))
    }

    // --- 셀 참조 ---

    @Test
    fun `셀 참조 변환`() {
        assertEquals(0, CellRef.column("A1"))
        assertEquals(27, CellRef.column("AB12"))
        assertEquals(11, CellRef.row("AB12"))
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA", "XFD"), listOf(0, 25, 26, 51, 52, 16383).map(CellRef::columnName))
        assertEquals(CellRange(0, 0, 2, 3), CellRef.range("D3:A1"))
        assertEquals(CellRange(1, 1, 1, 1), CellRef.range("B2"))
        assertEquals(null, CellRef.range("엉망"))
    }

    // --- xlsx 숫자 서식 ---

    @Test
    fun `서식 코드 판별`() {
        val cases = mapOf(
            "General" to Kind.GENERAL, "@" to Kind.TEXT, "0" to Kind.NUMBER, "#,##0.00" to Kind.NUMBER,
            "0.0%" to Kind.PERCENT, "yyyy-mm-dd" to Kind.DATE, "yyyy\"년\" m\"월\" d\"일\"" to Kind.DATE, "mmm" to Kind.DATE,
            "[\$-412]AM/PM h:mm" to Kind.TIME, "[h]:mm:ss" to Kind.TIME, "mm:ss" to Kind.TIME, "yyyy-mm-dd h:mm" to Kind.DATETIME,
            "[Red]#,##0;(#,##0)" to Kind.NUMBER, "\"₩\"#,##0" to Kind.NUMBER, "_(* #,##0_)" to Kind.NUMBER, "0.00E+00" to Kind.NUMBER
        )
        cases.forEach { (code, kind) -> assertEquals(code, kind, XlsxNumberFormat.analyze(code).kind) }
        assertEquals(2, XlsxNumberFormat.analyze("#,##0.00").decimals)
        assertTrue(XlsxNumberFormat.analyze("#,##0.00").grouping)
        assertFalse(XlsxNumberFormat.analyze("0.00").grouping)
        assertEquals("한국어 내장 날짜 서식 id", Kind.DATE, XlsxNumberFormat.spec(31, null).kind)
    }

    @Test
    fun `숫자 표시`() {
        fun fmt(raw: String, code: String) = XlsxNumberFormat.format(raw, XlsxNumberFormat.analyze(code), date1904 = false)
        assertEquals("1,234,567", fmt("1234567", "#,##0"))
        assertEquals("-3.50", fmt("-3.5", "0.00"))
        assertEquals("5%", fmt("0.05", "0%"))
        assertEquals("0.3", fmt("0.30000000000000004", "General"))
        assertEquals("123456789012", fmt("123456789012", "General"))
        assertEquals("1E+20", fmt("1E20", "General"))
        assertEquals("30:00:00", fmt("1.25", "[h]:mm:ss"))
        assertEquals("2026-10-02 18:30", fmt("46297.770833333336", "yyyy-mm-dd h:mm"))
        assertEquals("숫자가 아니면 원문", "abc", fmt("abc", "0.00"))
    }

    @Test
    fun `1900 기준 날짜는 엑셀의 윤년 버그를 따른다`() {
        assertEquals("1900-01-01", XlsxNumberFormat.date(1.0, false).toString())
        assertEquals("1900-02-28", XlsxNumberFormat.date(59.0, false).toString())
        assertEquals("1900-03-01", XlsxNumberFormat.date(61.0, false).toString())
        assertEquals(null, XlsxNumberFormat.date(-1.0, false))
    }

    // --- csv ---

    private fun csv(text: String, limits: DocumentLimits = DocumentLimits()) =
        (CsvReader(limits).read(text.toByteArray().inputStream(), "a.csv") as DocumentResult.Ok).value

    private fun Sheet.grid() = rows.map { r -> r.cells.associate { it.column to it.text } }

    @Test
    fun `따옴표 안의 쉼표 · 줄바꿈 · 이중 따옴표`() {
        val sheet = csv("이름,메모\r\n\"홍, 길동\",\"첫 줄\n둘째 줄\"\n\"말하길 \"\"안녕\"\"\",\n")
        assertEquals(
            listOf(mapOf(0 to "이름", 1 to "메모"), mapOf(0 to "홍, 길동", 1 to "첫 줄\n둘째 줄"), mapOf(0 to "말하길 \"안녕\"")),
            sheet.grid()
        )
        assertEquals(2, sheet.columnCount)
        assertFalse(sheet.truncated)
    }

    @Test
    fun `빈 칸은 담지 않고 열 위치는 지킨다`() {
        val sheet = csv("a,,c\n\n,b\n")
        assertEquals(listOf(mapOf(0 to "a", 2 to "c"), mapOf(1 to "b")), sheet.grid())
        assertEquals("빈 줄도 행 번호를 차지한다", listOf(0, 2), sheet.rows.map { it.index })
    }

    @Test
    fun `CP949 · BOM · 탭 구분`() {
        val cp949 = "이름,나이\n철수,30".toByteArray(charset("x-windows-949"))
        val sheet = (CsvReader().read(cp949.inputStream(), "a.csv") as DocumentResult.Ok).value
        assertEquals(mapOf(0 to "이름", 1 to "나이"), sheet.grid().first())

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "가,나".toByteArray()
        assertEquals(mapOf(0 to "가", 1 to "나"), (CsvReader().read(bom.inputStream(), "a.csv") as DocumentResult.Ok).value.grid().first())

        assertEquals("첫 줄에 쉼표가 없고 탭이 있으면 탭", mapOf(0 to "이름", 1 to "메모 내용"), csv("이름\t메모 내용\n").grid().first())
        assertEquals("쉼표가 있으면 쉼표", mapOf(0 to "a\tb", 1 to "c"), csv("a\tb,c\n").grid().first())
    }

    @Test
    fun `행 상한과 바이트 상한`() {
        val rows = csv("1\n2\n3\n4\n", DocumentLimits(maxRows = 2))
        assertEquals(listOf("1", "2"), rows.rows.map { it.cells.single().text })
        assertTrue(rows.truncated)

        val bytes = csv("aaaa\nbbbb\ncccc\n", DocumentLimits(maxCsvBytes = 12))
        assertEquals("마지막 줄바꿈까지만", listOf("aaaa", "bbbb"), bytes.rows.map { it.cells.single().text })
        assertTrue(bytes.truncated)

        assertEquals(0, csv("").rows.size)
    }
}
