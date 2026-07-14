package com.kosmos.app.feature.calendar

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.model.ScheduleData

val SkyBlue = Color(0xFF5BC2E7)

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
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Segmented Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SegmentButton(
                text = "오늘",
                isSelected = selectedRange == ScheduleData.RangeType.TODAY,
                onClick = { viewModel.loadSchedule(ScheduleData.RangeType.TODAY) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            SegmentButton(
                text = "이번 주",
                isSelected = selectedRange == ScheduleData.RangeType.WEEK,
                onClick = { viewModel.loadSchedule(ScheduleData.RangeType.WEEK) }
            )
        }

        // Content Area
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val state = uiState) {
                is CalendarUiState.Idle, is CalendarUiState.Loading -> {
                    CircularProgressIndicator(color = SkyBlue)
                }
                is CalendarUiState.Empty -> {
                    Text("일정이 없습니다.", color = Color.Gray)
                }
                is CalendarUiState.Error -> {
                    Text("오류 발생: ${state.error}", color = Color.Red)
                }
                is CalendarUiState.Success -> {
                    ScheduleContent(state.scheduleData)
                }
            }
        }
    }
}

@Composable
fun SegmentButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) SkyBlue else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.Gray

    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = bgColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = if (isSelected) SkyBlue else Color.LightGray,
            shape = RoundedCornerShape(18.dp)
        )
    ) {
        Text(text)
    }
}

@Composable
fun ScheduleContent(data: ScheduleData) {
    Column(modifier = Modifier.fillMaxSize()) {
        val summary = data.summary
        if (!summary.isNullOrBlank()) {
            Surface(
                color = SkyBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(16.dp),
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(data.events) { event ->
                EventCard(event)
            }
        }
    }
}

@Composable
fun EventCard(event: CalendarEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(text = event.title, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatIsoString(event.startIso),
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        if (!event.location.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "📍 ${event.location}", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
        }
        val description = event.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatIsoString(iso: String): String {
    return try {
        if (iso.contains("T")) {
            val parts = iso.split("T")
            val date = parts[0]
            val time = parts[1].substringBeforeLast(":") // 초(second) 제거
            "$date $time"
        } else {
            iso
        }
    } catch (e: Exception) {
        iso
    }
}
