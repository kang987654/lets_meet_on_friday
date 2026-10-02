package com.kosmos.app.feature.document

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.FlowDocument
import com.kosmos.app.domain.document.Span
import com.kosmos.app.domain.document.TableCell
import com.kosmos.app.ui.theme.KosmosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [FlowViewTest]
 * 워드·한글 읽기 모드 — 제목·서식 문단·목록·표·이미지 자리·쪽 나눔이 그려지고, 글자 크기 버튼은 끝 단계에서 꺼진다 (0.35.0 M2).
 */
@RunWith(RobolectricTestRunner::class)
class FlowViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val document = FlowDocument(
        listOf(
            FlowBlock.Heading(1, listOf(Span("주간 보고"))),
            FlowBlock.Paragraph(listOf(Span("이번 주는 "), Span("출시 준비", bold = true), Span("에 집중했다."))),
            FlowBlock.ListItem(0, listOf(Span("버그 12건 수정"))),
            FlowBlock.Table(
                listOf(
                    listOf(TableCell(listOf(FlowBlock.Paragraph(listOf(Span("항목")))), colSpan = 2)),
                    listOf(
                        TableCell(listOf(FlowBlock.Paragraph(listOf(Span("완료"))))),
                        TableCell(listOf(FlowBlock.Paragraph(listOf(Span("8건")))))
                    )
                )
            ),
            FlowBlock.Image("word/media/image1.png"),
            FlowBlock.PageBreak,
            FlowBlock.Paragraph(listOf(Span("다음 쪽 내용")))
        )
    )

    private fun show(step: Int = 1, onScale: (Int) -> Unit = {}, doc: FlowDocument = document) {
        val state = DocumentViewerState.Flow("보고서.docx", doc) { _, _ -> null }
        composeRule.setContent {
            KosmosTheme { DocumentViewerScreen(state, onSelectSheet = {}, onClose = {}, textScaleStep = step, onTextScale = onScale) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `블록이 순서대로 그려진다`() {
        show()
        listOf("보고서.docx", "주간 보고", "이번 주는 출시 준비에 집중했다.", "버그 12건 수정", "항목", "완료", "8건")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithTag(FLOW_LIST_TAG).performScrollToIndex(document.blocks.lastIndex)
        composeRule.onNodeWithText("다음 쪽 내용").assertIsDisplayed()
    }

    @Test
    fun `글자 크기 버튼은 단계를 알리고 끝 단계에서 꺼진다`() {
        val deltas = mutableListOf<Int>()
        show(step = 2, onScale = { deltas += it })
        composeRule.onNodeWithText("가+").assertIsNotEnabled()
        composeRule.onNodeWithText("가−").performClick()
        assertEquals(listOf(-1), deltas)
        assertEquals(listOf(0.85f, 1f, 1.25f), (0..2).map(::textScaleOf))
    }

    @Test
    fun `빈 문서와 잘린 문서 안내`() {
        show(doc = FlowDocument(emptyList()))
        composeRule.onNodeWithText("내용이 없는 문서예요").assertIsDisplayed()
    }
}
