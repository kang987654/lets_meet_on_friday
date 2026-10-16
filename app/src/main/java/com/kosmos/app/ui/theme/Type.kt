package com.kosmos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * | title 22/30 Bold | titleLarge (headlineMedium·headlineSmall 도) |
 * | subtitle 17/24 Medium | titleMedium |
 * | body 16/24 | bodyLarge |
 * | caption 14/20 | bodyMedium (bodySmall·titleSmall 도) |
 * | label 13/18 Medium | labelMedium (labelSmall 도) |
 * | button 15/20 Medium | labelLarge — M3 버튼의 기본 역할 |
 *
 * [WHY] 쓰지 않는 역할도 모두 채운다 — 비워 두면 M3 기본값(Roboto 크기표)이 남아 컴포넌트마다 크기가 제각각이 된다.
 */
private fun typographyOf(family: FontFamily): Typography {
    fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)
    val number = style(28, 32, FontWeight.Bold)
    val title = style(22, 30, FontWeight.Bold)
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
