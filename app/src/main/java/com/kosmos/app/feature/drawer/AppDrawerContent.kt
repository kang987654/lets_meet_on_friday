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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.feature.episode.EpisodeSheet
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 드로어 내용물 (시안 A′): 기억 검색바 + 에피소드 아카이브 + 하단 타일.
 *
 * ### Architecture Context
 * - **Layer**: Feature (Drawer)
 * - **Dependencies**: [DrawerViewModel], [EpisodeSheet]
 *
 * [WHY] 목록은 사용자가 만든 세션이 아니라 **시스템이 자동 분절·요약한 에피소드 문서**다
 * (ADR-022) — 관리 대상이 아니라 열람 대상. 프로필 고정 카드는 3층 기억 모델(C′) 배선 시
 * 검색바 위에 온다 — 데이터 소스가 아직 없어 자리만 비워 둔다.
 */
@Composable
fun AppDrawerContent(
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onJumpToTimeline: (startAt: Long) -> Unit = {},
    viewModel: DrawerViewModel = hiltViewModel()
) {
    val episodes = viewModel.episodePaging.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var openEpisodeId by remember { mutableStateOf<String?>(null) }

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
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // 기억 검색 — SearchMemory 툴과 같은 저장소·같은 방식 (DrawerViewModel [WHY]).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔍", fontSize = 13.sp)
            Spacer(modifier = Modifier.padding(start = 8.dp))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                singleLine = true,
                textStyle = TextStyle(
                    color = KosmosTheme.colors.textPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(KosmosTheme.colors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "기억 검색…",
                            color = KosmosTheme.colors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchResults != null) "검색 결과" else "기억 아카이브",
            style = MaterialTheme.typography.labelSmall,
            color = KosmosTheme.colors.textMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val results = searchResults
            if (results != null) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = "일치하는 기억이 없어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KosmosTheme.colors.textMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                items(results, key = { it.id }) { episode ->
                    EpisodeCard(episode) { openEpisodeId = episode.id }
                }
            } else {
                if (episodes.itemCount == 0) {
                    item {
                        Text(
                            text = "대화가 쌓이면 주제별로 자동 정리돼 여기 보여요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KosmosTheme.colors.textMuted,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                items(count = episodes.itemCount, key = episodes.itemKey { it.id }) { index ->
                    val episode = episodes[index] ?: return@items
                    EpisodeCard(episode) { openEpisodeId = episode.id }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        DrawerListItem(icon = "🧠", label = "메모 · 할 일", onClick = onNavigateToMemory)
        Spacer(modifier = Modifier.height(12.dp))

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

    openEpisodeId?.let { id ->
        EpisodeSheet(
            episodeId = id,
            onDismiss = { openEpisodeId = null },
            onJumpToTimeline = onJumpToTimeline
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = episode.title ?: "(제목 없음)",
            style = MaterialTheme.typography.bodyMedium,
            color = KosmosTheme.colors.textPrimary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        val date = java.time.Instant.ofEpochMilli(episode.startAt)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN))
        Text(
            text = "$date · ${episode.messageCount}개 대화" +
                if (episode.tags.isEmpty()) "" else " · ${episode.tags.take(2).joinToString(", ")}",
            style = MaterialTheme.typography.labelSmall,
            color = KosmosTheme.colors.textMuted,
            maxLines = 1
        )
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
