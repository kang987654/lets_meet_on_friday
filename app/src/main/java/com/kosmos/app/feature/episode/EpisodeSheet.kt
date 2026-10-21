package com.kosmos.app.feature.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 에피소드 시트 (시안 A′-3): 제목/태그/요약 열람 + 인라인 수정 + 삭제 + 원문 이동.
 *
 * ### Architecture Context
 * - **Layer**: Feature (Episode)
 * - **Dependencies**: [EpisodeSheetViewModel]
 *
 * [WHY] episodeId 만 받고 데이터는 자체 로드한다 — 드로어(아카이브 목록)와 회수 칩(채팅 버블)
 * 두 진입점이 같은 시트를 쓰는 가장 싼 방법이다 (M2-5 에서 칩이 재사용).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSheet(
    episodeId: String,
    onDismiss: () -> Unit,
    onJumpToTimeline: (startAt: Long) -> Unit = {},
    // [WHY] 수정·삭제가 **성공한 뒤** 불린다 — 드로어 아카이브는 무효화하지 않는 PagingSource 라
    // 호출자가 refresh 를 걸어야 바뀐 제목·삭제가 목록에 반영된다.
    onChanged: () -> Unit = {},
    viewModel: EpisodeSheetViewModel = hiltViewModel()
) {
    LaunchedEffect(episodeId) { viewModel.load(episodeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KosmosTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val current = (state as? EpisodeSheetState.Loaded)?.episode
        // [WHY] 수정 폼(요약 여러 줄) + 키보드에서 [저장]이 잘렸다 — 스크롤(0.38.3 M6 점검 P1). 좌우 20 은 승인·알림 시트와 같은 값.
        Column(
            modifier = Modifier
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            actionError?.let { message ->
                Text(
                    text = message,
                    color = KosmosTheme.colors.dangerText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (current == null) {
                Text(
                    text = when (val s = state) {
                        EpisodeSheetState.Loading, is EpisodeSheetState.Loaded -> "기억을 불러오는 중이에요…"
                        EpisodeSheetState.Missing -> "이 기억은 삭제됐어요. 원문 대화는 타임라인에 그대로 있어요."
                        is EpisodeSheetState.Error -> s.message
                    },
                    color = KosmosTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (editing) {
                EditForm(
                    episode = current,
                    onCancel = { editing = false },
                    onSave = { title, tags, summary ->
                        viewModel.dismissActionError()
                        viewModel.save(title, tags, summary, onSaved = onChanged)
                        editing = false
                    }
                )
            } else {
                Text(
                    text = current.title ?: "(제목 없음)",
                    style = MaterialTheme.typography.titleMedium,
                    color = KosmosTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRange(current),
                    style = MaterialTheme.typography.labelMedium,
                    color = KosmosTheme.colors.textMuted
                )
                if (current.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.tags.take(5).forEach { tag -> com.kosmos.app.ui.component.Tag(tag) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = current.summary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KosmosTheme.colors.textSecondary
                )
                // [WHY] 0.38.2 알림 수정 시트와 같은 배치(0.38.3 M5 시안 A) — 주 동작 두 개는 버튼, 되돌리기 어려운 삭제는 오른쪽 아래 글자 버튼.
                // 예전에는 빨간 "삭제"가 줄 맨 앞이라 가장 먼저 눈과 손이 갔다.
                Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KosmosButton("원문 대화 보기", {
                        onDismiss()
                        onJumpToTimeline(current.startAt)
                    }, Modifier.weight(1f))
                    KosmosButton("수정", { editing = true }, Modifier.weight(1f), accent = true)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("삭제", color = KosmosTheme.colors.dangerText)
                    }
                }
                Text(
                    text = "요약만 지워져요. 원문 대화는 타임라인에 그대로 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.textMuted
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 기억을 삭제할까요?") },
            // [WHY] 삭제되는 것은 **요약 문서뿐**임을 명시한다 — 원문 대화는 타임라인에 남는다.
            text = { Text("요약 문서만 삭제돼요. 원문 대화는 타임라인에 그대로 남아요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // [WHY] 성공 후에만 닫는다 — 실패하면 시트에 안내가 남아야 사용자가 안다.
                    viewModel.delete(onDeleted = {
                        onChanged()
                        onDismiss()
                    })
                }) { Text("삭제", color = KosmosTheme.colors.dangerText) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소", color = KosmosTheme.colors.textSecondary) }
            },
            containerColor = KosmosTheme.colors.surface,
            titleContentColor = KosmosTheme.colors.textPrimary,
            textContentColor = KosmosTheme.colors.textSecondary
        )
    }
}

@Composable
private fun EditForm(
    episode: Episode,
    onCancel: () -> Unit,
    onSave: (title: String, tags: String, summary: String) -> Unit
) {
    var title by remember { mutableStateOf(episode.title.orEmpty()) }
    var tags by remember { mutableStateOf(episode.tags.joinToString(", ")) }
    var summary by remember { mutableStateOf(episode.summary.orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fieldColors = com.kosmos.app.ui.component.kosmosTextFieldColors()
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("태그 (쉼표 구분)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("요약") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = fieldColors)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KosmosButton("취소", onCancel, Modifier.weight(1f))
            KosmosButton("저장", { onSave(title, tags, summary) }, Modifier.weight(1f), accent = true)
        }
    }
}

// [WHY] 표기는 IsoDateTimeParser 가 단일 출처다 — 예전의 24시간제 "HH:mm" 은 앱의 다른 시각
// 표기("오후 4:00")와 달랐다(AGENTS §4-7).
private fun formatRange(episode: Episode): String {
    val start = "${com.kosmos.app.domain.util.IsoDateTimeParser.monthDayKorean(episode.startAt)} ${com.kosmos.app.domain.util.IsoDateTimeParser.timeKorean(episode.startAt)}"
    val end = episode.endAt?.let { com.kosmos.app.domain.util.IsoDateTimeParser.timeKorean(it) }
    return if (end != null) "$start – $end, 대화 ${episode.messageCount}개" else start
}
