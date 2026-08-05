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
import androidx.hilt.navigation.compose.hiltViewModel
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KosmosTheme.colors.bg)
            .padding(top = 16.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Schedule", style = MaterialTheme.typography.headlineMedium, color = KosmosTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            val headerMonth = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
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
                    dayOfWeek = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    dayOfMonth = date.dayOfMonth.toString(),
                    isSelected = date == (selectedDate ?: today),
                    isFiltered = date == selectedDate,
                    onClick = { viewModel.onDateSelected(date) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            when (val state = uiState) {
                is CalendarUiState.Idle, is CalendarUiState.Loading -> {
                    CircularProgressIndicator(color = KosmosTheme.colors.accent, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Empty -> {
                    Text("일정이 없습니다.", color = KosmosTheme.colors.textMuted, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Error -> {
                    Text(
                        com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error),
                        color = KosmosTheme.colors.danger,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is CalendarUiState.Success -> {
                    val sectionLabel = when {
                        selectedDate != null && selectedDate == today -> "TODAY"
                        selectedDate != null -> selectedDate?.format(
                            java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN)
                        ).orEmpty()
                        selectedRange == ScheduleData.RangeType.WEEK -> "THIS WEEK"
                        else -> "TODAY"
                    }
                    ScheduleContent(state.scheduleData, sectionLabel)
                }
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
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.size(4.dp).background(KosmosTheme.colors.accent, androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
}

@Composable
fun ScheduleContent(data: ScheduleData, sectionLabel: String = "TODAY") {
    // [WHY] LazyListScope 람다는 @Composable이 아니므로 테마 토큰을 바깥에서 읽어둔다.
    val eventAccents = listOf(KosmosTheme.colors.accent, KosmosTheme.colors.accentAlt, KosmosTheme.colors.warning)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Today section
        item {
            Text(
                text = "$sectionLabel  ·  ${data.events.size} EVENTS",
                color = KosmosTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
    val amPm = if (hours >= 12) "PM" else "AM"
    val displayHour = if (hours % 12 == 0) 12 else hours % 12
    return "$displayHour:$mins $amPm"
}
