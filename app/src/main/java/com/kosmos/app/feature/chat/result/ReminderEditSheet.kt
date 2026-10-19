package com.kosmos.app.feature.chat.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.ui.component.IconSize
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 알림 카드를 누르면 열리는 수정 시트(0.38.2 M4, 캔버스 C382Sheet) — 10분 미루기·시간 바꾸기·알림 취소·닫기.
 *
 * 취소는 되살리기가 없어(결정 3) 한 번 더 묻는다. 이미 취소·완료한 알림은 상태만 보여 주고 닫기만 둔다.
 *
 * @param initialHour·initialMinute 시간 고르기의 처음 값(지금 알림 시각).
 * @param error 실패 안내(지난 시각 등) — 시트 안에 한 줄로.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditSheet(
    state: ResultCardState,
    initialHour: Int,
    initialMinute: Int,
    error: String?,
    onSnooze: () -> Unit,
    onReschedule: (hour: Int, minute: Int) -> Unit,
    onCancelReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = KosmosTheme.colors
    var picking by remember { mutableStateOf(false) }
    var confirmingCancel by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = KosmosShapes.sheet,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KosmosIcon(state.icon, contentDescription = null, size = IconSize.large, tint = if (state.muted) colors.textMuted else colors.brandText)
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(state.label, state.status).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.muted) colors.textMuted else colors.brandText
                    )
                    Text(state.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                    state.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                }
                state.value?.let { Text(it, style = MaterialTheme.typography.displaySmall, color = colors.textPrimary) }
            }

            if (state.muted) {
                Text(
                    if (state.struck) "취소한 알림이에요. 다시 필요하면 비서에게 새로 부탁해 주세요." else "이미 끝난 알림이에요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                KosmosButton("닫기", onDismiss, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KosmosButton("10분 미루기", onSnooze, Modifier.weight(1f))
                    KosmosButton("시간 바꾸기", { picking = true }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KosmosButton("알림 취소", { confirmingCancel = true }, Modifier.weight(1f), danger = true)
                    KosmosButton("닫기", onDismiss, Modifier.weight(1f))
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.dangerText) }
                Text(
                    "바꾸면 대화에도 한 줄 남아요 — 비서가 바뀐 시각을 알아요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }
        }
    }

    if (picking) {
        val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = false },
            containerColor = colors.surface,
            title = { Text("시간 바꾸기", style = MaterialTheme.typography.titleLarge) },
            text = {
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.surfaceSunk,
                        selectorColor = colors.brand,
                        periodSelectorSelectedContainerColor = colors.brandContainer,
                        timeSelectorSelectedContainerColor = colors.brandContainer
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { picking = false; onReschedule(pickerState.hour, pickerState.minute) }) {
                    Text("바꾸기", color = colors.brandText)
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("그대로 두기", color = colors.textSecondary) } }
        )
    }

    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            containerColor = colors.surface,
            title = { Text("알림을 취소할까요?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("취소한 알림은 되살릴 수 없어요. 할 일 목록에서도 빠져요.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { confirmingCancel = false; onCancelReminder() }) { Text("알림 취소", color = colors.dangerText) }
            },
            dismissButton = { TextButton(onClick = { confirmingCancel = false }) { Text("그대로 두기", color = colors.textSecondary) } }
        )
    }
}

/**
 * [ChatExtrasViewModel] 의 수정 대상이 있을 때만 [ReminderEditSheet] 를 띄운다 — 처음 값은 지금 알림 시각.
 */
@Composable
fun ReminderEditSheetHost(extras: ChatExtrasViewModel, onSaved: (com.kosmos.app.domain.model.ChatMessage) -> Unit) {
    val taskId by extras.editingReminder.collectAsStateWithLifecycle()
    val id = taskId ?: return
    val states by extras.results.collectAsStateWithLifecycle()
    val error by extras.editError.collectAsStateWithLifecycle()
    val state = states[ChatExtrasViewModel.keyOf(com.kosmos.app.domain.model.MessageResult(com.kosmos.app.domain.model.MessageResult.Kind.REMINDER, id))] ?: return
    val (hour, minute) = remember(state.value, state.sub) { extras.currentTime(id) }
    ReminderEditSheet(
        state = state,
        initialHour = hour,
        initialMinute = minute,
        error = error,
        onSnooze = { extras.snooze(id, onSaved) },
        onReschedule = { h, m -> extras.reschedule(id, h, m, onSaved) },
        onCancelReminder = { extras.cancelReminder(id, onSaved) },
        onDismiss = extras::closeReminder
    )
}
