package com.kosmos.app.feature.cleanup

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import com.kosmos.app.R
import com.kosmos.app.ui.component.KosmosButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.assistant.cleanup.MemoryCleanupRunner
import com.kosmos.app.domain.cleanup.EpisodeMergeProposal
import com.kosmos.app.domain.cleanup.MergeProposal
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.KosmosListGroup
import com.kosmos.app.ui.component.KosmosTopBar
import com.kosmos.app.ui.component.SectionHeader

/**
 * 기억 정리 화면 (0.30.0) — 시작 → 진행률(취소) → 회고 + 병합 제안(체크 기본 해제) → 적용 결과.
 *
 * [WHY] 상단은 TopAppBar — 슬롯 자신이 상태바 인셋을 처리한다(AGENTS §2-③, ModelManagementScreen 과 같은 셸).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCleanupScreen(
    viewModel: MemoryCleanupViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val checkedEpisodes by viewModel.checkedEpisodes.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            KosmosTopBar(title = stringResource(R.string.cleanup_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                MemoryCleanupRunner.State.Idle -> Intro(onStart = viewModel::start)
                MemoryCleanupRunner.State.Waiting -> Progress(
                    stringResource(R.string.cleanup_waiting_auto),
                    null,
                    viewModel::cancel
                )
                is MemoryCleanupRunner.State.Running -> Progress(
                    label = when (s.step) {
                        MemoryCleanupRunner.Step.WEEKLY_REVIEW -> stringResource(R.string.cleanup_running_review)
                        MemoryCleanupRunner.Step.MERGE -> if (s.total > 0) {
                            stringResource(R.string.cleanup_running_merge_progress, s.done, s.total)
                        } else {
                            stringResource(R.string.cleanup_running_merge)
                        }
                        MemoryCleanupRunner.Step.EPISODE_MERGE -> if (s.total > 0) {
                            stringResource(R.string.cleanup_running_episode_merge_progress, s.done, s.total)
                        } else {
                            stringResource(R.string.cleanup_running_episode_merge)
                        }
                    },
                    fraction = if (s.step != MemoryCleanupRunner.Step.WEEKLY_REVIEW && s.total > 0) s.done.toFloat() / s.total else null,
                    onCancel = viewModel::cancel
                )
                is MemoryCleanupRunner.State.Blocked -> Notice(s.reason, stringResource(R.string.common_retry), viewModel::start)
                MemoryCleanupRunner.State.Applying -> Progress(
                    stringResource(R.string.cleanup_running_apply),
                    null,
                    onCancel = null
                )
                is MemoryCleanupRunner.State.Review -> ReviewContent(
                    review = s.weeklyReview,
                    proposals = s.proposals,
                    checked = checked,
                    onToggle = viewModel::toggle,
                    episodeProposals = s.episodeProposals,
                    checkedEpisodes = checkedEpisodes,
                    onToggleEpisode = viewModel::toggleEpisode,
                    onApply = viewModel::applyChecked
                )
                is MemoryCleanupRunner.State.Done -> {
                    val reviewMsg = stringResource(
                        if (s.weeklyReview != null) R.string.cleanup_done_review_saved else R.string.cleanup_done_review_empty
                    )
                    val mergedMsg = if (s.merged > 0) {
                        stringResource(R.string.cleanup_done_merged_count, s.merged)
                    } else {
                        stringResource(R.string.cleanup_done_merged_none)
                    }
                    val episodesMergedMsg = if (s.episodesMerged > 0) {
                        stringResource(R.string.cleanup_done_episodes_merged, s.episodesMerged)
                    } else ""
                    // [WHY] 체크했는데 안 합쳐진 이유를 숨기지 않는다 — 재요약이 두 주제로 갈리면 합치지 않는다(exp44 갈린 점).
                    val episodesSkippedMsg = if (s.episodesSkipped > 0) {
                        stringResource(R.string.cleanup_done_episodes_skipped, s.episodesSkipped)
                    } else ""
                    Notice(
                        message = reviewMsg + mergedMsg + episodesMergedMsg + episodesSkippedMsg,
                        action = stringResource(R.string.common_close),
                        onAction = {
                            viewModel.finish()
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Card {
        Text(
            stringResource(R.string.cleanup_idle_body),
            color = KosmosTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    KosmosButton(stringResource(R.string.cleanup_start_button), onStart, Modifier.fillMaxWidth(), accent = true)
}

@Composable
private fun Progress(label: String, fraction: Float?, onCancel: (() -> Unit)?) {
    Card {
        Text(label, color = KosmosTheme.colors.textPrimary, modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
        Spacer(modifier = Modifier.height(12.dp))
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = KosmosTheme.colors.brand)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = KosmosTheme.colors.brand)
        }
    }
    // [WHY] 적용 중에는 취소가 없다 — 노트 병합·대화 통합이 반쯤 된 상태로 끊기지 않게(각 건은 트랜잭션이지만 묶음은 아니다).
    if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = KosmosTheme.colors.textMuted) }
}

@Composable
private fun Notice(message: String, action: String, onAction: () -> Unit) {
    Card { Text(message, color = KosmosTheme.colors.textPrimary) }
    KosmosButton(action, onAction, Modifier.fillMaxWidth(), accent = true)
}

@Composable
private fun ReviewContent(
    review: KnowledgeNote?,
    proposals: List<MergeProposal>,
    checked: Set<String>,
    onToggle: (MergeProposal) -> Unit,
    episodeProposals: List<EpisodeMergeProposal>,
    checkedEpisodes: Set<String>,
    onToggleEpisode: (EpisodeMergeProposal) -> Unit,
    onApply: () -> Unit
) {
    val lang = currentAppLanguage()
    // [WHY] 섹션 제목은 subtitle 단계 문장체, 제안은 한 묶음 안의 행(0.38.3 M2) — 제안마다 카드였고 제목은 작은 회색 글씨였다.
    SectionHeader(stringResource(R.string.cleanup_section_weekly_review))
    Card {
        Text(
            review?.content ?: stringResource(R.string.cleanup_empty_weekly_review),
            color = if (review != null) KosmosTheme.colors.textPrimary else KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    SectionHeader(stringResource(R.string.cleanup_section_note_merges))
    if (proposals.isEmpty()) {
        Text(stringResource(R.string.cleanup_empty_note_merges), color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            stringResource(R.string.cleanup_note_merges_guide),
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodySmall
        )
        KosmosListGroup {
            proposals.forEachIndexed { index, proposal ->
                ProposalRow(
                    lines = proposal.sources.map { it.content },
                    result = proposal.mergedContent,
                    resultEmphasis = true,
                    isChecked = proposal.key in checked,
                    divider = index < proposals.lastIndex,
                    onToggle = { onToggle(proposal) }
                )
            }
        }
    }
    SectionHeader(stringResource(R.string.cleanup_section_episode_merges))
    if (episodeProposals.isEmpty()) {
        Text(stringResource(R.string.cleanup_empty_episode_merges), color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            stringResource(R.string.cleanup_episode_merges_guide),
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodySmall
        )
        val mergeResultLabel = stringResource(R.string.cleanup_episode_merge_result)
        KosmosListGroup {
            episodeProposals.forEachIndexed { index, proposal ->
                ProposalRow(
                    lines = proposal.episodes.map { ep -> "${com.kosmos.app.domain.util.IsoDateTimeParser.monthDayKorean(ep.startAt, lang = lang)} ${ep.title.orEmpty()}" },
                    result = mergeResultLabel,
                    resultEmphasis = false,
                    isChecked = proposal.key in checkedEpisodes,
                    divider = index < episodeProposals.lastIndex,
                    onToggle = { onToggleEpisode(proposal) }
                )
            }
        }
    }
    val count = proposals.count { it.key in checked } + episodeProposals.count { it.key in checkedEpisodes }
    val buttonLabel = if (count > 0) {
        stringResource(R.string.cleanup_apply_selected, count)
    } else {
        stringResource(R.string.cleanup_finish_without_merge)
    }
    KosmosButton(buttonLabel, onApply, Modifier.fillMaxWidth(), accent = true)
}

/**
 * 제안 한 줄 — 체크 상자 · 합칠 원래 항목들 · 화살표 아이콘 + 합친 결과. 글자 기호 "→" 대신 Phosphor 화살표(DESIGN Icons, 0.38.3 M2 사용자 확인).
 */
@Composable
private fun ProposalRow(lines: List<String>, result: String, resultEmphasis: Boolean, isChecked: Boolean, divider: Boolean, onToggle: () -> Unit) {
    val colors = KosmosTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(value = isChecked, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle() }.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = isChecked, onCheckedChange = null, modifier = Modifier.padding(12.dp), colors = CheckboxDefaults.colors(checkedColor = colors.brand))
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    KosmosIcon(R.drawable.ic_ph_arrow_right, contentDescription = null, size = 16.dp, tint = colors.brandText, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        result,
                        color = if (resultEmphasis) colors.textPrimary else colors.textMuted,
                        style = if (resultEmphasis) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                        fontWeight = if (resultEmphasis) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
        if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.border)
    }
}

/** 안내·진행·회고 한 덩어리 — 공통 묶음 상자 하나에 여백(M3 `Card` 와 이름이 겹치지 않게 private). */
@Composable
private fun Card(content: @Composable () -> Unit) {
    KosmosListGroup(Modifier) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
