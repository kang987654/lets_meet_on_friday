package com.kosmos.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 글자 단계(DESIGN.md v3 Typography)와 글꼴 모드의 계약.
 */
class TypographyTest {

    private fun roles(t: androidx.compose.material3.Typography): List<TextStyle> = listOf(
        t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
        t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
        t.labelLarge, t.labelMedium, t.labelSmall,
    )

    @Test
    fun `모든 역할이 DESIGN 7단계 중 하나다 — M3 기본 크기표가 남지 않는다`() {
        val steps = setOf(28 to 32, 20 to 28, 17 to 24, 16 to 24, 14 to 20, 13 to 18, 15 to 20)
        for (style in roles(AppTypography)) {
            val step = style.fontSize.value.toInt() to style.lineHeight.value.toInt()
            assert(step in steps) { "단계 밖 크기 $step" }
        }
    }

    @Test
    fun `앱 글꼴은 하다 라운드, 시스템 글꼴은 기기 글꼴이고 크기·굵기는 같다`() {
        roles(AppTypography).forEach { assertEquals(HadaRound, it.fontFamily) }
        roles(SystemTypography).forEach { assertEquals(FontFamily.Default, it.fontFamily) }
        roles(AppTypography).zip(roles(SystemTypography)).forEach { (app, system) ->
            assertEquals(app.fontSize, system.fontSize)
            assertEquals(app.lineHeight, system.lineHeight)
            assertEquals(app.fontWeight, system.fontWeight)
        }
    }

    @Test
    fun `본문과 버튼 단계`() {
        assertEquals(16.sp, AppTypography.bodyLarge.fontSize)
        assertEquals(FontWeight.Normal, AppTypography.bodyLarge.fontWeight)
        assertEquals(15.sp, AppTypography.labelLarge.fontSize)
        assertEquals(FontWeight.Medium, AppTypography.labelLarge.fontWeight)
    }

    @Test
    fun `글꼴 모드 키 — 저장값이 없거나 모르면 앱 글꼴`() {
        assertEquals(FontMode.APP, FontMode.fromKey(null))
        assertEquals(FontMode.APP, FontMode.fromKey("WHATEVER"))
        FontMode.entries.forEach { assertEquals(it, FontMode.fromKey(it.key)) }
    }

    @Test
    fun `채팅 글자 크기 — 배율만큼 모든 역할이 커지고 글꼴·굵기는 그대로, 모르는 키는 기본`() {
        val large = AppTypography.scaled(ChatTextSize.XLARGE.scale)
        assertEquals(20.sp, large.bodyLarge.fontSize)
        assertEquals(30.sp, large.bodyLarge.lineHeight)
        assertEquals(AppTypography.bodyLarge.fontFamily, large.bodyLarge.fontFamily)
        assertEquals(AppTypography.titleLarge.fontWeight, large.titleLarge.fontWeight)
        assertEquals(AppTypography, AppTypography.scaled(ChatTextSize.DEFAULT.scale))
        assertEquals(ChatTextSize.DEFAULT, ChatTextSize.fromKey(null))
        assertEquals(ChatTextSize.DEFAULT, ChatTextSize.fromKey("HUGE"))
        assertEquals(listOf(14, 16, 18, 20), ChatTextSize.entries.map { it.bodySp })
    }
}
