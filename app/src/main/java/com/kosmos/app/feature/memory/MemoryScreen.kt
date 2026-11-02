package com.kosmos.app.feature.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kosmos.app.R
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.DeleteIconButton
import com.kosmos.app.ui.component.EmptyState
import com.kosmos.app.ui.component.GroupPosition
import com.kosmos.app.ui.component.IconSize
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.KosmosListRow
import com.kosmos.app.ui.component.KosmosSegmentedControl
import com.kosmos.app.ui.component.KosmosTopBar
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.component.groupItem
import com.kosmos.app.ui.component.groupPosition
import com.kosmos.app.ui.component.showUndo
import com.kosmos.app.ui.restartApp
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    // [WHY] 채팅 요약 타일(다음 알림·남은 할 일)에서 들어오면 할 일 탭으로 연다(0.38.2 결정 2). 처음 한 번만.
    initialFilter: MemoryFilterType? = null
) {
    if (initialFilter != null) {
        LaunchedEffect(Unit) { viewModel.onFilterSelected(initialFilter) }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knowledgeItems = viewModel.knowledgePagingData.collectAsLazyPagingItems()
    val taskItems = viewModel.taskPagingData.collectAsLazyPagingItems()
    val completedTaskItems = viewModel.completedTaskPagingData.collectAsLazyPagingItems()
    var confirmClearCompleted by remember { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val refreshTasks: () -> Unit = {
        taskItems.refresh()
        completedTaskItems.refresh()
    }

    val context = LocalContext.current
    val resources = LocalResources.current
    val lang = currentAppLanguage()

    // Import File Picker Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importData(uri)
    }

    // Export 저장 위치 선택 Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
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

    // [WHY] 목록 조작 실패(Task 완료·지식 삭제)를 알린다 — 없으면 체크했는데 항목이 남아 "탭이
    // 씹힌" 것처럼 보였다. 일시 안내는 앱 전체가 Snackbar 로 통일한다(2026-09-30 사용자 결정 —
    // 예전엔 이 화면만 Toast 였다). 상태 표시(일정 불러오기 실패 등)와 시트 안 안내는 인라인이다.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let { message ->
            viewModel.dismissActionError()
            snackbarHostState.showSnackbar(message)
        }
    }

    if (confirmClearCompleted) {
        ConfirmDialog(
            title = stringResource(R.string.memory_clear_completed_title),
            body = stringResource(R.string.memory_clear_completed_body),
            confirmText = stringResource(R.string.memory_clear_completed_confirm),
            confirmColor = KosmosTheme.colors.dangerText,
            onConfirm = {
                viewModel.clearCompletedTasks(refreshTasks)
                confirmClearCompleted = false
            },
            onDismiss = { confirmClearCompleted = false }
        )
    }

    val snackbarScope = rememberCoroutineScope()
    // [WHY] 한 건 삭제는 확인 창 대신 바로 지우고 "되돌리기"를 준다(0.38.3 M2 사용자 결정, ui-ux-pro-max `undo-support`) — 일괄 삭제·복원은 확인 창 유지.
    // 자동 저장 메모를 한 탭으로 되돌릴 수 있어야 하는 C′2 관리 장치는 되돌리기로 계속 성립한다.
    val deleteNote: (KnowledgeNote) -> Unit = { note ->
        viewModel.deleteKnowledge(note.id) {
            knowledgeItems.refresh()
            snackbarScope.launch {
                snackbarHostState.showUndo(
                    message = resources.getString(R.string.memory_note_deleted_snackbar),
                    actionLabel = resources.getString(R.string.common_undo)
                ) { viewModel.undeleteKnowledge(note) { knowledgeItems.refresh() } }
            }
        }
    }
    val deleteTask: (TaskItem) -> Unit = { task ->
        viewModel.deleteTask(task.id) {
            refreshTasks()
            snackbarScope.launch {
                snackbarHostState.showUndo(
                    message = resources.getString(R.string.memory_task_deleted_snackbar),
                    actionLabel = resources.getString(R.string.common_undo)
                ) { viewModel.undeleteTask(task, onDone = refreshTasks) }
            }
        }
    }
    // [WHY] 완료는 한 번의 터치라 실수할 수 있다 — 확인 대화상자 대신 사후 되돌리기를 둔다(0.36.2 사용자 결정).
    val completeWithUndo: (TaskItem) -> Unit = { task ->
        viewModel.completeTask(task.id) {
            refreshTasks()
            snackbarScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.memory_task_completed_snackbar),
                    actionLabel = resources.getString(R.string.common_undo),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(task, onDone = refreshTasks)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 드로어 타일("메모 · 할 일")과 같은 이름 — 진입점과 화면 제목이 다르면 길을 잃는다.
            KosmosTopBar(
                title = stringResource(R.string.memory_title),
                onBack = onBack,
                actions = {
                    BackupMenu(
                        backupState = backupState,
                        onExportClick = { viewModel.requestExport() },
                        onImportClick = { viewModel.requestImport() }
                    )
                }
            )
            KosmosSegmentedControl(
                options = listOf(
                    MemoryFilterType.KNOWLEDGE to stringResource(R.string.memory_tab_notes),
                    MemoryFilterType.TASK to stringResource(R.string.memory_tab_tasks)
                ),
                selected = uiState.selectedFilter,
                onSelect = viewModel::onFilterSelected,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.selectedFilter == MemoryFilterType.TASK) {
                LaunchedEffect(Unit) { viewModel.refreshTaskCounts() }
            }
            val taskCounts = uiState.taskCounts
            if (uiState.selectedFilter == MemoryFilterType.TASK && taskCounts != null) {
                // [WHY] 통계는 DB 전체 기준이다 — 예전에는 페이징 스냅샷(미완료만·로드된 페이지만)에서 세어 "완료 0"이 고정이었다.
                // 가운뎃점 통계 줄·작은 진행 바 대신 섹션 제목(0.38.3 M2 — taste "가운뎃점 정보 줄"); 완료 수는 완료됨 머리에.
                SectionHeader(
                    stringResource(R.string.memory_tasks_pending_section, taskCounts.pending),
                    Modifier.padding(horizontal = 16.dp)
                )
            }

            // Lists
            // [WHY] 목록 끝의 "할 일 추가" 입력이 키보드에 깔렸다 — edge-to-edge 라 창이 줄지 않는다(0.38.3 M6 점검 P1).
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 16.dp),
                // 마지막 카드가 화면 끝·제스처 바에 붙지 않게 두는 스크롤 여유(하단 내비 시절 값을 유지).
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (uiState.selectedFilter == MemoryFilterType.TASK) {
                    // 남은 할 일 + 맨 아래 추가 줄이 한 묶음이다.
                    val pendingRows = taskItems.itemCount + 1
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
                                onToggle = { completeWithUndo(task) },
                                remindAtDisplay = task.remindAtIso?.let {
                                    IsoDateTimeParser.toDisplayKorean(it, lang = lang)
                                },
                                onDelete = { deleteTask(task) },
                                position = groupPosition(index, pendingRows)
                            )
                        }
                    }

                    if (taskItems.itemCount == 0 && taskItems.loadState.refresh is LoadState.NotLoading) {
                        item(key = "tasks-empty") {
                            EmptyState(
                                icon = R.drawable.ic_ph_check_square,
                                message = stringResource(R.string.memory_empty_tasks)
                            )
                        }
                    }
                    item {
                        // 할 일 인라인 추가 — 예전에는 빈 스텁 버튼이었다 (MemoryViewModel.addTask [WHY]).
                        var newTaskText by remember { mutableStateOf("") }
                        fun submitNewTask() {
                            if (newTaskText.isBlank()) return
                            viewModel.addTask(newTaskText) { taskItems.refresh() }
                            newTaskText = ""
                        }
                        // [WHY] 추가 줄은 남은 할 일 묶음의 마지막 줄 — 따로 그린 테두리 상자였다(카드 문법 밖, 0.38.3 M2).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .groupItem(groupPosition(pendingRows - 1, pendingRows))
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KosmosIcon(R.drawable.ic_ph_plus, contentDescription = null, size = IconSize.inline, tint = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
                                BasicTextField(
                                    value = newTaskText,
                                    onValueChange = { newTaskText = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = KosmosTheme.colors.textPrimary
                                    ),
                                    cursorBrush = SolidColor(KosmosTheme.colors.brand),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submitNewTask() }
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (newTaskText.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.memory_task_add_hint),
                                                color = KosmosTheme.colors.textSecondary,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        inner()
                                    }
                                )
                                if (newTaskText.isNotBlank()) {
                                    // [WHY] 글자에 clickable 만 붙이면 누르는 영역이 약 20dp 였다 — 글자 버튼(48dp, 0.38.3 M6 점검 P1).
                                    TextButton(onClick = { submitNewTask() }) {
                                        Text(text = stringResource(R.string.common_add), color = KosmosTheme.colors.brandText, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    val completedCount = uiState.taskCounts?.completed ?: 0
                    if (completedCount > 0) {
                        item(key = "completed-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .clip(KosmosShapes.md)
                                    .clickable { showCompleted = !showCompleted }
                                    // [WHY] "남은 할 일" 섹션 제목·묶음과 시작점을 맞춘다 — 8dp 더 들어가 있었다(0.38.3 M6 점검 P1).
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.memory_tasks_completed_section, completedCount),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KosmosTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(if (showCompleted) R.string.common_collapse else R.string.common_expand),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KosmosTheme.colors.textMuted
                                )
                                KosmosIcon(if (showCompleted) R.drawable.ic_ph_caret_up else R.drawable.ic_ph_caret_down, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.textMuted)
                            }
                        }
                        if (showCompleted) {
                            items(
                                count = completedTaskItems.itemCount,
                                key = completedTaskItems.itemKey { "done-${it.id}" },
                                contentType = completedTaskItems.itemContentType { "Task" }
                            ) { index ->
                                val task = completedTaskItems[index]
                                if (task != null) {
                                    TaskItemRow(
                                        title = task.title,
                                        isCompleted = true,
                                        onToggle = { viewModel.restoreTask(task, onDone = refreshTasks) },
                                        remindAtDisplay = task.remindAtIso?.let {
                                            IsoDateTimeParser.toDisplayKorean(it, lang = lang)
                                        },
                                        onDelete = { deleteTask(task) },
                                        position = groupPosition(index, completedTaskItems.itemCount)
                                    )
                                }
                            }
                            item(key = "completed-clear") {
                                TextButton(onClick = { confirmClearCompleted = true }) {
                                    Text(stringResource(R.string.memory_tasks_clear_completed), color = KosmosTheme.colors.dangerText)
                                }
                            }
                        }
                    }
                }

                if (uiState.selectedFilter == MemoryFilterType.KNOWLEDGE) {
                    if (knowledgeItems.itemCount == 0 && knowledgeItems.loadState.refresh is LoadState.NotLoading) {
                        item(key = "notes-empty") {
                            EmptyState(
                                icon = R.drawable.ic_ph_note,
                                message = stringResource(R.string.memory_empty_notes)
                            )
                        }
                    }
                    // [WHY] 메모마다 카드였다 — 한 묶음 안의 행으로(0.38.3 M2, DESIGN "목록은 행"). 출처 "자동"·태그는 작은 면, 날짜는 글자.
                    items(
                        count = knowledgeItems.itemCount,
                        key = knowledgeItems.itemKey { it.id },
                        contentType = knowledgeItems.itemContentType { "Knowledge" }
                    ) { index ->
                        val note = knowledgeItems[index]
                        if (note != null) {
                            val position = groupPosition(index, knowledgeItems.itemCount)
                            KosmosListRow(
                                title = note.content,
                                supporting = IsoDateTimeParser.monthDayKorean(note.createdAt, lang = lang),
                                tags = listOfNotNull(if (note.source == KnowledgeNote.SOURCE_AUTO) stringResource(R.string.memory_note_auto_tag) else null) + note.tags,
                                divider = position == GroupPosition.First || position == GroupPosition.Middle,
                                modifier = Modifier.groupItem(position)
                            ) {
                                DeleteIconButton(
                                    onClick = { deleteNote(note) },
                                    contentDescription = stringResource(R.string.memory_delete_note_cd, note.content.take(20))
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    BackupDialogs(
        uiState = uiState,
        onConfirmExport = { viewModel.confirmExport() },
        onConfirmImport = { importLauncher.launch("application/zip") },
        onDismiss = { viewModel.dismissBackupState() },
        onRestartApp = { restartApp(context) }
    )
}
