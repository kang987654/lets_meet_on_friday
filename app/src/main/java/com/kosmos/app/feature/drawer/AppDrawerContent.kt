package com.kosmos.app.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.feature.episode.EpisodeSheet
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 드로어 내용물: 제목 + 프로필 버튼, 기억 검색, 기억(에피소드) 목록, 메뉴 다섯 줄(0.38.3 M1 — 상자 없는 드로어).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Drawer)
 * - **Dependencies**: [DrawerViewModel], [EpisodeSheet]
 *
 * [WHY] 목록은 사용자가 만든 세션이 아니라 **시스템이 자동 분절·요약한 에피소드 문서**다
 * (ADR-022) — 관리 대상이 아니라 열람 대상. 프로필 고정 카드(A′-2)는 검색바 위 — 상시 주입
 * 기억(C′1)의 열람·편집 진입점이다.
 */
@Composable
fun AppDrawerContent(
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onJumpToTimeline: (startAt: Long) -> Unit = {},
    viewModel: DrawerViewModel = hiltViewModel()
) {
    val episodes = viewModel.episodePaging.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val profileEntries by viewModel.profileEntries.collectAsStateWithLifecycle()
    val pendingSuggestionCount by viewModel.pendingSuggestionCount.collectAsStateWithLifecycle()
    var openEpisodeId by remember { mutableStateOf<String?>(null) }
    var showProfileSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        // [WHY] 상자 없는 드로어(0.38.3 M1 시안 A, 사용자 선택) — 프로필·검색·메뉴가 모두 테두리 상자라 상자가 9개였다(taste "상자 더미").
        // 프로필은 제목 옆 아이콘으로, 대기 제안이 있으면 점으로 알린다(종결은 채팅의 제안 시트).
        Row(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "KOSMOS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = KosmosTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            ProfileButton(hasSuggestions = pendingSuggestionCount > 0, onClick = { showProfileSheet = true })
        }

        // 기억 검색 — SearchMemory 툴과 같은 저장소·같은 방식 (DrawerViewModel [WHY]).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                // [WHY] 알약 모양은 채팅 입력바만(DESIGN Shape) — 검색칸은 다른 입력처럼 md(0.38.6 점검 P3).
                .background(KosmosTheme.colors.surfaceSunk, com.kosmos.app.ui.theme.KosmosShapes.md)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_magnifying_glass, contentDescription = null, size = 18.dp, tint = KosmosTheme.colors.textMuted)
            Spacer(modifier = Modifier.padding(start = 8.dp))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = KosmosTheme.colors.textPrimary),
                cursorBrush = SolidColor(KosmosTheme.colors.brand),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "지난 대화 검색",
                            color = KosmosTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        com.kosmos.app.ui.component.SectionHeader(
            title = if (searchResults != null) "검색 결과" else "지난 대화",
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            val results = searchResults
            if (results != null) {
                if (results.isEmpty()) {
                    item { DrawerEmptyLine("일치하는 대화가 없어요.") }
                }
                items(results.size, key = { results[it].id }) { index ->
                    EpisodeRow(results[index], divider = index < results.lastIndex) { openEpisodeId = results[index].id }
                }
            } else {
                if (episodes.itemCount == 0) {
                    item { DrawerEmptyLine("대화가 쌓이면 주제별로 자동 정리돼 여기 보여요.") }
                }
                items(count = episodes.itemCount, key = episodes.itemKey { it.id }) { index ->
                    val episode = episodes[index] ?: return@items
                    EpisodeRow(episode, divider = index < episodes.itemCount - 1) { openEpisodeId = episode.id }
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(color = KosmosTheme.colors.border)
        Spacer(modifier = Modifier.height(8.dp))
        // [WHY] 메뉴는 아이콘 + 글자 한 줄(M3 내비게이션 드로어 항목, mobile-android-design) — 타일 3개와 목록 2줄로 나뉘어 있던 것을 한 목록으로.
        DrawerNavItem(com.kosmos.app.R.drawable.ic_ph_calendar_blank, "일정", onNavigateToCalendar)
        DrawerNavItem(com.kosmos.app.R.drawable.ic_ph_note, "메모·할 일", onNavigateToMemory)
        DrawerNavItem(com.kosmos.app.R.drawable.ic_ph_file_text, "문서", onNavigateToDocuments)
        DrawerNavItem(com.kosmos.app.R.drawable.ic_ph_shield_check, "활동 기록", onNavigateToAudit)
        DrawerNavItem(com.kosmos.app.R.drawable.ic_ph_gear_six, "설정", onNavigateToSettings)
    }

    openEpisodeId?.let { id ->
        EpisodeSheet(
            episodeId = id,
            onDismiss = { openEpisodeId = null },
            onJumpToTimeline = onJumpToTimeline,
            onChanged = {
                episodes.refresh()
                viewModel.onEpisodesChanged()
            }
        )
    }

    if (showProfileSheet) {
        com.kosmos.app.feature.profile.ProfileSheet(onDismiss = { showProfileSheet = false })
    }
}

/** 제목 옆 프로필 버튼 — 대기 중인 프로필 제안이 있으면 하늘색 점. */
@Composable
private fun ProfileButton(hasSuggestions: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        androidx.compose.foundation.layout.Box {
            com.kosmos.app.ui.component.KosmosIcon(
                com.kosmos.app.R.drawable.ic_ph_user_circle,
                contentDescription = if (hasSuggestions) "프로필, 새 제안 있음" else "프로필",
                tint = KosmosTheme.colors.textSecondary
            )
            if (hasSuggestions) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(KosmosTheme.colors.brand, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, divider: Boolean, onClick: () -> Unit) {
    com.kosmos.app.ui.component.KosmosListRow(
        title = episode.title ?: "제목 없음",
        supporting = com.kosmos.app.domain.util.IsoDateTimeParser.monthDayKorean(episode.startAt),
        tags = episode.tags.take(2),
        titleMaxLines = 1,
        onClick = onClick,
        divider = divider
    )
}

@Composable
private fun DrawerEmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = KosmosTheme.colors.textMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 메뉴 한 줄 — 높이 48, 아이콘 20 + 글자(M3 내비게이션 드로어 항목). */
@Composable
private fun DrawerNavItem(@androidx.annotation.DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(99.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.kosmos.app.ui.component.KosmosIcon(icon, contentDescription = null, size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textSecondary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = KosmosTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
