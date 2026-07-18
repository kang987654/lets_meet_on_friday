package com.kosmos.app.feature.approval

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kosmos.app.assistant.approval.ApprovalRequest

val SkyBlue = com.kosmos.app.ui.theme.Cyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onReject,
        containerColor = com.kosmos.app.ui.theme.SurfaceColor,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = com.kosmos.app.ui.theme.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyLarge,
                color = com.kosmos.app.ui.theme.TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = com.kosmos.app.ui.theme.TextMuted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.kosmos.app.ui.theme.BorderColor)
                ) {
                    Text("취소")
                }

                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue)
                ) {
                    Text("승인", color = com.kosmos.app.ui.theme.BgColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
