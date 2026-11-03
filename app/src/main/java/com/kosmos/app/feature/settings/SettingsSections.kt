package com.kosmos.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kosmos.app.R
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.ui.component.IconSize
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.KosmosIcon
import com.kosmos.app.ui.component.KosmosListGroup
import com.kosmos.app.ui.component.KosmosListRow
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.modelDisplayName
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage

/**
 * 설정 한 섹션 — 섹션 제목 + 목록 묶음 + 묶음 아래 각주(Apple·Intercom 설정 문법, 0.38.3 M3).
 *
 * @param warning 각주 자리의 경고(권한 거부 등) — 각주보다 먼저, 경고색으로.
 */
@Composable
internal fun Section(
    title: String,
    footnote: String? = null,
    warning: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionHeader(title, Modifier.padding(top = 16.dp))
    KosmosListGroup(content = content)
    warning?.let { Footnote(it, KosmosTheme.colors.warningText) }
    footnote?.let { Footnote(it, KosmosTheme.colors.textMuted) }
}

@Composable
private fun Footnote(text: String, color: Color) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))

/** 묶음 안 세그먼트 위의 작은 이름표. */
@Composable
internal fun ControlLabel(text: String, top: Dp = 0.dp) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(top = top, bottom = 8.dp))

@Composable
internal fun Chevron() = KosmosIcon(
    R.drawable.ic_ph_caret_right, contentDescription = null,
    size = IconSize.inline, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 12.dp)
)

/** 값 행의 끝 — 현재 값 + 화살표. */
@Composable
internal fun ValueTrailing(value: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(value, style = MaterialTheme.typography.bodyMedium, color = KosmosTheme.colors.textSecondary, modifier = Modifier.padding(end = 4.dp))
    Chevron()
}

/** 라벨 + 스위치 한 줄 — 행 전체를 눌러도 바뀐다(누르는 영역). */
@Composable
internal fun SettingSwitchRow(label: String, checked: Boolean, divider: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    // [WHY] 행과 스위치가 따로 포커스를 잡아 화면 읽기가 두 번 읽었다 — 행 하나가 스위치 역할(0.38.3 점검 P3).
    KosmosListRow(
        title = label,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        divider = divider,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 12.dp),
                // [WHY] 꺼짐은 손잡이·테두리를 textMuted 로(흰 묶음 위에서 비텍스트 대비 3:1), 켜짐 손잡이는 면 색(검은 손잡이가 너무 무거웠다) — 0.38.3 M6 점검 P1.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = KosmosTheme.colors.surface,
                    checkedTrackColor = KosmosTheme.colors.brand,
                    checkedBorderColor = KosmosTheme.colors.brand,
                    uncheckedThumbColor = KosmosTheme.colors.textMuted,
                    uncheckedTrackColor = KosmosTheme.colors.surface,
                    uncheckedBorderColor = KosmosTheme.colors.textMuted
                )
            )
        }
    )
}

/**
 * 모델 상태 — 준비됨이면 모델 이름 행(누르면 모델 관리), 자세한 정보는 접어 둔다. 확인 중에도 내용 한가운데 스피너를 두지 않는다(impeccable "spinner in content").
 */
@Composable
internal fun ModelStatus(state: ModelLoadState, onManage: () -> Unit, onRefresh: () -> Unit) {
    val lang = currentAppLanguage()
    when (state) {
        is ModelLoadState.Loading, is ModelLoadState.FileFound, is ModelLoadState.InitializingEngine -> KosmosListRow(
            title = stringResource(R.string.settings_model_local_title),
            supporting = when (state) {
                is ModelLoadState.Loading -> stringResource(R.string.settings_model_checking)
                is ModelLoadState.FileFound -> stringResource(R.string.settings_model_file_found)
                else -> stringResource(R.string.settings_model_preparing)
            },
            tags = listOf(stringResource(R.string.settings_model_tag_preparing))
        )
        is ModelLoadState.Ready -> {
            var detailsOpen by rememberSaveable { mutableStateOf(false) }
            KosmosListRow(
                title = modelDisplayName(state.modelInfo.modelId),
                tags = listOf(stringResource(R.string.settings_model_tag_ready)),
                onClick = onManage,
                divider = true,
                trailing = { Chevron() }
            )
            KosmosListRow(
                title = stringResource(R.string.settings_model_info_title),
                onClick = { detailsOpen = !detailsOpen },
                trailing = {
                    KosmosIcon(
                        if (detailsOpen) R.drawable.ic_ph_caret_up else R.drawable.ic_ph_caret_down,
                        contentDescription = if (detailsOpen) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        size = IconSize.inline, tint = KosmosTheme.colors.textMuted, modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
            if (detailsOpen) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        stringResource(R.string.settings_model_version, state.modelInfo.modelVersion),
                        stringResource(R.string.settings_model_quantization, state.modelInfo.quantization),
                        state.modelInfo.modelPath
                    ).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.textSecondary)
                    }
                }
            }
        }
        is ModelLoadState.NotFound -> {
            KosmosListRow(
                title = stringResource(R.string.settings_model_not_found_title),
                supporting = stringResource(R.string.settings_model_not_found_body)
            )
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KosmosButton(stringResource(R.string.common_refresh), onRefresh, Modifier.weight(1f))
                KosmosButton(stringResource(R.string.common_download), onManage, Modifier.weight(1f), accent = true)
            }
        }
        is ModelLoadState.Error -> KosmosListRow(
            title = stringResource(R.string.settings_model_load_failed_title),
            supporting = ErrorMessages.userMessage(state.error, lang),
            onClick = onManage,
            trailing = { Chevron() }
        )
    }
}

/** 초기화 대상 한 줄 — 화면 문구가 곧 [ResetTarget] 의 범위 설명이다. */
private data class ResetOption(
    val target: ResetTarget,
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int
)

private val RESET_OPTIONS = listOf(
    ResetOption(ResetTarget.CONVERSATIONS, R.string.settings_reset_opt_conversations_title, R.string.settings_reset_opt_conversations_detail),
    ResetOption(ResetTarget.TASKS, R.string.settings_reset_opt_tasks_title, R.string.settings_reset_opt_tasks_detail),
    ResetOption(ResetTarget.KNOWLEDGE, R.string.settings_reset_opt_knowledge_title, R.string.settings_reset_opt_knowledge_detail),
    ResetOption(ResetTarget.PROFILE, R.string.settings_reset_opt_profile_title, R.string.settings_reset_opt_profile_detail),
    ResetOption(ResetTarget.LIBRARY, R.string.settings_reset_opt_library_title, R.string.settings_reset_opt_library_detail)
)

/**
 * 데이터 초기화(0.37.1) — 고른 항목만 지운다. 설정·AI 모델·최근 문서 목록은 남는다.
 *
 * [WHY] 확인 대화상자 안에서 고르게 한다 — 섹션에 체크박스를 늘어놓으면 실수로 누른 체크가 화면에 남아 다음 방문 때도 위험하다.
 */
@Composable
internal fun DataResetSection(
    state: DataResetState,
    onReset: (Set<ResetTarget>) -> Unit,
    onDismiss: () -> Unit,
    onRestart: () -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Section(stringResource(R.string.settings_section_data)) {
        KosmosListRow(
            title = stringResource(R.string.settings_data_reset_title),
            supporting = stringResource(R.string.settings_data_reset_sub),
            onClick = { showPicker = true },
            trailing = { Chevron() }
        )
    }

    if (showPicker && state !is DataResetState.Done) {
        var selected by remember { mutableStateOf(emptySet<ResetTarget>()) }
        val running = state == DataResetState.Running
        AlertDialog(
            onDismissRequest = { if (!running) showPicker = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title), color = KosmosTheme.colors.textPrimary) },
            text = {
                Column {
                    RESET_OPTIONS.forEach { option ->
                        val checked = option.target in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, enabled = !running, role = Role.Checkbox) {
                                    selected = if (checked) selected - option.target else selected + option.target
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                enabled = !running,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = KosmosTheme.colors.dangerText,
                                    uncheckedColor = KosmosTheme.colors.textMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(stringResource(option.titleRes), color = KosmosTheme.colors.textPrimary)
                                Text(stringResource(option.detailRes), style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.textMuted)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_reset_dialog_warning) +
                            if (ResetTarget.CONVERSATIONS in selected) stringResource(R.string.settings_reset_dialog_restart_note) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = KosmosTheme.colors.textSecondary
                    )
                    if (state is DataResetState.Failed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, style = MaterialTheme.typography.bodySmall, color = KosmosTheme.colors.dangerText)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onReset(selected) }, enabled = selected.isNotEmpty() && !running) {
                    Text(
                        if (running) stringResource(R.string.settings_reset_deleting) else stringResource(R.string.settings_reset_delete_action),
                        color = if (selected.isNotEmpty() && !running) KosmosTheme.colors.dangerText else KosmosTheme.colors.textMuted
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false; onDismiss() }, enabled = !running) {
                    Text(stringResource(R.string.common_cancel), color = KosmosTheme.colors.textMuted)
                }
            },
            containerColor = KosmosTheme.colors.surface
        )
    }

    if (state is DataResetState.Done) {
        AlertDialog(
            // [WHY] 재시작이 필요한 완료는 닫을 수 없다 — 낡은 메모리 상태로 계속 쓰면 지운 대화가 엔진 KV 에 남아 답에 섞인다.
            onDismissRequest = { if (!state.needsRestart) { showPicker = false; onDismiss() } },
            title = { Text(stringResource(R.string.settings_reset_done_title), color = KosmosTheme.colors.textPrimary) },
            text = {
                Text(
                    if (state.needsRestart) stringResource(R.string.settings_reset_done_restart_body) else stringResource(R.string.settings_reset_done_body),
                    color = KosmosTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.needsRestart) onRestart() else { showPicker = false; onDismiss() }
                }) {
                    Text(if (state.needsRestart) stringResource(R.string.settings_reset_restart_action) else stringResource(R.string.common_confirm), color = KosmosTheme.colors.brandText)
                }
            },
            containerColor = KosmosTheme.colors.surface
        )
    }
}
