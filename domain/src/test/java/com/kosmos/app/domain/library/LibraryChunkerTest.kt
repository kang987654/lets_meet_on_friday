package com.kosmos.app.domain.library

import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.SheetCell
import com.kosmos.app.domain.document.SheetRow
import com.kosmos.app.domain.document.Span
import com.kosmos.app.domain.document.TableCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문서 보관함 조각 나누기(0.41.0 M1) — 조문 단위·위치 머리말·상한 분할·형식별 변환.
 *
 * [WHY] 조각 단위가 곧 출처 단위다 — 조 하나가 다른 조와 섞이거나 머리말을 잃으면 "민법 제162조에 따르면"을 보일 수 없다(exp74).
 */
class LibraryChunkerTest {

    private fun body(vararg lines: String) = LibraryChunker.fromPlain(lines.joinToString("\n"))

    @Test
    fun `법령은 편장절 경로 아래 조 하나가 한 조각이고 조문 번호를 따로 든다`() {
        val chunks = LibraryChunker.chunk(
            "민법",
            body(
                "제1편 총칙",
                "제7장 소멸시효",
                "제162조(채권, 재산권의 소멸시효) ①채권은 10년간 행사하지 아니하면 소멸시효가 완성한다.",
                "②채권 및 소유권 이외의 재산권은 20년간 행사하지 아니하면 소멸시효가 완성한다.",
                "제163조(3년의 단기소멸시효) 다음 각호의 채권은 3년간 행사하지 아니하면 소멸시효가 완성한다. <개정 1997. 12. 13.>",
                "제5조의2(시험) 의 조문.",
                "제2편 물권",
                "제185조(물권의 종류) 물권은 법률 또는 관습법에 의하는 외에는 임의로 창설하지 못한다."
            )
        )

        assertEquals(4, chunks.size)
        assertEquals("민법 > 제1편 총칙 > 제7장 소멸시효 > 제162조(채권, 재산권의 소멸시효)", chunks[0].sectionPath)
        assertEquals("162", chunks[0].article)
        assertTrue("항이 같은 조각에 이어진다", chunks[0].text.contains("②채권 및 소유권"))
        assertTrue("개정 표지는 뺀다", !chunks[1].text.contains("개정 1997"))
        assertEquals("5의2", chunks[2].article)
        assertEquals("상위 제목이 바뀌면 그 아래 경로가 비워진다", "민법 > 제2편 물권 > 제185조(물권의 종류)", chunks[3].sectionPath)
        assertEquals(listOf(0, 1, 2, 3), chunks.map { it.index })
    }

    @Test
    fun `상한을 넘는 조는 줄 경계에서 나누고 머리말을 유지하며 몇 번째 조각인지 붙인다`() {
        val long = (1..6).map { "${it}. " + "가".repeat(90) }
        val chunks = LibraryChunker.chunk("규정", body("제3조(목적) 이 규정은", *long.toTypedArray()), maxChars = 200)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 200 })
        assertEquals("규정 > 제3조(목적) (1/${chunks.size})", chunks.first().sectionPath)
        assertTrue(chunks.all { it.article == "3" })
        assertTrue("줄 중간을 자르지 않는다", chunks.flatMap { it.text.lines() }.all { it == "제3조(목적) 이 규정은" || it.length == 93 })
    }

    @Test
    fun `한 줄이 상한보다 길면 그 줄만 글자로 자른다`() {
        val chunks = LibraryChunker.chunk("메모", body("나".repeat(450)), maxChars = 200)

        assertEquals(listOf(200, 200, 50), chunks.map { it.text.length })
        assertNull(chunks.first().article)
    }

    @Test
    fun `읽기 모드 제목은 수준별 경로가 되고 표는 열머리 값 줄이 된다`() {
        fun spans(t: String) = listOf(Span(t))
        fun cell(t: String) = TableCell(listOf(FlowBlock.Paragraph(spans(t))))
        val blocks = listOf(
            FlowBlock.Heading(1, spans("1장 출장")),
            FlowBlock.Heading(2, spans("1.1 여비")),
            FlowBlock.Paragraph(spans("여비는 실비로 지급한다.")),
            FlowBlock.Table(listOf(listOf(cell("구분"), cell("일비")), listOf(cell("국내"), cell("2만원")))),
            FlowBlock.Heading(2, spans("1.2 숙박")),
            FlowBlock.Paragraph(spans("숙박비는 상한 안에서."))
        )

        val chunks = LibraryChunker.chunk("출장 지침", LibraryChunker.fromFlow(blocks))

        assertEquals(listOf("출장 지침 > 1장 출장 > 1.1 여비", "출장 지침 > 1장 출장 > 1.2 숙박"), chunks.map { it.sectionPath })
        assertEquals("여비는 실비로 지급한다.\n구분: 국내, 일비: 2만원", chunks[0].text)
    }

    @Test
    fun `시트는 시트 이름이 제목이고 PDF 는 쪽을 넘지 않으며 쪽 번호를 든다`() {
        val sheet = Sheet(
            "예산",
            listOf(
                SheetRow(0, listOf(SheetCell(0, "항목"), SheetCell(1, "금액"))),
                SheetRow(1, listOf(SheetCell(0, "식대"), SheetCell(1, "8000")))
            ),
            columnCount = 2
        )
        val sheetChunks = LibraryChunker.chunk("가계부", LibraryChunker.fromSheets(listOf(sheet)))
        assertEquals("가계부 > 시트: 예산", sheetChunks.single().sectionPath)
        assertEquals("항목: 식대, 금액: 8000", sheetChunks.single().text)

        val pdf = LibraryChunker.chunk("교재", LibraryChunker.fromPages(listOf("1장 서론\n본문 A", null, "본문 B")))
        assertEquals(listOf(1, 3), pdf.map { it.page })
        assertEquals(listOf("1장 서론\n본문 A", "본문 B"), pdf.map { it.text })
    }
}
