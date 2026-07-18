package com.kosmos.app.feature.settings

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
                title = { Text("Audit Logs", fontWeight = FontWeight.Bold, color = com.kosmos.app.ui.theme.TextPrimary) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back", color = com.kosmos.app.ui.theme.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.kosmos.app.ui.theme.BgColor
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(com.kosmos.app.ui.theme.BgColor)
        ) {
            if (auditLogItems.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No audit logs recorded.", color = com.kosmos.app.ui.theme.TextMuted)
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
            .border(1.dp, com.kosmos.app.ui.theme.BorderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = com.kosmos.app.ui.theme.SurfaceColor)
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
                    color = com.kosmos.app.ui.theme.TextMuted
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.details,
                style = MaterialTheme.typography.bodyMedium,
                color = com.kosmos.app.ui.theme.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Session: ${event.sessionId.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                color = com.kosmos.app.ui.theme.TextMuted
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

private fun getTagColors(type: AuditEventType): Pair<Color, Color> {
    return when (type) {
        AuditEventType.MODEL_RUN -> com.kosmos.app.ui.theme.Cyan.copy(alpha=0.15f) to com.kosmos.app.ui.theme.Cyan
        AuditEventType.TOOL_CALL -> com.kosmos.app.ui.theme.Success.copy(alpha=0.15f) to com.kosmos.app.ui.theme.Success
        AuditEventType.APPROVAL_GRANTED -> com.kosmos.app.ui.theme.Amber.copy(alpha=0.15f) to com.kosmos.app.ui.theme.Amber
        AuditEventType.APPROVAL_REJECTED -> com.kosmos.app.ui.theme.Danger.copy(alpha=0.15f) to com.kosmos.app.ui.theme.Danger
        AuditEventType.ERROR -> com.kosmos.app.ui.theme.Danger.copy(alpha=0.1f) to com.kosmos.app.ui.theme.Danger
        else -> com.kosmos.app.ui.theme.GlassColor to com.kosmos.app.ui.theme.TextMuted
    }
}
