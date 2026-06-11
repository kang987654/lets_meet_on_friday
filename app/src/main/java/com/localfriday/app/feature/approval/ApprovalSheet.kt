package com.localfriday.app.feature.approval

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localfriday.app.domain.model.CalendarDraft

val SkyBlue = Color(0xFF5BC2E7)
val WarningYellow = Color(0xFFFFF3CD)
val WarningText = Color(0xFF856404)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    sessionId: String,
    draft: CalendarDraft,
    onDismiss: () -> Unit,
    viewModel: ApprovalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is ApprovalUiState.Success) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (uiState !is ApprovalUiState.Loading) {
                viewModel.reject(sessionId, draft)
            }
        },
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "새 일정 제안",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!draft.isConfident()) {
                WarningCard()
                Spacer(modifier = Modifier.height(16.dp))
            }

            DraftDetailCard(draft)
            
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is ApprovalUiState.Error) {
                Text(
                    text = "저장 실패: ${(uiState as ApprovalUiState.Error).message}",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.reject(sessionId, draft) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray),
                    enabled = uiState !is ApprovalUiState.Loading
                ) {
                    Text("취소")
                }

                Button(
                    onClick = { viewModel.approve(sessionId, draft) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                    enabled = uiState !is ApprovalUiState.Loading
                ) {
                    if (uiState is ApprovalUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("일정 추가", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp)) // 바텀 네비게이션 여백 고려
        }
    }
}

@Composable
fun WarningCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarningYellow, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚠️ AI가 일부 내용을 확실히 이해하지 못했을 수 있습니다. 저장 전 시간과 내용을 확인해 주세요.",
            color = WarningText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DraftDetailCard(draft: CalendarDraft) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(text = "제목", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Text(text = draft.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

        Text(text = "시간", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Text(text = "${draft.startIso} ~ ${draft.endIso}", modifier = Modifier.padding(bottom = 12.dp))

        if (!draft.note.isNullOrBlank()) {
            Text(text = "메모", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Text(text = draft.note)
        }
    }
}
