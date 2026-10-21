package com.kosmos.app.ui.component

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 입력란 색 — `OutlinedTextField(colors = kosmosTextFieldColors())`.
 *
 * [WHY] 색을 넘기지 않으면 포커스 라벨·커서·테두리가 M3 primary(= 하늘 면 `brand`)로 그려져 글자 대비가 약 2:1 이었다(0.38.3 M6 점검 P1). 글자 역할은 `brandText`.
 */
@Composable
fun kosmosTextFieldColors(): TextFieldColors {
    val c = KosmosTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.brandText,
        unfocusedBorderColor = c.border,
        focusedLabelColor = c.brandText,
        unfocusedLabelColor = c.textMuted,
        cursorColor = c.brandText,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary
    )
}
