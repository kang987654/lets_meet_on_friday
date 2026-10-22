package com.kosmos.app.feature.memory

import androidx.compose.foundation.selection.toggleable
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.component.groupItem
import com.kosmos.app.ui.component.showUndo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kosmos.app.ui.component.kosmosSurface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.kosmos.app.ui.component.KosmosSegmentedControl
import com.kosmos.app.ui.restartApp

@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    // [WHY] 채팅 요약 타일(다음 알림·남은 할 일)에서 들어오면 할 일 탭으로 연다(0.38.2 결정 2). 처음 한 번만.
    initialFilter: MemoryFilterType? = null
) {
    if (initialFilter != null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.onFilterSelected(initialFilter) }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knowledgeItems = viewModel.knowledgePagingData.collectAsLazyPagingItems()
    val taskItems = viewModel.taskPagingData.collectAsLazyPagingItems()
    val completedTaskItems = viewModel.completedTaskPagingData.collectAsLazyPagingItems()
    var confirmClearCompleted by remember { mutableStateOf(false) }
    var showCompleted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val refreshTasks: () -> Unit = {
        taskItems.refresh()
        completedTaskItems.refresh()
    }

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
            title = "완료한 할 일을 모두 지울까요?",
            body = "완료됨 목록의 항목이 모두 지워지고 되돌릴 수 없어요.",
            confirmText = "모두 지우기",
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
    val deleteNote: (com.kosmos.app.domain.model.KnowledgeNote) -> Unit = { note ->
        viewModel.deleteKnowledge(note.id) {
            knowledgeItems.refresh()
            snackbarScope.launch {
                snackbarHostState.showUndo("메모를 지웠어요.") { viewModel.undeleteKnowledge(note) { knowledgeItems.refresh() } }
            }
        }
    }
    val deleteTask: (com.kosmos.app.domain.model.TaskItem) -> Unit = { task ->
        viewModel.deleteTask(task.id) {
            refreshTasks()
            snackbarScope.launch {
                snackbarHostState.showUndo("할 일을 지웠어요.") { viewModel.undeleteTask(task, onDone = refreshTasks) }
            }
        }
    }
    // [WHY] 완료는 한 번의 터치라 실수할 수 있다 — 확인 대화상자 대신 사후 되돌리기를 둔다(0.36.2 사용자 결정).
    val completeWithUndo: (com.kosmos.app.domain.model.TaskItem) -> Unit = { task ->
        viewModel.completeTask(task.id) {
            refreshTasks()
            snackbarScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "완료했어요",
                    actionLabel = "되돌리기",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(task, onDone = refreshTasks)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 드로어 타일("메모 · 할 일")과 같은 이름 — 진입점과 화면 제목이 다르면 길을 잃는다.
            com.kosmos.app.ui.component.KosmosTopBar(
                title = "메모·할 일",
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
                options = listOf(MemoryFilterType.KNOWLEDGE to "메모", MemoryFilterType.TASK to "할 일"),
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
                com.kosmos.app.ui.component.SectionHeader("남은 할 일 ${taskCounts.pending}", Modifier.padding(horizontal = 16.dp))
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
                                    com.kosmos.app.domain.util.IsoDateTimeParser.toDisplayKorean(it)
                                },
                                onDelete = { deleteTask(task) },
                                position = com.kosmos.app.ui.component.groupPosition(index, pendingRows)
                            )
                        }
                    }

                    if (taskItems.itemCount == 0 && taskItems.loadState.refresh is androidx.paging.LoadState.NotLoading) {
                        item(key = "tasks-empty") {
                            com.kosmos.app.ui.component.EmptyState(
                                icon = com.kosmos.app.R.drawable.ic_ph_check_square,
                                message = "남은 할 일이 없어요.\n아래에 적거나 채팅에서 \"장보기 할 일 추가해 줘\"라고 말해 보세요."
                            )
                        }
                    }
                    item {
                        // 할 일 인라인 추가 — 예전에는 빈 스텁 버튼이었다 (MemoryViewModel.addTask [WHY]).
                        var newTaskText by androidx.compose.runtime.remember {
                            mutableStateOf("")
                        }
                        fun submitNewTask() {
                            if (newTaskText.isBlank()) return
                            viewModel.addTask(newTaskText) { taskItems.refresh() }
                            newTaskText = ""
                        }
                        // [WHY] 추가 줄은 남은 할 일 묶음의 마지막 줄 — 따로 그린 테두리 상자였다(카드 문법 밖, 0.38.3 M2).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .groupItem(com.kosmos.app.ui.component.groupPosition(pendingRows - 1, pendingRows))
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_plus, contentDescription = null, size = com.kosmos.app.ui.component.IconSize.inline, tint = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = newTaskText,
                                    onValueChange = { newTaskText = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = KosmosTheme.colors.textPrimary
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(KosmosTheme.colors.brand),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onDone = { submitNewTask() }
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (newTaskText.isEmpty()) {
                                            Text(
                                                text = "할 일 추가…",
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
                                        Text(text = "추가", color = KosmosTheme.colors.brandText, style = MaterialTheme.typography.labelLarge)
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showCompleted = !showCompleted }
                                    // [WHY] "남은 할 일" 섹션 제목·묶음과 시작점을 맞춘다 — 8dp 더 들어가 있었다(0.38.3 M6 점검 P1).
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "완료됨 $completedCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KosmosTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (showCompleted) "접기" else "펼치기",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KosmosTheme.colors.textMuted
                                )
                                com.kosmos.app.ui.component.KosmosIcon(if (showCompleted) com.kosmos.app.R.drawable.ic_ph_caret_up else com.kosmos.app.R.drawable.ic_ph_caret_down, contentDescription = null, size = 16.dp, tint = KosmosTheme.colors.textMuted)
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
                                            com.kosmos.app.domain.util.IsoDateTimeParser.toDisplayKorean(it)
                                        },
                                        onDelete = { deleteTask(task) },
                                        position = com.kosmos.app.ui.component.groupPosition(index, completedTaskItems.itemCount)
                                    )
                                }
                            }
                            item(key = "completed-clear") {
                                TextButton(onClick = { confirmClearCompleted = true }) {
                                    Text("완료 항목 모두 지우기", color = KosmosTheme.colors.dangerText)
                                }
                            }
                        }
                    }
                }

                if (uiState.selectedFilter == MemoryFilterType.KNOWLEDGE) {
                    if (knowledgeItems.itemCount == 0 && knowledgeItems.loadState.refresh is androidx.paging.LoadState.NotLoading) {
                        item(key = "notes-empty") {
                            com.kosmos.app.ui.component.EmptyState(
                                icon = com.kosmos.app.R.drawable.ic_ph_note,
                                message = "아직 저장한 메모가 없어요.\n채팅에서 \"기억해 줘\"라고 말하면 여기 쌓여요."
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
                            val position = com.kosmos.app.ui.component.groupPosition(index, knowledgeItems.itemCount)
                            com.kosmos.app.ui.component.KosmosListRow(
                                title = note.content,
                                supporting = com.kosmos.app.domain.util.IsoDateTimeParser.monthDayKorean(note.createdAt),
                                tags = listOfNotNull(if (note.source == com.kosmos.app.domain.model.KnowledgeNote.SOURCE_AUTO) "자동" else null) + note.tags,
                                divider = position == com.kosmos.app.ui.component.GroupPosition.First || position == com.kosmos.app.ui.component.GroupPosition.Middle,
                                modifier = Modifier.groupItem(position)
                            ) {
                                com.kosmos.app.ui.component.DeleteIconButton(onClick = { deleteNote(note) }, contentDescription = "${note.content.take(20)} 메모 지우기")
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

/**
 * 상단 바 ⋮ 메뉴의 백업 — 내보내기·가져오기(0.38.3 M2 사용자 결정: 자주 쓰지 않는 기능이라 목록 아래 큰 버튼 두 개 대신 메뉴로).
 *
 * [WHY] `prd.md` F8 은 "메모리 화면에서 export 선택"을 요구한다 — 진입점이 이 화면에 있어야 한다. 아이콘 두 개(↑↓)는 뜻이 모호해 글자 메뉴로.
 */
@Composable
private fun BackupMenu(
    backupState: BackupState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val busy = backupState is BackupState.Exporting ||
        backupState is BackupState.Importing ||
        backupState is BackupState.ReadyToSave
    var open by remember { mutableStateOf(false) }
    Box {
        androidx.compose.material3.IconButton(onClick = { open = true }) {
            com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_dots_three_vertical, contentDescription = "더보기", tint = KosmosTheme.colors.textSecondary)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = KosmosTheme.colors.surface) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (backupState is BackupState.Exporting) "백업 내보내는 중…" else "백업 내보내기") },
                onClick = { open = false; onExportClick() },
                enabled = !busy
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (backupState is BackupState.Importing) "백업 복원 중…" else "백업 가져오기") },
                onClick = { open = false; onImportClick() },
                enabled = !busy
            )
        }
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
        ConfirmDialog(
            title = "백업에 개인정보가 들어 있어요",
            body = "내보낼 파일에는 대화 내용, 메모, 프로필, 활동 기록이 " +
                "암호화 없이 그대로 담겨요. 믿을 수 있는 곳에만 저장하세요.",
            confirmText = "계속",
            onConfirm = onConfirmExport,
            onDismiss = onDismiss
        )
        return
    }

    if (uiState.showImportWarning) {
        ConfirmDialog(
            title = "지금 데이터를 덮어써요",
            body = "복원하면 지금 기기의 대화, 메모, 일정이 백업 파일의 내용으로 " +
                "모두 바뀌고 되돌릴 수 없어요. 계속할까요?",
            confirmText = "파일 고르기",
            confirmColor = KosmosTheme.colors.dangerText,
            onConfirm = onConfirmImport,
            onDismiss = onDismiss
        )
        return
    }

    when (val state = uiState.backup) {
        is BackupState.ImportSucceeded -> {
            // [WHY] DB가 이미 교체됐으므로 닫을 수 없는 다이얼로그다. 재시작만이 유효한 출구다.
            // 자동 타이머로 프로세스를 죽이던 기존 방식과 달리, 사용자가 누른 시점에만 죽인다.
            ConfirmDialog(
                title = "복원 완료",
                body = "복원한 데이터를 쓰려면 앱을 다시 시작해야 해요.",
                confirmText = "다시 시작",
                onConfirm = onRestartApp,
                onDismiss = {},
                showCancel = false
            )
        }
        is BackupState.Failed -> {
            ConfirmDialog(
                title = "백업 처리 실패",
                titleColor = KosmosTheme.colors.dangerText,
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

/** 확인 대화상자 — 백업·복원·완료 항목 일괄 삭제가 같은 모양을 쓴다(한 건 삭제는 되돌리기, 0.38.3 M2). */
@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    titleColor: Color = KosmosTheme.colors.textPrimary,
    confirmColor: Color = KosmosTheme.colors.brandText,
    showCancel: Boolean = true,
    bodyMaxLines: Int = Int.MAX_VALUE
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = titleColor) },
        text = { Text(body, maxLines = bodyMaxLines) },
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
 * 할 일 한 줄 — 체크 동그라미만 완료를 바꾼다.
 *
 * [WHY] 행 전체가 눌림 영역이면 스크롤하다 스치기만 해도 완료됐다(2026-10-13 사용자 피드백). 동그라미의 눌림 영역은
 * 48dp 로 두어 겨냥하기는 쉽게 한다.
 */
@Composable
fun TaskItemRow(
    title: String,
    isCompleted: Boolean,
    onToggle: () -> Unit,
    // [WHY] 기본값 필수 — 기존 호출부·테스트가 이 파라미터 없이 compose 된다 (B1).
    remindAtDisplay: String? = null,
    onDelete: (() -> Unit)? = null,
    // [WHY] 묶음 안의 자리(0.38.3 M2) — 행마다 카드였던 것을 묶음 한 조각으로. 기본값은 혼자 선 묶음.
    position: com.kosmos.app.ui.component.GroupPosition = com.kosmos.app.ui.component.GroupPosition.Single
) {
    Column(modifier = Modifier.fillMaxWidth().groupItem(position)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    // [WHY] 완료 원은 체크 상자다 — 일반 버튼으로 읽혔다(0.38.3 점검 P2). 설명은 행 제목이 맡는다.
                    .toggleable(value = isCompleted, role = androidx.compose.ui.semantics.Role.Checkbox) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = 1.dp,
                            color = if (isCompleted) KosmosTheme.colors.successText else KosmosTheme.colors.textMuted,
                            shape = CircleShape
                        )
                        .background(
                            color = if (isCompleted) KosmosTheme.colors.successText.copy(alpha = 0.1f) else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // [WHY] 체크가 작게 커지며 나타난다(0.38.4 M4 — 결과가 생길 때만 움직임). 애니메이션 제거면 바로.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCompleted,
                        enter = if (com.kosmos.app.ui.component.rememberReducedMotion()) androidx.compose.animation.EnterTransition.None
                            else androidx.compose.animation.scaleIn(com.kosmos.app.ui.theme.Motion.short(), initialScale = 0.4f) + androidx.compose.animation.fadeIn(com.kosmos.app.ui.theme.Motion.short()),
                        exit = androidx.compose.animation.ExitTransition.None
                    ) {
                        com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_check, contentDescription = null, size = 14.dp, tint = KosmosTheme.colors.successText)
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.textPrimary,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                )
                if (remindAtDisplay != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.kosmos.app.ui.component.KosmosIcon(com.kosmos.app.R.drawable.ic_ph_bell, contentDescription = null, size = 14.dp, tint = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.brandText, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = remindAtDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.brandText
                        )
                    }
                }
            }

            if (onDelete != null) com.kosmos.app.ui.component.DeleteIconButton(onClick = onDelete, contentDescription = "$title 지우기")
        }
        if (position == com.kosmos.app.ui.component.GroupPosition.First || position == com.kosmos.app.ui.component.GroupPosition.Middle) {
            androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 56.dp), color = KosmosTheme.colors.border)
        }
    }
}
