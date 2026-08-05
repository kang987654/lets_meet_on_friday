package com.kosmos.app.feature.settings

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    viewModel: AuditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val auditLogItems = viewModel.auditLogPagingData.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit Logs", fontWeight = FontWeight.Bold, color = KosmosTheme.colors.textPrimary) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back", color = KosmosTheme.colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KosmosTheme.colors.bg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(KosmosTheme.colors.bg)
        ) {
            if (auditLogItems.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No audit logs recorded.", color = KosmosTheme.colors.textMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        count = auditLogItems.itemCount,
                        key = auditLogItems.itemKey { it.id },
                        contentType = auditLogItems.itemContentType { "AuditLog" }
                    ) { index ->
                        val event = auditLogItems[index]
                        if (event != null) {
                            AuditEventCard(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditEventCard(event: AuditEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, KosmosTheme.colors.border, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = KosmosTheme.colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tagColors = getTagColors(event.type)
                Surface(
                    color = tagColors.first,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = event.type.name,
                        color = tagColors.second,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = KosmosTheme.colors.textMuted
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.details,
                style = MaterialTheme.typography.bodyMedium,
                color = KosmosTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Session: ${event.sessionId.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                color = KosmosTheme.colors.textMuted
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (e: Exception) {
        timestamp.toString()
    }
}

@Composable
private fun getTagColors(type: AuditEventType): Pair<Color, Color> {
    return when (type) {
        AuditEventType.MODEL_RUN -> KosmosTheme.colors.accent.copy(alpha=0.15f) to KosmosTheme.colors.accent
        AuditEventType.TOOL_CALL -> KosmosTheme.colors.success.copy(alpha=0.15f) to KosmosTheme.colors.success
        AuditEventType.APPROVAL_GRANTED -> KosmosTheme.colors.warning.copy(alpha=0.15f) to KosmosTheme.colors.warning
        AuditEventType.APPROVAL_REJECTED -> KosmosTheme.colors.danger.copy(alpha=0.15f) to KosmosTheme.colors.danger
        AuditEventType.ERROR -> KosmosTheme.colors.danger.copy(alpha=0.1f) to KosmosTheme.colors.danger
        else -> KosmosTheme.colors.glass to KosmosTheme.colors.textMuted
    }
}
