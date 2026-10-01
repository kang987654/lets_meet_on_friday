package com.kosmos.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.domain.model.CalendarEvent
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.domain.util.KoreanHolidays
import com.kosmos.app.ui.calendar.eventsByDate
import com.kosmos.app.ui.calendar.monthGrid
import com.kosmos.app.ui.theme.KosmosTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// [WHY] 페이저는 "기준 달 ± N" 의 유한 페이지다 — 무한 페이저 API 가 없어 큰 수로 대신한다. 100년이면 충분하다.
private const val MONTH_PAGE_RADIUS = 1200
private const val WEEK_ROWS_MAX = 6
private val CELL_HEIGHT = 48.dp

/**
 * 월 탭 — 년월 헤더(‹ ›, 좌우 스와이프), 일요일 시작 그리드(일정 점), 선택한 날의 일정 목록 (0.28.0).
 *
 * [WHY] 그리드 높이를 6주로 고정한다 — 달마다 4~6주라 높이가 바뀌면 스와이프할 때 아래 목록이 출렁인다.
 */
@Composable
fun MonthCalendarTab(
    viewModel: CalendarViewModel,
    today: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val monthState by viewModel.monthState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.monthSelectedDate.collectAsStateWithLifecycle()
    val baseMonth = remember { YearMonth.from(today) }
    val pagerState = rememberPagerState(
        initialPage = MONTH_PAGE_RADIUS + monthsBetween(baseMonth, visibleMonth),
        pageCount = { MONTH_PAGE_RADIUS * 2 + 1 }
    )
    val scope = rememberCoroutineScope()

    // [WHY] 두 방향 동기화 — 스와이프가 끝난 페이지를 뷰모델에 알리고, 뷰모델이 달을 바꾸면(이웃 달 칸 탭) 페이저를 옮긴다.
    // showMonth 가 같은 달이면 아무것도 안 하므로 서로를 부르는 루프가 생기지 않는다.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.showMonth(baseMonth.plusMonths((page - MONTH_PAGE_RADIUS).toLong()))
        }
    }
    LaunchedEffect(visibleMonth) {
        val target = MONTH_PAGE_RADIUS + monthsBetween(baseMonth, visibleMonth)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    val loaded = (monthState as? MonthUiState.Success)?.schedule?.takeIf { it.month == visibleMonth }
    val byDate = remember(loaded) { loaded?.let { eventsByDate(it.events, zoneId) } ?: emptyMap() }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = KosmosTheme.colors.textSecondary)
            }
            Text(
                text = "${visibleMonth.year}년 ${visibleMonth.monthValue}월",
                color = KosmosTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            if (visibleMonth != YearMonth.from(today)) {
                Text(
                    text = "오늘",
                    color = KosmosTheme.colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { viewModel.selectMonthDate(today) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = KosmosTheme.colors.textSecondary)
            }
        }

        WeekdayHeader()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT * WEEK_ROWS_MAX)
        ) { page ->
            val month = baseMonth.plusMonths((page - MONTH_PAGE_RADIUS).toLong())
            MonthGridPage(
                month = month,
                today = today,
                selectedDate = selectedDate,
                // [WHY] 조회 결과가 이 페이지의 달일 때만 점을 그린다 — 스와이프 중 옆 페이지에 이전 달 점이 비치지 않게.
                eventsByDate = if (month == visibleMonth) byDate else emptyMap(),
                onSelect = viewModel::selectMonthDate
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        selectedDate?.let { date ->
            SelectedDayEvents(
                date = date,
                events = byDate[date].orEmpty(),
                zoneId = zoneId
            )
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { day ->
            Text(
                text = IsoDateTimeParser.weekdayKorean(day),
                color = weekdayColor(day, inMonth = true),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MonthGridPage(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit
) {
    val weeks = remember(month) { monthGrid(month) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT)) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        events = eventsByDate[date].orEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KosmosTheme.colors
    val dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일" +
        (KoreanHolidays.nameOf(date)?.let { ", $it" } ?: "") +
        if (events.isNotEmpty()) ", 일정 ${events.size}건" else ""
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = dateLabel }
            .testTag("month_cell_$date")
            .padding(top = 4.dp)
    ) {
        val circle = Modifier.size(32.dp).let {
            when {
                isSelected -> it.background(colors.accent, CircleShape)
                isToday -> it.border(1.5.dp, colors.accent, CircleShape)
                else -> it
            }
        }
        Box(modifier = circle, contentAlignment = Alignment.Center) {
            Text(
                text = date.dayOfMonth.toString(),
                color = when {
                    isSelected -> colors.onAccent
                    isToday -> colors.accent
                    else -> weekdayColor(date.dayOfWeek, inMonth, holiday = KoreanHolidays.nameOf(date) != null)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
            events.take(3).forEach { event ->
                Box(modifier = Modifier.size(5.dp).background(sourceColor(event.source), CircleShape))
            }
        }
    }
}

@Composable
private fun SelectedDayEvents(date: LocalDate, events: List<CalendarEvent>, zoneId: ZoneId) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            text = IsoDateTimeParser.longDateKorean(date.atStartOfDay(zoneId).toInstant().toEpochMilli(), zoneId),
            color = KosmosTheme.colors.textPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = if (KoreanHolidays.nameOf(date) != null) 4.dp else 12.dp)
        )
        // [WHY] 공휴일 이름 한 줄(사용자 결정 D-B2) — 빨간 날짜만으로는 무슨 날인지 모른다.
        KoreanHolidays.nameOf(date)?.let { holiday ->
            Text(holiday, color = KosmosTheme.colors.danger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 12.dp))
        }
        if (events.isEmpty()) {
            Text("일정이 없어요.", color = KosmosTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                events.forEach { event -> TodayEventCard(event, sourceColor(event.source)) }
            }
        }
    }
}

/** 일정 출처별 점·띠 색 — 앱 일정은 accent, 기기 캘린더는 accentAlt (사용자 결정 D2). */
@Composable
internal fun sourceColor(source: CalendarEvent.Source): Color = when (source) {
    CalendarEvent.Source.APP -> KosmosTheme.colors.accent
    CalendarEvent.Source.DEVICE -> KosmosTheme.colors.accentAlt
}

/** 일요일·공휴일 빨강(0.33.0 내장 표), 토요일 accent, 이웃 달 날짜는 흐리게. */
@Composable
private fun weekdayColor(day: DayOfWeek, inMonth: Boolean, holiday: Boolean = false): Color {
    val colors = KosmosTheme.colors
    val base = when {
        holiday -> colors.danger
        else -> when (day) {
            DayOfWeek.SUNDAY -> colors.danger
            DayOfWeek.SATURDAY -> colors.accent
            else -> colors.textPrimary
        }
    }
    return if (inMonth) base else base.copy(alpha = 0.35f)
}

private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    ((to.year - from.year) * 12 + (to.monthValue - from.monthValue))
