package com.kosmos.app.feature.cleanup

import com.kosmos.app.ui.component.KosmosButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.kosmos.app.ui.component.kosmosSurface
import com.kosmos.app.ui.theme.KosmosTheme

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
            com.kosmos.app.ui.component.KosmosTopBar(title = "기억 정리", onBack = onBack)
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
                MemoryCleanupRunner.State.Waiting -> Progress("자동 정리가 끝나길 기다리는 중…", null, viewModel::cancel)
                is MemoryCleanupRunner.State.Running -> Progress(
                    label = when (s.step) {
                        MemoryCleanupRunner.Step.WEEKLY_REVIEW -> "이번 주 회고를 쓰는 중…"
                        MemoryCleanupRunner.Step.MERGE ->
                            if (s.total > 0) "겹치는 기억을 살펴보는 중… (${s.done}/${s.total})" else "겹치는 기억을 찾는 중…"
                        MemoryCleanupRunner.Step.EPISODE_MERGE ->
                            if (s.total > 0) "이어지는 대화를 살펴보는 중… (${s.done}/${s.total})" else "이어지는 대화를 찾는 중…"
                    },
                    fraction = if (s.step != MemoryCleanupRunner.Step.WEEKLY_REVIEW && s.total > 0) s.done.toFloat() / s.total else null,
                    onCancel = viewModel::cancel
                )
                is MemoryCleanupRunner.State.Blocked -> Notice(s.reason, "다시 시도", viewModel::start)
                MemoryCleanupRunner.State.Applying -> Progress("고른 항목을 정리하는 중… 대화를 합치면 다시 요약해요.", null, onCancel = null)
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
                is MemoryCleanupRunner.State.Done -> Notice(
                    message = buildString {
                        append(if (s.weeklyReview != null) "이번 주 회고를 기억에 저장했어요." else "회고할 대화가 없었어요.")
                        append(if (s.merged > 0) " 기억 ${s.merged}건을 합쳤어요." else " 합친 기억은 없어요.")
                        if (s.episodesMerged > 0) append(" 이어지는 대화 ${s.episodesMerged}건을 하나로 합쳤어요.")
                        // [WHY] 체크했는데 안 합쳐진 이유를 숨기지 않는다 — 재요약이 두 주제로 갈리면 합치지 않는다(exp44 갈린 점).
                        if (s.episodesSkipped > 0) append(" ${s.episodesSkipped}건은 다시 요약해 보니 다른 주제라 그대로 뒀어요.")
                    },
                    action = "닫기",
                    onAction = {
                        viewModel.finish()
                        onBack()
                    }
                )
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Card {
        Text(
            "지난 7일의 대화로 이번 주 회고를 만들어 기억에 저장하고, 같은 내용이 여러 번 저장된 기억을 찾아 합칠지 물어봐요. " +
                "기억이 많으면 몇 분 걸릴 수 있어요. 앱을 닫으면 멈추고, 다시 누르면 처음부터 해요.",
            color = KosmosTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    KosmosButton("정리 시작", onStart, Modifier.fillMaxWidth(), accent = true)
}

@Composable
private fun Progress(label: String, fraction: Float?, onCancel: (() -> Unit)?) {
    Card {
        Text(label, color = KosmosTheme.colors.textPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = KosmosTheme.colors.brand)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = KosmosTheme.colors.brand)
        }
    }
    // [WHY] 적용 중에는 취소가 없다 — 노트 병합·대화 통합이 반쯤 된 상태로 끊기지 않게(각 건은 트랜잭션이지만 묶음은 아니다).
    if (onCancel != null) TextButton(onClick = onCancel) { Text("취소", color = KosmosTheme.colors.textMuted) }
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
    // [WHY] 섹션 제목은 subtitle 단계 문장체, 제안은 한 묶음 안의 행(0.38.3 M2) — 제안마다 카드였고 제목은 작은 회색 글씨였다.
    com.kosmos.app.ui.component.SectionHeader("이번 주 회고")
    Card {
        Text(
            review?.content ?: "지난 7일 동안 정리된 대화가 없어 회고를 만들지 않았어요.",
            color = if (review != null) KosmosTheme.colors.textPrimary else KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    com.kosmos.app.ui.component.SectionHeader("합칠 수 있는 기억")
    if (proposals.isEmpty()) {
        Text("겹치는 기억이 없어요.", color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            "합칠 것만 골라 주세요. 고르지 않은 기억은 그대로 둬요.",
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodySmall
        )
        com.kosmos.app.ui.component.KosmosListGroup {
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
    com.kosmos.app.ui.component.SectionHeader("이어지는 대화")
    if (episodeProposals.isEmpty()) {
        Text("끊겨 있는 같은 대화가 없어요.", color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            "같은 일이 끊겨 여러 대화로 나뉜 것 같아요. 고르면 하나로 합친 뒤 다시 요약해요.",
            color = KosmosTheme.colors.textMuted,
            style = MaterialTheme.typography.bodySmall
        )
        com.kosmos.app.ui.component.KosmosListGroup {
            episodeProposals.forEachIndexed { index, proposal ->
                ProposalRow(
                    lines = proposal.episodes.map { ep -> "${com.kosmos.app.domain.util.IsoDateTimeParser.monthDayKorean(ep.startAt)} ${ep.title.orEmpty()}" },
                    result = "하나의 대화로 합쳐 다시 요약",
                    resultEmphasis = false,
                    isChecked = proposal.key in checkedEpisodes,
                    divider = index < episodeProposals.lastIndex,
                    onToggle = { onToggleEpisode(proposal) }
                )
            }
        }
    }
    val count = proposals.count { it.key in checked } + episodeProposals.count { it.key in checkedEpisodes }
    KosmosButton(if (count > 0) "선택한 ${count}건 합치기" else "합치지 않고 마치기", onApply, Modifier.fillMaxWidth(), accent = true)
}

/**
 * 제안 한 줄 — 체크 상자 · 합칠 원래 항목들 · 화살표 아이콘 + 합친 결과. 글자 기호 "→" 대신 Phosphor 화살표(DESIGN Icons, 0.38.3 M2 사용자 확인).
 */
@Composable
private fun ProposalRow(lines: List<String>, result: String, resultEmphasis: Boolean, isChecked: Boolean, divider: Boolean, onToggle: () -> Unit) {
    val colors = KosmosTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = isChecked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = colors.brand))
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_arrow_right, contentDescription = null, size = 16.dp, tint = colors.brandText, modifier = Modifier.padding(end = 6.dp))
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
    com.kosmos.app.ui.component.KosmosListGroup(Modifier) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
