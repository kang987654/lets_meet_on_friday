package com.kosmos.app.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 목록 묶음 — 흰 면 + hairline 상자 **하나** 안에 행을 담는다(DESIGN Layout "목록은 카드 더미가 아니라 행", 0.38.3 M0 시안 A).
 *
 * [WHY] 행마다 카드를 씌우면 1px 상자가 화면을 덮는다(taste "AI 티" — 상자 더미). 행 사이는 [KosmosListRow] 의 들여쓴 구분선(Apple·Intercom 행 문법).
 */
@Composable
fun KosmosListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth().kosmosSurface(KosmosShapes.md), content = content)
}

/**
 * 목록 행 — 앞 아이콘(선택) · 제목 · 보조 줄(날짜 등) + 태그(작은 면) · 끝 동작. 높이 ≥ 56, 끝 동작은 48dp.
 *
 * 보조 정보는 가운뎃점으로 잇지 않는다 — 날짜는 글자, 태그는 작은 면으로 나눈다(taste "AI 티" 목록, 0.38.2 M7b 와 같은 규칙).
 *
 * @param divider 아래에 들여쓴 구분선 — 묶음의 마지막 행은 false.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KosmosListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    tags: List<String> = emptyList(),
    @DrawableRes leadingIcon: Int? = null,
    titleMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = KosmosTheme.colors
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.let { KosmosIcon(it, contentDescription = null, size = IconSize.inline, tint = colors.textSecondary, modifier = Modifier.padding(end = 12.dp)) }
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
                if (supporting != null || tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.align(Alignment.CenterVertically)) }
                        tags.forEach { Tag(it) }
                    }
                }
            }
            trailing?.invoke()
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (leadingIcon != null) 48.dp else 16.dp), color = colors.border)
    }
}

/** 행 안의 태그 — 가라앉은 작은 면(모서리 6). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val colors = KosmosTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
        maxLines = 1,
        modifier = modifier.background(colors.surfaceSunk, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/**
 * 섹션 제목 — `subtitle` 단계 문장체(자간 넓힌 작은 대문자 eyebrow 금지, Intercom "sentence case"·taste), 오른쪽에 글자 버튼 하나(선택).
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = KosmosTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = colors.brandText) }
        }
    }
}

/**
 * 빈 화면 — 아이콘 32 + 안내 + 행동 버튼(선택)(DESIGN "빈 화면", ui-ux-pro-max `empty-states`: 안내와 행동을 함께).
 */
@Composable
fun EmptyState(@DrawableRes icon: Int, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val colors = KosmosTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KosmosIcon(icon, contentDescription = null, size = IconSize.large, tint = colors.textMuted)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) KosmosButton(actionLabel, onAction, Modifier.widthIn(min = 160.dp))
    }
}

/**
 * 행 끝 삭제 버튼 — 옅은 휴지통(48dp). 빨간 ✕ 가 화면에서 가장 눈에 띄는 색이었다(impeccable 0.38.2 P1). 지운 뒤에는 [showUndo] 로 되돌릴 기회를 준다.
 */
@Composable
fun DeleteIconButton(onClick: () -> Unit, contentDescription: String = "삭제") {
    IconButton(onClick = onClick) {
        KosmosIcon(R.drawable.ic_ph_trash, contentDescription = contentDescription, size = IconSize.inline, tint = KosmosTheme.colors.textMuted)
    }
}

/**
 * 지운 뒤 "○○을 지웠어요 · 되돌리기" 스낵바 — 되돌리기를 누르면 [onUndo](같은 객체를 다시 저장).
 * 확인 창 대신 쓴다(사용자 결정 2026-10-20, ui-ux-pro-max `undo-support`). 일괄 삭제·초기화는 확인 창을 유지한다.
 */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: () -> Unit) {
    val result = showSnackbar(message = message, actionLabel = "되돌리기", withDismissAction = false, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}
