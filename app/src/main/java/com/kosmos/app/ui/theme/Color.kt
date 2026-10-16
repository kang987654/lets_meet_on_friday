package com.kosmos.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * [KosmosColors]
 * 라이트/다크 테마에 따라 교체되는 시맨틱 색상 토큰 집합입니다. (ADR-005, `docs/DESIGN.md` v3 Colors)
 *
 * ### Architecture Context
 * - **Layer**: UI (Theme)
 * - **Dependencies**: 없음 (CompositionLocal로 하위 트리에 제공)
 *
 * ### Key Flow
 * 1. [KosmosTheme]이 현재 테마 모드에 맞는 인스턴스([DarkColors]/[LightColors])를 [LocalKosmosColors]로 제공합니다.
 * 2. 각 화면은 `KosmosTheme.colors.<토큰>`으로 접근하며, 하드코딩된 Color 리터럴을 사용하지 않습니다.
 *
 * [WHY] 면 색과 글자 색을 나눈다 — 하늘색 하나를 면·글자에 같이 쓰던 v2 는 흰 배경 위 글자 대비가 2:1 대였다(0.38.0 감사).
 * 글자로 쓰는 토큰(`text*`·`*Text`)은 모두 surface 위 4.5:1 이상이다(수치는 DESIGN.md 머리말).
 */
@Immutable
data class KosmosColors(
    // 표면
    val bg: Color,
    val surface: Color,
    /** 한 단계 가라앉은 면 — 분할 버튼·입력 바탕 */
    val surfaceSunk: Color,
    /** hairline */
    val border: Color,
    // 글자
    val textPrimary: Color,
    val textSecondary: Color,
    /** 날짜·태그·보조 라벨 */
    val textMuted: Color,
    // 브랜드
    /** 면 전용(주 버튼·선택 표시·진행 바·토글). 글자·아이콘 색으로 쓰지 않는다 */
    val brand: Color,
    /** 옅은 브랜드 면 — 사용자 말풍선·선택된 칩 */
    val brandContainer: Color,
    /** 사용자 말풍선 그라데이션의 끝 색 — [brandContainer] 와 같은 색상에서 조금 더 맑게(DESIGN.md "개성") */
    val brandContainerEnd: Color,
    /** [brand] 면 위 글자·아이콘 */
    val onBrand: Color,
    /** 링크·강조 글자·강조 아이콘 */
    val brandText: Color,
    // 상태 (글자·아이콘용, 면은 alpha 를 낮춰 쓴다)
    val dangerText: Color,
    val successText: Color,
    val warningText: Color,
    /** 시트·대화상자 뒤 */
    val scrim: Color,
    val isLight: Boolean
)

val LightColors = KosmosColors(
    bg = Color(0xFFF5FBFE),
    surface = Color(0xFFFFFFFF),
    surfaceSunk = Color(0xFFEBF1F4),
    border = Color(0xFFD9E6EC),
    textPrimary = Color(0xFF222729),
    textSecondary = Color(0xFF515659),
    textMuted = Color(0xFF676D6F),
    brand = Color(0xFF5BC2E7),
    brandContainer = Color(0xFFD7F4FF),
    brandContainerEnd = Color(0xFFBDEAFA),
    onBrand = Color(0xFF222729),
    brandText = Color(0xFF006E8B),
    dangerText = Color(0xFFBA3630),
    successText = Color(0xFF227C45),
    warningText = Color(0xFF9D6300),
    scrim = Color(0x7A111618),
    isLight = true
)

val DarkColors = KosmosColors(
    bg = Color(0xFF111618),
    surface = Color(0xFF1A1F21),
    surfaceSunk = Color(0xFF161A1C),
    border = Color(0xFF2F3436),
    textPrimary = Color(0xFFE6ECEF),
    textSecondary = Color(0xFFBFC5C8),
    textMuted = Color(0xFF93999C),
    brand = Color(0xFF5BC2E7),
    brandContainer = Color(0xFF133B49),
    brandContainerEnd = Color(0xFF184C5E),
    onBrand = Color(0xFF111618),
    brandText = Color(0xFF70CCEE),
    dangerText = Color(0xFFF7857D),
    successText = Color(0xFF65C98C),
    warningText = Color(0xFFEBB25F),
    scrim = Color(0x8F000000),
    isLight = false
)

val LocalKosmosColors = staticCompositionLocalOf { DarkColors }
