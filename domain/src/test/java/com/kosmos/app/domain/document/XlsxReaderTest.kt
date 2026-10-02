package com.kosmos.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [XlsxReaderTest]
 * 최소 xlsx 를 테스트 안에서 조립해 읽는다 — 공유/인라인 문자열, 서식별 숫자(날짜·백분율·천 단위·시각), 수식 캐시 값,
 * 병합·틀 고정, 상한, 손상 파일. 개인 문서를 픽스처로 커밋하지 않기 위해 파일을 만들지 않는다.
 */
class XlsxReaderTest {

    private val rootRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook(date1904: Boolean = false) = """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<workbookPr${if (date1904) " date1904=\"1\"" else ""}/>
<sheets><sheet name="요약" sheetId="1" r:id="rId1"/><sheet name="데이터" sheetId="2" r:id="rId2"/></sheets>
</workbook>"""

    private val workbookRels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private val sharedStrings = """<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
<si><t>이름</t></si>
<si><r><rPr><b/></rPr><t>굵</t></r><r><t>게</t></r></si>
<si><t>本文</t><rPh sb="0" eb="1"><t>ほん</t></rPh></si>
<si><t>줄_x000A_바꿈</t></si>
</sst>"""

    private val styles = """<?xml version="1.0" encoding="UTF-8"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy&quot;년&quot; m&quot;월&quot; d&quot;일&quot;"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts>
<cellStyleXfs count="1"><xf numFmtId="49"/></cellStyleXfs>
<cellXfs count="6"><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="165"/><xf numFmtId="10"/><xf numFmtId="21"/></cellXfs>
</styleSheet>"""

    private val sheet1 = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetViews><sheetView workbookViewId="0"><pane xSplit="1" ySplit="1" topLeftCell="B2" state="frozen"/></sheetView></sheetViews>
<cols><col min="1" max="2" width="20.5" customWidth="1"/></cols>
<sheetData>
<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
<row r="2"><c r="A2" t="inlineStr"><is><t>인라인</t></is></c><c r="B2" s="1"><v>46297</v></c><c r="C2"/></row>
<row r="3"><c r="A3" t="b"><v>1</v></c><c r="B3" t="e"><v>#DIV/0!</v></c><c r="C3" t="str"><f>A1&amp;"계"</f><v>합계</v></c><c r="D3" s="3"><f>SUM(X1:X9)</f><v>1234.5</v></c></row>
<row r="5"><c r="A5" s="4"><v>0.1234</v></c><c r="B5" s="5"><v>0.5</v></c><c r="C5"><v>0.30000000000000004</v></c><c r="D5" s="2"><v>46297</v></c></row>
</sheetData>
<mergeCells count="1"><mergeCell ref="C1:F1"/></mergeCells>
</worksheet>"""

    private val sheet2 = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row><c t="inlineStr"><is><t>가</t></is></c><c t="inlineStr"><is><t>나</t></is></c></row>
<row><c><v>1</v></c><c><v>2</v></c></row>
<row><c><v>3</v></c></row>
</sheetData></worksheet>"""

    private fun source(vararg overrides: Pair<String, String?>): ZipSource {
        val files = mutableMapOf<String, String?>(
            "_rels/.rels" to rootRels,
            "xl/workbook.xml" to workbook(),
            "xl/_rels/workbook.xml.rels" to workbookRels,
            "xl/sharedStrings.xml" to sharedStrings,
            "xl/styles.xml" to styles,
            "xl/worksheets/sheet1.xml" to sheet1,
            "xl/worksheets/sheet2.xml" to sheet2
        )
        overrides.forEach { (k, v) -> files[k] = v }
        return ZipSource { name -> files[name]?.toByteArray()?.inputStream() }
    }

    private fun <T> DocumentResult<T>.ok(): T = when (this) {
        is DocumentResult.Ok -> value
        is DocumentResult.Fail -> { fail("실패: $error"); throw IllegalStateException() }
    }

    private fun Sheet.text(row: Int, column: Int): String? =
        rows.firstOrNull { it.index == row }?.cells?.firstOrNull { it.column == column }?.text

    @Test
    fun `시트 이름과 순서`() {
        assertEquals(listOf("요약", "데이터"), XlsxReader(source()).open().ok().sheetNames)
    }

    @Test
    fun `셀 종류별 표시 값`() {
        val reader = XlsxReader(source())
        val sheet = reader.readSheet(reader.open().ok(), 0).ok()
        val expected = mapOf(
            (0 to 0) to "이름", (0 to 1) to "굵게", (0 to 2) to "本文", (0 to 3) to "줄\n바꿈", // 공유 문자열(런·후리가나·이스케이프)
            (1 to 0) to "인라인", (1 to 1) to "2026-10-02",                                     // 인라인, 내장 날짜 서식
            (2 to 0) to "TRUE", (2 to 1) to "#DIV/0!", (2 to 2) to "합계", (2 to 3) to "1,234.50", // 불리언·오류·수식 캐시 값
            (4 to 0) to "12.34%", (4 to 1) to "12:00:00", (4 to 2) to "0.3", (4 to 3) to "2026-10-02" // 백분율·시각·부동소수 오차·사용자 날짜 서식
        )
        expected.forEach { (pos, text) -> assertEquals("$pos", text, sheet.text(pos.first, pos.second)) }
        assertEquals("빈 칸과 빈 행은 담지 않는다", listOf(0, 1, 2, 4), sheet.rows.map { it.index })
        assertEquals("값 없는 C2 는 빠진다", listOf(0, 1), sheet.rows[1].cells.map { it.column })
    }

    @Test
    fun `틀 고정 · 병합 · 열 너비 · 열 수`() {
        val reader = XlsxReader(source())
        val sheet = reader.readSheet(reader.open().ok(), 0).ok()
        assertEquals(1, sheet.frozenRows)
        assertEquals(1, sheet.frozenColumns)
        assertEquals(listOf(CellRange(0, 2, 0, 5)), sheet.merges)
        assertEquals("병합 범위까지 열을 그린다", 6, sheet.columnCount)
        assertEquals(20.5f, sheet.columnWidths[1])
        assertFalse(sheet.truncated)
    }

    @Test
    fun `셀 참조가 없는 시트는 차례대로 놓는다`() {
        val reader = XlsxReader(source())
        val sheet = reader.readSheet(reader.open().ok(), 1).ok()
        assertEquals("데이터", sheet.name)
        assertEquals(listOf("가", "나", "1", "2", "3"), sheet.rows.flatMap { r -> r.cells.map { it.text } })
        assertEquals(listOf(0, 1), sheet.rows[1].cells.map { it.column })
        assertEquals(listOf(0, 1, 2), sheet.rows.map { it.index })
    }

    @Test
    fun `1904 기준 통합 문서`() {
        val reader = XlsxReader(source("xl/workbook.xml" to workbook(date1904 = true)))
        val sheet = reader.readSheet(reader.open().ok(), 0).ok()
        assertEquals("1904-01-01 + 46297일", java.time.LocalDate.of(1904, 1, 1).plusDays(46297).toString(), sheet.text(1, 1))
    }

    @Test
    fun `행 상한에 닿으면 앞부분만 담고 잘렸다고 표시한다`() {
        val reader = XlsxReader(source(), DocumentLimits(maxRows = 2))
        val sheet = reader.readSheet(reader.open().ok(), 0).ok()
        assertEquals(listOf(0, 1), sheet.rows.map { it.index })
        assertTrue(sheet.truncated)
    }

    @Test
    fun `셀 상한에 닿아도 앞부분은 남는다`() {
        val reader = XlsxReader(source(), DocumentLimits(maxCells = 5))
        val sheet = reader.readSheet(reader.open().ok(), 0).ok()
        assertEquals(5, sheet.rows.sumOf { it.cells.size })
        assertTrue(sheet.truncated)
    }

    @Test
    fun `큰 시트는 앞 200행을 먼저 한 번 내보내고 끝까지 읽은 시트를 돌려준다`() {
        val rows = (1..250).joinToString("") { r -> """<row r="$r"><c r="A$r"><v>$r</v></c></row>""" }
        val big = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
        val reader = XlsxReader(source("xl/worksheets/sheet1.xml" to big))
        val partials = mutableListOf<Sheet>()

        val sheet = reader.readSheet(reader.open().ok(), 0) { partials += it }.ok()

        assertEquals(1, partials.size)
        assertEquals(XlsxReader.FIRST_BATCH_ROWS, partials.single().rows.size)
        assertFalse(partials.single().complete)
        assertEquals(250, sheet.rows.size)
        assertTrue(sheet.complete)

        val small = mutableListOf<Sheet>()
        reader.readSheet(reader.open().ok(), 1) { small += it }
        assertTrue("짧은 시트는 부분 시트를 내지 않는다", small.isEmpty())
    }

    @Test
    fun `압축 해제 상한을 넘으면 너무 큼`() {
        assertEquals(DocumentResult.Fail(DocumentError.TOO_LARGE), XlsxReader(source(), DocumentLimits(maxEntryBytes = 100)).open())
    }

    @Test
    fun `깨진 XML 과 빠진 통합 문서는 손상됨`() {
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), XlsxReader(source("xl/workbook.xml" to "<workbook><sheets>")).open())
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), XlsxReader(source("xl/workbook.xml" to null)).open())
        val reader = XlsxReader(source("xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row>"))
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), reader.readSheet(reader.open().ok(), 0))
    }

    @Test
    fun `관계 파일 Target 경로 해석`() {
        assertEquals("xl/worksheets/sheet1.xml", resolvePartPath("xl", "worksheets/sheet1.xml"))
        assertEquals("xl/worksheets/sheet1.xml", resolvePartPath("xl", "/xl/worksheets/sheet1.xml"))
        assertEquals("media/a.png", resolvePartPath("xl/worksheets", "../../media/a.png"))
        assertEquals("xl/workbook.xml", resolvePartPath("", "xl/workbook.xml"))
    }
}
