package com.kosmos.app.feature.calendar

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

val SkyBlue = com.kosmos.app.ui.theme.Cyan

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()

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
            .background(com.kosmos.app.ui.theme.BgColor)
            .padding(top = 16.dp)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Schedule", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            val headerMonth = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
            Text(headerMonth, color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        }

        // Date Strip
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val today = java.time.LocalDate.now()
            val days = (0..6).map { i ->
                val date = today.plusDays(i.toLong())
                val dayStr = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                Triple(dayStr, date.dayOfMonth.toString(), date == today)
            }
            items(days.size) { i ->
                DatePill(dayOfWeek = days[i].first, dayOfMonth = days[i].second, isSelected = days[i].third) 
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            when (val state = uiState) {
                is CalendarUiState.Idle, is CalendarUiState.Loading -> {
                    CircularProgressIndicator(color = SkyBlue, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Empty -> {
                    Text("일정이 없습니다.", color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Error -> {
                    Text("오류 발생: ${state.error}", color = com.kosmos.app.ui.theme.Danger, modifier = Modifier.align(Alignment.Center))
                }
                is CalendarUiState.Success -> {
                    ScheduleContent(state.scheduleData)
                }
            }
        }
    }
}

@Composable
fun DatePill(dayOfWeek: String, dayOfMonth: String, isSelected: Boolean) {
    val bgColor = if (isSelected) com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.1f) else com.kosmos.app.ui.theme.GlassColor
    val borderColor = if (isSelected) com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.5f) else com.kosmos.app.ui.theme.BorderColor
    Box(
        modifier = Modifier
            .width(60.dp)
            .height(80.dp)
            .glassEffect(backgroundColor = bgColor, borderColor = borderColor, shape = RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = dayOfWeek, color = if (isSelected) com.kosmos.app.ui.theme.Cyan else com.kosmos.app.ui.theme.TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = dayOfMonth, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.size(4.dp).background(com.kosmos.app.ui.theme.Cyan, androidx.compose.foundation.shape.CircleShape))
            }
        }
    }
}

@Composable
fun ScheduleContent(data: ScheduleData) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Today section
        item {
            Text(
                text = "TODAY  ·  ${data.events.size} EVENTS",
                color = com.kosmos.app.ui.theme.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        val colors = listOf(com.kosmos.app.ui.theme.Cyan, com.kosmos.app.ui.theme.Violet, com.kosmos.app.ui.theme.Amber)

        items(data.events.size) { index ->
            val event = data.events[index]
            val color = colors[index % colors.size]
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
                backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                borderColor = com.kosmos.app.ui.theme.BorderColor,
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
                Text(text = event.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
