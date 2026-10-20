package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 앱 버튼 — 주 버튼(채도 낮춘 하늘 면 `brandSoft` + 진한 하늘 글자 굵게)과 보조 버튼(흰 면 + 테두리), 높이 48(DESIGN.md v3 Components).
 *
 * @param accent 주 버튼. 한 화면(시트)에 하나만 둔다. false 면 보조 버튼.
 * @param enabled false 면 누를 수 없고 가라앉은 면 + 흐린 글자가 된다.
 * @param progress 0~1 — 버튼 면이 왼쪽부터 차오른다(보조 버튼만, 옅은 하늘 — 회색은 '누를 수 없음'처럼 보였다). 매 프레임 바뀌므로 람다로 받아 그리기 단계에서만 읽는다.
 * @param danger 되돌릴 수 없는 동작(취소·삭제) — 보조 버튼 모양에 옅은 빨강 테두리 + 빨간 글자. 박스를 없애면 경계가 흐려져 더 불안해 보였다(0.38.2 M4).
 */
@Composable
fun KosmosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    progress: (() -> Float)? = null
) {
    val colors = KosmosTheme.colors
    val face = when {
        !enabled -> Modifier.background(colors.surfaceSunk, KosmosShapes.md)
        accent -> Modifier.background(colors.brandSoft, KosmosShapes.md)
        danger -> Modifier.background(colors.surface, KosmosShapes.md).border(1.dp, colors.dangerText.copy(alpha = 0.35f), KosmosShapes.md)
        else -> Modifier.background(colors.surface, KosmosShapes.md).border(1.dp, colors.border, KosmosShapes.md)
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(face)
            .then(
                if (progress == null) Modifier
                else Modifier.clip(KosmosShapes.md).drawBehind {
                    drawRect(colors.brand.copy(alpha = 0.18f), size = size.copy(width = size.width * progress().coerceIn(0f, 1f)))
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> colors.textMuted
                accent -> colors.onBrandSoft
                danger -> colors.dangerText
                else -> colors.textPrimary
            },
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
