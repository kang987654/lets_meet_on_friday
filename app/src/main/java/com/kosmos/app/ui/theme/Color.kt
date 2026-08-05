package com.kosmos.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * [KosmosColors]
 * 라이트/다크 테마에 따라 교체되는 시맨틱 색상 토큰 집합입니다. (ADR-005)
 *
 * ### Architecture Context
 * - **Layer**: UI (Theme)
 * - **Dependencies**: 없음 (CompositionLocal로 하위 트리에 제공)
 *
 * ### Key Flow
 * 1. [KosmosTheme]이 현재 테마 모드에 맞는 인스턴스([DarkColors]/[LightColors])를 [LocalKosmosColors]로 제공합니다.
 * 2. 각 화면은 `KosmosTheme.colors.<토큰>`으로 접근하며, 하드코딩된 Color 리터럴을 사용하지 않습니다.
 */
@Immutable
data class KosmosColors(
    // Surfaces
    val bg: Color,
    val surface: Color,
    val glass: Color,
    val glassMid: Color,
    val glassHigh: Color,
    val border: Color,
    val borderHigh: Color,
    // Accents
    val accent: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val accentAlt: Color,
    val accentAltDim: Color,
    /** accent 배경 위에 올리는 전경색(텍스트·아이콘) — 대비 확보용 */
    val onAccent: Color,
    // Status
    val success: Color,
    val successDim: Color,
    val danger: Color,
    val dangerDim: Color,
    val warning: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // 배경 오로라 연출 강도 — 라이트 테마에서는 과한 채도를 낮춘다
    val auroraAlpha: Float,
    val isLight: Boolean
)

/** 다크 Glassmorphism 팔레트 (기존 v0.4.0 구현 계승) */
val DarkColors = KosmosColors(
    bg = Color(0xFF040D1F),
    surface = Color(0xFF071526),
    glass = Color(0x0DFFFFFF),      // white ~0.05
    glassMid = Color(0x14FFFFFF),   // white ~0.08
    glassHigh = Color(0x1EFFFFFF),  // white ~0.12
    border = Color(0x14FFFFFF),     // white ~0.08
    borderHigh = Color(0x26FFFFFF), // white ~0.15
    accent = Color(0xFF22D3EE),
    accentDim = Color(0x2622D3EE),
    accentGlow = Color(0x4D22D3EE),
    accentAlt = Color(0xFF818CF8),
    accentAltDim = Color(0x26818CF8),
    onAccent = Color(0xFF040D1F), // 밝은 cyan 위 어두운 전경

    success = Color(0xFF34D399),
    successDim = Color(0x2634D399),
    danger = Color(0xFFF87171),
    dangerDim = Color(0x26F87171),
    warning = Color(0xFFFBBF24),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF475569),
    auroraAlpha = 1.0f,
    isLight = false
)

/**
 * 라이트 팔레트 — `docs/DESIGN.md` v1.2의 Sky Blue(#5BC2E7) 기획을 Glassmorphism 구조에 적용.
 * [WHY] 라이트에서는 흰색 반투명 대신 카드 표면을 불투명에 가깝게 올리고 hairline 테두리로
 * 계층을 만든다 — 밝은 캔버스 위 white-on-white는 경계가 사라지기 때문이다.
 */
val LightColors = KosmosColors(
    bg = Color(0xFFF7FBFD),         // canvas
    surface = Color(0xFFFFFFFF),    // surface-card
    glass = Color(0xCCFFFFFF),      // white ~0.80
    glassMid = Color(0xE6FFFFFF),   // white ~0.90
    glassHigh = Color(0xFFFFFFFF),
    border = Color(0xFFD9E6EC),     // hairline
    borderHigh = Color(0xFFBFD6E0),
    accent = Color(0xFF39AED8),     // primary-strong (밝은 배경 대비 확보)
    accentDim = Color(0x1F5BC2E7),
    accentGlow = Color(0x3D5BC2E7),
    accentAlt = Color(0xFF6366F1),
    accentAltDim = Color(0x1F6366F1),
    onAccent = Color(0xFFFFFFFF), // DESIGN.md on-primary

    success = Color(0xFF2FA36B),
    successDim = Color(0x1F2FA36B),
    danger = Color(0xFFE35D5D),
    dangerDim = Color(0x1FE35D5D),
    warning = Color(0xFFF4A62A),
    textPrimary = Color(0xFF1F2A33),   // ink
    textSecondary = Color(0xFF42515C), // body
    textMuted = Color(0xFF758390),     // muted
    auroraAlpha = 0.35f,
    isLight = true
)

val LocalKosmosColors = staticCompositionLocalOf { DarkColors }
