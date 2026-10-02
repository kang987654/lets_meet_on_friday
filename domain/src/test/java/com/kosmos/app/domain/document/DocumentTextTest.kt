package com.kosmos.app.domain.document

import com.kosmos.app.domain.document.DocumentText.TableFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DocumentTextTest]
 * 채팅 첨부 글자 — 머리 행 붙이기, 형식 3종, **행·블록 경계에서 자르기**(칸 값이 잘린 숫자를 모델에 주지 않는다), 길이 표시.
 */
class DocumentTextTest {

    private val sheet = Sheet(
        name = "가계부",
        rows = listOf(
            SheetRow(0, listOf(SheetCell(0, "날짜"), SheetCell(1, "항목"), SheetCell(2, "금액"))),
            SheetRow(1, listOf(SheetCell(0, "10-01"), SheetCell(1, "커피"), SheetCell(2, "4,500"))),
            SheetRow(2, listOf(SheetCell(0, "10-02"), SheetCell(2, "12,000"))),
            SheetRow(5, listOf(SheetCell(0, "10-05"), SheetCell(1, "영화|팝콘"), SheetCell(2, "15,000")))
        ),
        columnCount = 3
    )

    @Test
    fun `고른 행 앞에 머리 행을 붙이고 형식별로 만든다`() {
        assertEquals(
            "| 날짜 | 항목 | 금액 |\n|---|---|---|\n| 10-02 |  | 12,000 |\n| 10-05 | 영화/팝콘 | 15,000 |",
            DocumentText.sheet(sheet, 2..5, cap = 300, format = TableFormat.MARKDOWN).text
        )
        assertEquals("날짜\t항목\t금액\n10-01\t커피\t4,500", DocumentText.sheet(sheet, 1..1, 300, TableFormat.TSV).text)
        assertEquals("빈 칸은 건너뛴다", "날짜: 10-02, 금액: 12,000", DocumentText.sheet(sheet, 2..2, 300, TableFormat.KEY_VALUE).text)
        assertEquals("머리 행을 고르면 한 번만", "날짜\t항목\t금액\n10-01\t커피\t4,500", DocumentText.sheet(sheet, 0..1, 300, TableFormat.TSV).text)
    }

    @Test
    fun `상한을 넘으면 행 경계에서 자르고 전체 길이를 알린다`() {
        val clipped = DocumentText.sheet(sheet, 1..5, cap = 30, format = TableFormat.TSV)
        assertEquals("날짜\t항목\t금액\n10-01\t커피\t4,500", clipped.text)
        assertTrue(clipped.truncated)
        assertEquals("날짜\t항목\t금액\n10-01\t커피\t4,500\n10-02\t\t12,000\n10-05\t영화 팝콘\t15,000".length, clipped.fullLength)

        val one = DocumentText.plain("아주긴한줄".repeat(10), cap = 12)
        assertEquals("첫 단위가 이미 넘으면 글자로 자른다", 12, one.text.length)
        assertFalse(DocumentText.plain("짧다", 12).truncated)
    }

    @Test
    fun `읽기 모드 블록 — 문단 · 목록 · 표, 그림과 쪽 나눔은 뺀다`() {
        val blocks = listOf(
            FlowBlock.Heading(1, listOf(Span("회의록"))),
            FlowBlock.ListItem(1, listOf(Span("예산 10% 증액"))),
            FlowBlock.Image("a.png"),
            FlowBlock.PageBreak,
            FlowBlock.Table(listOf(
                listOf(TableCell(listOf(FlowBlock.Paragraph(listOf(Span("담당"))))), TableCell(listOf(FlowBlock.Paragraph(listOf(Span("기한")))))),
                listOf(TableCell(listOf(FlowBlock.Paragraph(listOf(Span("김민수"))))), TableCell(listOf(FlowBlock.Paragraph(listOf(Span("11월"))))))
            ))
        )
        assertEquals("회의록\n  - 예산 10% 증액\n담당\t기한\n김민수\t11월", DocumentText.flow(blocks, 300, TableFormat.TSV).text)
    }
}
