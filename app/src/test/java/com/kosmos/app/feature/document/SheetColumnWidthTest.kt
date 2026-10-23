package com.kosmos.app.feature.document

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.SheetCell
import com.kosmos.app.domain.document.SheetRow
import com.kosmos.app.ui.theme.KosmosTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 표 열 너비가 실제 글꼴 폭을 따르는지(0.38.6 M3 — 금액이 "100…"으로 잘리던 결함).
 *
 * [WHY] NATIVE 그래픽 모드에서만 글자 폭이 실제와 같다 — 기본(LEGACY) 모드는 "10-10" 을 5dp 로 재서 너비 검증이 무의미하다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SheetColumnWidthTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `내용으로 맞춘 열과 엑셀이 좁게 정한 숫자 열 모두 글자가 잘리지 않는다`() {
        val sheet = Sheet(
            name = "1월",
            rows = listOf(SheetRow(0, listOf(SheetCell(0, "10-10"), SheetCell(1, "1234567890"), SheetCell(2, "100000")))),
            columnCount = 3,
            columnWidths = mapOf(1 to 3f)
        )
        composeRule.setContent { KosmosTheme { SheetView(sheet) } }
        composeRule.waitForIdle()

        listOf("10-10", "1234567890", "100000").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val l = layouts.single()
            // didOverflowWidth 는 말줄임 없이 맞을 때도 참이 된다(배치 폭과 문단 폭의 반올림 차) — 말줄임 여부로 본다.
            assertFalse("$text 이 잘림", l.isLineEllipsized(0))
        }
    }
}
