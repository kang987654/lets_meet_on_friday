package com.kosmos.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSunk, KosmosShapes.md)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val cell = if (isSelected) {
                Modifier.background(colors.surface, KosmosShapes.sm).border(1.dp, colors.border, KosmosShapes.sm)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(cell)
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
