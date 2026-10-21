package com.kosmos.app.feature.calendar

import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.ui.component.kosmosSurface
import androidx.compose.foundation.clickable


@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    // [WHY] 헤더 월·날짜 띠·"오늘" 판정이 같은 today 를 본다 — 화면 복귀마다 갱신(rememberToday).
    val today = com.kosmos.app.ui.component.rememberToday()

    // [WHY] 기기 캘린더 병합 조회(ADR-004)를 위해 화면 진입 시 READ_CALENDAR를 요청한다.
    // 거부 시 로컬 일정만 표시되며, 권한 승인 직후 재조회한다.
    val context = androidx.compose.ui.platform.LocalContext.current
    // 카탈로그 고정 시계와 같은 기준 시각 — 앱에서는 기기 시계다.
    val clock = com.kosmos.app.ui.component.LocalClock.current
    val readCalendarLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadSchedule(now = clock.millis())
            viewModel.showMonth(viewModel.visibleMonth.value, force = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSchedule(now = clock.millis())
        viewModel.showMonth(java.time.YearMonth.from(today))
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            readCalendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        com.kosmos.app.ui.component.KosmosTopBar(title = "일정", onBack = onBack)

        // [WHY] 오늘 / 월 탭(0.38.3 M1 사용자 결정) — 옛 목록 탭의 오늘·이번 주 두 겹 분할 버튼을 "오늘" 탭 하나로 흡수했다.
        com.kosmos.app.ui.component.KosmosSegmentedControl(
            options = listOf(CalendarTab.TODAY to "오늘", CalendarTab.MONTH to "월"),
            selected = tab,
            onSelect = viewModel::onTabSelected,
            // [WHY] 화면 좌우 여백은 16 하나 — 20 이라 아래 묶음(16)과 끝선이 어긋났다(0.38.3 M6 점검 P1).
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val openSettings = {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                )
            )
        }
        when (tab) {
            CalendarTab.MONTH -> {
                val monthState by viewModel.monthState.collectAsStateWithLifecycle()
                if ((monthState as? MonthUiState.Success)?.schedule?.deviceCalendarFailed == true) {
                    DeviceCalendarNotice(
                        onRetry = { viewModel.showMonth(viewModel.visibleMonth.value, force = true) },
                        onOpenSettings = openSettings
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                MonthCalendarTab(viewModel = viewModel, today = today)
            }
            CalendarTab.TODAY -> TodayTab(viewModel = viewModel, today = today, onOpenSettings = openSettings, onRetry = { viewModel.loadSchedule(now = clock.millis()) })
        }
    }
}

/**
 * "오늘" 탭(0.38.3 M1, Things 3 의 오늘 화면 모티브) — 오늘 일정과 오늘 울릴 알림을 시간순으로 한 묶음에, 그 아래 날짜 없는 할 일(체크 원),
 * 그 밑에 "다가오는 일정"(이번 주 남은 날). 알림 줄을 누르면 0.38.2 수정 시트, 체크 원을 누르면 할 일 완료.
 *
 * [WHY] 묶음은 둘(오늘·다가오는 일정)만 — 섹션마다 상자를 두는 안은 상자가 넷이라 taste "상자 더미"에 걸렸다(시안 B, 사용자 선택).
 */
@Composable
private fun TodayTab(viewModel: CalendarViewModel, today: java.time.LocalDate, onOpenSettings: () -> Unit, onRetry: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val pending by viewModel.pendingTasks.collectAsStateWithLifecycle()
    val zone = remember { java.time.ZoneId.systemDefault() }
    val schedule = (uiState as? CalendarUiState.Success)?.scheduleData
    val model = remember(schedule, reminders, pending, today) { TodayComposer.compose(schedule, reminders, pending, today, zone) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("오늘", style = MaterialTheme.typography.titleLarge, color = KosmosTheme.colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    com.kosmos.app.domain.util.IsoDateTimeParser.longDateKorean(today.atStartOfDay(zone).toInstant().toEpochMilli(), zone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KosmosTheme.colors.textSecondary
                )
            }
        }
        // [WHY] 기기 캘린더를 못 읽었을 때 **조용히 넘어가지 않는다** — 앱 일정만 보여 주고 말이 없으면 기기 일정이 없다고 믿는다(PRD EC2·EC4).
        if (model.deviceCalendarFailed) item { DeviceCalendarNotice(onRetry = onRetry, onOpenSettings = onOpenSettings) }
        when (val state = uiState) {
            is CalendarUiState.Idle, is CalendarUiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = KosmosTheme.colors.brand) }
            }
            is CalendarUiState.Error -> item {
                Text(com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error), color = KosmosTheme.colors.dangerText, modifier = Modifier.padding(16.dp))
            }
            is CalendarUiState.Success -> {
                if (model.isEmpty) {
                    item {
                        com.kosmos.app.ui.component.EmptyState(
                            icon = com.kosmos.app.R.drawable.ic_ph_calendar_blank,
                            message = "오늘은 일정도 할 일도 없어요.\n채팅에서 \"내일 3시에 회의 잡아줘\"처럼 말해 보세요."
                        )
                    }
                } else {
                    item {
                        com.kosmos.app.ui.component.KosmosListGroup {
                            val last = model.timeline.size + model.tasks.size - 1
                            model.timeline.forEachIndexed { index, entry ->
                                TodayTimeRow(entry, divider = index < last, onClick = (entry as? TodayEntry.Reminder)?.let { { viewModel.openReminder(it.task.id) } })
                            }
                            model.tasks.forEachIndexed { index, task ->
                                TodayTaskRow(task.title, divider = model.timeline.size + index < last, onComplete = { viewModel.completeTask(task.id) })
                            }
                        }
                    }
                }
                if (model.upcoming.isNotEmpty()) {
                    item { com.kosmos.app.ui.component.SectionHeader("다가오는 일정", Modifier.padding(top = 8.dp)) }
                    item {
                        com.kosmos.app.ui.component.KosmosListGroup {
                            model.upcoming.forEachIndexed { index, entry ->
                                UpcomingRow(entry, divider = index < model.upcoming.lastIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    ReminderEditHost(viewModel = viewModel, reminders = reminders, today = today)
}

/** 오늘 묶음의 일정·알림 한 줄 — 알림 줄은 누르면 수정 시트. */
@Composable
private fun TodayTimeRow(entry: TodayEntry, divider: Boolean, onClick: (() -> Unit)?) {
    val isReminder = entry is TodayEntry.Reminder
    ScheduleRow(
        timeLabel = entry.timeLabel,
        title = entry.title,
        sub = when (entry) {
            is TodayEntry.Reminder -> "알림"
            is TodayEntry.Event -> eventSubLine(entry.endLabel, entry.event.source)
        },
        icon = if (isReminder) com.kosmos.app.R.drawable.ic_ph_bell else com.kosmos.app.R.drawable.ic_ph_calendar_blank,
        accentTime = isReminder,
        divider = divider,
        onClick = onClick
    )
}

/** 일정의 보조 줄 — 끝 시각과 출처. 출처는 색이 아니라 글자로(ui-ux-pro-max `color-not-only`). */
internal fun eventSubLine(endLabel: String?, source: CalendarEvent.Source): String? =
    listOfNotNull(endLabel, if (source == CalendarEvent.Source.DEVICE) "기기 캘린더" else null).joinToString("  ").ifEmpty { null }

/**
 * 일정 화면의 한 줄 — 왼쪽 시각 칸, 제목·보조 줄, 오른쪽 종류 아이콘. "오늘" 탭과 월 탭 선택한 날이 같이 쓴다(0.38.3 M1).
 */
@Composable
internal fun ScheduleRow(
    timeLabel: String,
    title: String,
    sub: String?,
    @androidx.annotation.DrawableRes icon: Int?,
    divider: Boolean,
    accentTime: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = KosmosTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(timeLabel, style = MaterialTheme.typography.titleSmall, color = if (accentTime) colors.brandText else colors.textPrimary, modifier = Modifier.width(TIME_COLUMN))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                sub?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.textMuted) }
            }
            icon?.let { com.kosmos.app.ui.component.KosmosIcon(it, contentDescription = null, size = 18.dp, tint = colors.textMuted) }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = TIME_COLUMN + 16.dp), color = colors.border)
    }
}

/** 날짜 없는 할 일 — 체크 원(48dp 영역)을 누르면 끝낸다. */
@Composable
private fun TodayTaskRow(title: String, divider: Boolean, onComplete: () -> Unit) {
    val colors = KosmosTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete) {
                Box(Modifier.size(22.dp).border(1.5.dp, colors.textMuted, androidx.compose.foundation.shape.CircleShape)
                    .semantics { contentDescription = "$title 완료" })
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 52.dp), color = colors.border)
    }
}

/** 다가오는 일정 한 줄 — "16일 금" · 제목 · 시각. */
@Composable
private fun UpcomingRow(entry: UpcomingEntry, divider: Boolean) {
    val colors = KosmosTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.dayLabel, style = MaterialTheme.typography.titleSmall, color = colors.textSecondary, modifier = Modifier.width(TIME_COLUMN))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(entry.event.title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(entry.timeLabel, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
        if (divider) HorizontalDivider(Modifier.padding(start = TIME_COLUMN + 16.dp), color = colors.border)
    }
}

/** 오늘 탭에서 연 알림의 수정 시트 — 0.38.2 [com.kosmos.app.feature.chat.result.ReminderEditSheet] 그대로. */
@Composable
private fun ReminderEditHost(viewModel: CalendarViewModel, reminders: List<com.kosmos.app.domain.model.TaskItem>, today: java.time.LocalDate) {
    val editing by viewModel.editingReminder.collectAsStateWithLifecycle()
    val error by viewModel.editError.collectAsStateWithLifecycle()
    val id = editing ?: return
    val task = reminders.firstOrNull { it.id == id } ?: return
    val at = task.remindAtIso?.let { com.kosmos.app.domain.util.IsoDateTimeParser.toEpochMillis(it) }
        ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()) }
    com.kosmos.app.feature.chat.result.ReminderEditSheet(
        state = com.kosmos.app.feature.chat.result.ResultCardMapper.reminder(task, today),
        initialHour = at?.hour ?: 9,
        initialMinute = at?.minute ?: 0,
        error = error,
        onSnooze = { viewModel.snooze(id) },
        onReschedule = { h, m -> viewModel.reschedule(id, h, m) },
        onCancelReminder = { viewModel.cancelReminder(id) },
        onDismiss = viewModel::closeReminder
    )
}

/**
 * 기기 캘린더를 읽지 못했을 때의 안내 카드입니다.
 *
 * [WHY] 재시도와 설정 이동을 함께 준다 — 원인이 일시적 Provider 오류일 수도(재시도),
 * 권한 영구 거부일 수도(설정 이동) 있는데 화면에서는 구분되지 않기 때문이다 (PRD EC2 "권한
 * 영구 거부 → 시스템 설정 화면으로 직접 이동 유도", EC4 "Provider 읽기 실패 → 재시도 버튼").
 */
@Composable
private fun DeviceCalendarNotice(
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .kosmosSurface(
                backgroundColor = KosmosTheme.colors.warningText.copy(alpha = 0.15f),
                borderColor = KosmosTheme.colors.warningText.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "기기 캘린더를 읽지 못했어요. 아래 목록에는 이 앱에 저장한 일정만 있습니다.",
            color = KosmosTheme.colors.textPrimary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KosmosButton("다시 시도", onRetry, Modifier.weight(1f))
            KosmosButton("권한 설정 열기", onOpenSettings, Modifier.weight(1f), accent = true)
        }
    }
}

/** 시각·날짜 칸 폭 — "오후 12:00"이 제목에 붙지 않는 폭(0.38.3 M1 렌더). */
private val TIME_COLUMN = 84.dp
