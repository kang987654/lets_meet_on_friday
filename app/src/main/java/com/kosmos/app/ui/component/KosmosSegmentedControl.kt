package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import com.kosmos.app.ui.theme.Motion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
 * [KosmosSegmentedControl]
 * 하나를 고르는 분할 버튼 — 가라앉은 트랙(`surfaceSunk`) 위에 선택 칸만 흰 면 + 테두리(DESIGN.md v3 Components).
 *
 * ### Architecture Context
 * - **Layer**: UI (Component)
 * - **Dependencies**: [KosmosTheme], [KosmosShapes]
 *
 * [WHY] 설정의 테마·글꼴·응답 스타일, 일정의 월/목록·오늘/이번 주, 메모의 메모/할 일이 같은 컨트롤을 쓴다 —
 * 화면마다 모서리·여백·글자 크기를 따로 주던 인자는 없앴다(모양 하나).
 */
@Composable
fun <T> KosmosSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KosmosTheme.colors
    val reduced = rememberReducedMotion()
    // [WHY] 선택 칸(흰 면)을 칸마다 따로 그리면 바뀔 때 뚝 옮겨 간다 — 한 장을 미끄러뜨린다(0.38.4 M4, 사용자 확인). 처음 그릴 때는 제자리.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSunk, KosmosShapes.md)
            .padding(4.dp)
            .selectableGroup()
    ) {
        val gap = 4.dp
        val cell = (maxWidth - gap * (options.size - 1)) / options.size
        val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val x by animateDpAsState((cell + gap) * index, if (reduced) snap() else Motion.medium(), label = "segment")
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset(x = x).width(cell).fillMaxHeight()
                    .background(colors.surface, KosmosShapes.sm).border(1.dp, colors.border, KosmosShapes.sm)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(KosmosShapes.sm)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) colors.brandText else colors.textSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
