package com.kosmos.app.feature.memory

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kosmos.app.ui.component.glassEffect

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knowledgeItems = viewModel.knowledgePagingData.collectAsLazyPagingItems()
    val taskItems = viewModel.taskPagingData.collectAsLazyPagingItems()

    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Import File Picker Launcher
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importData(
                uri = uri,
                onSuccess = {
                    android.widget.Toast.makeText(context, "복원이 완료되었습니다. 앱을 재시작합니다.", android.widget.Toast.LENGTH_LONG).show()
                    // [WHY] 컴포지션보다 오래 사는 raw thread + sleep 대신 메인 루퍼에 지연 예약한다.
                    // startActivity가 메인 스레드에서 실행된 뒤 프로세스를 종료해 재시작 경합을 줄인다.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        val componentName = launchIntent?.component
                        if (componentName != null) {
                            context.startActivity(android.content.Intent.makeRestartActivityTask(componentName))
                        }
                        kotlin.system.exitProcess(0)
                    }, 2000)
                },
                onError = { error ->
                    android.widget.Toast.makeText(context, "복원 실패: $error", android.widget.Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(KosmosTheme.colors.bg)) {
        Text(
            text = "Memory & Tasks",
            style = MaterialTheme.typography.headlineMedium,
            color = KosmosTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp)
        )
        // Top Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabButton(
                text = "🧠 Memory",
                isSelected = uiState.selectedFilter == MemoryFilterType.KNOWLEDGE,
                onClick = { viewModel.onFilterSelected(MemoryFilterType.KNOWLEDGE) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "✓ Tasks",
                isSelected = uiState.selectedFilter == MemoryFilterType.TASK,
                onClick = { viewModel.onFilterSelected(MemoryFilterType.TASK) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.selectedFilter == MemoryFilterType.TASK) {
            val pendingCount = taskItems.itemSnapshotList.count { it?.isCompleted == false }
            val doneCount = taskItems.itemSnapshotList.count { it?.isCompleted == true }
            val totalCount = pendingCount + doneCount
            val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

            // Stats & Progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$pendingCount pending  ·  $doneCount done",
                    style = MaterialTheme.typography.labelMedium,
                    color = KosmosTheme.colors.textSecondary
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .background(KosmosTheme.colors.glass, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(KosmosTheme.colors.success, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // Lists
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.selectedFilter == MemoryFilterType.TASK) {
                items(
                    count = taskItems.itemCount,
                    key = taskItems.itemKey { it.id },
                    contentType = taskItems.itemContentType { "Task" }
                ) { index ->
                    val task = taskItems[index]
                    if (task != null) {
                        TaskItemRow(
                            title = task.title,
                            isCompleted = task.isCompleted,
                            onToggle = { 
                                viewModel.completeTask(task.id)
                                taskItems.refresh()
                            }
                        )
                    }
                }

                item {
                    // Add new task button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                width = 1.dp,
                                color = KosmosTheme.colors.border,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                            .clickable { /* Add Task */ }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "+",
                                color = KosmosTheme.colors.textSecondary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = "Add new task...",
                                color = KosmosTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            if (uiState.selectedFilter == MemoryFilterType.KNOWLEDGE) {
                items(
                    count = knowledgeItems.itemCount,
                    key = knowledgeItems.itemKey { it.id },
                    contentType = knowledgeItems.itemContentType { "Knowledge" }
                ) { index ->
                    val note = knowledgeItems[index]
                    if (note != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassEffect(
                                    backgroundColor = KosmosTheme.colors.glass,
                                    borderColor = KosmosTheme.colors.borderHigh,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = note.content, style = MaterialTheme.typography.bodyLarge, color = KosmosTheme.colors.textPrimary)
                                if (note.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                    androidx.compose.foundation.layout.FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        note.tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = KosmosTheme.colors.accent.copy(alpha = 0.15f),
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = KosmosTheme.colors.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.1f) else KosmosTheme.colors.glass
    val borderColor = if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.5f) else KosmosTheme.colors.border
    val textColor = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textSecondary
    
    Box(
        modifier = modifier
            .glassEffect(
                backgroundColor = bgColor,
                borderColor = borderColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TaskItemRow(title: String, isCompleted: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glass,
                borderColor = KosmosTheme.colors.border,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Check Circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        width = 1.dp,
                        color = if (isCompleted) KosmosTheme.colors.success else KosmosTheme.colors.textMuted,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .background(
                        color = if (isCompleted) KosmosTheme.colors.success.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Text("✓", color = KosmosTheme.colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text
            Text(
                text = title, 
                style = MaterialTheme.typography.bodyLarge, 
                color = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.textPrimary,
                textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                modifier = Modifier.weight(1f)
            )
            
            // Dot indicator
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(KosmosTheme.colors.danger, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

