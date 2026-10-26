package com.kosmos.app.feature.document

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kosmos.app.domain.document.CellStyle
import com.kosmos.app.domain.document.XlsxColor

/**
 * 화면에 그릴 셀 서식 — 배경(없으면 null), 글자색, 굵기, 정렬.
 */
internal data class CellPaint(val fill: Color?, val text: Color, val bold: Boolean, val align: CellStyle.Align?)

/**
 * [CellPaint] 를 만든다 — 원본 엑셀 색을 테마에 맞춘다(0.38.7 결정 2).
 *
 * ### Key Flow
 * 1. 거의 흰 배경은 배경 없음(표 바탕과 같다 — 다크에서 회색 띠가 되지 않게).
 * 2. 다크 모드면 배경의 색조는 살리고 밝기를 낮춘다([darkFill]).
 * 3. 원본 글자색이 배경과 대비 [MIN_CONTRAST] 미만이면 색조를 두고 밝기만 옮긴다([ADJUSTED_CONTRAST] 까지). 그래도 안 되거나 글자색이
 *    없으면 테마 글자색, 그것도 모자라면(진한 원본 배경 + 라이트) 바탕색을 글자에 쓴다.
 *
 * [WHY] 원본 색은 흰 바탕 기준이라 그대로 두면 다크에서 눈부신 띠와 사라지는 글자가 된다 — 어느 칸이 강조됐는지는 남기고 읽을 수 있게 한다.
 */
internal object CellPaints {
    /** 원본 작성자 색을 되도록 살리는 낮은 기준 — 본문 대비 4.5 보다 느슨하다(서식은 원본의 뜻이다). */
    const val MIN_CONTRAST = 3.0

    /** 밝기를 옮겨야 했던 글자는 본문 기준까지 — 대비 3 근처에서 멈추면 다크에서 어둑했다(카탈로그 36 시안). */
    const val ADJUSTED_CONTRAST = 4.5

    fun of(style: CellStyle?, dark: Boolean, surface: Color, textPrimary: Color): CellPaint {
        val fill = style?.fill?.takeUnless(::isNearWhite)?.let { if (dark) darkFill(it) else it }?.let(::Color)
        val background = fill ?: surface
        val original = style?.fontColor?.let { readable(it, background) }
        val text = original ?: if (contrast(textPrimary, background) >= MIN_CONTRAST) textPrimary else surface
        return CellPaint(fill, text, style?.bold == true, style?.align)
    }

    /**
     * 원본 글자색을 배경에서 읽히게 — 대비가 모자라면 색조는 두고 밝기만 배경 반대쪽으로 옮긴다. 끝까지 모자라면 null(테마 글자색).
     *
     * [WHY] 다크에서 빨간 음수(C00000)를 테마 글자색으로 바꾸면 "음수는 빨강"이라는 원본의 뜻이 사라졌다(카탈로그 36 시안).
     */
    private fun readable(argb: Int, background: Color): Color? {
        val color = Color(argb)
        if (contrast(color, background) >= MIN_CONTRAST) return color
        val (h, s, l) = XlsxColor.toHsl(argb)
        val lighter = background.luminance() < 0.5f
        return (1..8).asSequence()
            .map { step -> if (lighter) l + (1 - l) * step / 8 else l * (1 - step / 8.0) }
            .map { Color(XlsxColor.fromHsl(h, s, it)) }
            .firstOrNull { contrast(it, background) >= ADJUSTED_CONTRAST }
    }

    /** 다크 모드 배경 — 색조 유지, 채도 0.7배, 밝은 원본일수록 어둡게(0.14~0.44). */
    fun darkFill(argb: Int): Int {
        val (h, s, l) = XlsxColor.toHsl(argb)
        return XlsxColor.fromHsl(h, s * 0.7, 0.14 + (1 - l) * 0.3)
    }

    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun isNearWhite(argb: Int): Boolean {
        val (_, s, l) = XlsxColor.toHsl(argb)
        return l > 0.96 || (l > 0.92 && s < 0.08)
    }
}
