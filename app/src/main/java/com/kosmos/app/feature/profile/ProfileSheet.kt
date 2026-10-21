package com.kosmos.app.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.showUndo
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kosmos.app.core.common.Constants
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * [ProfileSheet]
 * 프로필 키-값 항목의 열람·편집 시트 — 드로어 고정 카드에서 엽니다 (C′1, 시안 A′-2).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Profile)
 * - **Dependencies**: [ProfileSheetViewModel]
 *
 * [WHY] EpisodeSheet 의 하우스 크롬(surface·DragHandle·같은 패딩)을 따른다. 행 탭이 아래
 * 입력란에 값을 채우는 방식 — 인라인 행 편집보다 좁은 시트에서 오타 수정이 쉽다.
 * 한 항목 삭제는 휴지통 + 되돌리기(0.38.3 결정 2) — 빨간 ✕ 가 시트에서 가장 눈에 띄었다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    viewModel: ProfileSheetViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.tokenUsage.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }

    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colors = KosmosTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // [WHY] 시트 내용이 길면(항목 + 입력 2개 + 키보드) [저장]이 화면 밖으로 잘렸다 — 스크롤(0.38.3 M6 점검 P1). 좌우 20 은 승인·알림 시트와 같은 값.
        Column(
            modifier = Modifier
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "프로필",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                // [WHY] 토큰은 사용자가 알 단위가 아니다 — 상한 대비 남은 비율만 보인다(0.38.3 M5). 상한 검사·초과 안내는 그대로 토큰 기준.
                Text(
                    text = "남은 공간 ${remainingPercent(tokenUsage)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }
            Text(
                // 예시는 둘째 줄로 — 한 줄에 이어 쓰면 "직업."만 다음 줄로 넘어갔다(사용자 지적).
                text = "여기 적은 내용은 비서가 항상 기억해요.\n예: 이름, 호칭, 말투, 직업.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (entries.isEmpty()) {
                Text(
                    text = "아직 등록한 항목이 없어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            } else {
                com.kosmos.app.ui.component.KosmosListGroup {
                    entries.forEachIndexed { index, entry ->
                        EntryRow(
                            entry = entry,
                            divider = index < entries.lastIndex,
                            onEdit = {
                                keyInput = entry.key
                                valueInput = entry.value
                            },
                            onDelete = {
                                viewModel.delete(entry.key)
                                scope.launch {
                                    snackbar.showUndo("'${entry.key}'을(를) 지웠어요") { viewModel.restore(entry) }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    // [WHY] 폭 140 고정에 "항목 (예: 이름)" 라벨이 글자 1.3배에서 두 줄로 잘렸다 — 라벨은 짧게, 예시는 자리 표시로, 폭은 비율로(0.38.4 M0).
                    label = { Text("항목") },
                    placeholder = { Text("예: 이름") },
                    singleLine = true,
                    colors = com.kosmos.app.ui.component.kosmosTextFieldColors(),
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it },
                    label = { Text("내용") },
                    singleLine = true,
                    colors = com.kosmos.app.ui.component.kosmosTextFieldColors(),
                    modifier = Modifier.weight(3f)
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KosmosButton("비우기", {
                    keyInput = ""
                    valueInput = ""
                    viewModel.dismissError()
                }, Modifier.weight(1f))
                KosmosButton("저장", {
                    viewModel.dismissError()
                    viewModel.upsert(keyInput, valueInput) {
                        keyInput = ""
                        valueInput = ""
                    }
                }, Modifier.weight(1f), accent = true)
            }
            androidx.compose.material3.SnackbarHost(snackbar, Modifier.padding(top = 8.dp))
        }
    }
}

/** 프로필 상한 대비 남은 비율(0..100). */
internal fun remainingPercent(tokenUsage: Int, max: Int = Constants.PROFILE_MAX_TOKENS): Int =
    ((max - tokenUsage) * 100 / max).coerceIn(0, 100)

/** 항목 한 줄 — 이름(+ "자동" 태그) · 값 · 옅은 휴지통. 누르면 아래 입력란에 채운다. */
@Composable
private fun EntryRow(entry: com.kosmos.app.domain.model.ProfileEntry, divider: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = KosmosTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.key, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    // 출처 표시(관리 장치) — 자동 추출(C′2)로 들어온 항목.
                    if (entry.source == com.kosmos.app.domain.model.ProfileEntry.SOURCE_AUTO) com.kosmos.app.ui.component.Tag("자동")
                }
                Text(entry.value, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            }
            com.kosmos.app.ui.component.DeleteIconButton(onDelete, contentDescription = "${entry.key} 삭제")
        }
        if (divider) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 16.dp), color = colors.border)
    }
}
