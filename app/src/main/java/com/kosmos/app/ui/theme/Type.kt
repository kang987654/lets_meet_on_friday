package com.kosmos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.kosmos.app.R

/** 하다 라운드(SIL OFL 1.1, 고지 `assets/licenses/HadaRound-OFL.txt`) — 400/500/700 세 굵기만 싣는다(DESIGN.md v3). */
val HadaRound = FontFamily(
    Font(R.font.hada_round_regular, FontWeight.Normal),
    Font(R.font.hada_round_medium, FontWeight.Medium),
    Font(R.font.hada_round_bold, FontWeight.Bold),
)

/**
 * 글자 단계 — `docs/DESIGN.md` v3 Typography 의 7단계를 Material3 역할에 싣는다. 화면은 `MaterialTheme.typography.<역할>` 만 쓴다.
 *
 * | DESIGN 단계 | 역할 |
 * |---|---|
 * | number 28/32 Bold | displaySmall (display·headlineLarge 도 같은 값) |
 * | title 20/28 Bold | titleLarge (headlineMedium·headlineSmall 도) — 상단 바 제목 |
 * | subtitle 17/24 Medium | titleMedium |
 * | body 16/24 | bodyLarge |
 * | caption 14/20 | bodyMedium (bodySmall·titleSmall 도) |
 * | label 13/18 Medium | labelMedium (labelSmall 도) |
 * | button 15/20 Medium | labelLarge — M3 버튼의 기본 역할 |
 *
 * [WHY] 쓰지 않는 역할도 모두 채운다 — 비워 두면 M3 기본값(Roboto 크기표)이 남아 컴포넌트마다 크기가 제각각이 된다.
 */
private fun typographyOf(family: FontFamily): Typography {
    // [WHY] 한국어를 어절 단위로 줄바꿈한다 — 기본값은 음절 단위라 "있었/는데"처럼 낱말 중간에서 끊겼다(0.38.3 M6 점검 P1).
    // 어절 줄바꿈은 안드로이드 13+ 이 한국어 locale 에서만 하므로 locale 을 한국어로 고정한다(기기 언어가 영어여도 앱 글은 한국어). 13 미만은 음절 단위 그대로.
    fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(
            fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
            lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
            localeList = LocaleList("ko-KR")
        )
    val number = style(28, 32, FontWeight.Bold)
    val title = style(20, 28, FontWeight.Bold)
    val subtitle = style(17, 24, FontWeight.Medium)
    val body = style(16, 24, FontWeight.Normal)
    val caption = style(14, 20, FontWeight.Normal)
    val label = style(13, 18, FontWeight.Medium)
    val button = style(15, 20, FontWeight.Medium)
    return Typography(
        displayLarge = number, displayMedium = number, displaySmall = number,
        headlineLarge = number, headlineMedium = title, headlineSmall = title,
        titleLarge = title, titleMedium = subtitle, titleSmall = caption.copy(fontWeight = FontWeight.Medium),
        bodyLarge = body, bodyMedium = caption, bodySmall = caption,
        labelLarge = button, labelMedium = label, labelSmall = label,
    )
}

/** 앱 글꼴 모드(기본). */
val AppTypography = typographyOf(HadaRound)

/** 시스템 글꼴 모드 — 같은 단계, 기기에서 고른 글꼴. */
val SystemTypography = typographyOf(FontFamily.Default)

/**
 * 모든 역할의 크기·줄 높이를 [scale] 배 — 채팅 글자 크기 설정(0.38.2)이 채팅 목록 안에서만 쓴다. 1이면 그대로.
 */
fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    fun TextStyle.s() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = displayLarge.s(), displayMedium = displayMedium.s(), displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(), headlineMedium = headlineMedium.s(), headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(), titleMedium = titleMedium.s(), titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(), bodyMedium = bodyMedium.s(), bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(), labelMedium = labelMedium.s(), labelSmall = labelSmall.s(),
    )
}
