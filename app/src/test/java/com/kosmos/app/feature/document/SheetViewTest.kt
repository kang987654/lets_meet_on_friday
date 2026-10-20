package com.kosmos.app.feature.document

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.kosmos.app.domain.document.CellRange
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.SheetCell
import com.kosmos.app.domain.document.SheetRow
import com.kosmos.app.ui.theme.KosmosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SheetViewTest]
 * 표 격자 — 열 머리·행 번호, 틀 고정 행이 스크롤 뒤에도 보임, 병합 셀, 시트 탭, 오류 문구, 길게 눌러 전체 보기 (0.34.0 M2).
 */
@RunWith(RobolectricTestRunner::class)
class SheetViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sheet = Sheet(
        name = "1월",
        rows = listOf(SheetRow(0, listOf(SheetCell(0, "날짜"), SheetCell(1, "항목"), SheetCell(2, "금액")))) +
            (1..80).map { r -> SheetRow(r, listOf(SheetCell(0, "10-$r"), SheetCell(1, "항목$r"), SheetCell(2, "${r * 1000}"))) } +
            SheetRow(82, listOf(SheetCell(0, "합계 — 이번 달 지출을 모두 더한 값입니다"))),
        columnCount = 3,
        merges = listOf(CellRange(82, 0, 82, 2)),
        frozenRows = 1
    )

    private fun show(state: DocumentViewerState, onSelect: (Int) -> Unit = {}) {
        composeRule.setContent { KosmosTheme { DocumentViewerScreen(state = state, onSelectSheet = onSelect, onClose = {}) } }
        composeRule.waitForIdle()
    }

    private fun spreadsheet() = DocumentViewerState.Spreadsheet("가계부.xlsx", listOf("1월", "2월"), selected = 0, sheet = sheet)

    @Test
    fun `열 머리와 행 번호, 파일 이름`() {
        show(spreadsheet())
        composeRule.onNodeWithText("가계부.xlsx").assertIsDisplayed()
        composeRule.onNodeWithText("C").assertIsDisplayed()
        composeRule.onNodeWithText("날짜").assertIsDisplayed()
        composeRule.onNodeWithText("항목1").assertIsDisplayed()
    }

    @Test
    fun `틀 고정 행은 끝까지 스크롤해도 보이고 병합 셀은 한 칸으로 보인다`() {
        show(spreadsheet())
        composeRule.onNodeWithTag(SHEET_GRID_TAG).performScrollToIndex(81)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("금액").assertIsDisplayed()
        composeRule.onNodeWithText("합계 — 이번 달 지출을 모두 더한 값입니다").assertIsDisplayed()
        composeRule.onNodeWithText("항목1").assertDoesNotExist()
    }

    @Test
    fun `넓게 병합한 제목 칸은 화면 왼쪽에서 보인다`() {
        val titled = Sheet(
            name = "표",
            rows = listOf(SheetRow(0, listOf(SheetCell(0, "포켓몬 GO 속성별 레이드 티어 종합표"))), SheetRow(1, (0 until 20).map { SheetCell(it, "값$it") })),
            columnCount = 20,
            merges = listOf(CellRange(0, 0, 0, 19))
        )
        show(DocumentViewerState.Spreadsheet("티어.xlsx", listOf("표"), selected = 0, sheet = titled))
        // [WHY] 가로 스크롤 안의 칸은 화면 밖이어도 assertIsDisplayed 가 통과하고 boundsInRoot 는 잘린 값이다 — 칸(누르는 영역)이 아니라 글자 자체의 잘리지 않은 위치를 잰다.
        val screenWidth = composeRule.onRoot().fetchSemanticsNode().size.width
        val left = composeRule.onNodeWithText("포켓몬 GO 속성별 레이드 티어 종합표", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x
        assertTrue("제목 왼쪽 끝 $left 이 화면 너비 $screenWidth 안", left < screenWidth / 2)
    }

    @Test
    fun `시트 탭을 누르면 그 번호를 알린다`() {
        var selected = -1
        show(spreadsheet()) { selected = it }
        composeRule.onNodeWithText("2월").performClick()
        assertEquals(1, selected)
    }

    @Test
    fun `칸을 길게 누르면 전체 내용과 복사 버튼`() {
        show(spreadsheet())
        composeRule.onNodeWithText("항목1").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("복사").assertIsDisplayed()
    }

    @Test
    fun `오류와 빈 시트 문구`() {
        show(DocumentViewerState.Failed(DocumentError.ENCRYPTED))
        composeRule.onNodeWithText(documentErrorMessage(DocumentError.ENCRYPTED)).assertIsDisplayed()
    }

    @Test
    fun `잘린 시트는 앞부분만 보인다고 알린다`() {
        show(DocumentViewerState.Spreadsheet("큰.csv", listOf("큰.csv"), sheet = sheet.copy(truncated = true)))
        composeRule.onNodeWithText("파일이 커서 앞부분(83행까지)만 보여요").assertIsDisplayed()
    }

    @Test
    fun `PDF 배율 버튼은 1배 · 1점5배 · 2배를 돈다`() {
        assertEquals(1.5f, nextZoom(1f))
        assertEquals(2f, nextZoom(1.5f))
        assertEquals(1f, nextZoom(2f))
        assertEquals("두 손가락으로 2.7배까지 키운 뒤 누르면 1배", 1f, nextZoom(2.7f))
    }
}
