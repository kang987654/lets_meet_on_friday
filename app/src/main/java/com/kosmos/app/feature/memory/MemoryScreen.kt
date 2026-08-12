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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
        if (uri != null) viewModel.importData(uri)
    }

    // Export 저장 위치 선택 Launcher
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: android.net.Uri? ->
        if (uri != null) viewModel.saveExportTo(uri) else viewModel.onExportCancelled()
    }

    // [WHY] zip 생성이 끝나면 곧바로 저장 위치를 묻는다. 사용자가 버튼을 한 번 더 누르게
    // 하면 그 사이 cacheDir 이 비워질 수 있다.
    val backupState = uiState.backup
    LaunchedEffect(backupState) {
        if (backupState is BackupState.ReadyToSave) {
            exportLauncher.launch(backupState.suggestedName)
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

            item {
                BackupSection(
                    backupState = backupState,
                    onExportClick = { viewModel.requestExport() },
                    onImportClick = { viewModel.requestImport() }
                )
            }
        }
    }

    BackupDialogs(
        uiState = uiState,
        onConfirmExport = { viewModel.confirmExport() },
        onConfirmImport = { importLauncher.launch("application/zip") },
        onDismiss = { viewModel.dismissBackupState() },
        onRestartApp = { restartApp(context) }
    )
}

/**
 * [WHY] `prd.md` F8 은 "메모리 화면에서 export 선택"을 요구하는데 진입점이 아예 없었다.
 * `MemoryViewModel.exportData`/`importData` 는 구현돼 있었지만 호출자가 없어 도달 불가였다.
 */
@Composable
private fun BackupSection(
    backupState: BackupState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val busy = backupState is BackupState.Exporting ||
        backupState is BackupState.Importing ||
        backupState is BackupState.ReadyToSave

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = "BACKUP",
            style = MaterialTheme.typography.labelMedium,
            color = KosmosTheme.colors.textMuted,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupButton(
                text = if (backupState is BackupState.Exporting) "내보내는 중…" else "내보내기",
                enabled = !busy,
                onClick = onExportClick,
                modifier = Modifier.weight(1f)
            )
            BackupButton(
                text = if (backupState is BackupState.Importing) "복원 중…" else "가져오기",
                enabled = !busy,
                onClick = onImportClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BackupButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glass,
                borderColor = KosmosTheme.colors.border,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) KosmosTheme.colors.textPrimary else KosmosTheme.colors.textMuted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun BackupDialogs(
    uiState: MemoryUiState,
    onConfirmExport: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit,
    onRestartApp: () -> Unit
) {
    if (uiState.showExportNotice) {
        // [WHY] prd.md F8 정책 — 백업 파일에 개인정보가 포함됨을 UI에서 명시해야 한다.
        // v1은 암호화를 의도적으로 넣지 않았으므로 이 경고가 유일한 보호막이다.
        BackupDialog(
            title = "백업에 개인정보가 포함됩니다",
            body = "내보낼 파일에는 대화 내용, 지식 노트, 프로필, 감사 로그가 " +
                "암호화 없이 그대로 담깁니다. 신뢰할 수 있는 위치에만 저장하세요.",
            confirmText = "계속",
            onConfirm = onConfirmExport,
            onDismiss = onDismiss
        )
        return
    }

    if (uiState.showImportWarning) {
        BackupDialog(
            title = "기존 데이터를 덮어씁니다",
            body = "복원하면 현재 기기의 대화·지식·일정이 백업 파일의 내용으로 " +
                "완전히 교체되며 되돌릴 수 없습니다. 계속하시겠습니까?",
            confirmText = "파일 선택",
            confirmColor = KosmosTheme.colors.danger,
            onConfirm = onConfirmImport,
            onDismiss = onDismiss
        )
        return
    }

    when (val state = uiState.backup) {
        is BackupState.ImportSucceeded -> {
            // [WHY] DB가 이미 교체됐으므로 닫을 수 없는 다이얼로그다. 재시작만이 유효한 출구다.
            // 자동 타이머로 프로세스를 죽이던 기존 방식과 달리, 사용자가 누른 시점에만 죽인다.
            AlertDialog(
                onDismissRequest = { },
                title = { Text("복원 완료") },
                text = { Text("데이터를 적용하려면 앱을 다시 시작해야 합니다.") },
                confirmButton = {
                    TextButton(onClick = onRestartApp) {
                        Text("재시작", color = KosmosTheme.colors.accent)
                    }
                },
                containerColor = KosmosTheme.colors.surface,
                titleContentColor = KosmosTheme.colors.textPrimary,
                textContentColor = KosmosTheme.colors.textSecondary
            )
        }
        is BackupState.Failed -> {
            BackupDialog(
                title = "백업 처리 실패",
                titleColor = KosmosTheme.colors.danger,
                body = state.message,
                confirmText = "닫기",
                showCancel = false,
                onConfirm = onDismiss,
                onDismiss = onDismiss
            )
        }
        else -> {}
    }
}

@Composable
private fun BackupDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = KosmosTheme.colors.textPrimary,
    confirmColor: androidx.compose.ui.graphics.Color = KosmosTheme.colors.accent,
    showCancel: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = titleColor) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = confirmColor) }
        },
        dismissButton = {
            if (showCancel) {
                TextButton(onClick = onDismiss) {
                    Text("취소", color = KosmosTheme.colors.textMuted)
                }
            }
        },
        containerColor = KosmosTheme.colors.surface,
        titleContentColor = KosmosTheme.colors.textPrimary,
        textContentColor = KosmosTheme.colors.textSecondary
    )
}

/**
 * [WHY] 복원 후에는 프로세스를 새로 시작해야 한다 — `ExportImportManager` 가 열려 있는 Room
 * DB 파일을 교체하므로 현재 프로세스의 커넥션은 이미 낡은 상태다.
 */
private fun restartApp(context: android.content.Context) {
    val componentName = context.packageManager
        .getLaunchIntentForPackage(context.packageName)?.component
    if (componentName != null) {
        context.startActivity(android.content.Intent.makeRestartActivityTask(componentName))
    }
    kotlin.system.exitProcess(0)
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

