package com.kosmos.app.feature.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.ui.component.DeleteIconButton
import com.kosmos.app.ui.component.GroupPosition
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.groupItem
import com.kosmos.app.ui.component.rememberReducedMotion
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.Motion

/**
 * 상단 바 ⋮ 메뉴의 백업 — 내보내기·가져오기(0.38.3 M2 사용자 결정: 자주 쓰지 않는 기능이라 목록 아래 큰 버튼 두 개 대신 메뉴로).
 *
 * [WHY] `prd.md` F8 은 "메모리 화면에서 export 선택"을 요구한다 — 진입점이 이 화면에 있어야 한다. 아이콘 두 개(↑↓)는 뜻이 모호해 글자 메뉴로.
 */
@Composable
internal fun BackupMenu(
    backupState: BackupState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val busy = backupState is BackupState.Exporting ||
        backupState is BackupState.Importing ||
        backupState is BackupState.ReadyToSave
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            KosmosIcon(
                R.drawable.ic_ph_dots_three_vertical,
                contentDescription = stringResource(R.string.common_more),
                tint = KosmosTheme.colors.textSecondary
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = KosmosTheme.colors.surface) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (backupState is BackupState.Exporting) R.string.memory_menu_exporting else R.string.memory_menu_export
                        )
                    )
                },
                onClick = { open = false; onExportClick() },
                enabled = !busy
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (backupState is BackupState.Importing) R.string.memory_menu_importing else R.string.memory_menu_import
                        )
                    )
                },
                onClick = { open = false; onImportClick() },
                enabled = !busy
            )
        }
    }
}

@Composable
internal fun BackupDialogs(
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
            title = stringResource(R.string.memory_backup_export_notice_title),
            body = stringResource(R.string.memory_backup_export_notice_body),
            confirmText = stringResource(R.string.common_continue),
            onConfirm = onConfirmExport,
            onDismiss = onDismiss
        )
        return
    }

    if (uiState.showImportWarning) {
        ConfirmDialog(
            title = stringResource(R.string.memory_backup_import_warning_title),
            body = stringResource(R.string.memory_backup_import_warning_body),
            confirmText = stringResource(R.string.memory_backup_choose_file),
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
                title = stringResource(R.string.memory_backup_restore_done_title),
                body = stringResource(R.string.memory_backup_restore_done_body),
                confirmText = stringResource(R.string.settings_reset_restart_action),
                onConfirm = onRestartApp,
                onDismiss = {},
                showCancel = false
            )
        }
        is BackupState.Failed -> {
            ConfirmDialog(
                title = stringResource(R.string.memory_backup_failed_title),
                titleColor = KosmosTheme.colors.dangerText,
                body = state.message,
                confirmText = stringResource(R.string.common_close),
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
internal fun ConfirmDialog(
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
                    Text(stringResource(R.string.common_cancel), color = KosmosTheme.colors.textMuted)
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
    position: GroupPosition = GroupPosition.Single
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
                    .toggleable(value = isCompleted, role = Role.Checkbox) { onToggle() },
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
                        enter = if (rememberReducedMotion()) EnterTransition.None
                            else scaleIn(Motion.short(), initialScale = 0.4f) + fadeIn(Motion.short()),
                        exit = ExitTransition.None
                    ) {
                        KosmosIcon(R.drawable.ic_ph_check, contentDescription = null, size = 14.dp, tint = KosmosTheme.colors.successText)
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
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (remindAtDisplay != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KosmosIcon(R.drawable.ic_ph_bell, contentDescription = null, size = 14.dp, tint = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.brandText, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = remindAtDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCompleted) KosmosTheme.colors.textMuted else KosmosTheme.colors.brandText
                        )
                    }
                }
            }

            if (onDelete != null) DeleteIconButton(onClick = onDelete, contentDescription = stringResource(R.string.memory_delete_task_cd, title))
        }
        if (position == GroupPosition.First || position == GroupPosition.Middle) {
            HorizontalDivider(Modifier.padding(start = 56.dp), color = KosmosTheme.colors.border)
        }
    }
}
