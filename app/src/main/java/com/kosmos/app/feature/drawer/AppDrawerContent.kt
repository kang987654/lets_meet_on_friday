package com.kosmos.app.feature.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 드로어 내용물 (시안 A′).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Drawer)
 *
 * M2-2 골격: 브랜드 헤더 + 기억 아카이브 자리 + 기억 관리 항목 + 하단 타일 3개.
 * 검색바·에피소드 목록·프로필 카드는 M2-4 에서 채운다 — 백엔드(M1)가 이미 있으므로
 * 이 골격은 배선만 남은 상태다.
 */
@Composable
fun AppDrawerContent(
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = "KOSMOS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = KosmosTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        // M2-4: 검색바 + 프로필 카드 + 에피소드 아카이브(Paging)가 이 자리에 온다.
        Text(
            text = "기억 아카이브",
            style = MaterialTheme.typography.labelSmall,
            color = KosmosTheme.colors.textMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Text(
            text = "대화가 쌓이면 주제별로 자동 정리돼 여기 보여요.",
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        DrawerListItem(icon = "🧠", label = "메모 · 할 일", onClick = onNavigateToMemory)

        Spacer(modifier = Modifier.weight(1f))

        // [WHY] 아이콘 크게(20sp)·라벨 작게 — 시안 A′ 검토에서의 사용자 결정 (2026-08-15).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerTile(icon = "📅", label = "캘린더", onClick = onNavigateToCalendar, modifier = Modifier.weight(1f))
            DrawerTile(icon = "🛡", label = "활동 기록", onClick = onNavigateToAudit, modifier = Modifier.weight(1f))
            DrawerTile(icon = "⚙", label = "설정", onClick = onNavigateToSettings, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DrawerListItem(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = KosmosTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
private fun DrawerTile(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassEffect(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = KosmosTheme.colors.textMuted
        )
    }
}
