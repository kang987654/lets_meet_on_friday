package com.kosmos.app.feature.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    viewModel: ProfileSheetViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val tokenUsage by viewModel.tokenUsage.collectAsState()
    val error by viewModel.error.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = KosmosTheme.colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "프로필",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KosmosTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$tokenUsage/${Constants.PROFILE_MAX_TOKENS}토큰",
                    style = MaterialTheme.typography.labelSmall,
                    color = KosmosTheme.colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "여기 적은 내용은 비서가 항상 기억해요 — 예: 이름, 호칭, 말투, 직업.",
                style = MaterialTheme.typography.bodySmall,
                color = KosmosTheme.colors.textMuted
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isEmpty()) {
                Text(
                    text = "아직 등록한 항목이 없어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.textMuted
                )
            }
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            keyInput = entry.key
                            valueInput = entry.value
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.key,
                            style = MaterialTheme.typography.labelSmall,
                            color = KosmosTheme.colors.textMuted
                        )
                        Text(
                            text = entry.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = KosmosTheme.colors.textPrimary
                        )
                    }
                    Text(
                        text = "✕",
                        color = KosmosTheme.colors.danger,
                        modifier = Modifier
                            .clickable { viewModel.delete(entry.key) }
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("항목 (예: 이름)") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp)
                )
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it },
                    label = { Text("내용") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.danger
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    keyInput = ""
                    valueInput = ""
                    viewModel.dismissError()
                }) { Text("비우기", color = KosmosTheme.colors.textMuted) }
                TextButton(onClick = {
                    viewModel.dismissError()
                    viewModel.upsert(keyInput, valueInput) {
                        keyInput = ""
                        valueInput = ""
                    }
                }) { Text("저장", color = KosmosTheme.colors.accent) }
            }
        }
    }
}
