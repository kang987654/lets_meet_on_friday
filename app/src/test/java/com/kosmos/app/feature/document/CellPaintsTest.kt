package com.kosmos.app.feature.document

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kosmos.app.domain.document.CellStyle
import com.kosmos.app.domain.document.XlsxColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 원본 셀 서식 → 화면 색(0.38.7 M1) — 다크에서 배경은 색조를 살려 어둡게, 읽을 수 없는 글자색은 테마 글자색으로.
 */
class CellPaintsTest {

    private val lightSurface = Color(0xFFFFFFFF)
    private val lightText = Color(0xFF1B1F24)
    private val darkSurface = Color(0xFF1A1D21)
    private val darkText = Color(0xFFE6E8EB)

    private fun light(style: CellStyle) = CellPaints.of(style, dark = false, surface = lightSurface, textPrimary = lightText)
    private fun dark(style: CellStyle) = CellPaints.of(style, dark = true, surface = darkSurface, textPrimary = darkText)

    @Test
    fun `라이트는 원본 배경·글자색 그대로, 거의 흰 배경은 배경 없음`() {
        val p = light(CellStyle(fill = 0xFFFFFF00.toInt(), fontColor = 0xFFC00000.toInt(), bold = true, align = CellStyle.Align.CENTER))
        assertEquals(0xFFFFFF00.toInt(), p.fill?.toArgb())
        assertEquals(0xFFC00000.toInt(), p.text.toArgb())
        assertTrue(p.bold)
        assertEquals(CellStyle.Align.CENTER, p.align)
        assertNull(light(CellStyle(fill = 0xFFFFFFFF.toInt())).fill)
    }

    @Test
    fun `다크는 밝은 배경을 색조를 살려 어둡게 하고 글자는 읽힌다`() {
        val lightBlue = 0xFFDDEBF7.toInt()
        val p = dark(CellStyle(fill = lightBlue))
        val fill = p.fill ?: return assertTrue("배경이 사라지면 안 된다", false)
        val (h0, _, _) = XlsxColor.toHsl(lightBlue)
        val (h1, _, l1) = XlsxColor.toHsl(fill.toArgb())
        assertEquals("색조 유지", h0, h1, 0.02)
        assertTrue("어두워야 한다: $l1", l1 < 0.25)
        assertTrue("테마 글자가 읽힌다", CellPaints.contrast(p.text, fill) >= 4.5)
    }

    @Test
    fun `대비가 모자란 원본 글자색은 색조를 두고 밝기만 옮긴다`() {
        // 다크 바탕의 빨간 음수 — 빨강은 남기고 읽히게 밝힌다.
        val red = 0xFFC00000.toInt()
        val p = dark(CellStyle(fontColor = red))
        assertEquals("색조 유지", XlsxColor.toHsl(red).first, XlsxColor.toHsl(p.text.toArgb()).first, 0.02)
        assertTrue(CellPaints.contrast(p.text, darkSurface) >= CellPaints.MIN_CONTRAST)
        // 회색 계열 짙은 글자도 밝혀서 읽힌다(테마 글자색으로 바꾸지 않는다).
        assertTrue(CellPaints.contrast(dark(CellStyle(fontColor = 0xFF1F3864.toInt())).text, darkSurface) >= CellPaints.MIN_CONTRAST)
        // 진한 원본 배경 + 글자색 지정 없음 + 라이트 — 어두운 테마 글자 대신 바탕색 글자.
        assertEquals(lightSurface, light(CellStyle(fill = 0xFF1F3864.toInt())).text)
    }
}
