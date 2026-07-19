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

    LaunchedEffect(Unit) {
        viewModel.loadSchedule()
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
            Text("July 2026", color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
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



private fun formatIsoString(iso: String): String {
    return try {
        if (iso.contains("T")) {
            val parts = iso.split("T")
            val time = parts[1].substringBeforeLast(":")
            
            // Simple parsing for AM/PM format (assuming HH:mm)
            val hours = time.split(":")[0].toIntOrNull() ?: 0
            val mins = time.split(":")[1]
            val amPm = if (hours >= 12) "PM" else "AM"
            val displayHour = if (hours % 12 == 0) 12 else hours % 12
            "$displayHour:$mins $amPm"
        } else {
            iso
        }
    } catch (e: Exception) {
        iso
    }
}
