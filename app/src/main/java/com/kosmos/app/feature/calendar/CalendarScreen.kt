package com.kosmos.app.feature.calendar

import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.ui.component.glassEffect
import androidx.compose.foundation.clickable


@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    // [WHY] 기기 캘린더 병합 조회(ADR-004)를 위해 화면 진입 시 READ_CALENDAR를 요청한다.
    // 거부 시 로컬 일정만 표시되며, 권한 승인 직후 재조회한다.
    val context = androidx.compose.ui.platform.LocalContext.current
    val readCalendarLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadSchedule()
    }

    LaunchedEffect(Unit) {
        viewModel.loadSchedule()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            readCalendarLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    // [WHY] 불투명 배경을 깔지 않는다 — MemoryScreen 과 동일 (통일 회차).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("일정", style = MaterialTheme.typography.headlineMedium, color = KosmosTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            val headerMonth = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월", java.util.Locale.KOREAN))
            Text(headerMonth, color = KosmosTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        }

        // Range Segment (오늘 / 이번 주)
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .glassEffect(shape = RoundedCornerShape(16.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RangeSegment(
                label = "오늘",
                isSelected = selectedRange == ScheduleData.RangeType.TODAY,
                onClick = { viewModel.onRangeSelected(ScheduleData.RangeType.TODAY) },
                modifier = Modifier.weight(1f)
            )
            RangeSegment(
                label = "이번 주",
                isSelected = selectedRange == ScheduleData.RangeType.WEEK,
                onClick = { viewModel.onRangeSelected(ScheduleData.RangeType.WEEK) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Date Strip — 탭하면 해당 날짜만 필터링, 같은 날짜 재탭 시 해제
        val today = java.time.LocalDate.now()
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val days = (0..6).map { i -> today.plusDays(i.toLong()) }
            items(days.size) { i ->
                val date = days[i]
                DatePill(
                    dayOfWeek = date.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN
                    ),
                    dayOfMonth = date.dayOfMonth.toString(),
                    // [WHY] 주간 탭에서는 필터를 걸었을 때만 강조한다 — 필터 없이 오늘 칸을
                    // 강조하면 "오늘만 보는 중"처럼 읽히는데 목록은 주 전체라 헷갈린다
                    // (2026-08-15 사용자 피드백). 오늘 표시는 점(isFiltered 아님)이 계속 맡는다.
                    isSelected = if (selectedRange == ScheduleData.RangeType.WEEK) {
                        date == selectedDate
                    } else {
                        date == (selectedDate ?: today)
                    },
                    isFiltered = date == selectedDate,
                    isToday = date == today,
                    onClick = { viewModel.onDateSelected(date) }
                )
            }
        }

        // [WHY] 기기 캘린더를 못 읽었을 때 **조용히 넘어가지 않는다.** 예전에는 앱 안의 일정만
        // 보여 주고 아무 말도 하지 않아, 사용자는 기기 일정이 없다고 믿었다. 권한 거부와
        // Provider 오류가 모두 여기로 온다 (PRD EC2·EC4).
        val deviceCalendarFailed = (uiState as? CalendarUiState.Success)?.scheduleData?.deviceCalendarFailed == true
        if (deviceCalendarFailed) {
            DeviceCalendarNotice(
                onRetry = { viewModel.loadSchedule() },
                onOpenSettings = {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            when (val state = uiState) {
                is CalendarUiState.Idle, is CalendarUiState.Loading -> {
                    CircularProgressIndicator(color = KosmosTheme.colors.accent, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Error -> {
                    Text(
                        com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error),
                        color = KosmosTheme.colors.danger,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // [WHY] "비었다"는 판단을 여기서 한다 — 별도 상태로 두었더니 그 상태가
                // `ScheduleData` 를 버려 기기 캘린더 안내가 사라졌다.
                is CalendarUiState.Success -> if (state.scheduleData.events.isEmpty()) {
                    Text(
                        "일정이 없습니다.",
                        color = KosmosTheme.colors.textMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val sectionLabel = when {
                        selectedDate != null && selectedDate == today -> "오늘"
                        selectedDate != null -> selectedDate?.format(
                            java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN)
                        ).orEmpty()
                        selectedRange == ScheduleData.RangeType.WEEK -> "이번 주"
                        else -> "오늘"
                    }
                    ScheduleContent(state.scheduleData, sectionLabel)
                }
            }
        }
    }
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
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.warning.copy(alpha = 0.15f),
                borderColor = KosmosTheme.colors.warning.copy(alpha = 0.3f),
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glassEffect(shape = RoundedCornerShape(12.dp))
                    .clickable { onRetry() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("다시 시도", color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glassEffect(
                        backgroundColor = KosmosTheme.colors.accent.copy(alpha = 0.2f),
                        borderColor = KosmosTheme.colors.accent.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onOpenSettings() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("권한 설정 열기", color = KosmosTheme.colors.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 조회 범위 세그먼트 버튼 (오늘 / 이번 주) */
@Composable
private fun RangeSegment(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DatePill(
    dayOfWeek: String,
    dayOfMonth: String,
    isSelected: Boolean,
    isFiltered: Boolean = false,
    // [WHY] 오늘 점 표시를 강조(isSelected)에서 분리했다 — 주간 탭은 필터 전까지 아무 칸도
    // 강조하지 않지만 오늘이 어디인지는 계속 보여야 한다.
    isToday: Boolean = false,
    onClick: () -> Unit = {}
) {
    // 필터가 걸린 날짜는 테두리를 강조해 "이 날짜만 보고 있음"을 드러낸다
    val bgColor = if (isSelected) KosmosTheme.colors.accent.copy(alpha = 0.1f) else KosmosTheme.colors.glass
    val borderColor = when {
        isFiltered -> KosmosTheme.colors.accent
        isSelected -> KosmosTheme.colors.accent.copy(alpha = 0.5f)
        else -> KosmosTheme.colors.border
    }
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(80.dp)
            .glassEffect(backgroundColor = bgColor, borderColor = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = dayOfWeek, color = if (isSelected) KosmosTheme.colors.accent else KosmosTheme.colors.textMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = dayOfMonth, color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (isSelected || isToday) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.size(4.dp).background(KosmosTheme.colors.accent, androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
}

@Composable
fun ScheduleContent(data: ScheduleData, sectionLabel: String = "오늘") {
    // [WHY] LazyListScope 람다는 @Composable이 아니므로 테마 토큰을 바깥에서 읽어둔다.
    val eventAccents = listOf(KosmosTheme.colors.accent, KosmosTheme.colors.accentAlt, KosmosTheme.colors.warning)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Today section
        item {
            Text(
                text = "$sectionLabel  ·  일정 ${data.events.size}건",
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // [WHY] PRD F4 는 "일정 목록 + 요약 텍스트"를 요구하는데 이 화면은 `summary` 를 읽는
        // 코드가 한 줄도 없었다 — 유스케이스가 10초짜리 추론으로 만든 값을 매번 버리고 있었다
        // (2026-08-12 실기기).
        //
        // [WHY] 요약이 없으면 자리를 만들지 않는다. 요약은 목록 뒤에 도착하므로, 빈 카드를 먼저
        // 그리면 목록이 아래로 밀려 내려가는 레이아웃 점프가 생긴다.
        data.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            item {
                Text(
                    text = summary,
                    color = KosmosTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(shape = RoundedCornerShape(16.dp))
                        .padding(16.dp)
                )
            }
        }

        items(data.events.size) { index ->
            val event = data.events[index]
            val color = eventAccents[index % eventAccents.size]
            TodayEventCard(event, color)
        }

        item { Spacer(modifier = Modifier.height(80.dp)) } // padding for bottom nav
    }
}

@Composable
fun TodayEventCard(event: CalendarEvent, stripColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glass,
                borderColor = KosmosTheme.colors.border,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(stripColor, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).weight(1f)) {
                Text(
                    text = formatIsoString(event.startIso),
                    color = stripColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = event.title, color = KosmosTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}



// [WHY] 문자열 슬라이싱은 오프셋(+09:00)/UTC(Z) 포맷에서 깨지고 시간대 변환도 못 하므로
// java.time 파서로 기기 시간대 기준 표시 시각을 계산한다.
private fun formatIsoString(iso: String): String {
    val zoneId = java.time.ZoneId.systemDefault()
    val localTime: java.time.LocalTime? =
        runCatching { java.time.OffsetDateTime.parse(iso).atZoneSameInstant(zoneId).toLocalTime() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(iso).atZone(zoneId).toLocalTime() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(iso).toLocalTime() }.getOrNull()

    if (localTime == null) return iso

    val hours = localTime.hour
    val mins = "%02d".format(localTime.minute)
    val amPm = if (hours >= 12) "오후" else "오전"
    val displayHour = if (hours % 12 == 0) 12 else hours % 12
    return "$amPm $displayHour:$mins"
}
