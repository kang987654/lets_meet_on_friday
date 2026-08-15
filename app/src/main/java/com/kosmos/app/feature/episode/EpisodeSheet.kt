package com.kosmos.app.feature.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.ui.component.glassEffect
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
    viewModel: EpisodeSheetViewModel = hiltViewModel()
) {
    LaunchedEffect(episodeId) { viewModel.load(episodeId) }
    val episode by viewModel.episode.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KosmosTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        val current = episode
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            if (current == null) {
                Text(
                    text = "기억을 불러오는 중이에요…",
                    color = KosmosTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (editing) {
                EditForm(
                    episode = current,
                    onCancel = { editing = false },
                    onSave = { title, tags, summary ->
                        viewModel.save(title, tags, summary)
                        editing = false
                    }
                )
            } else {
                Text(
                    text = current.title ?: "(제목 없음)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KosmosTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRange(current),
                    style = MaterialTheme.typography.labelSmall,
                    color = KosmosTheme.colors.textMuted
                )
                if (current.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.tags.take(5).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = KosmosTheme.colors.textSecondary,
                                modifier = Modifier
                                    .glassEffect(shape = RoundedCornerShape(99.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = current.summary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KosmosTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("삭제", color = KosmosTheme.colors.danger)
                    }
                    TextButton(onClick = { editing = true }) {
                        Text("수정", color = KosmosTheme.colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onDismiss()
                        onJumpToTimeline(current.startAt)
                    }) {
                        Text("원문 대화 보기", color = KosmosTheme.colors.accent)
                    }
                }
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
                    viewModel.delete()
                    onDismiss()
                }) { Text("삭제", color = KosmosTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
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
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("태그 (쉼표 구분)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("요약") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Row {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("취소", color = KosmosTheme.colors.textMuted) }
            TextButton(onClick = { onSave(title, tags, summary) }) { Text("저장", color = KosmosTheme.colors.accent) }
        }
    }
}

private fun formatRange(episode: Episode): String {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm", java.util.Locale.KOREAN)
    val zone = java.time.ZoneId.systemDefault()
    val start = java.time.Instant.ofEpochMilli(episode.startAt).atZone(zone).format(fmt)
    val end = episode.endAt?.let {
        java.time.Instant.ofEpochMilli(it).atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }
    return if (end != null) "$start – $end · ${episode.messageCount}개 대화" else start
}
