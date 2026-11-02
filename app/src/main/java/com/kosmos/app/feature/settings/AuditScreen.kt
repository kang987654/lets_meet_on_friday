package com.kosmos.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.kosmos.app.R
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.GroupPosition
import com.kosmos.app.ui.component.groupItem
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    viewModel: AuditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val auditLogItems = viewModel.auditLogPagingData.collectAsLazyPagingItems()
    val lang = currentAppLanguage()

    Scaffold(
        topBar = {
            com.kosmos.app.ui.component.KosmosTopBar(title = stringResource(R.string.audit_title), onBack = onNavigateBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(KosmosTheme.colors.bg)
        ) {
            if (auditLogItems.itemCount == 0) {
                com.kosmos.app.ui.component.EmptyState(
                    R.drawable.ic_ph_shield_check,
                    stringResource(R.string.audit_empty)
                )
            } else {
                val today = com.kosmos.app.ui.component.rememberToday()
                val zone = ZoneId.systemDefault()
                fun dayOf(event: AuditEvent?): LocalDate? = event?.let { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        count = auditLogItems.itemCount,
                        key = auditLogItems.itemKey { it.id },
                        contentType = auditLogItems.itemContentType { "AuditLog" }
                    ) { index ->
                        val event = auditLogItems[index] ?: return@items
                        // [WHY] 페이징 목록이라 날짜 묶음을 이웃 항목과 비교해 그린다 — 머리글은 날짜가 바뀌는 첫 행이 함께 그린다(0.38.3 M3).
                        val day = dayOf(event)
                        val first = index == 0 || dayOf(auditLogItems.peek(index - 1)) != day
                        val last = index == auditLogItems.itemCount - 1 || dayOf(auditLogItems.peek(index + 1)).let { it != null && it != day }
                        val position = when {
                            first && last -> GroupPosition.Single
                            first -> GroupPosition.First
                            last -> GroupPosition.Last
                            else -> GroupPosition.Middle
                        }
                        Column {
                            if (first) {
                                if (index > 0) Spacer(Modifier.height(16.dp))
                                day?.let { com.kosmos.app.ui.component.SectionHeader(IsoDateTimeParser.dayLabelKorean(it, today, lang)) }
                            }
                            AuditEventRow(event, divider = !last, modifier = Modifier.groupItem(position))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 활동 기록 한 행 — 종류 아이콘 · 한국어 이름 · 내용 한 줄 · 시각(0.38.3 M3 시안 A). 누르면 기록 원문을 펼친다.
 *
 * [WHY] 세션 id 는 사용자에게 뜻이 없어 숨긴다 — 원문 펼침에도 넣지 않는다.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AuditEventRow(event: AuditEvent, divider: Boolean, modifier: Modifier = Modifier) {
    val colors = KosmosTheme.colors
    val lang = currentAppLanguage()
    val context = LocalContext.current
    val display = remember(event.id, lang) { AuditLabels.of(event, context) }
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    Column(modifier) {
        com.kosmos.app.ui.component.KosmosListRow(
            title = display.title,
            supporting = display.summary.takeIf { it.isNotEmpty() },
            leadingIcon = display.icon,
            supportingMaxLines = 1,
            onClick = { expanded = !expanded },
            trailing = {
                Text(
                    IsoDateTimeParser.timeKorean(event.timestamp, lang = lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        )
        if (expanded) {
            // [WHY] 펼치면 사람 말로 정리한 항목(물은 말·답·결과…)을 먼저, 기록 원문은 "원문 보기"를 한 번 더 눌러야(0.38.4 M2 결정 5 — AGENTS §4-7 예외).
            var showRaw by rememberSaveable(event.id) { mutableStateOf(false) }
            Column(Modifier.padding(start = 48.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                display.fields.forEach { f ->
                    Column {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(f.value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    }
                }
                if (display.tags.isNotEmpty()) {
                    Column {
                        Text(stringResource(R.string.audit_available_tools), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) { display.tags.forEach { com.kosmos.app.ui.component.Tag(it) } }
                    }
                }
                if (showRaw) {
                    Text(
                        event.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().background(colors.surfaceSunk, com.kosmos.app.ui.theme.KosmosShapes.sm).padding(12.dp)
                    )
                }
            }
            TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(start = 36.dp)) {
                Text(
                    if (showRaw) stringResource(R.string.audit_raw_toggle_hide) else stringResource(R.string.audit_raw_toggle_show),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.brandText
                )
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 48.dp), color = colors.border)
    }
}
