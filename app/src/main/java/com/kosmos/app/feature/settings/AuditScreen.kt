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
                title = { Text("Audit Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (auditLogItems.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No audit logs recorded.", color = Color.Gray)
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
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Session: ${event.sessionId.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
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
        AuditEventType.MODEL_RUN -> Color(0xFFE3F2FD) to Color(0xFF1565C0) // Light Blue
        AuditEventType.TOOL_CALL -> Color(0xFFE8F5E9) to Color(0xFF2E7D32) // Light Green
        AuditEventType.APPROVAL_GRANTED -> Color(0xFFFFF3E0) to Color(0xFFE65100) // Light Orange
        AuditEventType.APPROVAL_REJECTED -> Color(0xFFFFEBEE) to Color(0xFFC62828) // Light Red
        AuditEventType.ERROR -> Color(0xFFFFEBEE) to Color(0xFFD32F2F) // Red
        else -> Color(0xFFF5F5F5) to Color(0xFF616161) // Gray
    }
}
