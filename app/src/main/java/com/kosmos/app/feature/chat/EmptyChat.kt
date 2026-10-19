package com.kosmos.app.feature.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.kosmosSurface
import com.kosmos.app.ui.component.sparkle
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 대화 기록이 하나도 없을 때의 첫 화면(0.38.2 M7b, 처음 쓸 때·초기화 후 — 타임라인은 세션과 무관하다, ADR-022) — 작은 별자리 + 인사 + 눌러서 바로 시작하는 예시 세 개(알림·일정·기억).
 *
 * [WHY] 처음 열면 아무것도 없는 빈 화면이었다 — 무엇을 맡길 수 있는지 보여 주는 자리로 쓴다. 예시는 누르면 그 글자로 한 턴(답 칩과 같은 동작).
 */
@Composable
fun EmptyChat(onStarter: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = KosmosTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(Modifier.size(width = 120.dp, height = 64.dp)) {
            val points = CONSTELLATION.map { Offset(it.x * size.width, it.y * size.height) }
            points.zipWithNext().forEach { (a, b) -> drawLine(colors.brand.copy(alpha = 0.3f), a, b, strokeWidth = 1.dp.toPx()) }
            STAR_RADII.forEachIndexed { i, r -> sparkle(points[i], r.dp.toPx(), colors.brand.copy(alpha = if (i == BRIGHTEST) 1f else 0.7f)) }
        }
        Text("오늘은 무엇을 맡길까요?", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(
            "알림·일정·기억을 휴대폰 안에서만 처리해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            STARTERS.forEach { (icon, text) -> Starter(icon, text) { onStarter(text) } }
        }
    }
}

@Composable
private fun Starter(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    val colors = KosmosTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .kosmosSurface(shape = RoundedCornerShape(99.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        KosmosIcon(icon, contentDescription = null, size = 18.dp, tint = colors.brandText, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

private val CONSTELLATION = listOf(Offset(0.08f, 0.70f), Offset(0.32f, 0.30f), Offset(0.55f, 0.55f), Offset(0.78f, 0.18f), Offset(0.94f, 0.62f))
private val STAR_RADII = listOf(4f, 6f, 9f, 5f, 4f)
private const val BRIGHTEST = 2

private val STARTERS = listOf(
    R.drawable.ic_ph_bell to "30분 뒤에 물 마시라고 알려줘",
    R.drawable.ic_ph_calendar_blank to "오늘 일정 알려줘",
    R.drawable.ic_ph_clock_counter_clockwise to "지난번에 기억해 둔 거 찾아줘"
)
